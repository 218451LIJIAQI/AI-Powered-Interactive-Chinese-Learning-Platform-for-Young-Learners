/**
 * Local checking for AI-generated self-practice (FR-AIGEN-03 / FR-AIGEN-08). Everything here runs in the browser:
 * answers are never sent to the API, saved, or counted towards course marks.
 */
import type { AiQuizGeneratorResponse, AiStoryGeneratorResponse, AppLanguage } from '@/types/api'

type AiQuizQuestion = AiQuizGeneratorResponse['questions'][number]
type AiStoryVocabularyItem = AiStoryGeneratorResponse['vocabularyList'][number]

export interface PracticeQuizQuestion {
  questionText: string
  questionType: string
  options: string[]
  /** The AI answer key as given (may be an option text or a single letter A-D). */
  correctAnswer: string
  /** Text shown to the learner as the correct answer (a letter key is resolved to its option). */
  correctAnswerDisplay: string
  /** Indexes of the options that count as correct. */
  correctOptionIndexes: number[]
  explanation: string
  pinyin: string
  translation: string
}

export type PracticeAnswerStatus = 'correct' | 'incorrect' | 'unanswered' | 'noKey'

export interface PracticeQuizResult {
  statuses: PracticeAnswerStatus[]
  score: number
  /** Questions that have an answer key and therefore count towards the score. */
  total: number
}

const LETTER_KEY = /^\(?([a-d])[).:]?$/
const TRAILING_PUNCTUATION = /[\s.,;:!?。，；：！？、]+$/u

function clean(value: string | null | undefined): string {
  return (value ?? '').trim()
}

/** NFKC, trimmed, whitespace collapsed and lower-cased, so "  Ni Hao " matches "ni hao". */
export function normalizeAnswer(value: string | null | undefined): string {
  return (value ?? '').normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase()
}

/** True when two answers match after normalisation, also ignoring a trailing full stop or question mark. */
export function answersMatch(left: string | null | undefined, right: string | null | undefined): boolean {
  const a = normalizeAnswer(left)
  const b = normalizeAnswer(right)
  if (!a || !b) return false
  if (a === b) return true
  const looseA = a.replace(TRAILING_PUNCTUATION, '')
  const looseB = b.replace(TRAILING_PUNCTUATION, '')
  return Boolean(looseA) && looseA === looseB
}

/** 0-based option index for a single-letter answer key ("A", "b", "(C)", "D."), otherwise -1. */
export function letterKeyIndex(value: string | null | undefined): number {
  const match = LETTER_KEY.exec(normalizeAnswer(value))
  return match ? match[1].charCodeAt(0) - 'a'.charCodeAt(0) : -1
}

/**
 * Options that match the answer key. Exact (normalised) text wins; a single letter A-D is only used as an index when
 * no option text matches it.
 */
export function correctOptionIndexes(options: readonly string[], correctAnswer: string | null | undefined): number[] {
  const byText = options.flatMap((option, index) => (answersMatch(option, correctAnswer) ? [index] : []))
  if (byText.length) return byText
  const letterIndex = letterKeyIndex(correctAnswer)
  return letterIndex >= 0 && letterIndex < options.length ? [letterIndex] : []
}

/** Cleans AI quiz questions for display: blank questions and blank options are dropped. */
export function preparePracticeQuiz(quiz: AiQuizGeneratorResponse | null | undefined): PracticeQuizQuestion[] {
  return (quiz?.questions ?? [])
    .filter((question: AiQuizQuestion) => clean(question.questionText))
    .map((question) => {
      const options = (question.options ?? []).map((option) => clean(option)).filter(Boolean)
      const correctAnswer = clean(question.correctAnswer)
      const indexes = correctOptionIndexes(options, correctAnswer)
      const resolvedFromLetter = indexes.length === 1 && !answersMatch(options[indexes[0]], correctAnswer)
      return {
        questionText: clean(question.questionText),
        questionType: clean(question.questionType),
        options,
        correctAnswer,
        correctAnswerDisplay: resolvedFromLetter ? options[indexes[0]] : correctAnswer,
        correctOptionIndexes: indexes,
        explanation: clean(question.explanation),
        pinyin: clean(question.pinyin),
        translation: clean(question.translation),
      }
    })
}

/** Marks one answer: a selected option index, typed text, or null when the learner skipped it. */
export function checkPracticeAnswer(
  question: PracticeQuizQuestion,
  answer: number | string | null | undefined,
): PracticeAnswerStatus {
  if (!question.correctAnswer) return 'noKey'
  if (typeof answer === 'number') {
    if (answer < 0 || answer >= question.options.length) return 'unanswered'
    return question.correctOptionIndexes.includes(answer) ? 'correct' : 'incorrect'
  }
  if (!clean(answer)) return 'unanswered'
  return answersMatch(answer, question.correctAnswer) ? 'correct' : 'incorrect'
}

/** Marks every question and returns the local score; unanswered questions count as not correct. */
export function scorePracticeQuiz(
  questions: readonly PracticeQuizQuestion[],
  answers: ReadonlyArray<number | string | null | undefined>,
): PracticeQuizResult {
  const statuses = questions.map((question, index) => checkPracticeAnswer(question, answers[index]))
  return {
    statuses,
    score: statuses.filter((status) => status === 'correct').length,
    total: statuses.filter((status) => status !== 'noKey').length,
  }
}

/** Vocabulary meaning in the interface language: Malay for `ms`, English otherwise, each falling back to the other. */
export function vocabularyMeaning(item: AiStoryVocabularyItem, language: AppLanguage): string {
  const english = clean(item.englishMeaning)
  const malay = clean(item.bahasaMelayuMeaning)
  return language === 'ms' ? malay || english : english || malay
}

export interface StoryTranslation {
  language: 'en' | 'ms'
  text: string
}

/** Non-empty story translations, the one matching the interface language first. */
export function storyTranslations(story: AiStoryGeneratorResponse, language: AppLanguage): StoryTranslation[] {
  const english: StoryTranslation = { language: 'en', text: clean(story.englishTranslation) }
  const malay: StoryTranslation = { language: 'ms', text: clean(story.bahasaMelayuTranslation) }
  const ordered = language === 'ms' ? [malay, english] : [english, malay]
  return ordered.filter((item) => item.text)
}
