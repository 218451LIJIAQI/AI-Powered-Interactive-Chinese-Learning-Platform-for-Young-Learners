/**
 * Pure helpers for the student quiz attempt (W4 contract, section 4).
 *
 * - FILL_BLANK and REARRANGE_SENTENCE are answered by typing (`answerText`, trimmed);
 * - every other question type is answered by choosing one of the listed options (`optionId`);
 * - `hintUsed` is sent only for questions where the learner opened the teacher hint.
 */
import type {
  LearningStage,
  QuizAnswerSubmission,
  QuizAttemptResultQuestionResponse,
  QuizAttemptResultResponse,
  QuizQuestionType,
  StudentQuizQuestionResponse,
} from '@/types/api'
import { normalizeLearningStage } from '@/utils/learningStage'

/** A draft answer: an option id for option-based questions, typed text for text questions. */
export type QuizAnswerValue = number | string
export type QuizAnswerMap = Record<number, QuizAnswerValue | undefined>
export type QuizHintUsage = Record<number, boolean | undefined>

type QuestionShape = Pick<StudentQuizQuestionResponse, 'id' | 'questionType' | 'options'>

const TEXT_ANSWER_TYPES: ReadonlySet<string> = new Set<QuizQuestionType>(['FILL_BLANK', 'REARRANGE_SENTENCE'])

export function isTextAnswerType(type: QuizQuestionType | string | null | undefined): boolean {
  return Boolean(type && TEXT_ANSWER_TYPES.has(type.trim().toUpperCase()))
}

export function isQuestionAnswered(question: QuestionShape, answer: QuizAnswerValue | null | undefined): boolean {
  if (isTextAnswerType(question.questionType)) {
    return typeof answer === 'string' && answer.trim().length > 0
  }
  return typeof answer === 'number' && question.options.some((option) => option.id === answer)
}

export function countAnsweredQuestions(questions: QuestionShape[], answers: QuizAnswerMap): number {
  return questions.filter((question) => isQuestionAnswered(question, answers[question.id])).length
}

/** Index of the first unanswered question, or -1 when every question has a valid answer. */
export function firstUnansweredIndex(questions: QuestionShape[], answers: QuizAnswerMap): number {
  return questions.findIndex((question) => !isQuestionAnswered(question, answers[question.id]))
}

/**
 * Builds the `answers` array for `POST /students/quizzes/{quizId}/attempts`, one entry per question in the given order.
 * Option questions carry `optionId`, text questions the trimmed `answerText`; an invalid / missing answer carries no
 * value so the server can return its own "Every quiz question requires an answer." message.
 */
export function buildQuizSubmission(
  questions: QuestionShape[],
  answers: QuizAnswerMap,
  hintsUsed: QuizHintUsage = {},
): QuizAnswerSubmission[] {
  return questions.map((question) => {
    const submission: QuizAnswerSubmission = { questionId: question.id }
    const answer = answers[question.id]

    if (isTextAnswerType(question.questionType)) {
      const text = typeof answer === 'string' ? answer.trim() : ''
      if (text) submission.answerText = text
    } else if (isQuestionAnswered(question, answer)) {
      submission.optionId = answer as number
    }

    if (hintsUsed[question.id] === true) submission.hintUsed = true
    return submission
  })
}

/** Teacher hints are offered only when the question has one and the quiz is not INDEPENDENT practice (D8). */
export function canRequestHint(
  question: Pick<StudentQuizQuestionResponse, 'hintAvailable'>,
  learningStage: LearningStage | string | null | undefined,
): boolean {
  return question.hintAvailable === true && normalizeLearningStage(learningStage) !== 'INDEPENDENT'
}

function optionText(question: QuizAttemptResultQuestionResponse, optionId: number | null | undefined): string | null {
  if (optionId == null) return null
  return question.options?.find((option) => option.id === optionId)?.optionText ?? null
}

/** The learner's answer for the result view: the chosen option's text, falling back to the stored answer text. */
export function selectedAnswerLabel(question: QuizAttemptResultQuestionResponse): string {
  return (optionText(question, question.selectedOptionId) ?? question.studentAnswer ?? '').trim()
}

/** The correct answer for the result view: the correct option's text, falling back to the stored answer text. */
export function correctAnswerLabel(question: QuizAttemptResultQuestionResponse): string {
  return (optionText(question, question.correctOptionId) ?? question.correctAnswer ?? '').trim()
}

export function wrongResultAnswers(
  result: Pick<QuizAttemptResultResponse, 'answers'> | null | undefined,
): QuizAttemptResultQuestionResponse[] {
  return (result?.answers ?? []).filter((answer) => !answer.correct)
}
