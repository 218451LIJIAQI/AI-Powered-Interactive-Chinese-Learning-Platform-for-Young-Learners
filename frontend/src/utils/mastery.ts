import type { LessonMastery } from '@/types/api'

/** Mastery threshold used by the server (D25): lesson completed and best quiz percentage >= 80 %. */
export const MASTERY_QUIZ_THRESHOLD = 80

export const LESSON_MASTERY_ORDER: readonly LessonMastery[] = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'MASTERED']

export type MasteryTone = 'neutral' | 'info' | 'success' | 'mastered'

const MASTERY_TONES: Record<LessonMastery, MasteryTone> = {
  NOT_STARTED: 'neutral',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  MASTERED: 'mastered',
}

const MASTERY_ICONS: Record<LessonMastery, string> = {
  NOT_STARTED: 'history',
  IN_PROGRESS: 'flash',
  COMPLETED: 'status',
  MASTERED: 'star',
}

/** Maps any API value to a known mastery level (unknown / missing -> NOT_STARTED). */
export function normalizeMastery(value: string | null | undefined): LessonMastery {
  const normalised = value?.trim().toUpperCase()
  return (LESSON_MASTERY_ORDER as readonly string[]).includes(normalised ?? '')
    ? (normalised as LessonMastery)
    : 'NOT_STARTED'
}

export function masteryTone(value: string | null | undefined): MasteryTone {
  return MASTERY_TONES[normalizeMastery(value)]
}

export function masteryIcon(value: string | null | undefined): string {
  return MASTERY_ICONS[normalizeMastery(value)]
}

/** 0..3 rank, handy for sorting lessons by mastery. */
export function masteryRank(value: string | null | undefined): number {
  return LESSON_MASTERY_ORDER.indexOf(normalizeMastery(value))
}
