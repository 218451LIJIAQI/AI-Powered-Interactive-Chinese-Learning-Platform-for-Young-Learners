/**
 * Answer matching and vocabulary for browser-scored AI practice. The lesson editor also uses the answer key to fill
 * the quiz draft an admin reviews before saving.
 */
import type { AiStoryGeneratorResponse } from '@/types/ai'
import type { AppLanguage } from '@/types/api'

const LETTER_KEY = /^\(?([a-d])[).:]?$/
const TRAILING_PUNCTUATION = /[\s.,;:!?。，；：！？、]+$/u

/** NFKC, trimmed, whitespace collapsed and lower-cased, so "  Ni Hao " matches "ni hao". */
function normalizeAnswer(value: string): string {
  return value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase()
}

/** Ignore trailing punctuation on words. Punctuation-only options still have to match exactly. */
function answerKey(value: string): string {
  const normalized = normalizeAnswer(value)
  return normalized.replace(TRAILING_PUNCTUATION, '') || normalized
}

/** Compare each option against the same, nonempty answer key. */
export function matchesAnswer(answer: string): (option: string) => boolean {
  const key = answerKey(answer)
  return (option) => key !== '' && answerKey(option) === key
}

/** The editor accepts one right option. Prefer its literal text, then a text match, then a letter key such as "(C)". */
export function draftAnswerIndex(options: readonly string[], answer: string): number {
  const literal = options.indexOf(answer)
  if (literal !== -1) return literal
  const byText = options.findIndex(matchesAnswer(answer))
  if (byText !== -1) return byText
  const letter = LETTER_KEY.exec(normalizeAnswer(answer))
  if (!letter) return -1
  const index = letter[1].charCodeAt(0) - 'a'.charCodeAt(0)
  return index < options.length ? index : -1
}

/**
 * Story vocabulary rows for display, with the meaning in the interface language: Malay for `ms`, English otherwise,
 * each falling back to the other.
 */
export function storyVocabularyRows(
  story: AiStoryGeneratorResponse | null,
  language: AppLanguage,
): Array<{ chineseWord: string; pinyin: string; meaning: string }> {
  return (story?.vocabularyList ?? []).map(
    ({ chineseWord, pinyin, englishMeaning: english, bahasaMelayuMeaning: malay }) => ({
      chineseWord,
      pinyin: pinyin ?? '',
      meaning: (language === 'ms' ? malay || english : english || malay) ?? '',
    }),
  )
}
