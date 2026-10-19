import type { ContentStatus } from '@/types/api'

/** The five states the UI ever shows (D4, D14). `APPROVED` is a legacy database value shown as ARCHIVED. */
export type DisplayContentStatus = Exclude<ContentStatus, 'APPROVED'>
export type ContentStatusTone = 'success' | 'warning' | 'danger' | 'info' | 'primary'
export type ContentStatusMessageKey =
  | 'statusDraft'
  | 'statusSubmitted'
  | 'statusPublished'
  | 'statusRejected'
  | 'statusArchived'

const STATUS_TONES: Record<DisplayContentStatus, ContentStatusTone> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  PUBLISHED: 'success',
  REJECTED: 'danger',
  ARCHIVED: 'primary',
}

const STATUS_MESSAGE_KEYS: Record<DisplayContentStatus, ContentStatusMessageKey> = {
  DRAFT: 'statusDraft',
  SUBMITTED: 'statusSubmitted',
  PUBLISHED: 'statusPublished',
  REJECTED: 'statusRejected',
  ARCHIVED: 'statusArchived',
}

/** Maps any backend value (including unknown or legacy ones) onto a displayable review state. */
export function normalizeContentStatus(status: ContentStatus | string | null | undefined): DisplayContentStatus {
  switch (status) {
    case 'SUBMITTED':
    case 'PUBLISHED':
    case 'REJECTED':
    case 'ARCHIVED':
      return status
    case 'APPROVED':
      return 'ARCHIVED'
    default:
      return 'DRAFT'
  }
}

export function contentStatusTone(status: ContentStatus | string | null | undefined): ContentStatusTone {
  return STATUS_TONES[normalizeContentStatus(status)]
}

export function contentStatusMessageKey(status: ContentStatus | string | null | undefined): ContentStatusMessageKey {
  return STATUS_MESSAGE_KEYS[normalizeContentStatus(status)]
}

/** DRAFT and REJECTED items can be submitted (or resubmitted) for admin review. */
export function canSubmitForReview(status: ContentStatus | string | null | undefined): boolean {
  const normalized = normalizeContentStatus(status)
  return normalized === 'DRAFT' || normalized === 'REJECTED'
}

/** SUBMITTED items are under review and ARCHIVED items are closed: teachers cannot edit either. */
export function isTeacherEditLocked(status: ContentStatus | string | null | undefined): boolean {
  const normalized = normalizeContentStatus(status)
  return normalized === 'SUBMITTED' || normalized === 'ARCHIVED'
}

/** Saving a PUBLISHED item sends it back to SUBMITTED and hides it from students until approved again (D24). */
export function editReturnsToReview(status: ContentStatus | string | null | undefined): boolean {
  return normalizeContentStatus(status) === 'PUBLISHED'
}
