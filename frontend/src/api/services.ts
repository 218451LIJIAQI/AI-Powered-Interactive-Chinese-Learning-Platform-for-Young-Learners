/**
 * Typed thin-wrappers around every backend REST endpoint, grouped by
 * domain. Pages call these methods instead of raw `http.*` calls so
 * endpoint paths, request shapes, response unwrapping, downloads, and
 * AI streaming error handling stay in one place.
 */
import type { AxiosResponse } from 'axios'

import { getAccessToken, http, invalidateAuthSession, refreshAccessToken } from './http'
import { appEnv } from '@/config/env'
import type {
  AdminAnalyticsResponse,
  AdminActivitySummaryResponse,
  AdminActivityType,
  AdminAiResponseReportResponse,
  AdminAiUsageLogResponse,
  AdminDragDropActivityResponse,
  AdminFlashcardResponse,
  AdminListeningExerciseResponse,
  AdminMatchingGameResponse,
  AdminPlatformSettingsResponse,
  AdminPlatformSettingsUpdateRequest,
  AdminStoryActivityResponse,
  AdminDashboardResponse,
  AdminStudentListItemResponse,
  AdminTeacherDetailResponse,
  AdminTeacherListItemResponse,
  AdminTracingExerciseResponse,
  AdminUserListItemResponse,
  ActivityDifficulty,
  AiAvailabilityResponse,
  AiChatResponse,
  AiFeatureKey,
  AiPronunciationFeedbackResponse,
  AiQuizGeneratorResponse,
  AiResponseReportReason,
  AiResponseReportReviewStatus,
  AiSentencePracticeResponse,
  AiStoryGeneratorResponse,
  AiVocabularyHelperResponse,
  AiWritingFeedbackResponse,
  ApiResponse,
  AppLanguage,
  AuditLogResponse,
  AuthResponse,
  BadgeSummaryResponse,
  BulkOperationResponse,
  ContentReviewDetailResponse,
  ContentReviewItemResponse,
  ContentSubmissionDetailResponse,
  ContentSubmissionResponse,
  ContentStatus,
  CoursePackageResponse,
  CurrentUserResponse,
  DailyStreakResponse,
  HealthStatusResponse,
  LearningCategory,
  LearningLevel,
  LearningStage,
  LearningType,
  LessonResponse,
  LessonVideoProgressSummaryResponse,
  LiveClassEnrolmentResponse,
  LiveClassResponse,
  LiveClassStatus,
  NotificationResponse,
  NotificationUnreadCountResponse,
  PageResponse,
  PointsTransactionResponse,
  PublicCourseDetail,
  PublicCourseSummary,
  PublicPlatformSettingsResponse,
  QuizAttemptResultResponse,
  QuizQuestionType,
  RecordedLessonVideoResponse,
  RegistrationResponse,
  ReviewContentType,
  RewardSummaryResponse,
  StudentActivityCompletionResponse,
  StudentActivitySummaryResponse,
  StudentDashboardResponse,
  StudentDragDropActivityResponse,
  StudentFlashcardResponse,
  StudentLessonPlayerResponse,
  StudentListeningExerciseResponse,
  StudentMatchingGameResponse,
  StudentProgressSummaryResponse,
  StudentQuizResponse,
  StudentQuizSummaryResponse,
  StudentStoryActivityResponse,
  StudentTeacherFeedbackResponse,
  StudentTracingExerciseResponse,
  TeacherAnalyticsResponse,
  TeacherDashboardResponse,
  TeacherFeedbackResponse,
  TeacherProfileResponse,
  TeacherQuizAttemptSummaryResponse,
  TeacherQuizResponse,
  TeacherStudentSummaryResponse,
  TeacherVerificationStatusResponse,
  VideoProgressResponse,
  WrongAnswerResponse,
  LearningModuleResponse,
  ModerationRecordResponse,
  StudentAiResponseReportResponse,
  DragDropCompletionRequest,
  ListeningCompletionRequest,
  MatchingCompletionRequest,
  NotificationStatus,
  PracticeReviewStatus,
  PracticeSubmissionResponse,
  PracticeSubmissionType,
  QuizAnswerSubmission,
  QuizHintResponse,
  StoryCompletionRequest,
  StudentLessonProgressResponse,
  TeacherFeedbackType,
  TracingCompletionRequest,
} from '@/types/api'
import { apiClientMessage } from '@/plugins/apiClientMessages'
import type { AdminUserQueryParams } from '@/utils/adminUserDirectory'
import { normalizeAppLanguage } from '@/utils/locale'

const AI_CHAT_STREAM_PATH = '/ai/chat/stream'
const AI_STREAM_CONNECT_TIMEOUT_MS = 30_000
const API_BASE_TRAILING_SLASH_PATTERN = /\/$/
const AUTHORIZATION_HEADER = 'Authorization'
const CONTENT_DISPOSITION_ASCII_FILENAME_PATTERN = /filename="?([^";]+)"?/i
const CONTENT_DISPOSITION_UTF8_FILENAME_PATTERN = /filename\*=UTF-8''([^;\s]+)/i
const CONTENT_TYPE_HEADER = 'content-type'
const JSON_CONTENT_TYPE = 'application/json'
const REQUEST_CONTENT_TYPE_HEADER = 'Content-Type'
const SSE_ACCEPT_HEADER = 'Accept'
const SSE_CONTENT_TYPE = 'text/event-stream'
const SSE_DATA_PREFIX = 'data:'
const SSE_DEFAULT_EVENT = 'message'
const SSE_DELIMITER_PATTERN = /\r?\n\r?\n/
const SSE_EVENT_COMPLETE = 'complete'
const SSE_EVENT_DELTA = 'delta'
const SSE_EVENT_ERROR = 'error'
const SSE_EVENT_PREFIX = 'event:'
const SSE_LINE_PATTERN = /\r?\n/

/* Client-side fallback messages are localised (EN / MS / zh-CN) in plugins/apiClientMessages.ts. */
const API_ENVELOPE_ERROR_MESSAGES = {
  get rejected() {
    return apiClientMessage('rejected')
  },
  get unexpectedFormat() {
    return apiClientMessage('unexpectedFormat')
  },
  get missingData() {
    return apiClientMessage('missingData')
  },
}

const AI_STREAM_ERROR_MESSAGES = {
  get fallback() {
    return apiClientMessage('streamFallback')
  },
  get incomplete() {
    return apiClientMessage('streamIncomplete')
  },
  get malformedComplete() {
    return apiClientMessage('streamMalformedComplete')
  },
  get missingBody() {
    return apiClientMessage('streamMissingBody')
  },
  get rateLimited() {
    return apiClientMessage('streamRateLimited')
  },
  get serverTrouble() {
    return apiClientMessage('streamServerTrouble')
  },
  get signInAgain() {
    return apiClientMessage('streamSignInAgain')
  },
  get stopped() {
    return apiClientMessage('streamStopped')
  },
  get timeout() {
    return apiClientMessage('streamTimeout')
  },
}

type FieldErrors = Record<string, string[]>

type ApiEnvelopeData = {
  message: string
  errors: string[]
  fieldErrors: FieldErrors
}

type ApiEnvelopeErrorResponse = {
  status?: number
  data: ApiEnvelopeData
}

type AiStreamHandlers = {
  onDelta?: (chunk: string) => void
  onComplete?: (payload: AiChatResponse) => void
  /** Cancels the request and stops reading the stream; the call then rejects with a DOMException named `AbortError`. */
  signal?: AbortSignal
}

