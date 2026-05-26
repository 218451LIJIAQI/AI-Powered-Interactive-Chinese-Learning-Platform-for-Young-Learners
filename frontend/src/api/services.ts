/**
 * Typed thin-wrappers around every backend REST endpoint, grouped by
 * domain (auth, AI, commerce, content, dashboard, etc.).
 *
 * <h2>Why typed wrappers</h2>
 * <p>Pages call e.g. {@code authApi.login(payload)} instead of
 * {@code http.post('/auth/login', payload)} so:</p>
 * <ul>
 *   <li>URLs are typed and refactor-safe.</li>
 *   <li>Request / response shapes line up with the generated
 *       {@code types/api.ts} types.</li>
 *   <li>Cross-cutting behaviour (e.g. unwrapping {@code ApiResponse<T>})
 *       happens in one place.</li>
 * </ul>
 *
 * <h2>Response unwrapping</h2>
 * <p>The backend wraps payloads in {@code { success, message, data }}.
 * Each wrapper reads {@code response.data.data} so call sites work
 * directly with the domain type.</p>
 */
import type { AxiosResponse } from 'axios'

import { getAccessToken, http, invalidateAuthSession, refreshAccessToken } from './http'
import { appEnv } from '@/config/env'
import type {
  AdminAnalyticsResponse,
  AdminActivitySummaryResponse,
  AdminPaymentRecordResponse,
  AdminRefundRequestResponse,
  AdminPlatformSettingsResponse,
  AdminPlatformSettingsUpdateRequest,
  AdminAiUsageLogResponse,
  AdminDragDropActivityResponse,
  AdminFlashcardResponse,
  AdminListeningExerciseResponse,
  AdminMatchingGameResponse,
  AdminStoryActivityResponse,
  AdminDashboardResponse,
  AdminStudentListItemResponse,
  AdminTeacherDetailResponse,
  AdminTeacherListItemResponse,
  AdminUserListItemResponse,
  AccessType,
  ActivityDifficulty,
  AdminActivityType,
  AgeBand,
  AdminAiResponseReportResponse,
  AiAvailabilityResponse,
  AiChatResponse,
  AiLearningRecommendationResponse,
  AiPronunciationFeedbackResponse,
  AiQuizGeneratorResponse,
  AiSentencePracticeResponse,
  AiStoryGeneratorResponse,
  AiVocabularyHelperResponse,
  AiWritingFeedbackResponse,
  AuditLogResponse,
  AuthResponse,
  CartResponse,
  CheckoutResponse,
  CommissionSettingResponse,
  ContentSubmissionDetailResponse,
  ContentSubmissionResponse,
  ContentStatus,
  CoursePackageResponse,
  CurrentUserResponse,
  DailyStreakResponse,
  HealthStatusResponse,
  LearningCategory,
  LearningLevel,
  LearningType,
  LessonResponse,
  LessonVideoProgressSummaryResponse,
  LiveClassEnrolmentResponse,
  LiveClassResponse,
  LiveClassStatus,
  NotificationResponse,
  NotificationUnreadCountResponse,
  PageResponse,
  AdminPayoutRequestResponse,
  PublicCourseDetail,
  PublicCourseSummary,
  PublicPlatformSettingsResponse,
  QuizAttemptResultResponse,
  QuizQuestionType,
  RecordedLessonVideoResponse,
  RegistrationResponse,
  RewardSummaryResponse,
  StudentActivityCompletionResponse,
  StudentActivitySummaryResponse,
  StudentDashboardResponse,
  StudentDragDropActivityResponse,
  StudentFlashcardResponse,
  StudentLessonPlayerResponse,
  StudentListeningExerciseResponse,
  StudentMatchingGameResponse,
  StudentPaymentRecordResponse,
  StudentRefundRequestResponse,
  StudentProgressSummaryResponse,
  StudentQuizResponse,
  StudentQuizSummaryResponse,
  StudentStoryActivityResponse,
  StudentTeacherFeedbackResponse,
  StudentTracingExerciseResponse,
  TeacherAnalyticsResponse,
  TeacherDashboardResponse,
  TeacherEarningResponse,
  TeacherEarningSummaryResponse,
  TeacherFeedbackResponse,
  TeacherProfileResponse,
  TeacherPayoutRequestResponse,
  TeacherQuizAttemptSummaryResponse,
  TeacherQuizResponse,
  TeacherStudentSummaryResponse,
  TeacherVerificationStatusResponse,
  VideoProgressResponse,
  WrongAnswerResponse,
  LearningModuleResponse,
  ModerationRecordResponse,
  BadgeSummaryResponse,
  PointsTransactionResponse,
  BulkOperationResponse,
  AdminTracingExerciseResponse,
  AiResponseReportReason,
  AiResponseReportReviewStatus,
  StudentAiResponseReportResponse,
} from '@/types/api'
import type { ApiResponse } from '@/types/api'

