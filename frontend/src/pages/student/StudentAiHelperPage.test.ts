import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

const api = vi.hoisted(() => ({
  ai: {
    availability: vi.fn(),
    reports: vi.fn(),
    sentencePractice: vi.fn(),
    storyGenerator: vi.fn(),
  },
  student: { progress: vi.fn() },
  public: { platformSettings: vi.fn() },
}))

vi.mock('@/api/services', () => ({ aiApi: api.ai, studentApi: api.student, publicApi: api.public }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true, isPreviewSession: false }) }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ({ locale: 'en' }) }))

import StudentAiHelperPage from './StudentAiHelperPage.vue'

/** A real lesson title is up to 200 characters; the AI level fields accept 50 (sentence) / 100 (story). */
const LONG_LESSON_TITLE = 'Unit 3: Ordering food at the university canteen and asking for the price politely'

function mountPage() {
  return mount(StudentAiHelperPage, { global: { plugins: [i18n], stubs: { RouterLink: RouterLinkStub } } })
}

describe('StudentAiHelperPage learner level', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    vi.clearAllMocks()
    api.ai.availability.mockResolvedValue({
      available: true,
      status: 'AVAILABLE',
      message: 'Ready',
      textGenerationAvailable: true,
      audioAnalysisAvailable: false,
      imageAnalysisAvailable: false,
    })
    api.ai.reports.mockResolvedValue([])
    api.public.platformSettings.mockResolvedValue({ platformName: 'Spark', aiDisclaimerText: 'AI can be wrong.' })
    api.student.progress.mockResolvedValue({
      totalLessonsStarted: 2,
      completedLessons: 0,
      quizAverage: 0,
      overallCompletionPercent: 10,
      weakAreas: [],
      lastActivitySummary: LONG_LESSON_TITLE,
      recentLessons: [],
      lessons: [],
    })
    api.ai.sentencePractice.mockResolvedValue({
      originalSentence: '我是大学生。',
      correctedSentence: '我是大学生。',
      improvementExplanation: 'Good.',
      status: 'SUCCESS',
    })
  })

  it('sends a progress-based level, never the latest lesson title', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const sentenceCard = wrapper.get('.student-ai-helper-tool-card.is-orange')
    await sentenceCard.get('input').setValue('我是大学生。')
    await sentenceCard.get('button').trigger('click')
    await flushPromises()

    expect(api.ai.sentencePractice).toHaveBeenCalledTimes(1)
    const payload = api.ai.sentencePractice.mock.calls[0]?.[0]
    expect(payload.learnerLevel).not.toBe(LONG_LESSON_TITLE)
    expect(payload.learnerLevel).toBe('Active beginner')
    expect(payload.learnerLevel.length).toBeLessThanOrEqual(50)
  })

  it('accepts handwriting images up to the 10 MB server limit', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const imageInput = wrapper.get('.student-ai-helper-tool-card.is-red input[type="file"]')
    const selectImage = async (size: number) => {
      const file = new File(['x'], 'writing.png', { type: 'image/png' })
      Object.defineProperty(file, 'size', { value: size })
      Object.defineProperty(imageInput.element, 'files', { value: [file], configurable: true })
      await imageInput.trigger('change')
    }

    await selectImage(6 * 1024 * 1024)
    expect(wrapper.text()).not.toContain('Upload a PNG or JPG writing image under 10 MB.')

    await selectImage(10 * 1024 * 1024 + 1)
    expect(wrapper.text()).toContain('Upload a PNG or JPG writing image under 10 MB.')
  })
})
