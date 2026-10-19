/**
 * Pure rules for the teacher's practice-submission review (D7 / D7a): filters, the feedback-message validation
 * (5..2000 characters, same as the backend) and a local page helper for preview fixtures.
 */
import type {
  PageResponse,
  PracticeReviewStatus,
  PracticeSubmissionResponse,
  PracticeSubmissionType,
  TeacherFeedbackType,
} from '@/types/api'

export type PracticeReviewFilter = PracticeReviewStatus | 'ALL'

/** SUBMITTED (awaiting review) is the default, as on the server. */
export const PRACTICE_REVIEW_FILTERS: readonly PracticeReviewFilter[] = ['SUBMITTED', 'REVIEWED', 'ALL']

export const TEACHER_FEEDBACK_TYPES: readonly TeacherFeedbackType[] = [
  'GENERAL',
  'ENCOURAGING',
  'CORRECTIVE',
  'PROGRESS',
  'WEAK_AREA',
  'LESSON',
  'QUIZ',
]

export const PRACTICE_FEEDBACK_MIN_LENGTH = 5
export const PRACTICE_FEEDBACK_MAX_LENGTH = 2000

export type PracticeFeedbackValidation = 'required' | 'tooShort' | 'tooLong' | null

/** Mirrors the backend rule: message is required and 5..2000 characters after trimming. */
export function validatePracticeFeedbackMessage(message: string | null | undefined): PracticeFeedbackValidation {
  const trimmed = (message ?? '').trim()
  if (!trimmed) return 'required'
  if (trimmed.length < PRACTICE_FEEDBACK_MIN_LENGTH) return 'tooShort'
  if (trimmed.length > PRACTICE_FEEDBACK_MAX_LENGTH) return 'tooLong'
  return null
}

/** Audio for pronunciation recordings, image for handwriting uploads (falls back to the stored content type). */
export function practiceMediaKind(
  submission: Pick<PracticeSubmissionResponse, 'submissionType' | 'contentType'>,
): 'audio' | 'image' {
  const type: PracticeSubmissionType = submission.submissionType
  if (type === 'PRONUNCIATION_AUDIO') return 'audio'
  if (type === 'HANDWRITING_IMAGE') return 'image'
  return submission.contentType?.startsWith('audio/') ? 'audio' : 'image'
}

export function matchesPracticeReviewFilter(
  submission: Pick<PracticeSubmissionResponse, 'reviewStatus'>,
  filter: PracticeReviewFilter,
): boolean {
  return filter === 'ALL' || submission.reviewStatus === filter
}

/** Pages preview fixtures the way the server does (newest first, 0-based pages). */
export function pagePracticeSubmissions(
  items: PracticeSubmissionResponse[],
  filter: PracticeReviewFilter,
  page: number,
  size: number,
): PageResponse<PracticeSubmissionResponse> {
  const matching = items
    .filter((item) => matchesPracticeReviewFilter(item, filter))
    .sort((left, right) => new Date(right.submittedAt).getTime() - new Date(left.submittedAt).getTime())
  const safeSize = Math.max(1, Math.floor(size))
  const totalPages = Math.max(1, Math.ceil(matching.length / safeSize))
  const safePage = Math.min(Math.max(0, Math.floor(page)), totalPages - 1)
  return {
    items: matching.slice(safePage * safeSize, safePage * safeSize + safeSize),
    page: safePage,
    size: safeSize,
    totalItems: matching.length,
    totalPages,
  }
}

/** Replaces one submission in a page with the server's updated copy (e.g. REVIEWED after feedback). */
export function replacePracticeSubmission(
  page: PageResponse<PracticeSubmissionResponse>,
  updated: PracticeSubmissionResponse,
): PageResponse<PracticeSubmissionResponse> {
  return { ...page, items: page.items.map((item) => (item.id === updated.id ? updated : item)) }
}