type DownloadResult = {
  blob: Blob
  contentType: string | null
  fileName: string | null
}

type ApiRequest<T> = Promise<{ data: ApiResponse<T> }>
type ContentPublicationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
type ModerationAction = 'hide' | 'unpublish' | 'archive' | 'request-revision' | 'restore'
type ModerationPayload = { targetType: string; targetId: number; reason: string }
type QueryParams = Record<string, unknown>
type StudentQueryParams = Record<string, string | number | undefined>

/** Backend page-size ceiling (PaginationUtils.DEFAULT_MAX_PAGE_SIZE); larger sizes are clamped by the server. */
const MAX_PAGE_SIZE = 100
const STUDENT_LIVE_CLASS_PAGE_SIZE = MAX_PAGE_SIZE
const STUDENT_LIVE_CLASS_MAX_PAGES = 20

/** Teacher lists are shown without paging, so ask for the largest page instead of the server default of 20. */
const maxPageConfig = () => ({ params: { size: MAX_PAGE_SIZE } })

const MODERATION_ACTION_ENDPOINTS = {
  archive: '/admin/moderation/archive',
  hide: '/admin/moderation/hide',
  'request-revision': '/admin/moderation/request-revision',
  restore: '/admin/moderation/restore',
  unpublish: '/admin/moderation/unpublish',
} satisfies Record<ModerationAction, string>

class ApiEnvelopeError extends Error {
  response: ApiEnvelopeErrorResponse

  constructor(message: string, errors: string[] = [], status?: number, fieldErrors: FieldErrors = {}) {
    super(message)
    this.name = 'ApiEnvelopeError'
    this.response = {
      status,
      data: {
        message,
        errors,
        fieldErrors,
      },
    }
  }
}

function cleanErrorMessage(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function cleanErrorList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.map(cleanErrorMessage).filter((message): message is string => Boolean(message))
    : []
}

function cleanFieldErrors(value: unknown): FieldErrors {
  if (!value || typeof value !== 'object') {
    return {}
  }

  return Object.fromEntries(
    Object.entries(value)
      .map(([field, messages]) => [field, cleanErrorList(messages)] as const)
      .filter(([, messages]) => messages.length > 0),
  )
}

function fallbackSseHttpMessage(status: number): string {
  if (status === 401) return AI_STREAM_ERROR_MESSAGES.signInAgain
  if (status === 429) return AI_STREAM_ERROR_MESSAGES.rateLimited
  if (status >= 500) return AI_STREAM_ERROR_MESSAGES.serverTrouble
  return AI_STREAM_ERROR_MESSAGES.fallback
}

async function sseHttpError(response: Response): Promise<ApiEnvelopeError> {
  const fallbackMessage = fallbackSseHttpMessage(response.status)
  const contentType = response.headers.get(CONTENT_TYPE_HEADER) ?? ''

  if (contentType.toLowerCase().includes(JSON_CONTENT_TYPE)) {
    try {
      const payload = (await response.json()) as {
        message?: unknown
        errors?: unknown
        fieldErrors?: unknown
      } | null

      const message = cleanErrorMessage(payload?.message) ?? fallbackMessage
      return new ApiEnvelopeError(
        message,
        cleanErrorList(payload?.errors),
        response.status,
        cleanFieldErrors(payload?.fieldErrors),
      )
    } catch {
      return new ApiEnvelopeError(fallbackMessage, [], response.status)
    }
  }

  return new ApiEnvelopeError(fallbackMessage, [], response.status)
}

async function unwrap<T>(request: ApiRequest<T>): Promise<T> {
  const response = await request
  const payload = response.data

  if (!payload || typeof payload !== 'object' || !('success' in payload)) {
    throw new ApiEnvelopeError(API_ENVELOPE_ERROR_MESSAGES.unexpectedFormat)
  }

  if (payload.success === false) {
    throw new ApiEnvelopeError(payload.message || API_ENVELOPE_ERROR_MESSAGES.rejected, payload.errors ?? [])
  }

  if (!('data' in payload)) {
    throw new ApiEnvelopeError(API_ENVELOPE_ERROR_MESSAGES.missingData)
  }

  return payload.data
}

