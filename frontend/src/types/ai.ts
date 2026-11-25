/** Types of the AI Helper (student) and the admin AI area. Shapes shared with other modules stay in types/api.ts. */
import type {
  AiFeatureKey,
  AiResponseReportReason,
  AiResponseReportStatus,
  AiResultStatus,
  AiUsageFeatureKey,
  UserRole,
} from './api'
import type { PlanReason } from './plan'

export interface AiChatResponse {
  sessionId?: number | null
  /** The stored answer, named when the student reports it; null when SparkBot did not answer. */
  chatMessageId?: number | null
  aiResponse: string
  status: AiResultStatus
}

interface AiStoryVocabularyItemResponse {
  chineseWord: string
  pinyin?: string | null
  englishMeaning?: string | null
  bahasaMelayuMeaning?: string | null
}

interface AiStoryComprehensionQuestionResponse {
  question: string
  options: string[]
  answer: string
}

/**
 * Chinese with its pinyin and its translation into the student's language: a sentence of a story, or a line (or, for
 * a word, the meaning) in a talk with Xiaomei.
 */
export interface AiStoryLine {
  chinese: string
  pinyin?: string | null
  translation?: string | null
}

export interface AiStoryGeneratorResponse {
  title: string | null
  titleTranslation?: string | null
  lines: AiStoryLine[]
  vocabularyList: AiStoryVocabularyItemResponse[]
  comprehensionQuestions: AiStoryComprehensionQuestionResponse[]
  status: AiResultStatus
  /** Why there is no story (a fixed server message), when status is not SUCCESS. */
  message?: string | null
}

export interface StudentAiResponseReportResponse {
  id: number
  featureKey: AiFeatureKey
  reasonCategory: AiResponseReportReason
  promptSnapshot?: string | null
  aiResponseSnapshot?: string | null
  status: AiResponseReportStatus
  createdAt: string
  reviewedAt?: string | null
  /** The admin's note to the student, once the report is resolved or dismissed. */
  reviewerNote?: string | null
}

export interface AdminAiResponseReportResponse extends StudentAiResponseReportResponse {
  studentName: string
  studentEmail: string
  /** The student's own note about the report. */
  description?: string | null
  /** Who decided, once the report is resolved or dismissed. */
  reviewerName?: string | null
}

/** The AI Helper toolbar: what SparkBot can do now and today's request count. */
export interface AiHelperStatus {
  available: boolean
  speechCheckAvailable: boolean
  handwritingCheckAvailable: boolean
  audioUploadMaxBytes?: number
  imageUploadMaxBytes?: number
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
  /** A more natural Mandarin version of the student's last line; null when it was fine. */
  naturalVersion?: string | null
  /** A short note explaining why `naturalVersion` sounds more natural; null when none is needed. */
  tip?: string | null
}

export interface XiaomeiSummary {
  status: AiResultStatus
  message?: string | null
  goodbye?: AiStoryLine | null
  feedback?: string | null
  newExpressions: AiStoryLine[]
  wordsUsed: AiStoryLine[]
  /** What Xiaomei will remember; also listed when SparkBot could not write the rest (the plan's goal is no AI text). */
  memory?: XiaomeiMemoryNote[]
}

/**
 * One note Xiaomei remembers about the learner, used only in their own chats ("Xiaomei will remember"). The goal of the
 * learner's plan has `planReason`, and `text` is the plan's goal title (null for a plan built from rules, which the plan
 * pages name by the reason); any other note is a short fact from a chat, in `text`.
 */
export interface XiaomeiMemoryNote {
  id: number
  text: string | null
  planReason: PlanReason | null
}

export type PracticeCheckType = 'RECORDING' | 'HANDWRITING'

export type MandarinTone = 1 | 2 | 3 | 4

export function isMandarinTone(tone: unknown): tone is MandarinTone {
  return tone === 1 || tone === 2 || tone === 3 || tone === 4
}

export interface ToneComparison {
  text: string
  expectedTone: MandarinTone
  detectedTone: MandarinTone
}

export type PronunciationIssue =
  | (ToneComparison & { kind: 'TONE_MISMATCH' })
  | { text: string; kind: 'OMITTED' | 'MISPRONOUNCED'; expectedTone: null; detectedTone: null }

interface PronunciationFeedback {
  accuracy: number | null
  fluency: number | null
  completeness: number | null
  issues: PronunciationIssue[]
  toneComparisons?: ToneComparison[]
}

/** A pronunciation or handwriting practice score; only the student sees it. */
export interface PracticeCheck {
  id: number
  type: PracticeCheckType
  targetText: string
  lessonTitle?: string | null
  score: number
  /** Historical scores based on transcription. */
  clarityScore: number | null
  paceScore: number | null
  pronunciation?: PronunciationFeedback | null
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
  phrases: { chinese: string }[]
}

/** UTF-16 length limit for each prompt and response snapshot submitted with an AI report. */
export const AI_REPORT_SNAPSHOT_LIMIT = 4000

/** A student's report of an AI answer. */
export interface AiReportPayload {
  featureKey: Exclude<AiFeatureKey, 'QUIZ_DRAFT'>
  reasonCategory: AiResponseReportReason
  description?: string | null
  chatMessageId?: number
  practiceCheckId?: number
  promptSnapshot?: string
  aiResponseSnapshot?: string
}

export interface AdminAiUsageLogResponse {
  id: number
  userName: string
  /** Historical logs can have an unknown or missing role. */
  userRole: string | null
  featureKey: AiFeatureKey
  status: AiResultStatus
  errorSummary?: string | null
  createdAt: string
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
  featureType?: AiUsageFeatureKey | ''
  status?: AiResultStatus | ''
  userRole?: UserRole | ''
}
