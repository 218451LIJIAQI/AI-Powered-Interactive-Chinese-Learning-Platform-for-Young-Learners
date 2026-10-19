import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { teacherAiStoryDraft } from '@/demo/data'
import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({
  aiAvailability: vi.fn(),
  aiStoryDraft: vi.fn(),
  aiQuizDraft: vi.fn(),
  ui: { locale: 'en' as 'en' | 'ms' | 'zh-CN' },
}))

vi.mock('@/api/services', () => ({
  teacherApi: {
    aiAvailability: (...args: unknown[]) => mocks.aiAvailability(...args),
    aiStoryDraft: (...args: unknown[]) => mocks.aiStoryDraft(...args),
    aiQuizDraft: (...args: unknown[]) => mocks.aiQuizDraft(...args),
  },
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isPreviewSession: true, isAuthenticated: false }) }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => mocks.ui }))

import TeacherAiDraftPanel from './TeacherAiDraftPanel.vue'

describe('TeacherAiDraftPanel story vocabulary (FR-AIGEN-08)', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    mocks.ui.locale = 'en'
    mocks.aiAvailability.mockReset()
    mocks.aiStoryDraft.mockReset()
  })

  it('emits the draft and shows its vocabulary read-only without saving anything', async () => {
    const wrapper = mount(TeacherAiDraftPanel, {
      props: { mode: 'story' },
      global: { plugins: [i18n], stubs: { IconGlyph: true, RouterLink: true } },
    })
    expect(wrapper.find('[data-testid="teacher-ai-draft-vocabulary"]').exists()).toBe(false)

    await wrapper.get('[data-testid="teacher-ai-draft-topic"]').setValue('Lunch at the canteen')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('story-draft')?.[0]).toEqual([teacherAiStoryDraft])
    expect(mocks.aiStoryDraft).not.toHaveBeenCalled()
    const rows = wrapper.findAll('[data-testid="teacher-ai-draft-vocabulary-item"]')
    expect(rows).toHaveLength(teacherAiStoryDraft.vocabularyList.length)
    expect(rows[0].text()).toContain('食堂')
    expect(rows[0].text()).toContain('shítáng')
    expect(rows[0].text()).toContain('canteen')
    expect(wrapper.get('[data-testid="teacher-ai-draft-vocabulary"]').text()).toContain('Instructions field')
    expect(wrapper.get('[data-testid="teacher-ai-draft-result"]').text()).toContain('Not saved')
  })
})
