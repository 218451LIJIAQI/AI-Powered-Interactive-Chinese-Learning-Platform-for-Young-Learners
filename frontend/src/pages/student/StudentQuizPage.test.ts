import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { QuizIntro, QuizResult, QuizSession } from '@/types/classes'

const api = vi.hoisted(() => ({
  quiz: vi.fn(),
  startQuiz: vi.fn(),
  saveAnswer: vi.fn(),
  hint: vi.fn(),
  submitQuiz: vi.fn(),
  quizResult: vi.fn(),
}))

vi.mock('@/api/classes', () => ({
  studentClassesApi: api,
  activitySlug: (type: string) => type.toLowerCase(),
}))

import StudentQuizPage from './StudentQuizPage.vue'

const intro: QuizIntro = {
  id: 9,
  title: 'Greetings',
  stage: 'GUIDED',
  questionCount: 2,
  marks: 10,
  timeLimitMinutes: 10,
  hintsAvailable: true,
  classId: 7,
  classTitle: 'Mandarin for Beginners',
  lessonId: 11,
  lessonTitle: 'Greetings',
  bestPercent: null,
  openAttemptId: null,
}

const session: QuizSession = {
  attemptId: 40,
  quiz: intro,
  expiresAt: '2026-05-18T10:10:00',
  secondsLeft: 600,
  questions: [
    {
      id: 1,
      type: 'MULTIPLE_CHOICE',
      text: 'What does 你好 mean?',
      audioUrl: null,
      imageUrl: null,
      hasHint: true,
      options: [
        { id: 101, text: 'Hello' },
        { id: 102, text: 'Goodbye' },
      ],
    },
    {
      id: 2,
      type: 'MULTIPLE_CHOICE',
      text: 'What does 再见 mean?',
      audioUrl: null,
      imageUrl: null,
      hasHint: false,
      options: [
        { id: 201, text: 'Hello' },
        { id: 202, text: 'Goodbye' },
      ],
    },
  ],
  answers: [],
}

const result: QuizResult = {
  quiz: intro,
  submittedAt: '2026-05-18T10:04:00',
  score: 5,
  maxScore: 10,
  percent: 50,
  bestPercent: 50,
  hintsUsed: 1,
  correctCount: 1,
  wrongCount: 1,
  questions: [
    {
      questionId: 1,
      number: 1,
      text: 'What does 你好 mean?',
      yourAnswer: 'Hello',
      correctAnswer: 'Hello',
      correct: true,
      explanation: null,
      hintUsed: true,
    },
    {
      questionId: 2,
      number: 2,
      text: 'What does 再见 mean?',
      yourAnswer: 'Hello',
      correctAnswer: 'Goodbye',
      correct: false,
      explanation: '再见 (zàijiàn) is goodbye.',
      hintUsed: false,
    },
  ],
  lessonStatus: 'COMPLETED',
  activitiesLeft: 0,
  suggestion: {
    skill: 'WORD_MEANINGS',
    activity: { type: 'MATCHING', id: 3, title: 'Match the words' },
  },
  feedback: [],
}

async function mountQuiz(path = '/student/quiz/9') {
  const router = await classesTestRouter(path)
  const wrapper = mount(StudentQuizPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}

describe('StudentQuizPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.quiz.mockResolvedValue(intro)
    api.startQuiz.mockResolvedValue(session)
    api.saveAnswer.mockImplementation(async (_attempt: number, answer: { questionId: number; optionId: number }) => ({
      ...answer,
      answerText: null,
    }))
    api.submitQuiz.mockResolvedValue(result)
  })

  it('starts the quiz, saves each answer, shows a hint and submits once every question is answered', async () => {
    const { wrapper } = await mountQuiz()
    expect(wrapper.text()).toContain('You have 10 minutes once you start.')

    await wrapper.get('[data-testid="quiz-start"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Lesson quiz · 10:00 left')
    expect(wrapper.get('[data-testid="quiz-submit"]').text()).toBe('Answer 2 more')

    api.hint.mockResolvedValue({ questionId: 1, hint: 'A greeting.' })
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Show hint'))!
      .trigger('click')
    await flushPromises()
    expect(api.hint).toHaveBeenCalledWith(40, 1)
    expect(wrapper.text()).toContain('Hint: A greeting.')

    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledWith(40, { questionId: 1, optionId: 101 })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')!
      .trigger('click')
    await wrapper.get('[data-testid="quiz-option-201"]').trigger('click')
    await flushPromises()

    const submit = wrapper.get('[data-testid="quiz-submit"]')
    expect(submit.text()).toBe('Submit quiz')
    await submit.trigger('click')
    await flushPromises()

    expect(api.submitQuiz).toHaveBeenCalledWith(40)
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('5 / 10 marks')
    // Wrong answers first, with the explanation and a question for SparkBot.
    const review = wrapper.findAll('.quiz-review-item')
    expect(review).toHaveLength(1)
    expect(review[0]!.text()).toContain('再见 (zàijiàn) is goodbye.')
    expect(review[0]!.find('a').attributes('href')).toContain('/student/ai/ask?q=')
    expect(wrapper.text()).toContain('Practise "Word meanings"')
  })

  it('shows the result when the time ran out while answering', async () => {
    api.saveAnswer.mockRejectedValue({ response: { status: 409, data: { message: 'Time is up.' } } })
    const { wrapper } = await mountQuiz()

    await wrapper.get('[data-testid="quiz-start"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()

    expect(api.submitQuiz).toHaveBeenCalledWith(40)
    expect(wrapper.text()).toContain('Time is up. Your quiz was submitted with the answers you saved.')
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
  })

  it('shows a past attempt from the class results', async () => {
    api.quizResult.mockResolvedValue(result)
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')

    expect(api.quizResult).toHaveBeenCalledWith(40)
    expect(api.startQuiz).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('50%')
  })
})
