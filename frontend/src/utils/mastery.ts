import type { LessonMastery } from '@/types/api'

const LESSON_MASTERY_ORDER: readonly LessonMastery[] = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'MASTERED']

type MasteryTone = 'neutral' | 'info' | 'success' | 'mastered'

export const MASTERY_TONES: Record<LessonMastery, MasteryTone> = {
  NOT_STARTED: 'neutral',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  MASTERED: 'mastered',
}

export const MASTERY_ICONS: Record<LessonMastery, string> = {
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
