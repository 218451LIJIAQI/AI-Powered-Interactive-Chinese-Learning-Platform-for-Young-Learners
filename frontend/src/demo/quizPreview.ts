/**
 * Preview-only grading for the demo quiz (`studentQuiz` in demo/data.ts).
 *
 * The student quiz payload never contains correct answers or hint text (W4 contract); the preview build keeps them
 * here so the result view, hints and retake flow can be shown without a backend. Nothing is saved.
 */
import type {
  QuizAnswerSubmission,
  QuizAttemptResultResponse,
  QuizHintResponse,
  StudentQuizResponse,
} from '@/types/api'

interface DemoQuestionKey {
  correctOptionId?: number
  correctAnswerText?: string
  explanation: string
  hint?: string
}

const DEMO_QUIZ_KEYS: Record<number, DemoQuestionKey> = {
  101: {
    correctOptionId: 2,
    explanation: 'A polite reply to 你好吗？ is 我很好，谢谢！ (I am fine, thank you!).',
    hint: 'The polite reply also says thank you.',
  },
  102: { correctOptionId: 5, explanation: '谢谢 (xièxie) means "thank you".' },
  103: {
    correctOptionId: 7,
    explanation: '朋友 is read péngyou.',
    hint: '朋 starts with the same sound as "peng".',
  },
  104: { correctOptionId: 11, explanation: '老师 (lǎoshī) means "teacher" or "lecturer".' },
  105: { correctOptionId: 15, explanation: '早上好 (zǎoshang hǎo) means "good morning".' },
  106: { correctAnswerText: '我叫小明', explanation: 'Subject (我) + verb (叫) + name (小明): 我叫小明。' },
  107: { correctOptionId: 23, explanation: '朋友 means "friend".' },
  108: {
    correctOptionId: 27,
    explanation: 'The third tone is written with a caron: hǎo.',
    hint: 'The third tone mark looks like a small "v".',
  },
  109: { correctOptionId: 31, explanation: '大学 (dàxué) means "university".' },
  110: { correctOptionId: 35, explanation: '苹果 (píngguǒ) means "apple".' },
  111: { correctOptionId: 37, explanation: '书 (shū) means "book".' },
  112: {
    correctAnswerText: '好',
    explanation: '老师好 (lǎoshī hǎo) is a polite way to greet a teacher.',
    hint: 'It is the same word you use in 你好.',
  },
}

function normaliseText(value: string | null | undefined): string {
  return (value ?? '').replace(/[\s。，,.!！?？]/g, '').toLowerCase()
}

export function demoQuizHint(questionId: number): QuizHintResponse | null {
  const hint = DEMO_QUIZ_KEYS[questionId]?.hint
  return hint ? { questionId, hint } : null
}

export function gradeDemoQuiz(quiz: StudentQuizResponse, answers: QuizAnswerSubmission[]): QuizAttemptResultResponse {
  const now = new Date().toISOString()
  const results = quiz.questions.map((question) => {
    const key = DEMO_QUIZ_KEYS[question.id]
    const answer = answers.find((item) => item.questionId === question.id)
    const selected = question.options.find((option) => option.id === answer?.optionId)
    const correctOption = question.options.find((option) => option.id === key?.correctOptionId)
    const correct = key?.correctOptionId
      ? answer?.optionId === key.correctOptionId
      : normaliseText(answer?.answerText) === normaliseText(key?.correctAnswerText)
    return {
      questionId: question.id,
      questionType: question.questionType,
      questionText: question.questionText,
      studentAnswer: selected?.optionText ?? answer?.answerText ?? '',
      correctAnswer: correctOption?.optionText ?? key?.correctAnswerText ?? '',
      correct,
      points: question.points,
      explanation: key?.explanation ?? null,
      selectedOptionId: answer?.optionId ?? null,
      correctOptionId: key?.correctOptionId ?? null,
      hintUsed: Boolean(answer?.hintUsed && question.hintAvailable),
      options: question.options,
    }
  })
  const maxScore = quiz.questions.reduce((total, question) => total + question.points, 0)
  const score = results.reduce((total, item) => total + (item.correct ? (item.points ?? 0) : 0), 0)
  const percentage = maxScore ? Math.round((score / maxScore) * 100) : 0
  const correctCount = results.filter((item) => item.correct).length
  return {
    attemptId: 0,
    quizId: quiz.id,
    quizTitle: quiz.title,
    score,
    maxScore,
    percentage,
    learningStage: quiz.learningStage ?? null,
    totalQuestions: results.length,
    correctCount,
    wrongCount: results.length - correctCount,
    earnedPoints: 0,
    totalPoints: quiz.totalPoints,
    passed: percentage >= 60,
    startedAt: now,
    submittedAt: now,
    answers: results,
  }
}
