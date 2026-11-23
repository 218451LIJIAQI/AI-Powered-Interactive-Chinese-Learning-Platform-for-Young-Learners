/*
 * Frontend mirror of the backend REST API contracts that several areas share. Response types list only the fields the
 * frontend reads; the server may send more, which the client simply ignores.
 */
/** BCP-47 interface languages (FRLANG01). */
export const APP_LANGUAGES = ['en', 'ms', 'zh-CN'] as const
export type AppLanguage = (typeof APP_LANGUAGES)[number]
/** STUDENT is the learner role (the interface calls it "Learner"); there are no teacher accounts. */
export const USER_ROLES = ['STUDENT', 'ADMIN'] as const
export type UserRole = (typeof USER_ROLES)[number]
/**
 * Publishing states of platform content (FRCONT06, FRADM04): DRAFT -> PUBLISHED (an admin publishes; there is no
 * review step), DRAFT | PUBLISHED -> ARCHIVED -> the status before archive.
 */
export const CONTENT_STATUSES = ['PUBLISHED', 'DRAFT', 'ARCHIVED'] as const
export type ContentStatus = (typeof CONTENT_STATUSES)[number]
/** Optional scaffolding stage of a quiz or practice activity (FRCONT10). */
export type LearningStage = 'GUIDED' | 'SCAFFOLDED' | 'INDEPENDENT'
/** Open until an admin resolves it (the answer was wrong and the admin acted) or dismisses it (it was fine). */
export type AiResponseReportStatus = 'PENDING_REVIEW' | 'RESOLVED' | 'DISMISSED'
export type AiResponseReportReason =
  | 'INCORRECT_CONTENT'
  | 'UNCLEAR_EXPLANATION'
  | 'INAPPROPRIATE_CONTENT'
  | 'SCORE_SEEMS_WRONG'
  /** An AI-written plan text that does not match what the learner wrote in the setup (X2.23). */
  | 'DOES_NOT_MATCH_INPUT'
  | 'OTHER'
/** AI feature keys used by reports and usage filters. Rule-based hints can be reported without a provider request. */
export const AI_FEATURES = [
  'CHAT_TUTOR',
  'XIAOMEI_CONVERSATION',
  'QUIZ_GENERATOR',
  'STORY_GENERATOR',
  'PRACTICE_CHECK',
  'QUIZ_DRAFT',
  'LEARNING_PLAN',
  'HINT',
] as const
export type AiFeatureKey = (typeof AI_FEATURES)[number]
/** Outcomes of a provider-backed AI request, including a provider that is disabled or unavailable. */
export const AI_RESULT_STATUSES = ['SUCCESS', 'FAILED', 'UNAVAILABLE'] as const
export type AiResultStatus = (typeof AI_RESULT_STATUSES)[number]
export type LessonProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
export type NotificationStatus = 'UNREAD' | 'READ'
/** Class quiz question types in the order they appear in the lesson editor. */
export const QUIZ_QUESTION_TYPES = [
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'FILL_BLANK',
  'MATCH_WORD_MEANING',
  'MATCH_WORD_IMAGE',
  'LISTEN_CHOOSE',
  'REARRANGE_SENTENCE',
  'CHOOSE_PINYIN',
  'CHOOSE_TONE',
  'CHARACTER_MEANING',
] as const
export type QuizQuestionType = (typeof QUIZ_QUESTION_TYPES)[number]

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

/** A class topic (`/public/categories`). */
export interface ClassTopic {
  id: number
  name: string
}

export interface AuthResponse {
  token: string
}

export interface CurrentUserResponse {
  id: number
  fullName: string
  email: string
  /** en | ms | zh-CN */
  preferredLanguage?: string | null
  roles: UserRole[]
  studentProfile?: {
    totalPoints: number
  } | null
}

export interface NotificationResponse {
  id: number
  title: string
  message: string
  type: string
  status: NotificationStatus
  targetPath: string | null
  createdAt: string
}

export interface NotificationUnreadCountResponse {
  unreadCount: number
}

/** `/health`: whether the server and the database work (`status`) and whether the AI provider is configured. */
export interface HealthStatusResponse {
  status: 'UP' | 'DOWN'
  ai: 'AVAILABLE' | 'UNAVAILABLE'
}

export interface RewardSummaryResponse {
  totalPoints: number
  currentStreak: number
}

export interface AiAvailabilityResponse {
  available: boolean
}

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
  title: string
  titleTranslation?: string | null
  lines: AiStoryLine[]
  vocabularyList: AiStoryVocabularyItemResponse[]
  comprehensionQuestions: AiStoryComprehensionQuestionResponse[]
  status: AiResultStatus
}

interface AiGeneratedQuizQuestionResponse {
  questionText: string
  questionType: string
  options: string[]
  correctAnswer: string
  explanation?: string | null
  pinyin?: string | null
  translation?: string | null
}

export interface AiQuizGeneratorResponse {
  title: string
  questions: AiGeneratedQuizQuestionResponse[]
  status: AiResultStatus
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

export interface PublicPlatformSettingsResponse {
  aiDisclaimerText: string
  /** Configured AI model (`app.ai.model-name`); null when none is set. */
  aiModelName: string | null
  /**
   * The provider AI requests go to (`app.ai.provider` in lower case: openai, deepseek or openai-compatible); null while
   * no request can reach one (the AI Helper is off or not fully configured).
   */
  aiProvider: string | null
}

/** Each granted role contributes the counts for its sidebar (`GET /users/me/nav-counts`). */
export interface NavCounts {
  readonly mistakes?: number
  readonly aiReports?: number
  readonly supportOpen?: number
}
