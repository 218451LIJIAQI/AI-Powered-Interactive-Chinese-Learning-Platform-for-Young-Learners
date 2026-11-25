import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { adminAiQuizDraft as quizDraft } from '@/test/lessonEditorFixtures'
import { deferred } from '@/test/helpers'
import type { AiQuizGeneratorResponse } from '@/types/api'

const api = vi.hoisted(() => ({ availability: vi.fn(), draft: vi.fn() }))

vi.mock('@/api/adminClasses', () => ({ adminQuizDraftApi: api }))

import AdminQuizDraftPanel from './AdminQuizDraftPanel.vue'

function mountPanel(props: { remaining?: number; disabled?: boolean } = {}) {
  return mount(AdminQuizDraftPanel, {
    props: { lessonId: 11, remaining: 5, countHintId: 'count-hint', ...props },
  })
}

type Panel = ReturnType<typeof mountPanel>

const generate = (wrapper: Panel) => wrapper.get<HTMLButtonElement>('[data-testid="quiz-draft-generate"]')
const countHint = (wrapper: Panel) => wrapper.get('[data-testid="quiz-draft-count-hint"]')

async function askFor(wrapper: Panel, topic: string): Promise<void> {
  await wrapper.get('[data-testid="quiz-draft-topic"]').setValue(topic)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('AdminQuizDraftPanel', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.availability.mockResolvedValue({ available: true })
  })

  it('drafts the questions the quiz still needs, for this lesson, and hands them over unsaved', async () => {
    api.draft.mockResolvedValue(quizDraft)
    const wrapper = mountPanel({ remaining: 2 })
    await flushPromises()

    expect(wrapper.get('h2').text()).toBe('AI-assisted draft')
    expect(countHint(wrapper).text()).toBe('Up to 2: a lesson quiz has exactly 5 questions.')
    expect(countHint(wrapper).attributes('id')).toBe('count-hint')
    expect(wrapper.get<HTMLInputElement>('[data-testid="quiz-draft-count"]').element.value).toBe('2')
    expect(wrapper.text()).toContain('AI drafts may contain errors. Check every question.')

    await askFor(wrapper, '  places on campus ')

    expect(api.draft).toHaveBeenCalledWith({
      lessonId: 11,
      topic: 'places on campus',
      level: 'HSK 1',
      numberOfQuestions: 2,
      languagePreference: 'en',
    })
    expect(wrapper.emitted('quiz-draft')).toEqual([[quizDraft]])
  })

  it('never asks for more questions than the quiz has room for', async () => {
    api.draft.mockResolvedValue(quizDraft)
    const wrapper = mountPanel({ remaining: 1 })
    await flushPromises()

    await wrapper.get('[data-testid="quiz-draft-count"]').setValue('5')
    await askFor(wrapper, 'Food')

    expect(api.draft).toHaveBeenCalledWith(expect.objectContaining({ numberOfQuestions: 1 }))
  })

  it('stops drafting when the quiz is full and says why', async () => {
    const wrapper = mountPanel({ remaining: 0 })
    await flushPromises()

    expect(countHint(wrapper).text()).toBe(
      'Quiz is full. A lesson quiz has exactly 5 questions; delete one to add or generate another.',
    )
    expect(generate(wrapper).element.disabled).toBe(true)
    expect(wrapper.get<HTMLInputElement>('[data-testid="quiz-draft-topic"]').element.disabled).toBe(true)

    // A question deleted: the room comes back, and the count takes all of it.
    await wrapper.setProps({ remaining: 1 })
    expect(generate(wrapper).element.disabled).toBe(false)
    expect(wrapper.get<HTMLInputElement>('[data-testid="quiz-draft-count"]').element.value).toBe('1')
  })

  it('asks for a topic when the field is left empty or the draft is asked for without one', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="quiz-draft-topic"]').trigger('blur')
    expect(wrapper.get('[role="alert"]').text()).toBe('Add a topic for the questions.')
    await wrapper.get('form').trigger('submit')
    expect(api.draft).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="quiz-draft-topic"]').setValue('Food')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows the timed "Generating…" state and tells the quiz while it works', async () => {
    const pending = deferred<AiQuizGeneratorResponse>()
    api.draft.mockReturnValue(pending.promise)
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="quiz-draft-topic"]').setValue('Food')
    await wrapper.get('form').trigger('submit')

    expect(generate(wrapper).text()).toBe('Generating…')
    expect(generate(wrapper).element.disabled).toBe(true)
    expect(wrapper.emitted('generating')).toEqual([[true]])
    pending.resolve(quizDraft)
    await flushPromises()
    expect(generate(wrapper).text()).toBe('Generate draft')
    expect(wrapper.emitted('generating')).toEqual([[true], [false]])
  })

  it('says AI drafts are off when the AI is not available, and keeps Generate off', async () => {
    api.availability.mockResolvedValue({ available: false })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('AI drafts are not available right now.')
    expect(generate(wrapper).element.disabled).toBe(true)
  })

  it('checks again when a draft comes back unavailable, and says when one failed', async () => {
    api.draft.mockResolvedValueOnce({ ...quizDraft, status: 'FAILED', questions: [] })
    const wrapper = mountPanel()
    await flushPromises()

    await askFor(wrapper, 'Food')
    expect(wrapper.get('[role="alert"]').text()).toBe('Couldn’t create a draft. Try again, or add questions yourself.')
    expect(wrapper.emitted('quiz-draft')).toBeUndefined()

    api.draft.mockResolvedValueOnce({ ...quizDraft, status: 'UNAVAILABLE', questions: [] })
    api.availability.mockResolvedValue({ available: false })
    await askFor(wrapper, 'Food')
    expect(api.availability).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="status"]').text()).toContain('AI drafts are not available right now.')
  })

  it('shows the server’s reason when the draft is refused', async () => {
    const reason = 'A lesson quiz has exactly 5 questions. Delete one to add another.'
    api.draft.mockRejectedValue({
      response: { status: 400, data: { message: reason } },
    })
    const wrapper = mountPanel()
    await flushPromises()

    await askFor(wrapper, 'Food')
    expect(wrapper.get('[role="alert"]').text()).toBe(reason)
    expect(wrapper.emitted('quiz-draft')).toBeUndefined()
  })
})
