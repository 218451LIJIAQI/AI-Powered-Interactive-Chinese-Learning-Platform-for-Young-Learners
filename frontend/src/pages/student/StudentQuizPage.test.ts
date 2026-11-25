import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { studentClassesApi } from '@/api/classes'
import { setAccessToken } from '@/api/http'
import { useHintSettings } from '@/composables/useHintSettings'
import { buttonNamed, deferred } from '@/test/helpers'
import { hintSettings } from '@/test/learnFixtures'
import { mountAtRoute } from '@/test/routes'
import type { QuizIntro, QuizResult, QuizSession, SavedAnswer, StudentLesson } from '@/types/classes'
import type { HintMode } from '@/types/learn'

const api = vi.hoisted(() => ({
  startQuiz: vi.fn<typeof studentClassesApi.startQuiz>(),
  saveAnswer: vi.fn<typeof studentClassesApi.saveAnswer>(),
  clearAnswer: vi.fn(),
  hint: vi.fn(),
  submitQuiz: vi.fn(),
  quizResult: vi.fn(),
  lesson: vi.fn(),
  discardAttempt: vi.fn(),
}))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))
const aiHelperApi = vi.hoisted(() => ({ report: vi.fn() }))
const queueClassNotice = vi.hoisted(() => vi.fn())

vi.mock('@/api/classes', () => ({
  studentClassesApi: api,
}))
vi.mock('@/api/learn', () => ({ hintApi }))
vi.mock('@/api/ai', () => ({ aiHelperApi }))
vi.mock('@/composables/useClassNotice', () => ({ queueClassNotice }))

import StudentQuizPage from './StudentQuizPage.vue'

const intro: QuizIntro = {
  id: 9,
  classId: 7,
  classTitle: 'Mandarin for Campus Life',
  lessonId: 11,
  lessonTitle: 'Meeting Classmates',
}

const session: QuizSession = {
  attemptId: 40,
  quiz: intro,
  questions: [
    {
      id: 1,
      type: 'MULTIPLE_CHOICE',
      text: 'Reply to “你叫什么名字？” (“What’s your name?”)',
      audioUrl: null,
      imageUrl: null,
      hasHint: true,
      options: [
        { id: 101, text: '我是学生。\nwǒ shì xuésheng.' },
        { id: 102, text: '我叫 Amira。\nwǒ jiào Amira.' },
      ],
    },
    {
      id: 2,
      type: 'LISTEN_CHOOSE',
      text: 'Listen, then choose the phrase you hear.',
      audioUrl: null,
      imageUrl: null,
      hasHint: false,
      options: [
        { id: 201, text: '你是老师吗？' },
        { id: 202, text: '你是学生吗？' },
      ],
    },
  ],
  answers: [],
}

const result: QuizResult = {
  attemptId: 40,
  quiz: intro,
  submittedAt: '2026-05-18T10:04:00',
  percent: 50,
  bestPercent: 80,
  bestCorrect: 4,
  bestTotal: 5,
  correctCount: 1,
  wrongCount: 1,
  questions: [
    {
      questionId: 1,
      number: 1,
      text: session.questions[0]!.text,
      yourAnswer: session.questions[0]!.options[1]!.text,
      correctAnswer: session.questions[0]!.options[1]!.text,
      correct: true,
      explanation: null,
      optionFeedback: null,
    },
    {
      questionId: 2,
      number: 2,
      text: 'Listen, then choose the phrase you hear.',
      yourAnswer: '你是老师吗？',
      correctAnswer: '你是学生吗？',
      correct: false,
      explanation: '学生 (xuésheng) is “student”.',
      optionFeedback: '老师 (lǎoshī) is “teacher”.',
    },
  ],
  lessonStatus: 'IN_PROGRESS',
  activitiesLeft: 1,
}

function lesson(overrides: Partial<StudentLesson> = {}): StudentLesson {
  return {
    classId: intro.classId,
    classTitle: 'Mandarin for Campus Life',
    title: 'Meeting Classmates',
    available: true,
    video: null,
    keyPhrases: [],
    activities: [
      { type: 'FLASHCARDS', id: 6, title: 'Classmate Phrases', number: 1, done: true },
      { type: 'MATCHING', id: 8, title: 'Match the Answers', number: 2, done: false },
    ],
    quizzes: [
      {
        id: 9,
        questionCount: 2,
        bestPercent: 80,
        attemptCount: 1,
        bestAttemptId: 39,
        inProgress: true,
      },
    ],
    stepsDone: 1,
    stepsTotal: 3,
    status: 'IN_PROGRESS',
    archived: false,
    ...overrides,
  }
}

function mountQuiz(path = '/student/quiz/9') {
  return mountAtRoute(StudentQuizPage, path)
}

