/*
 * Frontend mirror of the backend REST API contracts that several areas share. Response types list only the fields the
 * frontend reads; the server may send more, which the client simply ignores.
 */
/** BCP-47 interface languages (FRLANG01). Old accounts may still return a legacy Chinese code; see utils/locale.ts. */
export type AppLanguage = 'en' | 'ms' | 'zh-CN'
export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'
export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
/**
 * Review workflow states (FRCONT06, FRADM04): DRAFT -> SUBMITTED -> PUBLISHED | REJECTED,
 * DRAFT | PUBLISHED | REJECTED -> ARCHIVED -> the status before archive.
 */
export type ContentStatus = 'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'PUBLISHED' | 'ARCHIVED'
/** Optional scaffolding stage of a quiz or practice activity (FRCONT10). */
export type LearningStage = 'GUIDED' | 'SCAFFOLDED' | 'INDEPENDENT'
/** What an item of the admin review queue is: a class (PACKAGE) or a lesson. */
export type ReviewContentType = 'PACKAGE' | 'LESSON'
/** Open until an admin resolves it (the answer was wrong and the admin acted) or dismisses it (it was fine). */
export type AiResponseReportStatus = 'PENDING_REVIEW' | 'RESOLVED' | 'DISMISSED'
export type AiResponseReportReason =
  | 'INCORRECT_CONTENT'
  | 'UNCLEAR_EXPLANATION'
  | 'INAPPROPRIATE_CONTENT'
  | 'SCORE_SEEMS_WRONG'
  | 'OTHER'
/** The AI features whose requests are logged; LEARNING_PLAN is logged by the progress module. */
export type AiFeatureKey =
  | 'CHAT_TUTOR'
  | 'XIAOMEI_CONVERSATION'
  | 'QUIZ_GENERATOR'
  | 'STORY_GENERATOR'
  | 'PRACTICE_CHECK'
  | 'TEACHER_DRAFT'
  | 'LEARNING_PLAN'
/** Lesson mastery indicator (FRPROG02): MASTERED = lesson completed and its best quiz scores average >= 80 %. */
export type LessonMastery = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'MASTERED'
export type NotificationStatus = 'UNREAD' | 'READ'
export type QuizQuestionType =
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'FILL_BLANK'
  | 'MATCH_WORD_MEANING'
  | 'MATCH_WORD_IMAGE'
  | 'LISTEN_CHOOSE'
  | 'REARRANGE_SENTENCE'
  | 'CHOOSE_PINYIN'
  | 'CHOOSE_TONE'
  | 'CHARACTER_MEANING'

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

/** A learning level (`/public/levels`); categories (`/public/categories`) have the same shape. */
export interface LearningLevel {
  id: number
  name: string
  description: string
}

export interface AuthResponse {
  token: string
}

export interface RegistrationResponse {
  email: string
}

export interface CurrentUserResponse {
  id: number
  fullName: string
  email: string
  /** en | ms | zh-CN; legacy accounts may return the old Chinese code (normalise with normalizeAppLanguage). */
  preferredLanguage?: string | null
  roles: UserRole[]
  studentProfile?: {
    totalPoints: number
  } | null
  teacherProfile?: {
    teacherStatus: TeacherStatus
    /** Where the application stands: documents not sent yet, waiting for an admin, or sent back with a reason. */
    verificationState: TeacherVerificationState
    /** When an admin approved the teacher (ISO date-time), for the sidebar's "Verified teacher" card. */
    approvedAt?: string | null
  } | null
}

export type TeacherVerificationState = 'NOT_SUBMITTED' | 'IN_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED'

export interface NotificationResponse {
  id: number
  title: string
  message: string
  type: string
  status: NotificationStatus
  targetPath?: string | null
  createdAt: string
  readAt?: string | null
}

export interface NotificationUnreadCountResponse {
  unreadCount: number
}

/** `/health`: the server, the database and whether the AI provider is configured. */
export interface HealthStatusResponse {
  status: 'UP' | 'DOWN' | string
  timestamp: string
  database?: 'UP' | 'DOWN'
  ai?: 'AVAILABLE' | 'UNAVAILABLE'
}

export interface RewardSummaryResponse {
  totalPoints: number
  currentStreak: number
}

export interface AnalyticsTrendPoint {
  label: string
  value: number
}

export interface AiAvailabilityResponse {
  available: boolean
}

export interface AiChatResponse {
  sessionId?: number | null
  /** The stored answer, named when the student reports it; null when SparkBot did not answer. */
  chatMessageId?: number | null
  aiResponse: string
  status: string
}

interface AiStoryVocabularyItemResponse {
  chineseWord?: string | null
  pinyin?: string | null
  englishMeaning?: string | null
  bahasaMelayuMeaning?: string | null
}

interface AiStoryComprehensionQuestionResponse {
  question?: string | null
  options: string[]
  answer?: string | null
  explanation?: string | null
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
  title: string
  titleTranslation?: string | null
  lines: AiStoryLine[]
  vocabularyList: AiStoryVocabularyItemResponse[]
  comprehensionQuestions: AiStoryComprehensionQuestionResponse[]
  status: string
}

interface AiGeneratedQuizQuestionResponse {
  questionText?: string | null
  questionType?: string | null
  options: string[]
  correctAnswer?: string | null
  explanation?: string | null
  pinyin?: string | null
  translation?: string | null
}

export interface AiQuizGeneratorResponse {
  title: string
  topic: string
  questions: AiGeneratedQuizQuestionResponse[]
  status: string
}

export interface StudentAiResponseReportResponse {
  id: number
  featureKey: string
  reasonCategory: AiResponseReportReason
  description?: string | null
  promptSnapshot?: string | null
  aiResponseSnapshot?: string | null
  status: AiResponseReportStatus
  createdAt: string
  reviewedAt?: string | null
  /** The admin's note to the student, once the report is resolved or dismissed. */
  reviewerNote?: string | null
}

export interface AdminAiResponseReportResponse extends StudentAiResponseReportResponse {
  studentName?: string | null
  studentEmail?: string | null
  reviewerName?: string | null
}

export interface AdminAiUsageLogResponse {
  id: number
  userName?: string | null
  userRole?: string | null
  featureKey: string
  status: string
  errorSummary?: string | null
  createdAt: string
}

export interface PublicPlatformSettingsResponse {
  aiDisclaimerText: string
  /** Configured AI model (`app.ai.model-name`); null when none is set. */
  aiModelName?: string | null
}
