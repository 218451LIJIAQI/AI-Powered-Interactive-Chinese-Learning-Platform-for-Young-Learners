/** Types of the AI Helper (student) and the admin AI area. Shapes shared with other modules stay in types/api.ts. */
import type { AiFeatureKey, AiStoryLine } from './api'

/** SUCCESS, or why there is no AI answer: FAILED (blocked, timed out, unusable reply) or UNAVAILABLE. */
export type AiResultStatus = 'SUCCESS' | 'FAILED' | 'UNAVAILABLE'

/** The AI Helper toolbar: what SparkBot can do now and today's request count. */
export interface AiHelperStatus {
  available: boolean
  speechCheckAvailable: boolean
  handwritingCheckAvailable: boolean
  usedToday: number
  dailyLimit: number
  /** The admin's AI notice, shown with every AI answer. */
  aiNotice: string
}

export type XiaomeiScenario = 'MEET_XIAOMEI' | 'CAFE' | 'ASK_THE_WAY' | 'WEEKEND_PLANS' | 'PASAR_MALAM' | 'CAMPUS_TOUR'

export interface XiaomeiTurn {
  speaker: 'XIAOMEI' | 'STUDENT'
  text: string
}

export interface XiaomeiReply {
  status: AiResultStatus
  /** Why there is no reply (a fixed server message), when status is not SUCCESS. */
  message?: string | null
  reply?: AiStoryLine | null
  /** A more natural version of the student's last line, with the reason; null when it was fine. */
  naturalVersion?: string | null
  tip?: string | null
}

export interface XiaomeiSuggestions {
  status: AiResultStatus
  message?: string | null
  suggestions: AiStoryLine[]
}

export interface XiaomeiSummary {
  status: AiResultStatus
  message?: string | null
  goodbye?: AiStoryLine | null
  feedback?: string | null
  newExpressions: AiStoryLine[]
  wordsUsed: AiStoryLine[]
}

export type PracticeCheckType = 'RECORDING' | 'HANDWRITING'

/** A pronunciation or handwriting practice score; only the student sees it. */
export interface PracticeCheck {
  id: number
  type: PracticeCheckType
  targetText: string
  lessonTitle?: string | null
  score: number
  /** Recordings only. */
  tonesScore?: number | null
  clarityScore?: number | null
  paceScore?: number | null
  headline: string
  feedback?: string | null
  tips: string[]
  checkedAt: string
}

export interface PracticeCheckResult {
  status: AiResultStatus
  message?: string | null
  check?: PracticeCheck | null
}

export interface PracticeCheckLesson {
  id: number
  title: string
}

/** A student's report of an AI answer. */
export interface AiReportPayload {
  featureKey: AiFeatureKey
  reasonCategory: string
  description?: string
  chatMessageId?: number
  practiceCheckId?: number
  promptSnapshot?: string
  aiResponseSnapshot?: string
}

export interface AdminAiReportCounts {
  open: number
  resolved: number
  dismissed: number
}

export interface AdminAiUsageSummary {
  totalRequests: number
  successful: number
  unavailable: number
  failed: number
  byFeature: { featureKey: AiFeatureKey; requests: number }[]
}

export interface AdminAiSettings {
  aiNoticeText: string
  sensitiveWordFilter: boolean
  aiSafetyCheck: boolean
  requestsPerMinute: number
  requestsPerDay: number
  lastChangedAt?: string | null
  lastChangedBy?: string | null
}

export type AdminAiSettingsUpdate = Pick<AdminAiSettings, 'aiNoticeText' | 'sensitiveWordFilter' | 'aiSafetyCheck'>

/** The admin request-log filters; an empty value means "all". */
export interface AdminAiUsageFilters {
  featureType?: AiFeatureKey | ''
  status?: AiResultStatus | ''
  userRole?: 'STUDENT' | 'TEACHER' | ''
}