function typedSession(): QuizSession {
  return { ...session, questions: [{ ...session.questions[0]!, type: 'FILL_BLANK', hasHint: false, options: [] }] }
}

/** One question, answered already. */
function answeredSession(): QuizSession {
  return {
    ...session,
    questions: session.questions.slice(0, 1),
    answers: [{ questionId: 1, optionId: 101, answerText: null }],
  }
}

/** The learner's hint setting, as the Account page would have saved it. */
function useMode(mode: HintMode): void {
  useHintSettings().remember(hintSettings({ mode, delaySeconds: 4 }))
}

describe('StudentQuizPage', () => {
  beforeEach(async () => {
    Object.values(api).forEach((fn) => fn.mockReset())
    queueClassNotice.mockReset()
    aiHelperApi.report.mockReset()
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'OFF', delaySeconds: 4 }))
    useHintSettings()
    await flushPromises()
    api.startQuiz.mockResolvedValue(session)
    api.lesson.mockResolvedValue(lesson())
    api.discardAttempt.mockResolvedValue(undefined)
    api.saveAnswer.mockImplementation(async (_attemptId, answer) => ({
      questionId: answer.questionId,
      optionId: answer.optionId ?? null,
      answerText: answer.answerText ?? null,
    }))
    api.submitQuiz.mockResolvedValue(result)
  })

  it('opens on the first question with its instruction, and on the last one says how many are answered', async () => {
    const { wrapper } = await mountQuiz()
    expect(api.startQuiz).toHaveBeenCalledWith(9)
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Lesson quiz')
    expect(wrapper.get('h1').text()).toBe('Lesson quiz: Meeting Classmates')
    // The gloss goes beside the instruction, the pinyin under the option.
    expect(wrapper.get('h2').text()).toBe('Reply to “你叫什么名字？”')
    expect(wrapper.get('.class-quiz-question .class-focus-sub').text().replace(/\s+/g, ' ')).toBe(
      '“What’s your name?” Choose the best answer.',
    )
    expect(wrapper.get('[data-testid="quiz-option-101"]').text()).toContain('wǒ shì xuésheng.')
    // Where the learner is, once, as text; the next question waits for this one's answer.
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 1 of 2')
    expect(wrapper.get('[data-testid="quiz-next"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="quiz-submit"]').exists()).toBe(false)

    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledWith(40, { questionId: 1, optionId: 102 })
    expect(wrapper.get('[data-testid="quiz-next"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 2 of 2')
    // A question that says what to do gets no second instruction.
    expect(wrapper.find('.class-quiz-question .class-focus-sub').exists()).toBe(false)
    expect(wrapper.find('.focus-footer-note').exists()).toBe(false)
    const submit = wrapper.get('[data-testid="quiz-submit"]')
    expect(submit.text()).toBe('Submit quiz')
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="quiz-option-202"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('.focus-footer-note').text()).toBe('All answered')
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('submits once every answer is in, leads with the result, and continues with the activity left', async () => {
    const { wrapper, router } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    await buttonNamed(wrapper, 'Next').trigger('click')
    await wrapper.get('[data-testid="quiz-option-201"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()

    expect(api.submitQuiz).toHaveBeenCalledWith(40)
    // S11.15: this attempt's score, then the best one, both as answers and a percentage.
    const head = wrapper.get('[data-testid="quiz-result"]')
    expect(head.get('h1').text()).toBe('You scored 1 out of 2 (50%)')
    expect(head.text()).toContain('Submitted just now · Your best is 4 out of 5 (80%)')
    // The wrong answer, with what was right, the explanation and a question for SparkBot.
    const review = wrapper.findAll('.quiz-review-item')
    expect(review).toHaveLength(1)
    expect(review[0]!.text()).toContain('学生 (xuésheng) is “student”.')
    expect(review[0]!.get('[data-testid="quiz-review-feedback"]').text()).toBe('老师 (lǎoshī) is “teacher”.')
    expect(review[0]!.find('a').attributes('href')).toContain('/student/ai/ask?q=')
    expect(wrapper.text()).toContain('1 answer to review')
    expect(wrapper.text()).toContain('Finish 1 more activity to complete this lesson')
    const next = wrapper.get('[data-testid="quiz-result-next"]')
    expect(next.text()).toBe('Continue: Match the Answers')
    expect(next.attributes('href')).toBe('/student/classes/7/lessons/11/activities/matching/8')
    // A reload shows this result instead of starting a new attempt.
    expect(router.currentRoute.value.query.attempt).toBe('40')
    expect(api.quizResult).not.toHaveBeenCalled()
    expect(api.startQuiz).toHaveBeenCalledTimes(1)
  })

  it('finishes the lesson when this attempt completed it, with the lesson page confirming it', async () => {
    api.lesson.mockResolvedValue(
      lesson({ activities: [{ type: 'FLASHCARDS', id: 6, title: 'Classmate Phrases', number: 1, done: true }] }),
    )
    api.startQuiz.mockResolvedValue(answeredSession())
    api.submitQuiz.mockResolvedValue({ ...result, lessonStatus: 'COMPLETED', activitiesLeft: 0 })
    const { wrapper } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.focus-footer-note').text()).toBe('Lesson completed')
    const finish = wrapper.get('[data-testid="quiz-result-next"]')
    expect(finish.text()).toBe('Finish lesson')
    expect(finish.attributes('href')).toBe('/student/classes/7/lessons/11')
    await finish.trigger('click')
    expect(queueClassNotice).toHaveBeenCalledWith({
      message: 'Lesson completed.',
      targetRoute: 'student-class-lesson',
    })
  })

  it('opens the lesson from an earlier result', async () => {
    api.quizResult.mockResolvedValue({ ...result, lessonStatus: 'COMPLETED', activitiesLeft: 0 })
    api.lesson.mockResolvedValue(lesson({ status: 'COMPLETED' }))
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')

    expect(api.quizResult).toHaveBeenCalledWith(40)
    expect(api.startQuiz).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('Submitted 18 May, 10:04 AM')
    expect(wrapper.get('[data-testid="quiz-result-next"]').text()).toBe('Open lesson')
  })

  it('keeps an archived result readable without offering a new attempt or activity', async () => {
    const details = deferred<StudentLesson>()
    api.quizResult.mockResolvedValue(result)
    api.lesson.mockReturnValueOnce(details.promise)
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')
    expect(wrapper.get('[data-testid="quiz-result"] h1').text()).toBe('You scored 1 out of 2 (50%)')
    expect(wrapper.text()).not.toContain('Retake quiz')
    details.resolve(lesson({ archived: true }))
    await flushPromises()
    expect(wrapper.get('[data-testid="quiz-result-next"]').text()).toBe('Open lesson')
    expect(wrapper.text()).not.toContain('Retake quiz')
    expect(wrapper.text()).not.toContain('Finish 1 more activity')
  })

  it('keeps the score on screen and retries the lesson before offering a retake', async () => {
    api.quizResult.mockResolvedValue(result)
    api.lesson.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')
    expect(wrapper.get('[data-testid="quiz-result"] h1').text()).toBe('You scored 1 out of 2 (50%)')
    expect(wrapper.get('[role="alert"]').text()).toContain('This lesson could not be loaded')
    expect(wrapper.text()).not.toContain('Retake quiz')

    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Retake quiz')
    expect(api.quizResult).toHaveBeenCalledTimes(1)
    expect(api.startQuiz).not.toHaveBeenCalled()
  })

  it('keeps a question’s gloss when its own instruction already says what to do', async () => {
    api.startQuiz.mockResolvedValue({
      ...session,
      questions: [{ ...session.questions[0], text: 'Choose the right answer (你好 means hello)' }],
    })
    const { wrapper } = await mountQuiz()
    expect(wrapper.get('h2').text()).toBe('Choose the right answer')
    expect(wrapper.get('.class-quiz-question .class-focus-sub').text()).toBe('你好 means hello')
  })

  it('retakes the quiz that owns the linked result and retains the rest of the link', async () => {
    api.quizResult.mockResolvedValue(result)
    const { wrapper, router } = await mountQuiz('/student/quiz/99?attempt=40&from=review#score')
    expect(router.currentRoute.value.fullPath).toBe('/student/quiz/9?attempt=40&from=review#score')
    await buttonNamed(wrapper, 'Retake quiz').trigger('click')
    await flushPromises()
    expect(api.startQuiz).toHaveBeenCalledWith(9)
    expect(router.currentRoute.value.fullPath).toBe('/student/quiz/9?from=review#score')
  })

  it.each(['no', '0', '9007199254740993', '40&attempt=41'])(
    'rejects malformed attempt %s without opening a new quiz',
    async (attempt) => {
      const { wrapper } = await mountQuiz(`/student/quiz/9?attempt=${attempt}`)
      expect(wrapper.text()).toContain('This quiz could not be loaded')
      expect(api.quizResult).not.toHaveBeenCalled()
      expect(api.startQuiz).not.toHaveBeenCalled()
    },
  )

  it('asks before leaving: the answers of this attempt go, the best score stays', async () => {
    const { wrapper, router } = await mountQuiz()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave the quiz?')
    expect(dialog.text()).toContain('Your answers in this attempt won’t be saved. Your best score, 80%, stays.')
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(router.currentRoute.value.name).toBe('student-quiz')
    expect(api.discardAttempt).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(api.discardAttempt).toHaveBeenCalledWith(40)
    expect(router.currentRoute.value.name).toBe('student-class-lesson')
  })

  it('keeps a first attempt open when discarding fails, and leaves once the retry succeeds', async () => {
    api.lesson.mockResolvedValue(lesson({ quizzes: [{ ...lesson().quizzes[0]!, bestPercent: null, attemptCount: 0 }] }))
    api.discardAttempt.mockRejectedValueOnce(new Error('offline'))
    const { wrapper, router } = await mountQuiz()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain(
      'Your answers in this attempt won’t be saved.',
    )
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).not.toContain('best score')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-quiz')
    expect(wrapper.text()).toContain('Couldn’t discard this attempt. Try again.')
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-class-lesson')
  })

  it('offers the SparkBot hint at once in “Only when I ask”, keeps it with its question, and lets it be reported', async () => {
    useMode('ON_REQUEST')
    api.hint.mockResolvedValue({ hint: '叫 means “to be called”. Say 我叫 + your name.' })
    aiHelperApi.report.mockResolvedValue(undefined)
    const { wrapper } = await mountQuiz()

    expect(wrapper.get('[data-testid="class-hint-offer"]').text()).toContain('Stuck? SparkBot can give you a hint.')
    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    expect(api.hint).toHaveBeenCalledWith(40, 1)
    const card = wrapper.get('[data-testid="class-hint"]')
    expect(card.text()).toContain('SparkBot hint')
    expect(card.text()).toContain('叫 means “to be called”. Say 我叫 + your name.')
    expect(card.text()).toContain('AI can make mistakes.')
    // S11.21: in focus mode the footnote is plain text, so the quiz is not left by accident.
    expect(card.get('[data-testid="class-hint-settings"]').text()).toBe('You can change hints in Account.')
    expect(card.find('a[href="/student/account"]').exists()).toBe(false)

    // The next question has no hint; back on this one the hint is still there, without asking again.
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    await buttonNamed(wrapper, 'Next').trigger('click')
    expect(wrapper.find('[data-testid="class-hint"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)
    await buttonNamed(wrapper, 'Previous').trigger('click')
    expect(wrapper.get('[data-testid="class-hint"]').text()).toContain('Say 我叫 + your name.')
    expect(api.hint).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="class-hint-report"]').trigger('click')
    await wrapper.get('[data-testid="hint-report-form"]').trigger('submit')
    await flushPromises()
    expect(aiHelperApi.report.mock.calls[0]![0]).toEqual(
      expect.objectContaining({
        featureKey: 'HINT',
        promptSnapshot: expect.stringContaining('Meeting Classmates · Reply to'),
      }),
    )
    expect(wrapper.get('[data-testid="class-hint-report"]').text()).toBe('Reported')

    // Moving to a question without a hint unmounts HintPanel, yet returning keeps its Reported state.
    await buttonNamed(wrapper, 'Next').trigger('click')
    await buttonNamed(wrapper, 'Previous').trigger('click')
    expect(wrapper.get('[data-testid="class-hint-report"]').text()).toBe('Reported')
    expect(wrapper.get<HTMLButtonElement>('[data-testid="class-hint-report"]').element.disabled).toBe(true)
  })

  it('offers the hint only once the learner is stuck, and never once they picked an answer', async () => {
    // Only the offer's wait is faked; the page's requests still settle.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    useMode('AUTOMATIC')
    const { wrapper } = await mountQuiz()
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(4000)
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(true)

    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)
  })

  it('offers no hint when the learner turned hints off', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    useMode('OFF')
    const { wrapper } = await mountQuiz()

    await vi.advanceTimersByTimeAsync(10_000)
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)
  })

  it('says the hint could not be loaded (not that an answer was lost), and clears that once it loads', async () => {
    useMode('ON_REQUEST')
    api.hint.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ hint: 'A greeting.' })
    const { wrapper } = await mountQuiz()

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('The hint could not be loaded. Try again.')
    expect(wrapper.text()).not.toContain('This answer could not be saved')

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="class-hint"]').text()).toContain('A greeting.')
    expect(wrapper.text()).not.toContain('The hint could not be loaded')
  })

  it('does not show a delayed hint failure on the next question', async () => {
    useMode('ON_REQUEST')
    const pending = deferred<{ hint: string }>()
    api.hint.mockReturnValueOnce(pending.promise)
    api.startQuiz.mockResolvedValue({
      ...session,
      questions: session.questions.map((item) => ({ ...item, hasHint: true })),
    })
    const { wrapper } = await mountQuiz()

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    // Q2's hint button stays available even while Q1's hint request is still in flight.
    expect(wrapper.get('[data-testid="class-hint-show"]').attributes('aria-disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="class-hint-show"]').attributes('aria-busy')).toBe('false')
    pending.reject(new Error('offline'))
    await flushPromises()

    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 2 of 2')
    expect(wrapper.get('[data-testid="class-hint-show"]').attributes('aria-disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('The hint could not be loaded')
  })

  it('picks an answer with its number key and goes on with Enter', async () => {
    const { wrapper } = await mountQuiz()
    // Each answer shows the key that picks it.
    expect(wrapper.findAll('.option-keycap').map((keycap) => keycap.text())).toEqual(['1', '2'])

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '2', bubbles: true }))
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledWith(40, { questionId: 1, optionId: 102 })

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.get('h2').text()).toBe('Listen, then choose the phrase you hear.')
  })

  it('keeps a choice pending until it is saved and permits retry after failure', async () => {
    const save = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(save.promise)
    const { wrapper } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    expect(api.saveAnswer).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="quiz-option-101"]').attributes('aria-checked')).toBe('true')

    save.reject(new Error('offline'))
    await flushPromises()
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenLastCalledWith(40, { questionId: 1, optionId: 102 })
    expect(wrapper.get('[data-testid="quiz-option-102"]').attributes('aria-checked')).toBe('true')
  })

  it('saves newer typed text after the pending text without overlapping or duplicate writes', async () => {
    api.startQuiz.mockResolvedValue(typedSession())
    const first = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(first.promise)
    const { wrapper } = await mountQuiz()
    const box = wrapper.get('#answer-1')
    await box.setValue('first text')
    await box.trigger('blur')
    await box.setValue('latest text')
    await box.trigger('blur')
    await box.trigger('blur')
    expect(api.saveAnswer).toHaveBeenCalledTimes(1)

    first.resolve({ questionId: 1, optionId: null, answerText: 'first text' })
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledTimes(2)
    expect(api.saveAnswer).toHaveBeenLastCalledWith(40, { questionId: 1, answerText: 'latest text' })
    await box.trigger('blur')
    expect(api.saveAnswer).toHaveBeenCalledTimes(2)
  })

  it.each(['saved', 'response lost'])('keeps a cleared answer empty after an earlier write (%s)', async (outcome) => {
    api.startQuiz.mockResolvedValue(typedSession())
    const pending = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountQuiz()
    const box = wrapper.get('#answer-1')
    await box.setValue('old answer')
    await box.trigger('blur')
    await box.setValue('')
    await box.trigger('blur')
    expect(api.clearAnswer).not.toHaveBeenCalled()
    if (outcome === 'saved') pending.resolve({ questionId: 1, optionId: null, answerText: 'old answer' })
    else pending.reject(new Error('response lost'))
    await flushPromises()

    expect(api.clearAnswer).toHaveBeenCalledExactlyOnceWith(40, 1)
    await box.trigger('blur')
    expect(api.clearAnswer).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="quiz-submit"]').attributes('disabled')).toBeDefined()
  })

  it('locks the typed answer while flushing its last edit and submitting', async () => {
    api.startQuiz.mockResolvedValue({
      ...typedSession(),
      answers: [{ questionId: 1, optionId: null, answerText: 'saved' }],
    })
    const save = deferred<SavedAnswer>()
    const submit = deferred<QuizResult>()
    api.saveAnswer.mockReturnValueOnce(save.promise)
    api.submitQuiz.mockReturnValueOnce(submit.promise)
    const { wrapper } = await mountQuiz()
    const box = wrapper.get('#answer-1')
    await box.setValue('final edit')
    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    expect(box.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="focus-close"]').attributes('disabled')).toBeDefined()
    expect(api.submitQuiz).not.toHaveBeenCalled()
    save.resolve({ questionId: 1, optionId: null, answerText: 'final edit' })
    await flushPromises()
    expect(api.submitQuiz).toHaveBeenCalledOnce()
    expect(box.attributes('disabled')).toBeDefined()
    submit.resolve(result)
    await flushPromises()
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
  })

  it('keeps a typed question open when saving before Next fails and allows the same text to be retried', async () => {
    api.startQuiz.mockResolvedValue({
      ...typedSession(),
      questions: [...typedSession().questions, session.questions[1]],
    })
    const { wrapper } = await mountQuiz()
    await wrapper.get('#answer-1').setValue('first answer')
    api.saveAnswer.mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 1 of 2')
    expect(wrapper.text()).toContain('This answer could not be saved')
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 2 of 2')
  })

  it('flushes restored and edited typed answers before submitting a mixed quiz', async () => {
    const typed = typedSession().questions[0]!
    api.startQuiz.mockResolvedValue({
      ...session,
      questions: [{ ...typed, id: 8 }, session.questions[1]!, { ...typed, id: 3 }],
      answers: [
        { questionId: 8, optionId: null, answerText: '  first  ' },
        { questionId: 2, optionId: 201, answerText: 'old option label' },
        { questionId: 3, optionId: null, answerText: '  last  ' },
      ],
    })
    const { wrapper } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    await flushPromises()
    await buttonNamed(wrapper, 'Previous').trigger('click')
    await wrapper.get('#answer-8').setValue('edited first')
    const pending = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(pending.promise)
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 1 of 3')
    pending.resolve({ questionId: 8, optionId: null, answerText: 'edited first' })
    await flushPromises()
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')
    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()
    expect(api.saveAnswer.mock.calls).toEqual([
      [40, { questionId: 8, answerText: 'first' }],
      [40, { questionId: 8, answerText: 'edited first' }],
      [40, { questionId: 3, answerText: 'last' }],
    ])
    expect(api.submitQuiz).toHaveBeenCalledExactlyOnceWith(40)
  })

  it('waits for a debounced edit and retries the saved draft if that request fails', async () => {
    api.startQuiz.mockResolvedValue({
      ...typedSession(),
      answers: [{ questionId: 1, optionId: null, answerText: 'old' }],
    })
    const pending = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountQuiz()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    await wrapper.get('#answer-1').setValue('new')
    await vi.advanceTimersByTimeAsync(700)
    const submit = wrapper.get<HTMLButtonElement>('[data-testid="quiz-submit"]')
    await submit.trigger('click')
    expect(submit.element.disabled).toBe(true)
    expect(api.submitQuiz).not.toHaveBeenCalled()
    pending.reject(new Error('offline'))
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledTimes(1)
    expect(api.submitQuiz).not.toHaveBeenCalled()
    expect(submit.element.disabled).toBe(false)
    await submit.trigger('click')
    await flushPromises()
    expect(api.saveAnswer.mock.calls).toEqual([
      [40, { questionId: 1, answerText: 'new' }],
      [40, { questionId: 1, answerText: 'new' }],
    ])
    expect(api.submitQuiz).toHaveBeenCalledExactlyOnceWith(40)
  })

  it('advances on the first click of Next even when blurring the typed box starts saving it', async () => {
    api.startQuiz.mockResolvedValue({
      ...typedSession(),
      questions: [...typedSession().questions, session.questions[1]!],
    })
    const pending = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountQuiz()

    const box = wrapper.get('#answer-1')
    await box.setValue('ni hao')
    await box.trigger('blur')
    const next = wrapper.get<HTMLButtonElement>('[data-testid="quiz-next"]')
    expect(next.element.disabled).toBe(false)
    await next.trigger('click')

    pending.resolve({ questionId: 1, optionId: null, answerText: 'ni hao' })
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 2 of 2')
  })

  it('keeps Submit enabled on a brand-new typed answer after a debounced save fails, and retries on submit', async () => {
    api.startQuiz.mockResolvedValue(typedSession())
    const pending = deferred<SavedAnswer>()
    api.saveAnswer.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountQuiz()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })

    await wrapper.get('#answer-1').setValue('ni hao')
    await vi.advanceTimersByTimeAsync(700)
    pending.reject(new Error('offline'))
    await flushPromises()

    const submit = wrapper.get<HTMLButtonElement>('[data-testid="quiz-submit"]')
    expect(submit.element.disabled).toBe(false)
    await submit.trigger('click')
    await flushPromises()

    expect(api.saveAnswer.mock.calls).toEqual([
      [40, { questionId: 1, answerText: 'ni hao' }],
      [40, { questionId: 1, answerText: 'ni hao' }],
    ])
    expect(api.submitQuiz).toHaveBeenCalledExactlyOnceWith(40)
  })

  it('reloads cleanly when navigating to another quiz while mounted', async () => {
    api.startQuiz
      .mockResolvedValueOnce(session)
      .mockResolvedValueOnce({ ...session, attemptId: 41, quiz: { ...intro, id: 10 } })
    const { wrapper, router } = await mountQuiz('/student/quiz/9')

    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    await buttonNamed(wrapper, 'Next').trigger('click')
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 2 of 2')

    await router.push('/student/quiz/10')
    await flushPromises()

    expect(api.startQuiz).toHaveBeenNthCalledWith(1, 9)
    expect(api.startQuiz).toHaveBeenNthCalledWith(2, 10)
    expect(wrapper.get('[data-testid="quiz-position"]').text()).toBe('Question 1 of 2')
  })

  it('says in the footer when the quiz could not be submitted, and submits again from there (S11.26)', async () => {
    api.startQuiz.mockResolvedValue(answeredSession())
    api.submitQuiz.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(result)
    const { wrapper } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.focus-footer [data-testid="quiz-submit-error"]').text()).toBe(
      'Couldn’t submit. Your answers are kept. Try again.',
    )
    expect(wrapper.find('.data-notice').exists()).toBe(false)
    expect(wrapper.get('[data-testid="quiz-option-101"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.get('[data-testid="quiz-submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()
    expect(api.submitQuiz).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
  })

  it('keeps the submitted options fixed while the result is pending', async () => {
    api.startQuiz.mockResolvedValue(answeredSession())
    api.submitQuiz.mockReturnValue(new Promise(() => {}))
    const { wrapper } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    expect(api.saveAnswer).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="quiz-option-101"]').attributes('aria-checked')).toBe('true')
  })

  it('does not rewrite the next page after a submitted result arrives late', async () => {
    api.startQuiz.mockResolvedValue(answeredSession())
    const submit = deferred<QuizResult>()
    api.submitQuiz.mockReturnValueOnce(submit.promise)
    const { wrapper, router } = await mountQuiz()
    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()
    wrapper.unmount()
    await router.push('/student/classes/7/lessons/11')
    submit.resolve(result)
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/student/classes/7/lessons/11')
  })

  it('keeps the newer attempt result when an earlier result finishes last', async () => {
    const earlier = deferred<QuizResult>()
    api.quizResult.mockReturnValueOnce(earlier.promise).mockResolvedValueOnce({
      ...result,
      attemptId: 41,
      correctCount: 2,
      wrongCount: 0,
      percent: 100,
      bestPercent: 100,
      bestCorrect: 2,
      bestTotal: 2,
      questions: result.questions.map((question) => ({
        ...question,
        yourAnswer: question.correctAnswer,
        correct: true,
        optionFeedback: null,
      })),
    })
    const { wrapper, router } = await mountQuiz('/student/quiz/9?attempt=40')
    await router.replace({ query: { attempt: '41' } })
    await flushPromises()
    earlier.resolve(result)
    await flushPromises()
    expect(wrapper.get('[data-testid="quiz-result"]').text()).toContain('You scored 2 out of 2')
    expect(router.currentRoute.value.query.attempt).toBe('41')
  })

  it('keeps a requested result when starting the previous attempt completes late', async () => {
    const opening = deferred<QuizSession>()
    api.startQuiz.mockReturnValueOnce(opening.promise)
    api.quizResult.mockResolvedValue(result)
    const { wrapper, router } = await mountQuiz()
    await router.replace({ query: { attempt: '40' } })
    await flushPromises()
    opening.resolve(session)
    await flushPromises()
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
    expect(router.currentRoute.value.query.attempt).toBe('40')
  })

  it('retries the requested result without opening a different attempt', async () => {
    api.quizResult.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(result)
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(api.startQuiz).not.toHaveBeenCalled()
    expect(api.quizResult).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
  })

  it('retains the result and offers retake again when opening a retake fails', async () => {
    api.quizResult.mockResolvedValue(result)
    api.startQuiz.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(session)
    const { wrapper } = await mountQuiz('/student/quiz/9?attempt=40')
    await buttonNamed(wrapper, 'Retake quiz').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(true)
    await buttonNamed(wrapper, 'Retake quiz').trigger('click')
    await flushPromises()
    expect(api.startQuiz).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="quiz-option-101"]').exists()).toBe(true)
  })

  it('reports only a clip that failed, not one stopped before it started', async () => {
    const failPlay: ((reason: unknown) => void)[] = []
    vi.stubGlobal(
      'Audio',
      class {
        play() {
          const played = deferred<void>()
          failPlay.push(played.reject)
          return played.promise
        }
        pause() {}
      },
    )
    api.startQuiz.mockResolvedValue({
      ...session,
      questions: [{ ...session.questions[1]!, audioUrl: 'https://example.com/hello.mp3' }],
    })
    const { wrapper } = await mountQuiz()

    // Played again while the first clip still loads: the browser rejects the first one's play() as aborted.
    await buttonNamed(wrapper, 'Play audio').trigger('click')
    await buttonNamed(wrapper, 'Play audio').trigger('click')
    failPlay[0]!(new DOMException('The play() request was interrupted by a call to pause().', 'AbortError'))
    await flushPromises()
    expect(wrapper.text()).not.toContain('The audio could not be played.')

    failPlay[1]!(new DOMException('No supported source was found.', 'NotSupportedError'))
    await flushPromises()
    expect(wrapper.text()).toContain('The audio could not be played.')
  })

  it('stops the recording on the next question and ignores its delayed playback failure', async () => {
    const playback = deferred<void>()
    const pause = vi.fn()
    vi.stubGlobal(
      'Audio',
      class {
        play = () => playback.promise
        pause = pause
      },
    )
    api.startQuiz.mockResolvedValue({
      ...session,
      questions: [{ ...session.questions[0]!, audioUrl: 'https://example.com/hello.mp3' }, session.questions[1]!],
    })
    const { wrapper } = await mountQuiz()
    await buttonNamed(wrapper, 'Play audio').trigger('click')
    await wrapper.get('[data-testid="quiz-option-102"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="quiz-next"]').trigger('click')

    expect(pause).toHaveBeenCalledOnce()
    playback.reject(new Error('playback interrupted'))
    await flushPromises()
    expect(wrapper.text()).not.toContain('The audio could not be played.')
  })

  it('names the picture of a word-and-image question for screen readers', async () => {
    api.startQuiz.mockResolvedValue({
      ...session,
      questions: [{ ...session.questions[0]!, type: 'MATCH_WORD_IMAGE', imageUrl: '/files/apple.png' }],
    })
    const { wrapper } = await mountQuiz()

    expect(wrapper.get('img.class-quiz-image').attributes('alt')).toBe('Picture for this question')
  })

  it('says so and takes the answer back when it cannot be saved', async () => {
    api.saveAnswer.mockRejectedValue(new Error('nope'))
    const { wrapper } = await mountQuiz()

    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('This answer could not be saved. Try again.')
    expect(wrapper.get('[data-testid="quiz-option-101"]').attributes('aria-checked')).toBe('false')
    expect(api.submitQuiz).not.toHaveBeenCalled()

    api.saveAnswer.mockResolvedValue({ questionId: 1, optionId: 101, answerText: null })
    await wrapper.get('[data-testid="quiz-option-101"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('This answer could not be saved. Try again.')
  })

  it('counts a typed answer only while its box holds text, and does not submit over a failed save', async () => {
    api.startQuiz.mockResolvedValue(typedSession())
    const { wrapper } = await mountQuiz()
    const box = wrapper.get('#answer-1')
    const submit = wrapper.get('[data-testid="quiz-submit"]')

    await box.setValue('ni hao')
    await box.trigger('blur')
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledWith(40, { questionId: 1, answerText: 'ni hao' })
    expect(submit.attributes('disabled')).toBeUndefined()

    await box.setValue('')
    expect(submit.attributes('disabled')).toBeDefined()

    await box.setValue('nǐ hǎo')
    api.saveAnswer.mockRejectedValueOnce(new Error('nope'))
    await submit.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('This answer could not be saved. Try again.')
    expect(api.submitQuiz).not.toHaveBeenCalled()
  })

  it('offers a retry when the quiz cannot be opened', async () => {
    api.startQuiz.mockRejectedValue(new Error('nope'))
    const { wrapper } = await mountQuiz()

    expect(wrapper.text()).toContain('This quiz could not be loaded')
    api.startQuiz.mockResolvedValue(session)
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="quiz-option-101"]').exists()).toBe(true)
  })

  it('clears the discarded attempt and does not override a concurrent navigation when leaving finishes', async () => {
    const discard = deferred<void>()
    const nav = deferred<void>()
    api.discardAttempt.mockReturnValueOnce(discard.promise)
    const { wrapper, router } = await mountQuiz()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    router.beforeResolve((to) => (to.name === 'student-classes' ? nav.promise : true))
    const pendingNav = router.push({ name: 'student-classes' })
    await flushPromises()

    discard.resolve()
    await flushPromises()
    expect(wrapper.find('[data-testid="quiz-option-101"]').exists()).toBe(false)
    nav.resolve()
    await pendingNav

    expect(router.currentRoute.value.name).toBe('student-classes')
  })

  it('ignores a stale submit response whose attemptId does not match the active session', async () => {
    api.startQuiz.mockResolvedValueOnce(answeredSession())
    api.submitQuiz.mockResolvedValueOnce({ ...result, attemptId: 999 })
    const { wrapper } = await mountQuiz()
    const submit = wrapper.get<HTMLButtonElement>('[data-testid="quiz-submit"]')
    await submit.trigger('click')
    await flushPromises()

    expect(api.submitQuiz).toHaveBeenCalledWith(40)
    expect(wrapper.find('[data-testid="quiz-result"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="quiz-option-101"]').exists()).toBe(true)
    expect(submit.element.disabled).toBe(false)
  })

  it('renders a discarded recovery state instead of a load error when leaving navigation rejects', async () => {
    api.discardAttempt.mockResolvedValueOnce(undefined)
    const { wrapper, router } = await mountQuiz()
    vi.spyOn(router, 'push').mockRejectedValueOnce(new Error('navigation blocked'))

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    const discarded = wrapper.get('[data-testid="quiz-discarded"]')
    expect(discarded.text()).toContain('Attempt discarded')
    expect(discarded.get('a').attributes('href')).toBe('/student/classes/7/lessons/11')
    expect(wrapper.text()).not.toContain('This quiz could not be loaded')
  })
})
