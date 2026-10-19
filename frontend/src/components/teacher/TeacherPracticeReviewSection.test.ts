import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { teacherPracticeSubmissions } from '@/demo/data'
import { i18n } from '@/plugins/i18n'
import type { PageResponse, PracticeSubmissionResponse } from '@/types/api'

const api = vi.hoisted(() => ({
  practiceSubmissions: vi.fn(),
  practiceSubmissionFile: vi.fn(),
  reviewPracticeSubmission: vi.fn(),
}))

vi.mock('@/api/services', () => ({ teacherApi: api }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import TeacherPracticeReviewSection from './TeacherPracticeReviewSection.vue'

function pageOf(items: PracticeSubmissionResponse[]): PageResponse<PracticeSubmissionResponse> {
  return { items, page: 0, size: 5, totalItems: items.length, totalPages: 1 }
}

function mountSection() {
  return mount(TeacherPracticeReviewSection, { global: { plugins: [i18n] } })
}

describe('TeacherPracticeReviewSection', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    vi.clearAllMocks()
    api.practiceSubmissions.mockResolvedValue(pageOf([teacherPracticeSubmissions[0]]))
  })

  it('loads submissions awaiting review and filters by review status', async () => {
    const wrapper = mountSection()
    await flushPromises()

    expect(api.practiceSubmissions).toHaveBeenCalledWith({ reviewStatus: 'SUBMITTED', page: 0, size: 5 })
    const item = wrapper.get('[data-testid="practice-review-item"]')
    expect(item.text()).toContain('Learner')
    expect(item.text()).toContain('Awaiting teacher review')
    expect(item.get('[data-testid="ai-result-label"]').text()).toMatch(/AI-assisted/)
    expect(item.find('[data-testid="authorized-media"]').exists()).toBe(true)

    await wrapper.get('[data-testid="practice-review-filter-reviewed"]').trigger('click')
    await flushPromises()
    expect(api.practiceSubmissions).toHaveBeenLastCalledWith({ reviewStatus: 'REVIEWED', page: 0, size: 5 })
  })

  it('validates the message (5..2000) and replaces the item with the REVIEWED response', async () => {
    const reviewed: PracticeSubmissionResponse = {
      ...teacherPracticeSubmissions[0],
      reviewStatus: 'REVIEWED',
      reviewedAt: '2026-05-19T02:00:00Z',
      teacherFeedback: [
        {
          id: 9,
          teacherName: 'Li Wei',
          feedbackType: 'ENCOURAGING',
          message: 'Nice clear tones.',
          createdAt: '2026-05-19T02:00:00Z',
        },
      ],
    }
    api.reviewPracticeSubmission.mockResolvedValue(reviewed)
    const wrapper = mountSection()
    await flushPromises()

    await wrapper.get('[data-testid="practice-review-message"]').setValue('Good')
    await wrapper.get('[data-testid="practice-review-form"]').trigger('submit')
    expect(api.reviewPracticeSubmission).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="practice-review-form"]').text()).toContain('at least 5 characters')
    const message = wrapper.get('[data-testid="practice-review-message"]')
    expect(message.attributes('aria-invalid')).toBe('true')
    expect(message.attributes('aria-describedby')).toContain('practice-review-error-1')
    expect(wrapper.find('#practice-review-error-1').text()).toContain('at least 5 characters')

    await wrapper.get('select').setValue('ENCOURAGING')
    await wrapper.get('[data-testid="practice-review-message"]').setValue('  Nice clear tones.  ')
    await wrapper.get('[data-testid="practice-review-form"]').trigger('submit')
    await flushPromises()

    expect(api.reviewPracticeSubmission).toHaveBeenCalledWith(1, {
      feedbackType: 'ENCOURAGING',
      message: 'Nice clear tones.',
    })
    const item = wrapper.get('[data-testid="practice-review-item"]')
    expect(item.text()).toContain('Reviewed by teacher')
    expect(item.text()).toContain('Nice clear tones.')
    expect(wrapper.text()).toContain('The submission is now marked as reviewed.')
    expect(wrapper.emitted('reviewed')?.[0]).toEqual([reviewed])
  })

  it('reports a 403 so the page can show the approval lock', async () => {
    api.practiceSubmissions.mockRejectedValue({
      response: { status: 403, data: { message: 'Only approved teachers' } },
    })
    const wrapper = mountSection()
    await flushPromises()

    expect(wrapper.emitted('forbidden')).toHaveLength(1)
  })
})