const AI_STREAM_CONNECT_TIMEOUT_MS = 30000

class ApiEnvelopeError extends Error {
  response: { status?: number; data: { message: string; errors: string[]; fieldErrors: Record<string, string[]> } }

  constructor(message: string, errors: string[] = [], status?: number, fieldErrors: Record<string, string[]> = {}) {
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

function cleanFieldErrors(value: unknown): Record<string, string[]> {
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
  if (status === 401) return 'Please sign in again to continue.'
  if (status === 429) return 'You are doing that too often. Please wait a moment and try again.'
  if (status >= 500) return 'The learning service is having trouble right now. Please try again shortly.'
  return 'Unable to get an AI chat response.'
}

async function sseHttpError(response: Response): Promise<ApiEnvelopeError> {
  const fallbackMessage = fallbackSseHttpMessage(response.status)
  const contentType = response.headers.get('content-type') ?? ''

  if (contentType.toLowerCase().includes('application/json')) {
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

async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await request
  const payload = response.data

  if (!payload || typeof payload !== 'object' || !('success' in payload)) {
    throw new ApiEnvelopeError('The server returned an unexpected response format.')
  }

  if (payload.success === false) {
    throw new ApiEnvelopeError(payload.message || 'The request was not accepted by the server.', payload.errors ?? [])
  }

  if (!('data' in payload)) {
    throw new ApiEnvelopeError('The server response did not include the expected data payload.')
  }

  return payload.data
}

function apiUrl(path: string) {
  const baseUrl = appEnv.apiBaseUrl.replace(/\/$/, '')
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl}${suffix}`
}

function parseSseEvent(rawEvent: string): { event: string; data: string } | null {
  let event = 'message'
  const dataLines: string[] = []
  rawEvent.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  })
  if (!dataLines.length) return null
  return { event, data: dataLines.join('\n') }
}

function parseSseCompletePayload(data: string): AiChatResponse {
  try {
    return JSON.parse(data) as AiChatResponse
  } catch {
    throw new ApiEnvelopeError('The AI stream returned a malformed final response.')
  }
}

async function readSseStream(
  response: Response,
  handlers: {
    onDelta?: (chunk: string) => void
    onComplete?: (payload: AiChatResponse) => void
  },
): Promise<AiChatResponse> {
  if (!response.body) {
    throw new ApiEnvelopeError('The server did not open an AI stream.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finalPayload: AiChatResponse | null = null

  const processEvent = (rawEvent: string) => {
    if (!rawEvent.trim()) return
    const parsed = parseSseEvent(rawEvent)
    if (!parsed) return
    if (parsed.event === 'delta') {
      handlers.onDelta?.(parsed.data)
    } else if (parsed.event === 'complete') {
      finalPayload = parseSseCompletePayload(parsed.data)
      handlers.onComplete?.(finalPayload)
    } else if (parsed.event === 'error') {
      throw new ApiEnvelopeError(parsed.data || 'The AI stream stopped unexpectedly.')
    }
  }

  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() ?? ''

      events.forEach(processEvent)

      if (done) break
    }
  } finally {
    reader.releaseLock()
  }

  if (buffer.trim()) {
    processEvent(buffer)
  }

  if (!finalPayload) {
    throw new ApiEnvelopeError('The AI stream ended before the final response arrived.')
  }
  return finalPayload
}

async function postSseChat(
  payload: AiChatPayload,
  handlers: {
    onDelta?: (chunk: string) => void
    onComplete?: (payload: AiChatResponse) => void
  } = {},
) {
  const sendRequest = async () => {
    const abortController = new AbortController()
    const timeoutId = globalThis.setTimeout(() => abortController.abort(), AI_STREAM_CONNECT_TIMEOUT_MS)
    const headers = new Headers({
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    })
    const token = getAccessToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }

    try {
      return await fetch(apiUrl('/ai/chat/stream'), {
        method: 'POST',
        credentials: 'include',
        headers,
        signal: abortController.signal,
        body: JSON.stringify(payload),
      })
    } catch (error) {
      if (abortController.signal.aborted) {
        throw new ApiEnvelopeError('The AI stream took too long to respond. Please try again.')
      }

      throw error
    } finally {
      globalThis.clearTimeout(timeoutId)
    }
  }

  let response = await sendRequest()
  if (response.status === 401) {
    const refreshedToken = await refreshAccessToken()
    if (!refreshedToken) {
      invalidateAuthSession()
      throw await sseHttpError(response)
    }

    response = await sendRequest()
    if (response.status === 401) {
      invalidateAuthSession()
    }
  }

  if (!response.ok) {
    throw await sseHttpError(response)
  }

  return readSseStream(response, handlers)
}

async function unwrapPageItems<T>(request: Promise<{ data: ApiResponse<PageResponse<T>> }>): Promise<T[]> {
  const page = await unwrap<PageResponse<T>>(request)
  return page?.items ?? []
}

function parseContentDispositionFileName(contentDisposition: string | undefined): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;\s]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }

  const asciiMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return asciiMatch?.[1] ?? null
}

async function unwrapDownload(request: Promise<AxiosResponse<Blob>>) {
  const response = await request
  return {
    blob: response.data,
    fileName: parseContentDispositionFileName(response.headers['content-disposition']),
    contentType: response.headers['content-type'] ?? null,
  }
}

interface PublicCourseFilters {
  keyword?: string
  levelId?: number
  categoryId?: number
  accessType?: string
  learningType?: string
  sort?: string
  page?: number
  size?: number
}

interface AiChatPayload {
  message: string
  languagePreference?: string
  sessionId?: number
  context?: string
  relatedLessonId?: number
}

export interface TeacherModulePayload {
  title: string
  description?: string | null
  levelId: number
  categoryId: number
  accessType: AccessType
  price?: number | null
  coverImageUrl?: string | null
}

export interface TeacherLessonPayload {
  title: string
  description?: string | null
  moduleId?: number | null
  levelId: number
  categoryId: number
  learningType: LearningType
  accessType: AccessType
  price?: number | null
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
  accessType: AccessType
  price?: number | null
  coverImageUrl?: string | null
}

export interface TeacherLiveClassPayload {
  title: string
  description?: string | null
  lessonId?: number | null
  packageId?: number | null
  startTime: string
  endTime: string
  meetingLink: string
  meetingPlatform: 'ZOOM' | 'GOOGLE_MEET' | 'MICROSOFT_TEAMS' | 'OTHER'
  status?: LiveClassStatus | null
  maxParticipants?: number | null
}

export interface TeacherQuizPayload {
  title: string
  description?: string | null
  timeLimitMinutes?: number | null
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
  ageBand?: AgeBand | ''
  difficulty?: ActivityDifficulty | ''
  page?: number
  size?: number
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
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
}

export interface AdminMatchingGamePayload {
  title: string
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  items: AdminMatchingGameItemPayload[]
}

export interface AdminListeningExercisePayload {
  title: string
  audioUrl?: string | null
  questionPayload?: string | null
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
}

export interface AdminTracingExercisePayload {
  title: string
  worksheetUrl?: string | null
  targetCharacter?: string | null
  pinyin?: string | null
  meaning?: string | null
  instructions?: string | null
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
}

export interface AdminDragDropActivityPayload {
  title: string
  instructions?: string | null
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
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
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags?: string[] | null
  xpReward?: number | null
  estimatedSeconds?: number | null
  questions: AdminStoryQuestionPayload[]
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
    preferredLanguage?: string
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
  me() {
    return unwrap<CurrentUserResponse>(http.get('/auth/me'))
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
    return unwrap<PublicCourseDetail>(http.get(`/public/courses/${id}`))
  },
}

export const notificationsApi = {
  list(params: Record<string, unknown> = {}) {
    return unwrapPageItems<NotificationResponse>(http.get('/notifications', { params }))
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
  liveClasses() {
    return unwrapPageItems<LiveClassResponse>(http.get('/students/live-classes'))
  },
  joinLiveClass(id: number) {
    return unwrap<{ joinUrl: string; meetingPlatform: string; title: string; startsAt: string }>(
      http.get(`/students/live-classes/${id}/join`),
    )
  },
  enrolLiveClass(id: number) {
    return unwrap<LiveClassResponse>(http.post(`/students/live-classes/${id}/enrol`))
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
  quizzes(params: Record<string, string | number | undefined> = {}) {
    return unwrap<PageResponse<StudentQuizSummaryResponse>>(http.get('/students/quizzes', { params }))
  },
  quiz(quizId: number) {
    return unwrap<StudentQuizResponse>(http.get(`/students/quizzes/${quizId}`))
  },
  submitQuiz(
    quizId: number,
    answers: Array<{
      questionId: number
      answerText: string
    }>,
  ) {
    return unwrap<QuizAttemptResultResponse>(http.post(`/students/quizzes/${quizId}/attempts`, { answers }))
  },
  wrongAnswers() {
    return unwrapPageItems<WrongAnswerResponse>(http.get('/students/wrong-answers'))
  },
  feedback() {
    return unwrapPageItems<StudentTeacherFeedbackResponse>(http.get('/students/feedback'))
  },
  activityLibrary(params: Record<string, string | number | undefined> = {}) {
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
  completeMatchingGame(id: number) {
    return unwrap<StudentActivityCompletionResponse>(http.post(`/students/activities/matching-games/${id}/complete`))
  },
  listeningExercise(id: number) {
    return unwrap<StudentListeningExerciseResponse>(http.get(`/students/activities/listening-exercises/${id}`))
  },
  completeListeningExercise(id: number) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/listening-exercises/${id}/complete`),
    )
  },
  tracingExercise(id: number) {
    return unwrap<StudentTracingExerciseResponse>(http.get(`/students/activities/tracing-exercises/${id}`))
  },
  completeTracingExercise(id: number) {
    return unwrap<StudentActivityCompletionResponse>(http.post(`/students/activities/tracing-exercises/${id}/complete`))
  },
  dragDropActivity(id: number) {
    return unwrap<StudentDragDropActivityResponse>(http.get(`/students/activities/drag-drop-activities/${id}`))
  },
  completeDragDropActivity(id: number) {
    return unwrap<StudentActivityCompletionResponse>(
      http.post(`/students/activities/drag-drop-activities/${id}/complete`),
    )
  },
  storyActivity(id: number) {
    return unwrap<StudentStoryActivityResponse>(http.get(`/students/activities/story-activities/${id}`))
  },
  completeStoryActivity(id: number) {
    return unwrap<StudentActivityCompletionResponse>(http.post(`/students/activities/story-activities/${id}/complete`))
  },
  cart() {
    return unwrap<CartResponse>(http.get('/students/cart'))
  },
  addCartItem(payload: { itemType: string; itemId: number; featureKey?: string }) {
    return unwrap<CartResponse>(http.post('/students/cart/items', payload))
  },
  removeCartItem(cartItemId: number) {
    return unwrap<CartResponse>(http.delete(`/students/cart/items/${cartItemId}`))
  },
  clearCart() {
    return unwrap<CartResponse>(http.delete('/students/cart/clear'))
  },
  checkoutCart() {
    return unwrap<CheckoutResponse>(http.post('/students/checkout/cart'))
  },
  buyNow(payload: { itemType: string; itemId: number; featureKey?: string }) {
    return unwrap<CheckoutResponse>(http.post('/students/checkout/buy-now', payload))
  },
  payments(params: Record<string, unknown> = {}) {
    return unwrapPageItems<StudentPaymentRecordResponse>(http.get('/students/payments', { params }))
  },
  refunds(params: Record<string, unknown> = {}) {
    return unwrapPageItems<StudentRefundRequestResponse>(http.get('/students/refund-requests', { params }))
  },
  createRefund(payload: { orderId: number; reason: string }) {
    return unwrap<StudentRefundRequestResponse>(http.post('/students/refund-requests', payload))
  },
}