function apiUrl(path: string): string {
  const baseUrl = appEnv.apiBaseUrl.replace(API_BASE_TRAILING_SLASH_PATTERN, '')
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl}${suffix}`
}

function parseSseEvent(rawEvent: string): { event: string; data: string } | null {
  let event = SSE_DEFAULT_EVENT
  const dataLines: string[] = []
  rawEvent.split(SSE_LINE_PATTERN).forEach((line) => {
    if (line.startsWith(SSE_EVENT_PREFIX)) {
      event = line.slice(SSE_EVENT_PREFIX.length).trim()
    } else if (line.startsWith(SSE_DATA_PREFIX)) {
      dataLines.push(line.slice(SSE_DATA_PREFIX.length).trimStart())
    }
  })
  if (!dataLines.length) return null
  return { event, data: dataLines.join('\n') }
}

function parseSseCompletePayload(data: string): AiChatResponse {
  try {
    return JSON.parse(data) as AiChatResponse
  } catch {
    throw new ApiEnvelopeError(AI_STREAM_ERROR_MESSAGES.malformedComplete)
  }
}

function streamAbortError(): DOMException {
  return new DOMException('The AI chat stream was cancelled.', 'AbortError')
}

async function readSseStream(response: Response, handlers: AiStreamHandlers): Promise<AiChatResponse> {
  if (!response.body) {
    throw new ApiEnvelopeError(AI_STREAM_ERROR_MESSAGES.missingBody)
  }

  const { signal } = handlers
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finalPayload: AiChatResponse | null = null

  // Cancelling the reader settles a pending read() and closes the connection.
  const cancelRead = () => {
    void reader.cancel().catch(() => undefined)
  }
  const throwIfAborted = () => {
    if (!signal?.aborted) return
    cancelRead()
    throw streamAbortError()
  }
  signal?.addEventListener('abort', cancelRead, { once: true })

  const processEvent = (rawEvent: string) => {
    if (!rawEvent.trim()) return
    const parsed = parseSseEvent(rawEvent)
    if (!parsed) return
    if (parsed.event === SSE_EVENT_DELTA) {
      handlers.onDelta?.(parsed.data)
    } else if (parsed.event === SSE_EVENT_COMPLETE) {
      finalPayload = parseSseCompletePayload(parsed.data)
      handlers.onComplete?.(finalPayload)
    } else if (parsed.event === SSE_EVENT_ERROR) {
      throw new ApiEnvelopeError(parsed.data || AI_STREAM_ERROR_MESSAGES.stopped)
    }
  }

  try {
    while (true) {
      throwIfAborted()
      const { value, done } = await reader.read()
      throwIfAborted()
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })
      const events = buffer.split(SSE_DELIMITER_PATTERN)
      buffer = events.pop() ?? ''

      events.forEach(processEvent)

      if (done) break
    }
  } finally {
    signal?.removeEventListener('abort', cancelRead)
    reader.releaseLock()
  }

  if (buffer.trim()) {
    processEvent(buffer)
  }

  if (!finalPayload) {
    throw new ApiEnvelopeError(AI_STREAM_ERROR_MESSAGES.incomplete)
  }
  return finalPayload
}

function aiStreamHeaders(): Headers {
  const headers = new Headers({
    [SSE_ACCEPT_HEADER]: SSE_CONTENT_TYPE,
    [REQUEST_CONTENT_TYPE_HEADER]: JSON_CONTENT_TYPE,
  })
  const token = getAccessToken()
  if (token) {
    headers.set(AUTHORIZATION_HEADER, `Bearer ${token}`)
  }
  return headers
}

async function sendAiChatStreamRequest(payload: AiChatPayload, signal?: AbortSignal): Promise<Response> {
  if (signal?.aborted) {
    throw streamAbortError()
  }

  // One controller serves both the connect timeout and the caller's signal.
  const abortController = new AbortController()
  let timedOut = false
  const timeoutId = globalThis.setTimeout(() => {
    timedOut = true
    abortController.abort()
  }, AI_STREAM_CONNECT_TIMEOUT_MS)
  const abortFromCaller = () => abortController.abort()
  signal?.addEventListener('abort', abortFromCaller, { once: true })

  try {
    return await fetch(apiUrl(AI_CHAT_STREAM_PATH), {
      method: 'POST',
      credentials: 'include',
      headers: aiStreamHeaders(),
      signal: abortController.signal,
      body: JSON.stringify(payload),
    })
  } catch (error) {
    if (signal?.aborted) {
      throw streamAbortError()
    }

    if (timedOut) {
      throw new ApiEnvelopeError(AI_STREAM_ERROR_MESSAGES.timeout)
    }

    throw error
  } finally {
    globalThis.clearTimeout(timeoutId)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

async function postSseChat(payload: AiChatPayload, handlers: AiStreamHandlers = {}): Promise<AiChatResponse> {
  let response = await sendAiChatStreamRequest(payload, handlers.signal)
  if (response.status === 401) {
    const refreshedToken = await refreshAccessToken()
    if (!refreshedToken) {
      invalidateAuthSession()
      throw await sseHttpError(response)
    }

    response = await sendAiChatStreamRequest(payload, handlers.signal)
    if (response.status === 401) {
      invalidateAuthSession()
    }
  }

  if (!response.ok) {
    throw await sseHttpError(response)
  }

  return readSseStream(response, handlers)
}

async function unwrapPageItems<T>(request: ApiRequest<PageResponse<T>>): Promise<T[]> {
  const page = await unwrap<PageResponse<T>>(request)
  return page?.items ?? []
}

function parseContentDispositionFileName(contentDisposition: string | undefined): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(CONTENT_DISPOSITION_UTF8_FILENAME_PATTERN)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }

  const asciiMatch = contentDisposition.match(CONTENT_DISPOSITION_ASCII_FILENAME_PATTERN)
  return asciiMatch?.[1] ?? null
}

function headerValue(value: AxiosResponse['headers'][string]): string | null {
  return typeof value === 'string' ? value : null
}

async function unwrapDownload(request: Promise<AxiosResponse<Blob>>): Promise<DownloadResult> {
  const response = await request
  return {
    blob: response.data,
    fileName: parseContentDispositionFileName(headerValue(response.headers['content-disposition']) ?? undefined),
    contentType: headerValue(response.headers[CONTENT_TYPE_HEADER]),
  }
}

interface PublicCourseFilters {
  keyword?: string
  levelId?: number
  categoryId?: number
  learningType?: string
  sort?: string
  page?: number
  size?: number
}

interface AiChatPayload {
  message: string
  languagePreference?: AppLanguage
  sessionId?: number
  context?: string
  relatedLessonId?: number
}

export interface TeacherModulePayload {
  title: string
  description?: string | null
  levelId: number
  categoryId: number
  coverImageUrl?: string | null
}

export interface TeacherLessonPayload {
  title: string
  description?: string | null
  moduleId?: number | null
  levelId: number
  categoryId: number
  learningType: LearningType
  estimatedDurationMinutes?: number | null
  basicContent?: string | null
  coverImageUrl?: string | null
}

export interface TeacherPackagePayload {
  title: string
  description?: string | null
  levelId: number
  categoryId: number
  learningType: LearningType
  coverImageUrl?: string | null
}

export interface TeacherLiveClassPayload {
  title: string
  description?: string | null
  lessonId?: number | null
  packageId?: number | null
  startTime: string
  endTime: string
  timeZone?: string | null
  meetingLink: string
  meetingPlatform: 'ZOOM' | 'GOOGLE_MEET' | 'MICROSOFT_TEAMS' | 'OTHER'
  status?: LiveClassStatus | null
  maxParticipants?: number | null
}

export interface TeacherQuizPayload {
  title: string
  description?: string | null
  timeLimitMinutes?: number | null
  learningStage?: LearningStage | null
}

export interface TeacherQuizOptionPayload {
  optionText: string
  correctOption?: boolean
  displayOrder?: number | null
}

export interface TeacherQuizQuestionPayload {
  questionType: QuizQuestionType
  questionText: string
  correctAnswerText?: string | null
  points: number
  displayOrder?: number | null
  options?: TeacherQuizOptionPayload[] | null
  explanation?: string | null
  /** Optional hint (<= 1000 chars, D8); hidden from students on INDEPENDENT quizzes. */
  hint?: string | null
}

export interface NotificationQuery {
  /** Omit for all notifications. */
  status?: NotificationStatus
  page?: number
  size?: number
}

export interface UpdateProfilePayload {
  displayName: string
  preferredLanguage: AppLanguage
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface PracticeSubmissionQuery {
  type?: PracticeSubmissionType
  page?: number
  size?: number
}

export interface ReviewPracticeSubmissionQuery extends PracticeSubmissionQuery {
  /** Teacher default SUBMITTED, admin default ALL. */
  reviewStatus?: PracticeReviewStatus | 'ALL'
}

export interface PracticeSubmissionFeedbackPayload {
  feedbackType?: TeacherFeedbackType
  /** 5..2000 characters. */
  message: string
}

interface ReviewAiReportPayload {
  status: AiResponseReportReviewStatus
  reviewerNote: string
}

interface AdminActivityFilters {
  type?: AdminActivityType | ''
  status?: ContentStatus | ''
  keyword?: string
  topic?: string
  difficulty?: ActivityDifficulty | ''
  page?: number
  size?: number
}

export interface TeacherActivityFilters {
  type?: AdminActivityType
  status?: ContentStatus
  keyword?: string
  page?: number
  size?: number
}

/** Server-side audit-log filters (D16). `from` / `to` are inclusive `yyyy-MM-dd` dates. */
export interface AuditLogQuery {
  actionType?: string
  targetType?: string
  actor?: string
  actorId?: number
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface ContentReviewQuery {
  /** Defaults to SUBMITTED on the server; `ALL` lists every status. */
  status?: ContentStatus | 'ALL'
  type?: ReviewContentType
  keyword?: string
  page?: number
  size?: number
}

export interface TeacherAiQuizDraftPayload {
  topic: string
  level?: string
  category?: string
  numberOfQuestions?: number
  questionTypes?: string[]
  languagePreference?: AppLanguage
}

export interface TeacherAiStoryDraftPayload {
  topic: string
  level?: string
  languagePreference?: AppLanguage
  lengthPreference?: string
}

export interface AdminMatchingGameItemPayload {
  leftText: string
  rightText: string
  leftImageUrl?: string | null
  rightImageUrl?: string | null
  pinyin?: string | null
  sortOrder?: number | null
}

export interface AdminDragDropItemPayload {
  dragText: string
  targetZone: string
  pinyin?: string | null
  imageUrl?: string | null
  sortOrder?: number | null
}

export interface AdminStoryQuestionPayload {
  questionText: string
  optionsPayload: string
  explanation?: string | null
  sortOrder?: number | null
  /** 0-based index of the correct option; nullable while drafting. */
  correctOptionIndex?: number | null
}

export interface AdminFlashcardPayload {
  title?: string | null
  frontText: string
  backText: string
  pinyin?: string | null
  englishMeaning?: string | null
  malayMeaning?: string | null
  imageUrl?: string | null
  audioUrl?: string | null
  sortOrder?: number | null
  extraPayload?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
}

export interface AdminMatchingGamePayload {
  title: string
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  items: AdminMatchingGameItemPayload[]
}

export interface AdminListeningExercisePayload {
  title: string
  audioUrl?: string | null
  questionPayload?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
}

export interface AdminTracingExercisePayload {
  title: string
  worksheetUrl?: string | null
  targetCharacter?: string | null
  pinyin?: string | null
  meaning?: string | null
  instructions?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
}

export interface AdminDragDropActivityPayload {
  title: string
  instructions?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  items: AdminDragDropItemPayload[]
}

export interface AdminStoryActivityPayload {
  title: string
  instructions?: string | null
  storyText: string
  pinyin?: string | null
  translation?: string | null
  imageUrl?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  questions: AdminStoryQuestionPayload[]
}

/** Request body per practice-activity type (shared by the admin library and teacher-authored activities, D5). */
export interface ActivityPayloadByType {
  FLASHCARD: AdminFlashcardPayload
  MATCHING_GAME: AdminMatchingGamePayload
  LISTENING_EXERCISE: AdminListeningExercisePayload
  TRACING_EXERCISE: AdminTracingExercisePayload
  DRAG_DROP_ACTIVITY: AdminDragDropActivityPayload
  STORY_ACTIVITY: AdminStoryActivityPayload
}

/** Response body per practice-activity type. */
export interface ActivityResponseByType {
  FLASHCARD: AdminFlashcardResponse
  MATCHING_GAME: AdminMatchingGameResponse
  LISTENING_EXERCISE: AdminListeningExerciseResponse
  TRACING_EXERCISE: AdminTracingExerciseResponse
  DRAG_DROP_ACTIVITY: AdminDragDropActivityResponse
  STORY_ACTIVITY: AdminStoryActivityResponse
}

/** URL segment used by `/admin/activities/{slug}` and `/teachers/activities/{slug}`. */
const ACTIVITY_TYPE_SLUGS = {
  FLASHCARD: 'flashcards',
  MATCHING_GAME: 'matching-games',
  LISTENING_EXERCISE: 'listening-exercises',
  TRACING_EXERCISE: 'tracing-exercises',
  DRAG_DROP_ACTIVITY: 'drag-drop-activities',
  STORY_ACTIVITY: 'story-activities',
} as const satisfies Record<AdminActivityType, string>

/** Typed CRUD client for one practice-activity collection root (admin library or the teacher's own activities). */
export interface ActivityGateway {
  detail<T extends AdminActivityType>(type: T, id: number): Promise<ActivityResponseByType[T]>
  create<T extends AdminActivityType>(type: T, payload: ActivityPayloadByType[T]): Promise<ActivityResponseByType[T]>
  update<T extends AdminActivityType>(
    type: T,
    id: number,
    payload: ActivityPayloadByType[T],
  ): Promise<ActivityResponseByType[T]>
  remove(type: AdminActivityType, id: number): Promise<void>
}

export interface TeacherActivityGateway extends ActivityGateway {
  list<T extends AdminActivityType>(
    type: T,
    params?: { status?: ContentStatus; page?: number; size?: number; sort?: string },
  ): Promise<PageResponse<ActivityResponseByType[T]>>
  submit<T extends AdminActivityType>(type: T, id: number): Promise<ActivityResponseByType[T]>
}

function createActivityGateway(basePath: string): ActivityGateway {
  const path = (type: AdminActivityType, id?: number) =>
    id === undefined ? `${basePath}/${ACTIVITY_TYPE_SLUGS[type]}` : `${basePath}/${ACTIVITY_TYPE_SLUGS[type]}/${id}`

  return {
    detail<T extends AdminActivityType>(type: T, id: number) {
      return unwrap<ActivityResponseByType[T]>(http.get(path(type, id)))
    },
    create<T extends AdminActivityType>(type: T, payload: ActivityPayloadByType[T]) {
      return unwrap<ActivityResponseByType[T]>(http.post(path(type), payload))
    },
    update<T extends AdminActivityType>(type: T, id: number, payload: ActivityPayloadByType[T]) {
      return unwrap<ActivityResponseByType[T]>(http.put(path(type, id), payload))
    },
    remove(type: AdminActivityType, id: number) {
      return unwrap<void>(http.delete(path(type, id)))
    },
  }
}

const ADMIN_ACTIVITIES_PATH = '/admin/activities'
const TEACHER_ACTIVITIES_PATH = '/teachers/activities'

/** Admin practice-activity library (full management of every activity). */
export const adminActivityGateway: ActivityGateway = createActivityGateway(ADMIN_ACTIVITIES_PATH)

/** Teacher-authored practice activities: own items only, created as DRAFT and submitted for admin review (D5). */
export const teacherActivityGateway: TeacherActivityGateway = {
  ...createActivityGateway(TEACHER_ACTIVITIES_PATH),
  list<T extends AdminActivityType>(
    type: T,
    params: { status?: ContentStatus; page?: number; size?: number; sort?: string } = {},
  ) {
    return unwrap<PageResponse<ActivityResponseByType[T]>>(
      http.get(`${TEACHER_ACTIVITIES_PATH}/${ACTIVITY_TYPE_SLUGS[type]}`, { params }),
    )
  },
  submit<T extends AdminActivityType>(type: T, id: number) {
    return unwrap<ActivityResponseByType[T]>(
      http.post(`${TEACHER_ACTIVITIES_PATH}/${ACTIVITY_TYPE_SLUGS[type]}/${id}/submit`),
    )
  },
}

export const authApi = {
  login(payload: { email: string; password: string }) {
    return unwrap<AuthResponse>(http.post('/auth/login', payload))
  },
  refresh() {
    return unwrap<AuthResponse>(http.post('/auth/refresh'))
  },
  registerStudent(payload: {
    fullName: string
    email: string
    password: string
    confirmPassword: string
    preferredLanguage?: AppLanguage
  }) {
    return unwrap<RegistrationResponse>(http.post('/auth/register/student', payload))
  },
  registerTeacher(payload: {
    fullName: string
    email: string
    password: string
    confirmPassword: string
    bio?: string
  }) {
    return unwrap<RegistrationResponse>(http.post('/auth/register/teacher', payload))
  },
  async me() {
    const user = await unwrap<CurrentUserResponse>(http.get('/auth/me'))
    // Old accounts may still carry the legacy Chinese code; the client only uses en | ms | zh-CN.
    const preferredLanguage = normalizeAppLanguage(user.preferredLanguage)
    return preferredLanguage && preferredLanguage !== user.preferredLanguage ? { ...user, preferredLanguage } : user
  },
  logout() {
    return unwrap<void>(http.post('/auth/logout'))
  },
  forgotPassword(email: string) {
    return unwrap<void>(http.post('/auth/forgot-password', { email }))
  },
  resetPassword(payload: { token: string; password: string; confirmPassword: string }) {
    return unwrap<void>(http.post('/auth/reset-password', payload))
  },
}

/** Account management for every signed-in role (D12). */
export const accountApi = {
  async updateProfile(payload: UpdateProfilePayload) {
    const user = await unwrap<CurrentUserResponse>(http.put('/users/me/profile', payload))
    const preferredLanguage = normalizeAppLanguage(user.preferredLanguage)
    return preferredLanguage && preferredLanguage !== user.preferredLanguage ? { ...user, preferredLanguage } : user
  },
  /** Returns a fresh access token; the server revokes every other session and sets a new refresh cookie. */
  changePassword(payload: ChangePasswordPayload) {
    return unwrap<AuthResponse>(http.post('/users/me/password', payload))
  },
}

export const publicApi = {
  getLevels() {
    return unwrap<LearningLevel[]>(http.get('/public/levels'))
  },
  getCategories() {
    return unwrap<LearningCategory[]>(http.get('/public/categories'))
  },
  platformSettings() {
    return unwrap<PublicPlatformSettingsResponse>(http.get('/public/platform-settings'))
  },
  listCourses(filters: PublicCourseFilters = {}) {
    return unwrap<PageResponse<PublicCourseSummary>>(http.get('/public/courses', { params: filters }))
  },
  getCourseDetail(id: string) {
    return unwrap<PublicCourseDetail>(http.get(`/public/courses/${encodeURIComponent(id)}`))
  },
}

export const notificationsApi = {
  list(params: QueryParams = {}) {
    return unwrapPageItems<NotificationResponse>(http.get('/notifications', { params }))
  },
  /** Paged list for the notifications page; `status` filters UNREAD / READ (omit for all). */
  page(query: NotificationQuery = {}) {
    return unwrap<PageResponse<NotificationResponse>>(http.get('/notifications', { params: query }))
  },
  unreadCount() {
    return unwrap<NotificationUnreadCountResponse>(http.get('/notifications/unread-count'))
  },
  markAsRead(id: number) {
    return unwrap<NotificationResponse>(http.patch(`/notifications/${id}/read`))
  },
  markAllRead() {
    return unwrap<BulkOperationResponse>(http.patch('/notifications/read-all'))
  },
}

export const systemApi = {
  health() {
    return unwrap<HealthStatusResponse>(http.get('/health'))
  },
}

export const studentApi = {
  dashboard() {
    return unwrap<StudentDashboardResponse>(http.get('/students/dashboard'))
  },
  progress() {
    return unwrap<StudentProgressSummaryResponse>(http.get('/students/progress'))
  },
  rewards() {
    return unwrap<RewardSummaryResponse>(http.get('/students/rewards'))
  },
  badges() {
    return unwrapPageItems<BadgeSummaryResponse>(http.get('/students/rewards/badges'))
  },
  pointsHistory() {
    return unwrapPageItems<PointsTransactionResponse>(http.get('/students/rewards/points-history'))
  },
  streak() {
    return unwrap<DailyStreakResponse>(http.get('/students/rewards/streak'))
  },
  lessonPlayer(lessonId: number) {
    return unwrap<StudentLessonPlayerResponse>(http.get(`/students/lessons/${lessonId}`))
  },
  /** Records that the lesson content was shown (part of lesson completion for lessons without quizzes). */
  markLessonViewed(lessonId: number) {
    return unwrap<StudentLessonProgressResponse>(http.post(`/students/lessons/${lessonId}/viewed`))
  },
  /** One page of the upcoming published live classes the learner can see, ordered by start time. */
  liveClassesPage(params: { page?: number; size?: number } = {}) {
    return unwrap<PageResponse<LiveClassResponse>>(http.get('/students/live-classes', { params }))
  },
  /** Every upcoming published live class (FR-LIVE-05): walks all pages at the maximum page size. */
  async liveClasses() {
    const items: LiveClassResponse[] = []
    for (let page = 0; page < STUDENT_LIVE_CLASS_MAX_PAGES; page += 1) {
      const response = await studentApi.liveClassesPage({ page, size: STUDENT_LIVE_CLASS_PAGE_SIZE })
      items.push(...(response?.items ?? []))
      if (!response || page + 1 >= (response.totalPages ?? 0)) break
    }
    return items
  },
  joinLiveClass(id: number) {
    return unwrap<{ liveClassId: number; title: string; meetingPlatform: string; meetingLink: string }>(
      http.get(`/students/live-classes/${id}/join`),
    )
  },
  enrolLiveClass(id: number) {
    return unwrap<LiveClassResponse>(http.post(`/students/live-classes/${id}/enrol`))
  },
  cancelLiveClassEnrolment(id: number) {
    return unwrap<LiveClassResponse>(http.post(`/students/live-classes/${id}/cancel-enrolment`))
  },
  videoProgress(videoId: number) {
    return unwrap<VideoProgressResponse>(http.get(`/students/video-progress/${videoId}`))
  },
  saveVideoProgress(
    videoId: number,
    payload: { watchedSeconds?: number; totalSeconds?: number; progressPercentage?: number; completed?: boolean },
  ) {
    return unwrap<VideoProgressResponse>(http.post(`/students/video-progress/${videoId}`, payload))
  },
  completeVideo(videoId: number) {
    return unwrap<VideoProgressResponse>(http.post(`/students/video-progress/${videoId}/complete`))
  },
  quizzes(params: StudentQueryParams = {}) {
    return unwrap<PageResponse<StudentQuizSummaryResponse>>(http.get('/students/quizzes', { params }))
  },
  quiz(quizId: number) {
    return unwrap<StudentQuizResponse>(http.get(`/students/quizzes/${quizId}`))
  },
  /** Option types send optionId, FILL_BLANK / REARRANGE_SENTENCE send answerText (see utils/quizAnswers.ts). */
  submitQuiz(quizId: number, answers: QuizAnswerSubmission[]) {
    return unwrap<QuizAttemptResultResponse>(http.post(`/students/quizzes/${quizId}/attempts`, { answers }))
  },
  quizHint(quizId: number, questionId: number) {
    return unwrap<QuizHintResponse>(http.get(`/students/quizzes/${quizId}/questions/${questionId}/hint`))
  },
  wrongAnswers(params: StudentQueryParams = {}) {
    return unwrapPageItems<WrongAnswerResponse>(http.get('/students/wrong-answers', { params }))
  },
  practiceSubmissions(query: PracticeSubmissionQuery = {}) {
    return unwrap<PageResponse<PracticeSubmissionResponse>>(
      http.get('/students/practice-submissions', { params: query }),
    )
  },
  practiceSubmission(id: number) {
    return unwrap<PracticeSubmissionResponse>(http.get(`/students/practice-submissions/${id}`))
  },
  /** Authorised file stream fetched with the bearer token as a Blob. */
  practiceSubmissionFile(id: number) {
    return unwrapDownload(http.get(`/students/practice-submissions/${id}/file`, { responseType: 'blob' }))
  },
  feedback() {
    return unwrapPageItems<StudentTeacherFeedbackResponse>(http.get('/students/feedback'))
  },
  activityLibrary(params: StudentQueryParams = {}) {
    return unwrap<PageResponse<StudentActivitySummaryResponse>>(http.get('/students/activities', { params }))
  },
  flashcard(id: number) {
    return unwrap<StudentFlashcardResponse>(http.get(`/students/activities/flashcards/${id}`))
  },
  completeFlashcard(id: number) {
    return unwrap<StudentActivityCompletionResponse>(http.post(`/students/activities/flashcards/${id}/complete`))
  },
  matchingGame(id: number) {
    return unwrap<StudentMatchingGameResponse>(http.get(`/students/activities/matching-games/${id}`))
  },
  completeMatchingGame(id: number, body: MatchingCompletionRequest) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/matching-games/${id}/complete`, body),
    )
  },
  listeningExercise(id: number) {
    return unwrap<StudentListeningExerciseResponse>(http.get(`/students/activities/listening-exercises/${id}`))
  },
  completeListeningExercise(id: number, body: ListeningCompletionRequest) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/listening-exercises/${id}/complete`, body),
    )
  },
  tracingExercise(id: number) {
    return unwrap<StudentTracingExerciseResponse>(http.get(`/students/activities/tracing-exercises/${id}`))
  },
  /** Tracing is practice only and never graded; the server records completion for strokeCount >= 1. */
  completeTracingExercise(id: number, body: TracingCompletionRequest) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/tracing-exercises/${id}/complete`, body),
    )
  },
  dragDropActivity(id: number) {
    return unwrap<StudentDragDropActivityResponse>(http.get(`/students/activities/drag-drop-activities/${id}`))
  },
  completeDragDropActivity(id: number, body: DragDropCompletionRequest) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/drag-drop-activities/${id}/complete`, body),
    )
  },
  storyActivity(id: number) {
    return unwrap<StudentStoryActivityResponse>(http.get(`/students/activities/story-activities/${id}`))
  },
  completeStoryActivity(id: number, body: StoryCompletionRequest) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/story-activities/${id}/complete`, body),
    )
  },
}

