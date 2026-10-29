import type { PillTone } from '@/components/common/StatusPill.vue'
import type { ContentStatus, LearningStage } from '@/types/api'
import type {
  ClassActivityType,
  FeedbackType,
  LearningType,
  MeetingPlatform,
  QuizSkill,
  RevisionStatus,
  SessionStatus,
} from '@/types/classes'
import { formatDate, parseDate } from '@/utils/format'

/*
 * Full message keys of the shared `classes` bundle (plugins/classesMessages.ts), so every key stays findable.
 */

export const LEARNING_TYPES: readonly LearningType[] = ['RECORDED', 'LIVE', 'HYBRID']

export const LEARNING_TYPE_KEYS: Record<LearningType, string> = {
  RECORDED: 'classes.learningType.RECORDED',
  LIVE: 'classes.learningType.LIVE',
  HYBRID: 'classes.learningType.HYBRID',
}

export const STAGE_KEYS: Record<LearningStage, string> = {
  GUIDED: 'classes.stage.GUIDED',
  SCAFFOLDED: 'classes.stage.SCAFFOLDED',
  INDEPENDENT: 'classes.stage.INDEPENDENT',
}

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

export const SESSION_STATUS_KEYS: Record<SessionStatus, string> = {
  SCHEDULED: 'classes.session.SCHEDULED',
  RESCHEDULED: 'classes.session.RESCHEDULED',
  FULL: 'classes.session.FULL',
  CANCELLED: 'classes.session.CANCELLED',
  COMPLETED: 'classes.session.COMPLETED',
}

export const SESSION_STATUS_TONES: Record<SessionStatus, PillTone> = {
  SCHEDULED: 'info',
  RESCHEDULED: 'info',
  FULL: 'warning',
  CANCELLED: 'danger',
  COMPLETED: 'success',
}

export const PLATFORM_KEYS: Record<MeetingPlatform, string> = {
  ZOOM: 'classes.platform.ZOOM',
  GOOGLE_MEET: 'classes.platform.GOOGLE_MEET',
  MICROSOFT_TEAMS: 'classes.platform.MICROSOFT_TEAMS',
  OTHER: 'classes.platform.OTHER',
}

export const FEEDBACK_TYPES: readonly FeedbackType[] = ['ENCOURAGING', 'LESSON', 'WEAK_AREA', 'CORRECTIVE', 'PROGRESS']

export const FEEDBACK_TYPE_KEYS: Record<FeedbackType, string> = {
  ENCOURAGING: 'classes.feedbackType.ENCOURAGING',
  LESSON: 'classes.feedbackType.LESSON',
  WEAK_AREA: 'classes.feedbackType.WEAK_AREA',
  CORRECTIVE: 'classes.feedbackType.CORRECTIVE',
  PROGRESS: 'classes.feedbackType.PROGRESS',
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

const CONTENT_STATUS_KEYS: Record<ContentStatus, string> = {
  DRAFT: 'classes.status.DRAFT',
  SUBMITTED: 'classes.status.SUBMITTED',
  PUBLISHED: 'classes.status.PUBLISHED',
  REJECTED: 'classes.status.REJECTED',
  ARCHIVED: 'classes.status.ARCHIVED',
}

const REVISION_KEYS: Record<RevisionStatus, string> = {
  DRAFT: 'classes.status.revisionDraft',
  SUBMITTED: 'classes.status.revisionSubmitted',
  REJECTED: 'classes.status.revisionRejected',
}

/** Label key and tone of a class or lesson: its status, or "Published · Revision: …" while a change is pending. */
export function contentStatusView(
  status: ContentStatus,
  revisionStatus: RevisionStatus | null = null,
): { key: string; tone: PillTone; icon: string } {
  if (status === 'PUBLISHED' && revisionStatus) {
    return {
      key: REVISION_KEYS[revisionStatus],
      tone: revisionStatus === 'REJECTED' ? 'warning' : revisionStatus === 'SUBMITTED' ? 'info' : 'neutral',
      icon: revisionStatus === 'REJECTED' ? 'alert' : revisionStatus === 'SUBMITTED' ? 'history' : 'edit',
    }
  }
  const tones: Record<ContentStatus, PillTone> = {
    DRAFT: 'neutral',
    SUBMITTED: 'info',
    PUBLISHED: 'success',
    REJECTED: 'warning',
    ARCHIVED: 'neutral',
  }
  const icons: Record<ContentStatus, string> = {
    DRAFT: 'edit',
    SUBMITTED: 'history',
    PUBLISHED: 'check',
    REJECTED: 'alert',
    ARCHIVED: 'archive',
  }
  return { key: CONTENT_STATUS_KEYS[status], tone: tones[status], icon: icons[status] }
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

/** One calendar day of a timetable: the sessions that start on it, in the order given. */
interface SessionDay<T> {
  key: string
  /** The start of the day's first session, for the day heading. */
  at: string
  items: T[]
}

/** Groups sessions (already in time order, either way) by the local calendar day they start on. */
export function groupByDay<T extends { startsAt: string }>(sessions: readonly T[]): SessionDay<T>[] {
  const days: SessionDay<T>[] = []
  for (const session of sessions) {
    const key = parseDate(session.startsAt)?.toDateString() ?? session.startsAt
    const last = days[days.length - 1]
    if (last && last.key === key) last.items.push(session)
    else days.push({ key, at: session.startsAt, items: [session] })
  }
  return days
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

/** Share of the seats that are booked, 0–100 (0 when the session has no seat limit). */
export function bookedPercent(booked: number, capacity: number | null): number {
  if (!capacity) return 0
  return Math.min(100, Math.round((booked / capacity) * 100))
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
