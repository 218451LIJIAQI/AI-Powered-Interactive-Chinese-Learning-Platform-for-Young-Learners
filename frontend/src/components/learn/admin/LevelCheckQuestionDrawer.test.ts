import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { buttonNamed, deferred } from '@/test/helpers'
import type { LevelCheckQuestionDetail } from '@/types/learn'
import { localizeServerMessage } from '@/utils/serverMessages'

const api = vi.hoisted(() => ({ question: vi.fn(), saveQuestion: vi.fn() }))
vi.mock('@/api/learn', () => ({ adminLearningApi: api }))

import LevelCheckQuestionDrawer from './LevelCheckQuestionDrawer.vue'

const question: LevelCheckQuestionDetail = {
  id: 1,
  prompt: 'Which means hello?',
  hanzi: null,
  pinyin: null,
  audioText: null,
  skill: 'VOCABULARY',
  difficulty: 'EASY',
  level: 'BEGINNER_1',
  status: 'DRAFT',
  answeredCount: 0,
  correctPercent: null,
  options: [
    { text: '再见', correct: false, explanation: null },
    { text: '你好', correct: true, explanation: null },
    { text: '谢谢', correct: false, explanation: null },
  ],
}

async function mountDrawer(questionId: number | null = 1) {
  const wrapper = mount(LevelCheckQuestionDrawer, {
    props: { questionId },
    global: { stubs: { teleport: true } },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  api.question.mockReset().mockResolvedValue(question)
  api.saveQuestion.mockReset().mockResolvedValue(question)
})

describe('LevelCheckQuestionDrawer', () => {
  it('loads an existing question when it mounts', async () => {
    const wrapper = await mountDrawer()
    expect(api.question).toHaveBeenCalledWith(1)
    expect(wrapper.get<HTMLInputElement>('[data-testid="question-prompt"]').element.value).toBe(question.prompt)
  })

  it('offers Try again instead of an empty form and a Save that does nothing when the question cannot be opened', async () => {
    api.question.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountDrawer()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('[role="alert"] h3').text()).toBe('The question could not be opened')
    expect(wrapper.get('[data-testid="question-save"]').attributes('disabled')).toBeDefined()

    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(api.question).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get<HTMLInputElement>('[data-testid="question-prompt"]').element.value).toBe(question.prompt)
    expect(wrapper.get('[data-testid="question-save"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).toHaveBeenCalledOnce()
  })

  it('names no status while the question loads or after it could not be opened, then the status it has', async () => {
    const opening = deferred<LevelCheckQuestionDetail>()
    api.question.mockReturnValueOnce(opening.promise).mockResolvedValueOnce({ ...question, status: 'PUBLISHED' })
    const wrapper = await mountDrawer()
    expect(wrapper.find('.overlay-subtitle').exists()).toBe(false)

    opening.reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.find('.overlay-subtitle').exists()).toBe(false)

    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.get('.overlay-subtitle').text()).toBe('Level check · Published · new learners get your changes')
  })

  it('starts a new question as a draft and names its levels as HSK levels', async () => {
    const wrapper = await mountDrawer(null)

    expect(api.question).not.toHaveBeenCalled()
    expect(wrapper.get('.overlay-subtitle').text()).toBe('Level check · saved as a draft until you publish it')
    const levels = wrapper.findAll<HTMLOptionElement>('select[id$="-level"] option')
    expect(levels.map((option) => [option.element.value, option.text()])).toEqual([
      ['BEGINNER_1', 'HSK 1'],
      ['BEGINNER_2', 'HSK 2'],
    ])
    expect(wrapper.get<HTMLSelectElement>('select[id$="-status"]').element.value).toBe('DRAFT')
  })

  it('keeps the selected answer when an earlier option is removed', async () => {
    const wrapper = await mountDrawer()
    await wrapper.get('[aria-label="Remove answer 1"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        options: [
          { text: '你好', correct: true, explanation: null },
          { text: '谢谢', correct: false, explanation: null },
        ],
      }),
    )
  })

  it('requires a new selection after the right answer is removed', async () => {
    const wrapper = await mountDrawer()
    await wrapper.get('[aria-label="Remove answer 2"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('Tick exactly one right answer.')
    await wrapper.findAll('input[type="radio"]')[0]!.setValue()
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).toHaveBeenCalledOnce()
  })

  it('keeps the question open until a repeated answer has different visible text', async () => {
    const wrapper = await mountDrawer()
    const otherAnswer = wrapper.get('[data-testid="answer-0"]')
    await otherAnswer.setValue('  你好  ')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toBe('The answers of a question must all be different.')
    const duplicate = wrapper.get('[data-testid="answer-1"]')
    expect(duplicate.attributes('aria-invalid')).toBe('true')
    expect(duplicate.attributes('aria-errormessage')).toBe(
      wrapper.get('.overlay-footer [role="alert"]').attributes('id'),
    )
    expect(api.saveQuestion).not.toHaveBeenCalled()

    await otherAnswer.setValue('再见')
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).toHaveBeenCalledOnce()
  })

  it('submits only once while a save is pending', async () => {
    const saved = deferred<LevelCheckQuestionDetail>()
    api.saveQuestion.mockReturnValue(saved.promise)
    const wrapper = await mountDrawer()
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).toHaveBeenCalledOnce()
    saved.resolve(question)
    await flushPromises()
  })

  it('requires read-aloud text for a published listening question but allows a draft', async () => {
    api.question.mockResolvedValue({ ...question, skill: 'LISTENING', status: 'PUBLISHED' })
    const wrapper = await mountDrawer()
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('Every listening question needs the Chinese that is read aloud.')
    await wrapper.get('select[id$="-status"]').setValue('DRAFT')
    await wrapper.get('form').trigger('submit')
    expect(api.saveQuestion).toHaveBeenCalledOnce()
  })

  it('words its own checks as the server words the same rules, in every language', async () => {
    api.question.mockResolvedValue({ ...question, skill: 'LISTENING', status: 'PUBLISHED' })
    for (const locale of ['en', 'ms', 'zh-CN'] as const) {
      i18n.global.locale.value = locale
      const wrapper = await mountDrawer()
      await wrapper.get('form').trigger('submit')
      expect(wrapper.get('[role="alert"]').text(), locale).toBe(
        localizeServerMessage('Every listening question needs the Chinese that is read aloud.', locale),
      )
      wrapper.unmount()
    }
  })
})