export const aiApi = {
  availability() {
    return unwrap<AiAvailabilityResponse>(http.get('/ai/availability'))
  },
  chatStream: postSseChat,
  vocabularyHelper(payload: {
    word: string
    inputLanguage?: string
    outputLanguage?: AppLanguage
    learnerLevel?: string
    languagePreference?: AppLanguage
  }) {
    return unwrap<AiVocabularyHelperResponse>(http.post('/ai/vocabulary-helper', payload))
  },
  sentencePractice(payload: { sentence: string; learnerLevel?: string; languagePreference?: AppLanguage }) {
    return unwrap<AiSentencePracticeResponse>(http.post('/ai/sentence-practice', payload))
  },
  storyGenerator(payload: {
    topic: string
    level?: string
    languagePreference?: AppLanguage
    lengthPreference?: string
  }) {
    return unwrap<AiStoryGeneratorResponse>(http.post('/ai/story-generator', payload))
  },
  quizGenerator(payload: {
    topic: string
    level?: string
    category?: string
    numberOfQuestions?: number
    questionTypes?: string[]
    languagePreference?: AppLanguage
  }) {
    return unwrap<AiQuizGeneratorResponse>(http.post('/ai/quiz-generator', payload))
  },
  pronunciationFeedback(formData: FormData) {
    return unwrap<AiPronunciationFeedbackResponse>(http.post('/ai/pronunciation-feedback', formData))
  },
  writingFeedback(formData: FormData) {
    return unwrap<AiWritingFeedbackResponse>(http.post('/ai/writing-feedback', formData))
  },
  reports(params: QueryParams = {}) {
    return unwrapPageItems<StudentAiResponseReportResponse>(http.get('/ai/reports', { params }))
  },
  reportResponse(payload: {
    aiUsageLogId?: number
    chatSessionId?: number
    chatMessageId?: number
    featureKey: AiFeatureKey
    reasonCategory: AiResponseReportReason
    description?: string
    promptSnapshot?: string
    aiResponseSnapshot?: string
  }) {
    return unwrap<StudentAiResponseReportResponse>(http.post('/ai/reports', payload))
  },
}

