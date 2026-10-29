/**
 * Answer matching and story helpers for AI-generated practice (Quick quiz, Story and teachers' AI drafts). Everything
 * here runs in the browser: answers are never sent to the API, saved or counted towards marks.
 */
import type { AiStoryGeneratorResponse, AppLanguage } from '@/types/api'

type AiStoryVocabularyItem = AiStoryGeneratorResponse['vocabularyList'][number]

const LETTER_KEY = /^\(?([a-d])[).:]?$/
const TRAILING_PUNCTUATION = /[\s.,;:!?。，；：！？、]+$/u

export function clean(value: string | null | undefined): string {
  return (value ?? '').trim()
}

/** NFKC, trimmed, whitespace collapsed and lower-cased, so "  Ni Hao " matches "ni hao". */
function normalizeAnswer(value: string | null | undefined): string {
  return (value ?? '').normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase()
}

/** True when two answers match after normalisation, also ignoring trailing spaces and punctuation (.,;:!? and CJK). */
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
function letterKeyIndex(value: string | null | undefined): number {
  const match = LETTER_KEY.exec(normalizeAnswer(value))
  return match ? match[1].charCodeAt(0) - 'a'.charCodeAt(0) : -1
}

/**
 * Options that match the answer key. Exact (normalised) text wins; a single letter A-D is only used as an index when
 * no option text matches it.
 */
function correctOptionIndexes(options: readonly string[], correctAnswer: string | null | undefined): number[] {
  const byText = options.flatMap((option, index) => (answersMatch(option, correctAnswer) ? [index] : []))
  if (byText.length) return byText
  const letterIndex = letterKeyIndex(correctAnswer)
  return letterIndex >= 0 && letterIndex < options.length ? [letterIndex] : []
}

/**
 * Correct option indexes for an AI answer key plus the text to show as the answer: a single-letter key (A-D) is
 * resolved to its option text, any other key is shown as given.
 */
export function resolveAnswerKey(options: readonly string[], answer: string): { indexes: number[]; display: string } {
  const indexes = correctOptionIndexes(options, answer)
  const resolvedFromLetter = indexes.length === 1 && !answersMatch(options[indexes[0]], answer)
  return { indexes, display: resolvedFromLetter ? options[indexes[0]] : answer }
}

/** Vocabulary meaning in the interface language: Malay for `ms`, English otherwise, each falling back to the other. */
function vocabularyMeaning(item: AiStoryVocabularyItem, language: AppLanguage): string {
  const english = clean(item.englishMeaning)
  const malay = clean(item.bahasaMelayuMeaning)
  return language === 'ms' ? malay || english : english || malay
}

/** Story vocabulary rows for display: blank words dropped, word and Pinyin trimmed, meaning in the interface language. */
export function storyVocabularyRows(
  story: AiStoryGeneratorResponse | null | undefined,
  language: AppLanguage,
): Array<{ chineseWord: string; pinyin: string; meaning: string }> {
  return (story?.vocabularyList ?? [])
    .filter((item) => item.chineseWord?.trim())
    .map((item) => ({
      chineseWord: item.chineseWord?.trim() ?? '',
      pinyin: item.pinyin?.trim() ?? '',
      meaning: vocabularyMeaning(item, language),
    }))
}
