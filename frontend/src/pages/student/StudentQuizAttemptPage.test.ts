import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { QuizResult } from '@/types/classes'

const api = vi.hoisted(() => ({ quizResult: vi.fn() }))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))

import StudentQuizAttemptPage from './StudentQuizAttemptPage.vue'

function result(attemptId: number, correctCount: number): QuizResult {
  return {
    attemptId,
    quiz: {
      id: 9,
      classId: 7,
      classTitle: 'Mandarin for Campus Life',
      lessonId: 11,
      lessonTitle: 'Meeting Classmates',
    },
    submittedAt: '2026-05-18T10:04:00',
    percent: correctCount * 50,
    bestPercent: 100,
    bestCorrect: 2,
    bestTotal: 2,
    correctCount,
    wrongCount: 2 - correctCount,
    questions: [1, 2].map((number) => {
      const correct = number <= correctCount
      return {
        questionId: number,
        number,
        text: `Question ${number}`,
        yourAnswer: 'Hello',
        correctAnswer: correct ? 'Hello' : 'Goodbye',
        correct,
        explanation: correct ? null : '再见 (zàijiàn) means “goodbye”.',
        optionFeedback: null,
      }
    }),
    lessonStatus: 'IN_PROGRESS',
    activitiesLeft: 0,
  }
}

describe('StudentQuizAttemptPage', () => {
  beforeEach(() => {
    api.quizResult.mockReset()
  })

  it('shows the attempt of its link: when it was sent, its score and the answers to review', async () => {
    api.quizResult.mockResolvedValue(result(40, 1))
    const { wrapper } = await mountAtRoute(StudentQuizAttemptPage, '/student/quiz/9/attempts/40')

    expect(api.quizResult).toHaveBeenCalledWith(40)
    expect(wrapper.get('h1').text()).toBe('Lesson quiz')
    expect(wrapper.findAll('.breadcrumb li').map((item) => item.text())).toEqual([
      'Classes',
      'Mandarin for Campus Life',
      'Meeting Classmates',
      'Lesson quiz',
    ])
    const summary = wrapper.get('[data-testid="quiz-result"]')
    expect(summary.text()).toContain('Submitted 18 May')
    // Not the best attempt (that one scored 100%): its own score, plainly.
    expect(summary.get('dt').text()).toBe('Score')
    expect(summary.get('dd').text()).toBe('1 out of 2 (50%)')
    expect(wrapper.findAll('.quiz-review-item')).toHaveLength(1)
    expect(wrapper.get('.quiz-review-item').text()).toContain('再见 (zàijiàn) means “goodbye”.')
    expect(wrapper.get('.quiz-review-item').text()).toContain('Ask SparkBot about this')
  })

  it('says the best attempt is the best score (S11.16)', async () => {
    api.quizResult.mockResolvedValue(result(41, 2))
    const { wrapper } = await mountAtRoute(StudentQuizAttemptPage, '/student/quiz/9/attempts/41')

    const summary = wrapper.get('[data-testid="quiz-result"]')
    expect(summary.get('dt').text()).toBe('Best score')
    expect(summary.get('dd').text()).toBe('2 out of 2 (100%)')
  })

  it('corrects an attempt link to the quiz that owns it without fetching the attempt twice', async () => {
    api.quizResult.mockResolvedValue(result(40, 1))
    const { router } = await mountAtRoute(StudentQuizAttemptPage, '/student/quiz/99/attempts/40?from=history#score')
    expect(router.currentRoute.value.fullPath).toBe('/student/quiz/9/attempts/40?from=history#score')
    expect(api.quizResult).toHaveBeenCalledOnce()
  })

  it('does not keep the last attempt on screen while another attempt loads or when it fails', async () => {
    api.quizResult.mockResolvedValueOnce(result(40, 1))
    const { wrapper, router } = await mountAtRoute(StudentQuizAttemptPage, '/student/quiz/9/attempts/40')
    const next = deferred<QuizResult>()
    api.quizResult.mockReturnValueOnce(next.promise)

    await router.push('/student/quiz/9/attempts/41')
    await flushPromises()
    expect(api.quizResult).toHaveBeenLastCalledWith(41)
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(false)

    next.reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('This quiz could not be loaded')
  })

  it('does not fetch or offer retry when the attemptId param is malformed', async () => {
    const { wrapper } = await mountAtRoute(StudentQuizAttemptPage, '/student/quiz/9/attempts/not-a-number')

    expect(api.quizResult).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('This quiz could not be loaded')
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