export const teacherApi = {
  dashboard() {
    return unwrap<TeacherDashboardResponse>(http.get('/teachers/dashboard'))
  },
  analytics() {
    return unwrap<TeacherAnalyticsResponse>(http.get('/teachers/analytics'))
  },
  profile() {
    return unwrap<TeacherProfileResponse>(http.get('/teachers/me'))
  },
  updateProfile(payload: {
    fullName: string
    bio?: string
    shortIntroduction?: string
    teachingCategories?: string[]
  }) {
    return unwrap<TeacherProfileResponse>(http.put('/teachers/me/profile', payload))
  },
  verificationStatus() {
    return unwrap<TeacherVerificationStatusResponse>(http.get('/teachers/verification-status'))
  },
  uploadVerificationDocument(formData: FormData) {
    return unwrap<TeacherVerificationStatusResponse>(http.post('/teachers/verification-documents', formData))
  },
  downloadVerificationDocument(id: number) {
    return unwrapDownload(http.get(`/teachers/verification-documents/${id}`, { responseType: 'blob' }))
  },
  resubmitVerification() {
    return unwrap<TeacherVerificationStatusResponse>(http.post('/teachers/resubmit-verification'))
  },
  modules() {
    return unwrapPageItems<LearningModuleResponse>(http.get('/teachers/modules', maxPageConfig()))
  },
  createModule(payload: TeacherModulePayload) {
    return unwrap<LearningModuleResponse>(http.post('/teachers/modules', payload))
  },
  updateModule(id: number, payload: TeacherModulePayload) {
    return unwrap<LearningModuleResponse>(http.put(`/teachers/modules/${id}`, payload))
  },
  archiveModule(id: number) {
    return unwrap<LearningModuleResponse>(http.patch(`/teachers/modules/${id}/archive`))
  },
  lessons() {
    return unwrapPageItems<LessonResponse>(http.get('/teachers/lessons', maxPageConfig()))
  },
  createLesson(payload: TeacherLessonPayload) {
    return unwrap<LessonResponse>(http.post('/teachers/lessons', payload))
  },
  updateLesson(id: number, payload: TeacherLessonPayload) {
    return unwrap<LessonResponse>(http.put(`/teachers/lessons/${id}`, payload))
  },
  archiveLesson(id: number) {
    return unwrap<LessonResponse>(http.patch(`/teachers/lessons/${id}/archive`))
  },
  packages() {
    return unwrapPageItems<CoursePackageResponse>(http.get('/teachers/packages', maxPageConfig()))
  },
  createPackage(payload: TeacherPackagePayload) {
    return unwrap<CoursePackageResponse>(http.post('/teachers/packages', payload))
  },
  updatePackage(id: number, payload: TeacherPackagePayload) {
    return unwrap<CoursePackageResponse>(http.put(`/teachers/packages/${id}`, payload))
  },
  archivePackage(id: number) {
    return unwrap<CoursePackageResponse>(http.patch(`/teachers/packages/${id}/archive`))
  },
  addPackageItem(id: number, payload: { itemType: string; itemId: number; displayOrder?: number }) {
    return unwrap<CoursePackageResponse>(http.post(`/teachers/packages/${id}/items`, payload))
  },
  removePackageItem(packageId: number, itemId: number) {
    return unwrap<CoursePackageResponse>(http.delete(`/teachers/packages/${packageId}/items/${itemId}`))
  },
  liveClasses() {
    return unwrapPageItems<LiveClassResponse>(http.get('/teachers/live-classes', maxPageConfig()))
  },
  createLiveClass(payload: TeacherLiveClassPayload) {
    return unwrap<LiveClassResponse>(http.post('/teachers/live-classes', payload))
  },
  updateLiveClass(id: number, payload: TeacherLiveClassPayload) {
    return unwrap<LiveClassResponse>(http.put(`/teachers/live-classes/${id}`, payload))
  },
  cancelLiveClass(id: number) {
    return unwrap<LiveClassResponse>(http.patch(`/teachers/live-classes/${id}/cancel`))
  },
  enrolments(id: number) {
    return unwrapPageItems<LiveClassEnrolmentResponse>(
      http.get(`/teachers/live-classes/${id}/enrolments`, maxPageConfig()),
    )
  },
  recordedVideos(lessonId: number) {
    return unwrapPageItems<RecordedLessonVideoResponse>(
      http.get(`/teachers/lessons/${lessonId}/recorded-videos`, maxPageConfig()),
    )
  },
  createRecordedVideo(lessonId: number, formData: FormData) {
    return unwrap<RecordedLessonVideoResponse>(http.post(`/teachers/lessons/${lessonId}/recorded-videos`, formData))
  },
  updateRecordedVideo(lessonId: number, videoId: number, formData: FormData) {
    return unwrap<RecordedLessonVideoResponse>(
      http.put(`/teachers/lessons/${lessonId}/recorded-videos/${videoId}`, formData),
    )
  },
  archiveRecordedVideo(lessonId: number, videoId: number) {
    return unwrap<RecordedLessonVideoResponse>(
      http.patch(`/teachers/lessons/${lessonId}/recorded-videos/${videoId}/archive`),
    )
  },
  lessonVideoProgress(lessonId: number) {
    return unwrapPageItems<LessonVideoProgressSummaryResponse>(
      http.get(`/teachers/lessons/${lessonId}/video-progress`, maxPageConfig()),
    )
  },
  lessonQuizzes(lessonId: number) {
    return unwrapPageItems<TeacherQuizResponse>(http.get(`/teachers/lessons/${lessonId}/quizzes`, maxPageConfig()))
  },
  createQuiz(lessonId: number, payload: TeacherQuizPayload) {
    return unwrap<TeacherQuizResponse>(http.post(`/teachers/lessons/${lessonId}/quizzes`, payload))
  },
  updateQuiz(quizId: number, payload: TeacherQuizPayload) {
    return unwrap<TeacherQuizResponse>(http.put(`/teachers/quizzes/${quizId}`, payload))
  },
  deleteQuiz(quizId: number) {
    return unwrap<void>(http.delete(`/teachers/quizzes/${quizId}`))
  },
  addQuizQuestion(quizId: number, payload: TeacherQuizQuestionPayload) {
    return unwrap<TeacherQuizResponse>(http.post(`/teachers/quizzes/${quizId}/questions`, payload))
  },
  updateQuizQuestion(questionId: number, payload: TeacherQuizQuestionPayload) {
    return unwrap<TeacherQuizResponse>(http.put(`/teachers/quiz-questions/${questionId}`, payload))
  },
  deleteQuizQuestion(questionId: number) {
    return unwrap<TeacherQuizResponse>(http.delete(`/teachers/quiz-questions/${questionId}`))
  },
  quizAttempts(quizId: number) {
    return unwrapPageItems<TeacherQuizAttemptSummaryResponse>(
      http.get(`/teachers/quizzes/${quizId}/attempts`, maxPageConfig()),
    )
  },
  feedbackList() {
    return unwrapPageItems<TeacherFeedbackResponse>(http.get('/teachers/feedback', maxPageConfig()))
  },
  students() {
    return unwrapPageItems<TeacherStudentSummaryResponse>(http.get('/teachers/students', maxPageConfig()))
  },
  createFeedback(payload: {
    studentId: number
    lessonId?: number
    quizAttemptId?: number
    practiceSubmissionId?: number
    feedbackType?: string
    message: string
  }) {
    return unwrap<TeacherFeedbackResponse>(http.post('/teachers/feedback', payload))
  },
  /* Practice submissions of learners on the teacher's roster (D7). */
  practiceSubmissions(query: ReviewPracticeSubmissionQuery = {}) {
    return unwrap<PageResponse<PracticeSubmissionResponse>>(
      http.get('/teachers/practice-submissions', { params: query }),
    )
  },
  practiceSubmission(id: number) {
    return unwrap<PracticeSubmissionResponse>(http.get(`/teachers/practice-submissions/${id}`))
  },
  practiceSubmissionFile(id: number) {
    return unwrapDownload(http.get(`/teachers/practice-submissions/${id}/file`, { responseType: 'blob' }))
  },
  reviewPracticeSubmission(id: number, payload: PracticeSubmissionFeedbackPayload) {
    return unwrap<PracticeSubmissionResponse>(http.post(`/teachers/practice-submissions/${id}/feedback`, payload))
  },
  updateFeedback(id: number, payload: { feedbackType?: string; message: string }) {
    return unwrap<TeacherFeedbackResponse>(http.put(`/teachers/feedback/${id}`, payload))
  },
  /* Review workflow (D4): submit DRAFT/REJECTED content for admin review. */
  submitPackage(id: number) {
    return unwrap<CoursePackageResponse>(http.post(`/teachers/packages/${id}/submit`))
  },
  submitModule(id: number) {
    return unwrap<LearningModuleResponse>(http.post(`/teachers/modules/${id}/submit`))
  },
  submitLesson(id: number) {
    return unwrap<LessonResponse>(http.post(`/teachers/lessons/${id}/submit`))
  },
  submitQuiz(quizId: number) {
    return unwrap<TeacherQuizResponse>(http.post(`/teachers/quizzes/${quizId}/submit`))
  },
  quiz(quizId: number) {
    return unwrap<TeacherQuizResponse>(http.get(`/teachers/quizzes/${quizId}`))
  },
  /* Teacher-authored practice activities (D5); per-type CRUD lives in teacherActivityGateway. */
  activities(params: TeacherActivityFilters = {}) {
    return unwrap<PageResponse<AdminActivitySummaryResponse>>(http.get('/teachers/activities', { params }))
  },
  /* AI-assisted drafts (D6): nothing is saved; the teacher edits and saves through the normal flow. */
  aiAvailability() {
    return unwrap<AiAvailabilityResponse>(http.get('/teachers/ai/availability'))
  },
  aiQuizDraft(payload: TeacherAiQuizDraftPayload) {
    return unwrap<AiQuizGeneratorResponse>(http.post('/teachers/ai/quiz-draft', payload))
  },
  aiStoryDraft(payload: TeacherAiStoryDraftPayload) {
    return unwrap<AiStoryGeneratorResponse>(http.post('/teachers/ai/story-draft', payload))
  },
}

