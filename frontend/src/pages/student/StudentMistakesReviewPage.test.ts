import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerCheck, learnRouter, lessonPlay, xpResult } from '@/test/learnFixtures'
import type { MistakeReview } from '@/types/learn'

const api = vi.hoisted(() => ({ review: vi.fn(), check: vi.fn(), complete: vi.fn() }))

vi.mock('@/api/learn', () => ({ mistakesApi: api }))

import StudentMistakesReviewPage from './StudentMistakesReviewPage.vue'

function review(): MistakeReview {
  const [first] = lessonPlay().questions
  return {
    waiting: 6,
    questions: [{ ...first!, id: 7 }],
    context: [{ sourceTitle: 'Polite phrases', fromLearn: true, yourAnswer: 'teacher' }],
  }
}

async function mountPage() {
  const router = await learnRouter('/student/practice/review')
  const wrapper = mount(StudentMistakesReviewPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentMistakesReviewPage (focus mode review)', () => {
  beforeEach(() => {
    api.review.mockResolvedValue(review())
  })

  it('asks each waiting mistake again with where it came from and clears it when answered right', async () => {
    api.check.mockResolvedValue(answerCheck())
    api.complete.mockResolvedValue({ answered: 1, cleared: 1, left: 5, xp: xpResult({ xpEarned: 10 }) })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Mistakes review · 1 of 1')
    expect(wrapper.text()).toContain('Learn · Polite phrases · you answered “teacher” last time')
    // The tally counts down as each mistake is checked.
    expect(wrapper.get('.practice-review-left').text()).toBe('1 left')
    expect(wrapper.get('.practice-review-tally li').text()).toBe('Mistake 1: now')

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.check).toHaveBeenCalledWith({ questionId: 7, answer: 'name' })
    expect(wrapper.get('.practice-review-left').text()).toBe('0 left')
    expect(wrapper.get('.practice-review-tally li').classes()).toContain('is-right')

    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(api.complete).toHaveBeenCalledWith([{ questionId: 7, answer: 'name' }])
    const done = wrapper.get('[data-testid="review-complete"]')
    expect(done.text()).toContain('Review complete!')
    expect(done.text()).toContain('1 / 1')
    expect(done.text()).toContain('5 mistakes to try again next time')
  })

  it('celebrates an empty list once the last mistake is fixed', async () => {
    api.check.mockResolvedValue(answerCheck())
    api.complete.mockResolvedValue({ answered: 1, cleared: 1, left: 0, xp: xpResult({ xpEarned: 10 }) })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    const done = wrapper.get('[data-testid="review-complete"]')
    expect(done.text()).toContain('All fixed!')
    expect(done.text()).toContain('Your list of mistakes is empty.')
    expect(done.text()).not.toContain('Still on your list')
  })

  it('says there is nothing to review when no mistake is waiting', async () => {
    api.review.mockResolvedValue({ waiting: 0, questions: [], context: [] })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Nothing to review')
    expect(wrapper.find('[data-testid="check-bar"]').exists()).toBe(false)
  })
})