export const paymentApi = {
  status(paymentId: number) {
    return unwrap<StudentPaymentRecordResponse>(http.get(`/payments/status/${paymentId}`))
  },
}

export const aiApi = {
  availability() {
    return unwrap<AiAvailabilityResponse>(http.get('/ai/availability'))
  },
  chat(payload: AiChatPayload) {
    return unwrap<AiChatResponse>(http.post('/ai/chat', payload))
  },
  chatStream: postSseChat,
  vocabularyHelper(payload: {
    word: string
    inputLanguage?: string
    outputLanguage?: string
    learnerLevel?: string
    languagePreference?: string
  }) {
    return unwrap<AiVocabularyHelperResponse>(http.post('/ai/vocabulary-helper', payload))
  },
  sentencePractice(payload: { sentence: string; learnerLevel?: string; languagePreference?: string }) {
    return unwrap<AiSentencePracticeResponse>(http.post('/ai/sentence-practice', payload))
  },
  storyGenerator(payload: { topic: string; level?: string; languagePreference?: string; lengthPreference?: string }) {
    return unwrap<AiStoryGeneratorResponse>(http.post('/ai/story-generator', payload))
  },
  quizGenerator(payload: {
    topic: string
    level?: string
    category?: string
    numberOfQuestions?: number
    questionTypes?: string[]
    targetRoleContext?: string
    languagePreference?: string
  }) {
    return unwrap<AiQuizGeneratorResponse>(http.post('/ai/quiz-generator', payload))
  },
  learningRecommendation(payload: {
    studentId?: number
    currentLevel?: string
    recentQuizResults?: string[]
    wrongAnswers?: string[]
    weakAreas?: string[]
    completedLessons?: string[]
    languagePreference?: string
  }) {
    return unwrap<AiLearningRecommendationResponse>(http.post('/ai/learning-recommendation', payload))
  },
  pronunciationFeedback(formData: FormData) {
    return unwrap<AiPronunciationFeedbackResponse>(http.post('/ai/pronunciation-feedback', formData))
  },
  writingFeedback(formData: FormData) {
    return unwrap<AiWritingFeedbackResponse>(http.post('/ai/writing-feedback', formData))
  },
  reports(params: Record<string, unknown> = {}) {
    return unwrapPageItems<StudentAiResponseReportResponse>(http.get('/ai/reports', { params }))
  },
  reportResponse(payload: {
    aiUsageLogId?: number
    chatSessionId?: number
    chatMessageId?: number
    featureKey: string
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
    return unwrapPageItems<LearningModuleResponse>(http.get('/teachers/modules'))
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
    return unwrapPageItems<LessonResponse>(http.get('/teachers/lessons'))
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
    return unwrapPageItems<CoursePackageResponse>(http.get('/teachers/packages'))
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
    return unwrapPageItems<LiveClassResponse>(http.get('/teachers/live-classes'))
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
    return unwrapPageItems<LiveClassEnrolmentResponse>(http.get(`/teachers/live-classes/${id}/enrolments`))
  },
  recordedVideos(lessonId: number) {
    return unwrapPageItems<RecordedLessonVideoResponse>(http.get(`/teachers/lessons/${lessonId}/recorded-videos`))
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
    return unwrapPageItems<LessonVideoProgressSummaryResponse>(http.get(`/teachers/lessons/${lessonId}/video-progress`))
  },
  lessonQuizzes(lessonId: number) {
    return unwrapPageItems<TeacherQuizResponse>(http.get(`/teachers/lessons/${lessonId}/quizzes`))
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
    return unwrapPageItems<TeacherQuizAttemptSummaryResponse>(http.get(`/teachers/quizzes/${quizId}/attempts`))
  },
  feedbackList() {
    return unwrapPageItems<TeacherFeedbackResponse>(http.get('/teachers/feedback'))
  },
  students() {
    return unwrapPageItems<TeacherStudentSummaryResponse>(http.get('/teachers/students'))
  },
  createFeedback(payload: {
    studentId: number
    lessonId?: number
    quizAttemptId?: number
    feedbackType?: string
    message: string
  }) {
    return unwrap<TeacherFeedbackResponse>(http.post('/teachers/feedback', payload))
  },
  updateFeedback(id: number, payload: { feedbackType?: string; message: string }) {
    return unwrap<TeacherFeedbackResponse>(http.put(`/teachers/feedback/${id}`, payload))
  },
  earnings(params: Record<string, unknown> = {}) {
    return unwrapPageItems<TeacherEarningResponse>(http.get('/teachers/earnings', { params }))
  },
  earningsSummary() {
    return unwrap<TeacherEarningSummaryResponse>(http.get('/teachers/earnings/summary'))
  },
  payouts(params: Record<string, unknown> = {}) {
    return unwrapPageItems<TeacherPayoutRequestResponse>(http.get('/teachers/payout-requests', { params }))
  },
  createPayoutRequest(payload: { amount: number; requestedNote?: string }) {
    return unwrap<TeacherPayoutRequestResponse>(http.post('/teachers/payout-requests', payload))
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
  users(params: Record<string, unknown> = {}) {
    return unwrap<PageResponse<AdminUserListItemResponse>>(http.get('/admin/users', { params }))
  },
  updateUserStatus(id: number, payload: { active: boolean }) {
    return unwrap<AdminUserListItemResponse>(http.patch(`/admin/users/${id}/status`, payload))
  },
  students(params: Record<string, unknown> = {}) {
    return unwrap<PageResponse<AdminStudentListItemResponse>>(http.get('/admin/students', { params }))
  },
  teachers(params: Record<string, unknown> = {}) {
    return unwrap<PageResponse<AdminTeacherListItemResponse>>(http.get('/admin/teachers', { params }))
  },
  pendingTeachers(params: Record<string, unknown> = {}) {
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
  aiUsageLogs(params: Record<string, unknown> = {}) {
    return unwrap<PageResponse<AdminAiUsageLogResponse>>(http.get('/admin/ai/usage-logs', { params }))
  },
  flashcard(id: number) {
    return unwrap<AdminFlashcardResponse>(http.get(`/admin/activities/flashcards/${id}`))
  },
  createFlashcard(payload: AdminFlashcardPayload) {
    return unwrap<AdminFlashcardResponse>(http.post('/admin/activities/flashcards', payload))
  },
  updateFlashcard(id: number, payload: AdminFlashcardPayload) {
    return unwrap<AdminFlashcardResponse>(http.put(`/admin/activities/flashcards/${id}`, payload))
  },
  updateFlashcardStatus(id: number, status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED') {
    return unwrap<AdminFlashcardResponse>(http.patch(`/admin/activities/flashcards/${id}/status`, { status }))
  },
  deleteFlashcard(id: number) {
    return unwrap<void>(http.delete(`/admin/activities/flashcards/${id}`))
  },
  matchingGame(id: number) {
    return unwrap<AdminMatchingGameResponse>(http.get(`/admin/activities/matching-games/${id}`))
  },
  createMatchingGame(payload: AdminMatchingGamePayload) {
    return unwrap<AdminMatchingGameResponse>(http.post('/admin/activities/matching-games', payload))
  },
  updateMatchingGame(id: number, payload: AdminMatchingGamePayload) {
    return unwrap<AdminMatchingGameResponse>(http.put(`/admin/activities/matching-games/${id}`, payload))
  },
  updateMatchingGameStatus(id: number, status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED') {
    return unwrap<AdminMatchingGameResponse>(http.patch(`/admin/activities/matching-games/${id}/status`, { status }))
  },
  deleteMatchingGame(id: number) {
    return unwrap<void>(http.delete(`/admin/activities/matching-games/${id}`))
  },
  listeningExercise(id: number) {
    return unwrap<AdminListeningExerciseResponse>(http.get(`/admin/activities/listening-exercises/${id}`))
  },
  createListeningExercise(payload: AdminListeningExercisePayload) {
    return unwrap<AdminListeningExerciseResponse>(http.post('/admin/activities/listening-exercises', payload))
  },
  updateListeningExercise(id: number, payload: AdminListeningExercisePayload) {
    return unwrap<AdminListeningExerciseResponse>(http.put(`/admin/activities/listening-exercises/${id}`, payload))
  },
  updateListeningExerciseStatus(id: number, status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED') {
    return unwrap<AdminListeningExerciseResponse>(
      http.patch(`/admin/activities/listening-exercises/${id}/status`, { status }),
    )
  },
  deleteListeningExercise(id: number) {
    return unwrap<void>(http.delete(`/admin/activities/listening-exercises/${id}`))
  },
  tracingExercise(id: number) {
    return unwrap<AdminTracingExerciseResponse>(http.get(`/admin/activities/tracing-exercises/${id}`))
  },
  createTracingExercise(payload: AdminTracingExercisePayload) {
    return unwrap<AdminTracingExerciseResponse>(http.post('/admin/activities/tracing-exercises', payload))
  },
  updateTracingExercise(id: number, payload: AdminTracingExercisePayload) {
    return unwrap<AdminTracingExerciseResponse>(http.put(`/admin/activities/tracing-exercises/${id}`, payload))
  },
  updateTracingExerciseStatus(id: number, status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED') {
    return unwrap<AdminTracingExerciseResponse>(
      http.patch(`/admin/activities/tracing-exercises/${id}/status`, { status }),
    )
  },
  deleteTracingExercise(id: number) {
    return unwrap<void>(http.delete(`/admin/activities/tracing-exercises/${id}`))
  },
  dragDropActivity(id: number) {
    return unwrap<AdminDragDropActivityResponse>(http.get(`/admin/activities/drag-drop-activities/${id}`))
  },
  createDragDropActivity(payload: AdminDragDropActivityPayload) {
    return unwrap<AdminDragDropActivityResponse>(http.post('/admin/activities/drag-drop-activities', payload))
  },
  updateDragDropActivity(id: number, payload: AdminDragDropActivityPayload) {
    return unwrap<AdminDragDropActivityResponse>(http.put(`/admin/activities/drag-drop-activities/${id}`, payload))
  },
  updateDragDropActivityStatus(id: number, status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED') {
    return unwrap<AdminDragDropActivityResponse>(
      http.patch(`/admin/activities/drag-drop-activities/${id}/status`, { status }),
    )
  },
  deleteDragDropActivity(id: number) {
    return unwrap<void>(http.delete(`/admin/activities/drag-drop-activities/${id}`))
  },
  storyActivity(id: number) {
    return unwrap<AdminStoryActivityResponse>(http.get(`/admin/activities/story-activities/${id}`))
  },
  createStoryActivity(payload: AdminStoryActivityPayload) {
    return unwrap<AdminStoryActivityResponse>(http.post('/admin/activities/story-activities', payload))
  },
  updateStoryActivity(id: number, payload: AdminStoryActivityPayload) {
    return unwrap<AdminStoryActivityResponse>(http.put(`/admin/activities/story-activities/${id}`, payload))
  },
  updateStoryActivityStatus(id: number, status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED') {
    return unwrap<AdminStoryActivityResponse>(http.patch(`/admin/activities/story-activities/${id}/status`, { status }))
  },
  deleteStoryActivity(id: number) {
    return unwrap<void>(http.delete(`/admin/activities/story-activities/${id}`))
  },
  content(params: Record<string, unknown> = {}) {
    return unwrap<PageResponse<ContentSubmissionResponse>>(http.get('/admin/content/submissions', { params }))
  },
  contentDetail(contentType: string, id: number) {
    return unwrap<ContentSubmissionDetailResponse>(http.get(`/admin/content/submissions/${contentType}/${id}`))
  },
  archiveContent(contentType: string, id: number, note?: string) {
    return unwrap<ContentSubmissionDetailResponse>(
      http.patch(`/admin/content/${contentType}/${id}/archive`, note ? { note } : {}),
    )
  },
  moderationRecords(params: Record<string, unknown> = {}) {
    return unwrapPageItems<ModerationRecordResponse>(http.get('/admin/moderation-records', { params }))
  },
  aiReports(status?: string, params: Record<string, unknown> = {}) {
    return unwrapPageItems<AdminAiResponseReportResponse>(
      http.get('/admin/ai-response-reports', { params: { ...params, status } }),
    )
  },
  reviewAiReport(id: number, payload: ReviewAiReportPayload) {
    return unwrap<AdminAiResponseReportResponse>(http.post(`/admin/ai-response-reports/${id}/review`, payload))
  },
  moderationAction(
    action: 'hide' | 'unpublish' | 'archive' | 'request-revision' | 'restore',
    payload: { targetType: string; targetId: number; reason: string },
  ) {
    return unwrap<ModerationRecordResponse>(http.post(`/admin/moderation/${action}`, payload))
  },
  notifications(params: Record<string, unknown> = {}) {
    return unwrapPageItems<NotificationResponse>(http.get('/admin/notifications', { params }))
  },
  auditLogs(params: Record<string, unknown> = {}) {
    return unwrapPageItems<AuditLogResponse>(http.get('/admin/audit-logs', { params }))
  },
  payments(params: Record<string, unknown> = {}) {
    return unwrapPageItems<AdminPaymentRecordResponse>(http.get('/admin/payments', { params }))
  },
  refunds(params: Record<string, unknown> = {}) {
    return unwrapPageItems<AdminRefundRequestResponse>(http.get('/admin/refund-requests', { params }))
  },
  approveRefund(id: number) {
    return unwrap<AdminRefundRequestResponse>(http.post(`/admin/refund-requests/${id}/approve`))
  },
  rejectRefund(id: number, payload: { reason: string }) {
    return unwrap<AdminRefundRequestResponse>(http.post(`/admin/refund-requests/${id}/reject`, payload))
  },
  markRefunded(id: number) {
    return unwrap<AdminRefundRequestResponse>(http.post(`/admin/refund-requests/${id}/mark-refunded`))
  },
  payouts(params: Record<string, unknown> = {}) {
    return unwrapPageItems<AdminPayoutRequestResponse>(http.get('/admin/payout-requests', { params }))
  },
  approvePayout(id: number) {
    return unwrap<AdminPayoutRequestResponse>(http.post(`/admin/payout-requests/${id}/approve`))
  },
  rejectPayout(id: number, payload: { reason: string }) {
    return unwrap<AdminPayoutRequestResponse>(http.post(`/admin/payout-requests/${id}/reject`, payload))
  },
  markPayoutPaid(id: number) {
    return unwrap<AdminPayoutRequestResponse>(http.post(`/admin/payout-requests/${id}/mark-paid`))
  },
  commissionSetting() {
    return unwrap<CommissionSettingResponse>(http.get('/admin/system-settings/commission'))
  },
  updateCommissionSetting(payload: { commissionRate: number }) {
    return unwrap<CommissionSettingResponse>(http.put('/admin/system-settings/commission', payload))
  },
  platformSettings() {
    return unwrap<AdminPlatformSettingsResponse>(http.get('/admin/system-settings/platform'))
  },
  updatePlatformSettings(payload: AdminPlatformSettingsUpdateRequest) {
    return unwrap<AdminPlatformSettingsResponse>(http.put('/admin/system-settings/platform', payload))
  },
}