export const adminApi = {
  dashboard() {
    return unwrap<AdminDashboardResponse>(http.get('/admin/dashboard'))
  },
  analytics() {
    return unwrap<AdminAnalyticsResponse>(http.get('/admin/analytics'))
  },
  liveClasses() {
    return unwrapPageItems<LiveClassResponse>(http.get('/admin/live-classes'))
  },
  /** Server-side user search: keyword (name/e-mail), role, teacher status, active, 0-based page and size (max 100). */
  users(params: Partial<AdminUserQueryParams> = {}) {
    return unwrap<PageResponse<AdminUserListItemResponse>>(http.get('/admin/users', { params }))
  },
  updateUserStatus(id: number, payload: { active: boolean }) {
    return unwrap<AdminUserListItemResponse>(http.patch(`/admin/users/${id}/status`, payload))
  },
  students(params: QueryParams = {}) {
    return unwrap<PageResponse<AdminStudentListItemResponse>>(http.get('/admin/students', { params }))
  },
  teachers(params: QueryParams = {}) {
    return unwrap<PageResponse<AdminTeacherListItemResponse>>(http.get('/admin/teachers', { params }))
  },
  pendingTeachers(params: QueryParams = {}) {
    return unwrapPageItems<AdminTeacherListItemResponse>(http.get('/admin/teachers/pending', { params }))
  },
  teacherDetail(id: number) {
    return unwrap<AdminTeacherDetailResponse>(http.get(`/admin/teachers/${id}`))
  },
  downloadTeacherDocument(id: number) {
    return unwrapDownload(http.get(`/admin/teacher-documents/${id}`, { responseType: 'blob' }))
  },
  approveTeacher(id: number, note?: string) {
    return unwrap<AdminTeacherDetailResponse>(http.post(`/admin/teachers/${id}/approve`, note ? { note } : {}))
  },
  rejectTeacher(id: number, reason: string) {
    return unwrap<AdminTeacherDetailResponse>(http.post(`/admin/teachers/${id}/reject`, { reason }))
  },
  activityFeed(filters: AdminActivityFilters = {}) {
    return unwrap<PageResponse<AdminActivitySummaryResponse>>(http.get('/admin/activities', { params: filters }))
  },
  aiUsageLogs(params: QueryParams = {}) {
    return unwrap<PageResponse<AdminAiUsageLogResponse>>(http.get('/admin/ai/usage-logs', { params }))
  },
  updateFlashcardStatus(id: number, status: ContentPublicationStatus) {
    return unwrap<AdminFlashcardResponse>(http.patch(`/admin/activities/flashcards/${id}/status`, { status }))
  },
  updateMatchingGameStatus(id: number, status: ContentPublicationStatus) {
    return unwrap<AdminMatchingGameResponse>(http.patch(`/admin/activities/matching-games/${id}/status`, { status }))
  },
  updateListeningExerciseStatus(id: number, status: ContentPublicationStatus) {
    return unwrap<AdminListeningExerciseResponse>(
      http.patch(`/admin/activities/listening-exercises/${id}/status`, { status }),
    )
  },
  updateTracingExerciseStatus(id: number, status: ContentPublicationStatus) {
    return unwrap<AdminTracingExerciseResponse>(
      http.patch(`/admin/activities/tracing-exercises/${id}/status`, { status }),
    )
  },
  updateDragDropActivityStatus(id: number, status: ContentPublicationStatus) {
    return unwrap<AdminDragDropActivityResponse>(
      http.patch(`/admin/activities/drag-drop-activities/${id}/status`, { status }),
    )
  },
  updateStoryActivityStatus(id: number, status: ContentPublicationStatus) {
    return unwrap<AdminStoryActivityResponse>(http.patch(`/admin/activities/story-activities/${id}/status`, { status }))
  },
  content(params: QueryParams = {}) {
    return unwrap<PageResponse<ContentSubmissionResponse>>(http.get('/admin/content/submissions', { params }))
  },
  contentDetail(contentType: string, id: number) {
    return unwrap<ContentSubmissionDetailResponse>(
      http.get(`/admin/content/submissions/${encodeURIComponent(contentType)}/${id}`),
    )
  },
  archiveContent(contentType: string, id: number, note?: string) {
    return unwrap<ContentSubmissionDetailResponse>(
      http.patch(`/admin/content/${encodeURIComponent(contentType)}/${id}/archive`, note ? { note } : {}),
    )
  },
  moderationRecords(params: QueryParams = {}) {
    return unwrapPageItems<ModerationRecordResponse>(http.get('/admin/moderation-records', { params }))
  },
  aiReports(status?: string, params: QueryParams = {}) {
    return unwrapPageItems<AdminAiResponseReportResponse>(
      http.get('/admin/ai-response-reports', { params: { ...params, status } }),
    )
  },
  reviewAiReport(id: number, payload: ReviewAiReportPayload) {
    return unwrap<AdminAiResponseReportResponse>(http.post(`/admin/ai-response-reports/${id}/review`, payload))
  },
  moderationAction(action: ModerationAction, payload: ModerationPayload) {
    return unwrap<ModerationRecordResponse>(http.post(MODERATION_ACTION_ENDPOINTS[action], payload))
  },
  notifications(params: QueryParams = {}) {
    return unwrapPageItems<NotificationResponse>(http.get('/admin/notifications', { params }))
  },
  auditLogs(params: QueryParams = {}) {
    return unwrapPageItems<AuditLogResponse>(http.get('/admin/audit-logs', { params }))
  },
  auditLogPage(query: AuditLogQuery = {}) {
    return unwrap<PageResponse<AuditLogResponse>>(http.get('/admin/audit-logs', { params: query }))
  },
  /* Content review queue (D4): approve / reject (reason required) / archive / restore. */
  contentReviewQueue(query: ContentReviewQuery = {}) {
    return unwrap<PageResponse<ContentReviewItemResponse>>(http.get('/admin/content-review', { params: query }))
  },
  contentReviewDetail(type: ReviewContentType, id: number) {
    return unwrap<ContentReviewDetailResponse>(http.get(`/admin/content-review/${type}/${id}`))
  },
  approveContent(type: ReviewContentType, id: number, note?: string) {
    return unwrap<ContentReviewItemResponse>(
      http.post(`/admin/content-review/${type}/${id}/approve`, note ? { note } : {}),
    )
  },
  rejectContent(type: ReviewContentType, id: number, reason: string) {
    return unwrap<ContentReviewItemResponse>(http.post(`/admin/content-review/${type}/${id}/reject`, { reason }))
  },
  archiveReviewedContent(type: ReviewContentType, id: number, note?: string) {
    return unwrap<ContentReviewItemResponse>(
      http.post(`/admin/content-review/${type}/${id}/archive`, note ? { note } : {}),
    )
  },
  restoreContent(type: ReviewContentType, id: number, note?: string) {
    return unwrap<ContentReviewItemResponse>(
      http.post(`/admin/content-review/${type}/${id}/restore`, note ? { note } : {}),
    )
  },
  /* Practice submissions (read-only moderation view). */
  practiceSubmissions(query: ReviewPracticeSubmissionQuery = {}) {
    return unwrap<PageResponse<PracticeSubmissionResponse>>(http.get('/admin/practice-submissions', { params: query }))
  },
  practiceSubmission(id: number) {
    return unwrap<PracticeSubmissionResponse>(http.get(`/admin/practice-submissions/${id}`))
  },
  practiceSubmissionFile(id: number) {
    return unwrapDownload(http.get(`/admin/practice-submissions/${id}/file`, { responseType: 'blob' }))
  },
  platformSettings() {
    return unwrap<AdminPlatformSettingsResponse>(http.get('/admin/system-settings/platform'))
  },
  updatePlatformSettings(payload: AdminPlatformSettingsUpdateRequest) {
    return unwrap<AdminPlatformSettingsResponse>(http.put('/admin/system-settings/platform', payload))
  },
}
