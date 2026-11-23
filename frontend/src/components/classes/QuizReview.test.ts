import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/plugins/i18n'
import type { QuizResult } from '@/types/classes'

import QuizReview from './QuizReview.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/student/ai/:tab', name: 'student-ai', component: { template: '<div />' } },
  ],
})

/** A five-question attempt with these question numbers right. */
function attempt(right: number[]): QuizResult {
  const questions = [1, 2, 3, 4, 5].map((number) => {
    const correct = right.includes(number)
    return {
      questionId: number,
      number,
      text: `Question text ${number}`,
      yourAnswer: 'Student',
      correctAnswer: correct ? 'Student' : 'Name',
      correct,
      explanation: correct ? null : '名字 (míngzi) means “name”.',
      // The admin wrote feedback for the wrong answer picked in question 4 only.
      optionFeedback: number === 4 && !correct ? '学生 (xuésheng) means “student”.' : null,
    }
  })
  return {
    attemptId: 40,
    quiz: { id: 21, classId: 7, classTitle: 'Class', lessonId: 11, lessonTitle: 'Lesson' },
    submittedAt: '2026-05-18T10:04:00',
    percent: right.length * 20,
    bestPercent: 80,
    bestCorrect: 4,
    bestTotal: 5,
    correctCount: right.length,
    wrongCount: 5 - right.length,
    questions,
    lessonStatus: 'IN_PROGRESS',
    activitiesLeft: 0,
  }
}

function mountReview(right: number[], title: string) {
  return mount(QuizReview, { props: { result: attempt(right), title }, global: { plugins: [router] } })
}

function heading(right: number[]): string {
  return mountReview(right, 'Answers to review').get('h2').text().replace(/\s+/g, ' ')
}

describe('QuizReview', () => {
  it('names the right answers in the heading, numbers in a row as a range', () => {
    expect(heading([1, 2, 3])).toBe('Answers to review · Questions 1–3 were correct.')
    expect(heading([1, 3, 4])).toBe('Answers to review · Questions 1 and 3–4 were correct.')
    // British English lists: no comma before "and".
    expect(heading([1, 3, 5])).toBe('Answers to review · Questions 1, 3 and 5 were correct.')
    expect(heading([2])).toBe('Answers to review · Question 2 was correct.')
    expect(heading([])).toBe('Answers to review')
  })

  it('writes the list in the interface language, and again after a switch', async () => {
    const wrapper = mountReview([1, 3, 5], 'Answers to review')
    i18n.global.locale.value = 'zh-CN'
    await wrapper.vm.$nextTick()

    expect(wrapper.get('h2').text()).toBe('Answers to review · 第 1、3和5 题答对了。')
  })

  it('shows every wrong answer with the right one, its explanation and a question for SparkBot, and no toggle', () => {
    const wrapper = mountReview([1, 2, 3], '2 answers to review')

    const items = wrapper.findAll('.quiz-review-item')
    expect(items).toHaveLength(2)
    expect(items[0]!.text()).toContain('4. Question text 4')
    expect(items[0]!.text()).toContain('Incorrect')
    expect(items[0]!.text().replace(/\s+/g, ' ')).toContain('Your answer: Student · Correct answer: Name')
    expect(items[0]!.text()).toContain('名字 (míngzi) means “name”.')
    expect(items[0]!.get('a').attributes('href')).toContain('/student/ai/ask?q=')
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
  })

  it('says why a wrong pick was wrong when the admin wrote feedback for it, before the explanation', () => {
    const wrapper = mountReview([1, 2, 3], '2 answers to review')

    const [withFeedback, without] = wrapper.findAll('.quiz-review-item')
    const lines = withFeedback!.findAll('.quiz-review-line').map((line) => line.text())
    expect(lines.slice(1)).toEqual(['学生 (xuésheng) means “student”.', '名字 (míngzi) means “name”.'])
    expect(without!.find('[data-testid="quiz-review-feedback"]').exists()).toBe(false)
  })
})
