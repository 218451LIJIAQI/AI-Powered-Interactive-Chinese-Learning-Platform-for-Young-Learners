import type { ContentStatus, ReviewContentType } from '@/types/api'
import { normalizeContentStatus } from '@/utils/contentStatus'

export const REVIEW_CONTENT_TYPES = [
  'PACKAGE',
  'MODULE',
  'LESSON',
  'QUIZ',
  'FLASHCARD',
  'MATCHING_GAME',
  'LISTENING_EXERCISE',
  'TRACING_EXERCISE',
  'DRAG_DROP_ACTIVITY',
  'STORY_ACTIVITY',
] as const satisfies readonly ReviewContentType[]

export type ReviewAction = 'approve' | 'reject' | 'archive' | 'restore'

const TYPE_MESSAGE_KEYS = {
  PACKAGE: 'typePackage',
  MODULE: 'typeModule',
  LESSON: 'typeLesson',
  QUIZ: 'typeQuiz',
  FLASHCARD: 'typeFlashcard',
  MATCHING_GAME: 'typeMatchingGame',
  LISTENING_EXERCISE: 'typeListeningExercise',
  TRACING_EXERCISE: 'typeTracingExercise',
  DRAG_DROP_ACTIVITY: 'typeDragDropActivity',
  STORY_ACTIVITY: 'typeStoryActivity',
} as const satisfies Record<ReviewContentType, string>

/** Accepts the backend spelling or a route query value (case-insensitive, `-` for `_`). */
export function normalizeReviewContentType(value: unknown): ReviewContentType | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string') return null
  const normalized = raw.trim().toUpperCase().replaceAll('-', '_')
  return (REVIEW_CONTENT_TYPES as readonly string[]).includes(normalized) ? (normalized as ReviewContentType) : null
}

export function reviewContentTypeMessageKey(type: ReviewContentType): (typeof TYPE_MESSAGE_KEYS)[ReviewContentType] {
  return TYPE_MESSAGE_KEYS[type]
}

/**
 * Admin decisions allowed by the review state machine (W3 contract): approve SUBMITTED; reject SUBMITTED or
 * PUBLISHED (reason required); archive anything that is not archived; restore ARCHIVED (back to PUBLISHED).
 */
export function availableReviewActions(status: ContentStatus | string | null | undefined): ReviewAction[] {
  switch (normalizeContentStatus(status)) {
    case 'SUBMITTED':
      return ['approve', 'reject', 'archive']
    case 'PUBLISHED':
      return ['reject', 'archive']
    case 'ARCHIVED':
      return ['restore']
    default:
      return ['archive']
  }
}
