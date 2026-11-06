import type { ClassActivityType, QuizSkill } from '@/types/classes'
import { formatDate, parseDate } from '@/utils/format'

/*
 * Full message keys of the shared `classes` bundle (plugins/classesMessages.ts), so every key stays findable.
 */

export const ACTIVITY_TYPE_KEYS: Record<ClassActivityType, string> = {
  FLASHCARDS: 'classes.activityType.FLASHCARDS',
  TRACING: 'classes.activityType.TRACING',
  MATCHING: 'classes.activityType.MATCHING',
}

export const ACTIVITY_ICONS: Record<ClassActivityType, string> = {
  FLASHCARDS: 'flash',
  TRACING: 'brush',
  MATCHING: 'puzzle',
}

export const SKILL_KEYS: Record<QuizSkill, string> = {
  WORD_MEANINGS: 'classes.skill.WORD_MEANINGS',
  PINYIN_TONES: 'classes.skill.PINYIN_TONES',
  LISTENING: 'classes.skill.LISTENING',
  SENTENCES: 'classes.skill.SENTENCES',
}

const QUESTION_TYPE_KEYS: Record<string, string> = {
  MULTIPLE_CHOICE: 'classes.questionType.MULTIPLE_CHOICE',
  TRUE_FALSE: 'classes.questionType.TRUE_FALSE',
  FILL_BLANK: 'classes.questionType.FILL_BLANK',
  MATCH_WORD_MEANING: 'classes.questionType.MATCH_WORD_MEANING',
  MATCH_WORD_IMAGE: 'classes.questionType.MATCH_WORD_IMAGE',
  LISTEN_CHOOSE: 'classes.questionType.LISTEN_CHOOSE',
  REARRANGE_SENTENCE: 'classes.questionType.REARRANGE_SENTENCE',
  CHOOSE_PINYIN: 'classes.questionType.CHOOSE_PINYIN',
  CHOOSE_TONE: 'classes.questionType.CHOOSE_TONE',
  CHARACTER_MEANING: 'classes.questionType.CHARACTER_MEANING',
}

export const QUESTION_TYPES = Object.keys(QUESTION_TYPE_KEYS)

export function questionTypeKey(type: string): string {
  return QUESTION_TYPE_KEYS[type] ?? QUESTION_TYPE_KEYS.MULTIPLE_CHOICE!
}

/** Typed answers (no options to pick from). */
export function isTypedQuestion(type: string): boolean {
  return type === 'FILL_BLANK' || type === 'REARRANGE_SENTENCE'
}

// ----- live session timetables -----

/** "MON" over "18" (and "May") for the date tiles of session lists. */
export function dateTile(value: string, locale?: string): { weekday: string; day: string; month: string } {
  return {
    weekday: formatDate(value, { weekday: 'short' }, locale),
    day: formatDate(value, { day: 'numeric' }, locale),
    month: formatDate(value, { month: 'short' }, locale),
  }
}

/** 'today', 'tomorrow' or 'yesterday' for a time that falls on one of those days, otherwise null. */
export function relativeDay(value: string, now: Date = new Date()): 'today' | 'tomorrow' | 'yesterday' | null {
  const date = parseDate(value)
  if (!date) return null
  const start = (day: Date) => new Date(day.getFullYear(), day.getMonth(), day.getDate()).getTime()
  const days = Math.round((start(date) - start(now)) / 86_400_000)
  if (days === 0) return 'today'
  if (days === 1) return 'tomorrow'
  if (days === -1) return 'yesterday'
  return null
}

/** True for a link the browser may open (http or https only). */
export function isWebLink(value: string | null | undefined): boolean {
  if (!value) return false
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:'
  } catch {
    return false
  }
}
