import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { QuizIntro, QuizResult, QuizSession } from '@/types/classes'

const api = vi.hoisted(() => ({
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
      text: 'Reply to “你好” (“Hello”)',
      audioUrl: null,
      imageUrl: null,
      hasHint: true,
      options: [
        { id: 101, text: '你好\nnǐ hǎo' },
        { id: 102, text: '再见\nzàijiàn' },
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
  attemptId: 40,
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
  activitiesLeft: 1,
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

function buttonNamed(wrapper: Awaited<ReturnType<typeof mountQuiz>>['wrapper'], name: string) {
  return wrapper.findAll('button').find((button) => button.text() === name)!
}

describe('StudentQuizPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.startQuiz.mockResolvedValue(session)
    api.saveAnswer.mockImplementation(async (_attempt: number, answer: { questionId: number; optionId: number }) => ({
      ...answer,
      answerText: null,
    }))
    api.submitQuiz.mockResolvedValue(result)
  })

  it('opens on the first question, saves each answer, shows a hint and submits on the last question', async () => {
    const { wrapper, router } = await mountQuiz()
    expect(api.startQuiz).toHaveBeenCalledWith(9)
    expect(wrapper.get('h1').text()).toBe('Lesson quiz: Greetings')
    expect(wrapper.text()).toContain('Question 1 of 2')
    // No timer, whatever the quiz's limit.
    expect(wrapper.text()).not.toContain('left')
    // The gloss goes beside the instruction, the pinyin under the option.
    expect(wrapper.get('h2').text()).toBe('Reply to “你好”')
    expect(wrapper.text()).toContain('“Hello”')
    expect(wrapper.get('[data-testid="quiz-option-101"]').text()).toContain('nǐ hǎo')
    expect(wrapper.find('[data-testid="quiz-submit"]').exists()).toBe(false)

    api.hint.mockResolvedValue({ questionId: 1, hint: 'A greeting.' })
    await buttonNamed(wrapper, 'Show hint').trigger('click')
    await flushPromises()
    expect(api.hint).toHaveBeenCalledWith(40, 1)
    expect(wrapper.text()).toContain('Hint · A greeting.')
    await buttonNamed(wrapper, 'Hide hint').trigger('click')
    expect(wrapper.text()).not.toContain('Hint · A greeting.')
    await buttonNamed(wrapper, 'Show hint').trigger('click')
    await flushPromises()
    expect(api.hint).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledWith(40, { questionId: 1, optionId: 101 })
    expect(wrapper.text()).toContain('1 of 2 answered')

    await buttonNamed(wrapper, 'Next').trigger('click')
    expect(wrapper.get('[data-testid="quiz-submit"]').text()).toBe('Answer 1 more')
    await wrapper.get('[data-testid="quiz-option-201"]').trigger('click')
    await flushPromises()

    const submit = wrapper.get('[data-testid="quiz-submit"]')
    expect(submit.text()).toBe('Submit quiz')
    await submit.trigger('click')
    await flushPromises()

    expect(api.submitQuiz).toHaveBeenCalledWith(40)
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('You scored 1 out of 2')
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('Your best score is 50%')
    // The wrong answer, with what was right, the explanation and a question for SparkBot.
    const review = wrapper.findAll('.quiz-review-item')
    expect(review).toHaveLength(1)
    expect(review[0]!.text()).toContain('再见 (zàijiàn) is goodbye.')
    expect(review[0]!.find('a').attributes('href')).toContain('/student/ai/ask?q=')
    expect(wrapper.text()).toContain('1 answer to review')
    expect(wrapper.text()).toContain('Practise word meanings')
    expect(wrapper.text()).toContain('Finish 1 more activity to complete this lesson')
    // A reload shows this result instead of starting a new attempt.
    await flushPromises()
    expect(router.currentRoute.value.query.attempt).toBe('40')
    expect(api.quizResult).not.toHaveBeenCalled()
    expect(api.startQuiz).toHaveBeenCalledTimes(1)
  })

  it('retakes the quiz from its result', async () => {
    api.quizResult.mockResolvedValue(result)
    const { wrapper, router } = await mountQuiz('/student/quiz/9?attempt=40')
    expect(api.startQuiz).not.toHaveBeenCalled()

    await buttonNamed(wrapper, 'Retake quiz').trigger('click')
    await flushPromises()

    expect(api.startQuiz).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('Question 1 of 2')
    expect(router.currentRoute.value.query.attempt).toBeUndefined()
  })

  it('asks before leaving an attempt', async () => {
    const { wrapper, router } = await mountQuiz()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(wrapper.text()).toContain('Your answers are saved. You can finish later.')
    expect(router.currentRoute.value.name).toBe('student-quiz')

    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-class-lesson')
  })

  it('shows the result when the time ran out while answering', async () => {
    api.saveAnswer.mockRejectedValue({ response: { status: 409, data: { message: 'Time is up.' } } })
    const { wrapper } = await mountQuiz()

    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()

    expect(api.submitQuiz).toHaveBeenCalledWith(40)
    expect(wrapper.text()).toContain('Time is up. Your quiz was submitted with the answers you saved.')
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
  })

  it('offers a retry when the quiz cannot be opened', async () => {
    api.startQuiz.mockRejectedValue(new Error('nope'))
    const { wrapper } = await mountQuiz()

    expect(wrapper.text()).toContain('This quiz could not be loaded')
    api.startQuiz.mockResolvedValue(session)
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Question 1 of 2')
  })

  it('shows a submitted attempt from its link', async () => {
    api.quizResult.mockResolvedValue(result)
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')

    expect(api.quizResult).toHaveBeenCalledWith(40)
    expect(api.startQuiz).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('You scored 1 out of 2')
  })
})
