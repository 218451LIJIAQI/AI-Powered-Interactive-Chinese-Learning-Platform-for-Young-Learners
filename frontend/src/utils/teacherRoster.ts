/**
 * Pure rules for the teacher's student roster (W4): the "Needs support" signal, per-lesson mastery rows and the
 * follow-up message suggestion. Kept free of Vue / i18n so they can be unit-tested.
 */
import type {
  LessonMastery,
  StudentSupportReason,
  TeacherFeedbackType,
  TeacherStudentLessonMasteryResponse,
  TeacherStudentSummaryResponse,
} from '@/types/api'
import { clampPercent } from '@/utils/format'
import { masteryRank, normalizeMastery } from '@/utils/mastery'

/** Canonical order of the server's support reasons (quiz average < 60 %, no activity for 14 days). */
const SUPPORT_REASONS: readonly StudentSupportReason[] = ['LOW_QUIZ_SCORE', 'INACTIVE']

type SupportSource = Pick<TeacherStudentSummaryResponse, 'needsSupport' | 'supportReasons'>

/** Known support reasons only, de-duplicated, in canonical order. */
export function studentSupportReasons(student: Partial<SupportSource> | null | undefined): StudentSupportReason[] {
  const reasons = new Set((student?.supportReasons ?? []).map((reason) => String(reason).trim().toUpperCase()))
  return SUPPORT_REASONS.filter((reason) => reasons.has(reason))
}

/**
 * "Needs support" comes ONLY from the server's `needsSupport` flag / `supportReasons`. Receiving teacher feedback
 * never flags a learner (the old latestFeedbackType/At heuristic is gone).
 */
export function studentNeedsSupport(student: Partial<SupportSource> | null | undefined): boolean {
  return student?.needsSupport === true || studentSupportReasons(student).length > 0
}

export interface LessonMasteryRow {
  lessonId: number
  lessonTitle: string
  completionPercent: number
  mastery: LessonMastery
  bestQuizPercentage: number | null
}

/** Normalised mastery rows, weakest first (mastery rank, then completion, then title). */
export function lessonMasteryRows(items: TeacherStudentLessonMasteryResponse[] | null | undefined): LessonMasteryRow[] {
  return (items ?? [])
    .map((item) => ({
      lessonId: item.lessonId,
      lessonTitle: item.lessonTitle?.trim() || `#${item.lessonId}`,
      completionPercent: clampPercent(item.completionPercent),
      mastery: normalizeMastery(item.mastery),
      bestQuizPercentage: item.bestQuizPercentage == null ? null : clampPercent(item.bestQuizPercentage),
    }))
    .sort(
      (left, right) =>
        masteryRank(left.mastery) - masteryRank(right.mastery) ||
        left.completionPercent - right.completionPercent ||
        left.lessonTitle.localeCompare(right.lessonTitle),
    )
}

/** Weak areas are lesson titles of recent wrong answers: trimmed, non-empty, de-duplicated. */
export function weakAreaLessons(weakAreas: string[] | null | undefined, limit = 4): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const entry of weakAreas ?? []) {
    const title = entry?.trim()
    if (!title || seen.has(title)) continue
    seen.add(title)
    result.push(title)
  }
  return result.slice(0, Math.max(0, limit))
}

export type FollowUpTone = 'ENCOURAGING' | 'SPECIFIC' | 'CORRECTIVE'
export type FollowUpFocus = 'WEAK_AREAS' | 'QUIZ_ACCURACY' | 'CONSISTENCY'

/** Feedback type stored with the follow-up note. */
export function followUpFeedbackType(tone: FollowUpTone, focus: FollowUpFocus): TeacherFeedbackType {
  if (tone === 'CORRECTIVE') return 'CORRECTIVE'
  if (focus === 'QUIZ_ACCURACY') return 'QUIZ'
  if (focus === 'WEAK_AREAS') return 'WEAK_AREA'
  return 'GENERAL'
}

export type Translate = (key: string, params?: Record<string, unknown>) => string

export interface FollowUpSuggestionInput {
  name: string
  tone: FollowUpTone
  focus: FollowUpFocus
  /** First weak-area lesson title, if any. */
  weakLesson?: string | null
  /** Already formatted quiz average (e.g. "54%"), or null when there are no attempts. */
  quizAverageLabel?: string | null
}

/**
 * Builds the suggested follow-up message. `translate` resolves keys of the teacherStudents bundle
 * (toneX, lessonFocus, quizAveragePossessive, recentActivities, consistentPractice, nextLessonTarget, draftMessage).
 */
export function buildFollowUpSuggestion(input: FollowUpSuggestionInput, translate: Translate): string {
  const lead =
    input.tone === 'CORRECTIVE'
      ? translate('toneCorrective')
      : input.tone === 'SPECIFIC'
        ? translate('toneSpecific')
        : translate('toneEncouraging')

  let focus: string
  if (input.focus === 'QUIZ_ACCURACY') {
    focus = input.quizAverageLabel
      ? translate('quizAveragePossessive', { average: input.quizAverageLabel })
      : translate('recentActivities')
  } else if (input.focus === 'CONSISTENCY') {
    focus = translate('consistentPractice')
  } else {
    const lesson = input.weakLesson?.trim()
    focus = lesson ? translate('lessonFocus', { lesson }) : translate('nextLessonTarget')
  }

  return translate('draftMessage', { lead, focus, name: input.name })
}

/**
 * A5 #11: a new suggestion may only replace the textarea when the teacher has not typed anything of their own -
 * the textarea is empty or still holds exactly the last generated suggestion.
 */
export function shouldApplySuggestion(currentMessage: string, lastSuggestion: string | null): boolean {
  if (!currentMessage.trim()) return true
  return lastSuggestion !== null && currentMessage === lastSuggestion
}
