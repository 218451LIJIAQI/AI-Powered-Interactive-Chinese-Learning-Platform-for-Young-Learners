import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { teacherAiQuizDraft } from '@/test/aiDraftFixtures'

const api = vi.hoisted(() => ({ aiAvailability: vi.fn(), aiQuizDraft: vi.fn() }))
const mocks = vi.hoisted(() => ({
  ui: { locale: 'en' as 'en' | 'ms' | 'zh-CN' },
}))

vi.mock('@/api/services', () => ({ teacherApi: api }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => mocks.ui }))

import TeacherAiDraftPanel from './TeacherAiDraftPanel.vue'

function mountPanel(props: { disabled?: boolean } = {}) {
  return mount(TeacherAiDraftPanel, { props, global: { stubs: { IconGlyph: true, AiDisclaimer: true } } })
}

describe('TeacherAiDraftPanel (FRAI05)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.aiAvailability.mockResolvedValue({ available: true })
  })

  it('emits the quiz draft for the parent to fill in and saves nothing itself', async () => {
    api.aiQuizDraft.mockResolvedValue(teacherAiQuizDraft)
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="teacher-ai-draft-topic"]').setValue('Ordering food')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('quiz-draft')?.[0]).toEqual([teacherAiQuizDraft])
    expect(api.aiQuizDraft).toHaveBeenCalledWith(
      expect.objectContaining({ topic: 'Ordering food', level: 'HSK 1', languagePreference: 'en' }),
    )
    const result = wrapper.get('[data-testid="teacher-ai-draft-result"]').text()
    expect(result).toContain(teacherAiQuizDraft.title)
    expect(result).toContain('Not saved')
  })

  it('checks AI availability once the panel becomes enabled and clamps the live quiz question count', async () => {
    api.aiQuizDraft.mockResolvedValue({ title: 'Food', questions: [] })
    const wrapper = mountPanel({ disabled: true })
    await flushPromises()
    expect(api.aiAvailability).not.toHaveBeenCalled()

    await wrapper.setProps({ disabled: false })
    await flushPromises()
    expect(api.aiAvailability).toHaveBeenCalledOnce()

    await wrapper.get('[data-testid="teacher-ai-draft-topic"]').setValue('Food')
    await wrapper.get('input[type="number"]').setValue(25)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.aiQuizDraft).toHaveBeenCalledWith(expect.objectContaining({ topic: 'Food', numberOfQuestions: 10 }))
  })

  it('stays disabled with the unavailable note when AI is off', async () => {
    api.aiAvailability.mockResolvedValue({ available: false })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('AI drafts are not available right now.')
    expect(wrapper.get('[data-testid="teacher-ai-draft-generate"]').attributes('disabled')).toBeDefined()
  })
})
