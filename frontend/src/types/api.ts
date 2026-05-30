/**
 * Frontend mirror of the backend REST API contracts.
 *
 * <h2>What lives here</h2>
 * <p>Every request payload, response shape, and enum the frontend
 * sends or receives. The names intentionally mirror the Java DTO and
 * enum types in {@code com.example.aichinese.dto.*} and
 * {@code com.example.aichinese.entity.enums.*} so backend contract
 * changes have an explicit frontend review surface.</p>
 *
 * <h2>Why hand-maintained</h2>
 * <p>We deliberately avoid OpenAPI-generated types because:</p>
 * <ul>
 *   <li>The frontend often needs intersection / utility types
 *       (e.g. {@code AiResponseReportReviewStatus =
 *       Exclude<AiResponseReportStatus, 'PENDING_REVIEW'>}) that
 *       generators struggle with.</li>
 *   <li>Hand-curated naming is more idiomatic Vue / TS than the
 *       generator output.</li>
 *   <li>Backend-frontend contract changes get a code-review
 *       diff in this file, which doubles as a changelog of the
 *       wire format.</li>
 * </ul>
 *
 * <p>When adding a new endpoint: define request + response types
 * here first, then add the wrapper in {@code api/services.ts}.</p>
 */
export type AppLanguage = 'en' | 'ms' | 'zh'
export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'
export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type AccessType = 'FREE' | 'PAID'
export type LearningType = 'LIVE' | 'RECORDED' | 'HYBRID'
export type AgeBand = 'KINDERGARTEN' | 'LOWER_PRIMARY' | 'UPPER_PRIMARY' | 'ALL_AGES'
export type ActivityDifficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type ContentStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'ARCHIVED'
export type LiveClassStatus = 'SCHEDULED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED'
export type AiResponseReportStatus = 'PENDING_REVIEW' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED'
export type AiResponseReportReviewStatus = Exclude<AiResponseReportStatus, 'PENDING_REVIEW'>
export type AiResponseReportReason =
  | 'INCORRECT_CONTENT'
  | 'HALLUCINATION'
  | 'UNSAFE_CONTENT'
  | 'AGE_INAPPROPRIATE'
  | 'OTHER'
export type ManagedContentType = 'LESSON' | 'MODULE' | 'PACKAGE'
export type PackageItemType = 'MODULE' | 'LESSON'
export type TeacherVerificationDocumentType = 'CERTIFICATE' | 'QUALIFICATION' | 'IDENTITY' | 'OTHER'
export type AdminActivityType =
  | 'FLASHCARD'
  | 'MATCHING_GAME'
  | 'LISTENING_EXERCISE'
  | 'TRACING_EXERCISE'
  | 'DRAG_DROP_ACTIVITY'
  | 'STORY_ACTIVITY'
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
  fieldErrors: Record<string, string[]>
  requestId?: string | null
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface LearningLevel {
  id: number
  name: string
  description: string
}

export interface LearningCategory {
  id: number
  name: string
  description: string
}

export interface PublicTeacherSummary {
  teacherId: number
  displayName: string
  verified: boolean
}

export interface TeacherOwnerSummary {
  teacherId: number
  userId: number
  fullName: string
  teacherStatus: TeacherStatus
}

export interface PackageItemResponse {
  id: number
  packageId: number
  itemType: PackageItemType
  itemId: number
  displayOrder: number
  titleSnapshot: string
}

export interface AuthUserSummary {
  id: number
  fullName: string
  email: string
  roles: UserRole[]
  teacherStatus?: TeacherStatus | null
}

export interface AuthResponse {
  token: string
  user: AuthUserSummary
}

export interface RegistrationResponse {
  id: number
  fullName: string
  email: string
  preferredLanguage?: AppLanguage | null
  roles: UserRole[]
  teacherStatus?: TeacherStatus | null
}

export interface CurrentUserResponse {
  id: number
  fullName: string
  email: string
  preferredLanguage?: AppLanguage | null
  roles: UserRole[]
  studentProfile?: {
    id: number
    displayName: string
    currentLevel: string
    totalPoints: number
  } | null
  teacherProfile?: {
    id: number
    teacherStatus: TeacherStatus
    bio?: string | null
    rejectionReason?: string | null
  } | null
}

export interface PublicCourseSummary {
  id: string
  contentId: number
  type: ManagedContentType
  title: string
  shortDescription?: string | null
  teacher: PublicTeacherSummary
  level: LearningLevel
  category: LearningCategory
  learningType: LearningType
  accessType: AccessType
  price: number
  currencyCode: string
  rating?: number | null
  coverImageUrl?: string | null
  locked: boolean
  status: ContentStatus
}

export interface PublicCourseItemSummary {
  type: PackageItemType
  contentId: number
  title: string
  displayOrder: number
}

export interface PublicCourseDetail {
  id: string
  contentId: number
  type: ManagedContentType
  title: string
  description?: string | null
  teacher: PublicTeacherSummary
  level: LearningLevel
  category: LearningCategory
  learningType: LearningType
  accessType: AccessType
  price: number
  currencyCode: string
  coverImageUrl?: string | null
  locked: boolean
  status: ContentStatus
  basicContent?: string | null
  packageItems: PublicCourseItemSummary[]
  callToAction?: string | null
  lockedMessage?: string | null
}

export interface NotificationResponse {
  id: number
  title: string
  message: string
  type: string
  status: string
  targetType?: string | null
  targetId?: number | null
  targetPath?: string | null
  createdAt: string
  readAt?: string | null
}

export interface NotificationUnreadCountResponse {
  unreadCount: number
}

export interface BulkOperationResponse {
  updatedCount: number
}

export interface HealthStatusResponse {
  status: string
  service: string
  timestamp: string
}

export interface StudentDashboardResponse {
  fullName: string
  currentLevel: string
  learningProgressPercent: number
  dailyStreak: number
  points: number
  badgeCount: number
  recentQuizResults: Array<{
    attemptId: number
    quizId: number | null
    quizTitle: string
    score: number
    maxScore: number
    completedAt: string
  }>
  weakAreas: string[]
  unfinishedLessons: Array<{
    lessonId: number
    lessonTitle: string
    completionPercent: number
    lastAccessedAt: string
  }>
  recentNotifications: Array<{
    notificationId: number
    title: string
    message: string
    status: string
    targetPath?: string | null
    createdAt: string
  }>
  recommendedNextStep?: string | null
}

export interface StudentProgressLessonResponse {
  lessonId: number
  lessonTitle: string
  completionPercent: number
  completed: boolean
  lastAccessedAt?: string | null
}

export interface StudentProgressSummaryResponse {
  totalLessonsStarted: number
  completedLessons: number
  quizAverage: number
  overallCompletionPercent: number
  weakAreas: string[]
  lastActivitySummary?: string | null
  recentLessons: StudentProgressLessonResponse[]
}

export interface DailyStreakResponse {
  currentStreak: number
  bestStreak: number
  lastLearningDate?: string | null
}

export interface BadgeSummaryResponse {
  badgeId: number
  name: string
  description: string
  iconKey?: string | null
  awardedAt?: string | null
}

export interface PointsTransactionResponse {
  id: number
  pointsDelta: number
  reason: string
  sourceType: string
  sourceId?: number | null
  createdAt: string
}

export interface RewardSummaryResponse {
  totalPoints: number
  currentLevelNumber: number
  progressToNextLevelPercent: number
  streak: DailyStreakResponse
  badges: BadgeSummaryResponse[]
  recentPoints: PointsTransactionResponse[]
}

export interface LiveClassTeacherSummaryResponse {
  teacherId: number
  userId: number
  displayName: string
}

export interface LiveClassLessonSummaryResponse {
  lessonId: number
  lessonTitle: string
  learningType: string
  accessType: string
}

export interface LiveClassPackageSummaryResponse {
  packageId: number
  packageTitle: string
  learningType: string
  accessType: string
}

export interface LiveClassResponse {
  id: number
  title: string
  description?: string | null
  meetingPlatform: string
  liveClassStatus: LiveClassStatus
  startTime: string
  endTime: string
  timeZone: string
  durationMinutes?: number | null
  maxParticipants?: number | null
  enrolledCount: number
  enrolled: boolean
  canEnrol: boolean
  accessAllowed: boolean
  canJoin: boolean
  meetingLinkVisible: boolean
  meetingLink?: string | null
  teacher?: LiveClassTeacherSummaryResponse | null
  lesson?: LiveClassLessonSummaryResponse | null
  coursePackage?: LiveClassPackageSummaryResponse | null
  createdAt: string
  updatedAt: string
}

export interface RecordedLessonVideoResponse {
  id: number
  lessonId: number
  packageId?: number | null
  title: string
  description?: string | null
  sourceType: string
  externalVideoUrl?: string | null
  streamUrl?: string | null
  durationSeconds?: number | null
  sortOrder: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface StudentLessonPlayerResponse {
  lessonId: number
  title: string
  description?: string | null
  basicContent?: string | null
  estimatedDurationMinutes?: number | null
  completionPercent: number
  completed: boolean
  locked: boolean
  learningType: string
  liveClasses: LiveClassResponse[]
  recordedVideos: RecordedLessonVideoResponse[]
}

export interface VideoProgressResponse {
  id: number
  lessonId: number
  recordedVideoId: number
  watchedSeconds: number
  totalSeconds?: number | null
  progressPercentage: number
  completed: boolean
  lastWatchedAt?: string | null
  updatedAt: string
}

export interface StudentActivitySummaryResponse {
  id: number
  type: string
  title: string
  hskLevel?: number | null
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  updatedAt: string
  completed: boolean
}

export interface StudentActivityCompletionResponse {
  activityId: number
  type: string
  earnedPoints: number
  totalPoints: number
  firstCompletion: boolean
}

export interface StudentFlashcardResponse {
  id: number
  title: string
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
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  completed: boolean
}

export interface StudentMatchingGameItemResponse {
  id: number
  leftText: string
  rightText: string
  leftImageUrl?: string | null
  rightImageUrl?: string | null
  pinyin?: string | null
  sortOrder?: number | null
}

export interface StudentMatchingGameResponse {
  id: number
  title: string
  hskLevel?: number | null
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  completed: boolean
  items: StudentMatchingGameItemResponse[]
}

export interface StudentListeningExerciseResponse {
  id: number
  title: string
  audioUrl?: string | null
  questionPayload?: string | null
  hskLevel?: number | null
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  completed: boolean
}

export interface StudentTracingExerciseResponse {
  id: number
  title: string
  worksheetUrl?: string | null
  targetCharacter: string
  pinyin?: string | null
  meaning?: string | null
  instructions?: string | null
  hskLevel?: number | null
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  completed: boolean
}

export interface StudentDragDropItemResponse {
  id: number
  dragText: string
  targetZone: string
  pinyin?: string | null
  imageUrl?: string | null
  sortOrder?: number | null
}

export interface StudentDragDropActivityResponse {
  id: number
  title: string
  instructions?: string | null
  hskLevel?: number | null
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  completed: boolean
  items: StudentDragDropItemResponse[]
}

export interface StudentStoryQuestionResponse {
  id: number
  questionText: string
  optionsPayload: string
  explanation?: string | null
  sortOrder?: number | null
}

export interface StudentStoryActivityResponse {
  id: number
  title: string
  instructions?: string | null
  storyText: string
  pinyin?: string | null
  translation?: string | null
  imageUrl?: string | null
  hskLevel?: number | null
  ageBand?: string | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  completed: boolean
  questions: StudentStoryQuestionResponse[]
}

export interface AdminActivitySummaryResponse {
  id: number
  type: AdminActivityType
  title: string
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminMatchingGameItemResponse {
  id: number
  leftText: string
  rightText: string
  leftImageUrl?: string | null
  rightImageUrl?: string | null
  pinyin?: string | null
  sortOrder?: number | null
}

export interface AdminDragDropItemResponse {
  id: number
  dragText: string
  targetZone: string
  pinyin?: string | null
  imageUrl?: string | null
  sortOrder?: number | null
}

export interface AdminStoryQuestionResponse {
  id: number
  questionText: string
  optionsPayload: string
  explanation?: string | null
  sortOrder?: number | null
}

export interface AdminFlashcardResponse {
  id: number
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
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminMatchingGameResponse {
  id: number
  title: string
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
  items: AdminMatchingGameItemResponse[]
}

export interface AdminListeningExerciseResponse {
  id: number
  title: string
  audioUrl?: string | null
  questionPayload?: string | null
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminTracingExerciseResponse {
  id: number
  title: string
  worksheetUrl?: string | null
  targetCharacter?: string | null
  pinyin?: string | null
  meaning?: string | null
  instructions?: string | null
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminDragDropActivityResponse {
  id: number
  title: string
  instructions?: string | null
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
  items: AdminDragDropItemResponse[]
}

export interface AdminStoryActivityResponse {
  id: number
  title: string
  instructions?: string | null
  storyText: string
  pinyin?: string | null
  translation?: string | null
  imageUrl?: string | null
  status: ContentStatus
  hskLevel?: number | null
  ageBand?: AgeBand | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
  questions: AdminStoryQuestionResponse[]
}

export interface QuizOptionResponse {
  id: number
  optionText: string
  displayOrder: number
}

export interface StudentQuizQuestionResponse {
  id: number
  questionType: QuizQuestionType
  questionText: string
  points: number
  displayOrder: number
  options: QuizOptionResponse[]
}

export interface StudentQuizResponse {
  id: number
  lessonId: number
  lessonTitle: string
  title: string
  description?: string | null
  timeLimitMinutes?: number | null
  totalPoints: number
  questions: StudentQuizQuestionResponse[]
}

export interface StudentQuizSummaryResponse {
  id: number
  lessonId: number
  lessonTitle: string
  title: string
  description?: string | null
  timeLimitMinutes?: number | null
  totalPoints: number
  questionCount: number
}

export interface QuizAttemptResultQuestionResponse {
  questionId: number
  questionType: string
  questionText: string
  studentAnswer: string
  correctAnswer: string
  correct: boolean
  points?: number | null
  explanation?: string | null
}

export interface QuizAttemptResultResponse {
  attemptId: number
  quizId: number
  quizTitle: string
  score: number
  maxScore: number
  totalQuestions: number
  correctCount: number
  wrongCount: number
  earnedPoints: number
  totalPoints: number
  passed: boolean
  startedAt: string
  submittedAt: string
  answers: QuizAttemptResultQuestionResponse[]
}

export interface WrongAnswerResponse {
  attemptId: number
  questionId: number
  quizTitle: string
  questionText: string
  questionType: string
  studentAnswer: string
  correctAnswer: string
  explanation?: string | null
  submittedAt: string
}

export interface TeacherDashboardResponse {
  fullName: string
  teacherStatus: TeacherStatus
  totalLessons: number
  totalStudents: number
  averageQuizScore: number
  earningsSummary: {
    grossAmount: number
    netAmount: number
    availableBalance: number
    pendingBalance: number
    currencyCode: string
  }
  recentQuizResults: Array<{
    attemptId: number
    quizTitle: string
    studentName: string
    score: number
    maxScore: number
    completedAt: string
  }>
  recentNotifications: Array<{
    notificationId: number
    title: string
    message: string
    status: string
    targetPath?: string | null
    createdAt: string
  }>
}

export interface AnalyticsTrendPoint {
  label: string
  value: number
}

export interface AnalyticsBreakdownItem {
  label: string
  value: number
}

export interface TeacherAnalyticsResponse {
  overview: {
    totalModules: number
    totalLessons: number
    totalPackages: number
    publishedContentItems: number
    submittedContentItems: number
    totalActiveStudents: number
    averageQuizScore: number
    netEarnings: number
    availableBalance: number
    currencyCode: string
    upcomingLiveClasses: number
    totalLiveEnrolments: number
  }
  quizScoreTrend: AnalyticsTrendPoint[]
  earningsTrend: AnalyticsTrendPoint[]
  contentStatusBreakdown: AnalyticsBreakdownItem[]
}

export interface TeacherVerificationDocumentSummaryResponse {
  id: number
  documentType: TeacherVerificationDocumentType
  description?: string | null
  originalFileName: string
  contentType: string
  fileSize: number
  submittedAt: string
  currentVersion: boolean
}

export interface TeacherVerificationReviewSummaryResponse {
  id: number
  decisionStatus: TeacherStatus
  note?: string | null
  reviewerName?: string | null
  reviewedAt: string
}

export interface TeacherVerificationStatusResponse {
  teacherStatus: TeacherStatus
  latestSubmittedDocuments: TeacherVerificationDocumentSummaryResponse[]
  latestReviewResult?: TeacherVerificationReviewSummaryResponse | null
  rejectionReason?: string | null
  resubmissionAllowed: boolean
}

export interface TeacherProfileResponse {
  userId: number
  fullName: string
  email: string
  teacherProfileId: number
  bio?: string | null
  shortIntroduction?: string | null
  teachingCategories: string[]
  teacherStatus: TeacherStatus
  rejectionReason?: string | null
  verificationDocuments: TeacherVerificationDocumentSummaryResponse[]
  createdAt: string
  updatedAt: string
}

export interface TeacherStudentSummaryResponse {
  studentId: number
  userId: number
  fullName: string
  email: string
  displayName?: string | null
  currentLevel?: string | null
  progressPercent: number
  quizAveragePercent?: number | null
  atRisk: boolean
  weakAreas: string[]
  latestFeedbackType?: string | null
  latestFeedbackAt?: string | null
  latestActivityAt?: string | null
  joinedAt: string
}

export interface LearningModuleResponse {
  id: number
  title: string
  description?: string | null
  level: LearningLevel
  category: LearningCategory
  accessType: AccessType
  price: number
  coverImageUrl?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  teacher: TeacherOwnerSummary
  createdAt: string
  updatedAt: string
}

export interface LessonResponse {
  id: number
  title: string
  description?: string | null
  basicContent?: string | null
  moduleId?: number | null
  moduleTitle?: string | null
  level: LearningLevel
  category: LearningCategory
  learningType: LearningType
  accessType: AccessType
  price: number
  estimatedDurationMinutes?: number | null
  coverImageUrl?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  teacher: TeacherOwnerSummary
  createdAt: string
  updatedAt: string
}

export interface CoursePackageResponse {
  id: number
  title: string
  description?: string | null
  level: LearningLevel
  category: LearningCategory
  learningType: LearningType
  accessType: AccessType
  price: number
  coverImageUrl?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  teacher: TeacherOwnerSummary
  packageItems: PackageItemResponse[]
  createdAt: string
  updatedAt: string
}

export interface TeacherQuizOptionResponse {
  id: number
  optionText: string
  correctOption?: boolean | null
  displayOrder: number
}

export interface TeacherQuizQuestionResponse {
  id: number
  questionType: QuizQuestionType
  questionText: string
  correctAnswerText?: string | null
  points: number
  displayOrder: number
  explanation?: string | null
  options: TeacherQuizOptionResponse[]
}

export interface TeacherQuizResponse {
  id: number
  lessonId: number
  lessonTitle: string
  title: string
  description?: string | null
  contentStatus: ContentStatus
  timeLimitMinutes?: number | null
  totalPoints: number
  questions: TeacherQuizQuestionResponse[]
  createdAt: string
  updatedAt: string
}

export interface TeacherQuizAttemptSummaryResponse {
  attemptId: number
  studentId: number
  studentName: string
  score: number
  maxScore: number
  submittedAt: string
}

export interface LiveClassEnrolmentResponse {
  id: number
  liveClassId: number
  studentId: number
  studentName: string
  studentEmail: string
  enrolmentStatus: string
  enrolledAt: string
  cancelledAt?: string | null
  attendedAt?: string | null
}

export interface LessonVideoProgressSummaryResponse {
  studentId: number
  studentName: string
  studentEmail: string
  recordedVideoId: number
  recordedVideoTitle: string
  progressPercentage: number
  watchedSeconds: number
  totalSeconds: number
  completed: boolean
  lastWatchedAt?: string | null
}

export interface TeacherFeedbackResponse {
  id: number
  teacherId: number
  teacherName: string
  studentId: number
  studentName: string
  lessonId?: number | null
  lessonTitle?: string | null
  quizAttemptId?: number | null
  quizTitle?: string | null
  feedbackType?: string | null
  message: string
  createdAt: string
  updatedAt: string
}

export interface StudentTeacherFeedbackResponse {
  id: number
  teacherName: string
  lessonId?: number | null
  lessonTitle?: string | null
  quizAttemptId?: number | null
  quizTitle?: string | null
  feedbackType?: string | null
  message: string
  createdAt: string
  updatedAt: string
}

export interface TeacherEarningSummaryResponse {
  grossAmount: number
  totalNetAmount: number
  availableBalance: number
  pendingBalance: number
  currencyCode: string
}

export interface TeacherEarningResponse {
  id: number
  orderItemId?: number | null
  itemTitle: string
  grossAmount: number
  commissionAmount: number
  netAmount: number
  currencyCode: string
  earningStatus: string
  createdAt: string
}

export interface TeacherPayoutRequestResponse {
  id: number
  amount: number
  currencyCode: string
  requestedNote?: string | null
  decisionReason?: string | null
  payoutStatus: string
  createdAt: string
}

export interface AdminPayoutRequestResponse extends TeacherPayoutRequestResponse {
  teacherId: number
  teacherName: string
  teacherEmail: string
}

export interface AdminDashboardResponse {
  fullName: string
  totalStudents: number
  totalTeachers: number
  pendingTeacherApprovals: number
  totalModules: number
  totalLessons: number
  totalQuizzes: number
  totalGames: number
  recentUserActivity: Array<{
    userId: number
    fullName: string
    email: string
    roles: UserRole[]
    createdAt: string
  }>
  aiUsageSummary: {
    totalRequests: number
    successfulRequests: number
  }
  paymentRevenueSummary: {
    totalTransactions: number
    paidTransactions: number
    totalRevenue: number
    currencyCode: string
  }
  pendingRefunds: number
  pendingPayouts: number
  recentAuditLogs: Array<{
    auditLogId: number
    actionType: string
    targetType: string
    description: string
    createdAt: string
  }>
}

export interface AdminAnalyticsResponse {
  overview: {
    totalStudents: number
    totalTeachers: number
    pendingTeacherApprovals: number
    approvedTeachers: number
    rejectedTeachers: number
    publishedContentItems: number
    totalRevenue: number
    currencyCode: string
    pendingRefunds: number
    pendingPayouts: number
    moderationActions: number
    auditLogEntries: number
    adminUnreadNotifications: number
    aiRequests: number
  }
  userRegistrationTrend: AnalyticsTrendPoint[]
  teacherApprovalBreakdown: AnalyticsBreakdownItem[]
  contentStatusBreakdown: AnalyticsBreakdownItem[]
  paymentStatusBreakdown: AnalyticsBreakdownItem[]
  aiStatusBreakdown: AnalyticsBreakdownItem[]
}

export interface AdminUserListItemResponse {
  userId: number
  fullName: string
  email: string
  roles: UserRole[]
  active: boolean
  teacherStatus?: TeacherStatus | null
  createdAt: string
  updatedAt: string
}

export interface AdminTeacherListItemResponse {
  teacherId: number
  userId: number
  fullName: string
  email: string
  teacherStatus: TeacherStatus
  bioSummary?: string | null
  verificationDocumentCount: number
  latestReviewDate?: string | null
  latestSubmissionTime?: string | null
  createdAt: string
}

export interface AdminAuditSummaryResponse {
  auditLogId: number
  actionType: string
  targetType: string
  description: string
  createdAt: string
}

export interface AdminTeacherDetailResponse {
  teacherId: number
  userId: number
  fullName: string
  email: string
  bio?: string | null
  shortIntroduction?: string | null
  teachingCategories: string[]
  teacherStatus: TeacherStatus
  rejectionReason?: string | null
  verifiedAt?: string | null
  verificationDocuments: TeacherVerificationDocumentSummaryResponse[]
  verificationReviewHistory: TeacherVerificationReviewSummaryResponse[]
  recentAuditLogs: AdminAuditSummaryResponse[]
  createdAt: string
  updatedAt: string
}

export interface AdminStudentListItemResponse {
  studentId: number
  userId: number
  fullName: string
  email: string
  preferredLanguage?: AppLanguage | null
  createdAt: string
}

export interface ContentSubmissionResponse {
  contentType: ManagedContentType
  id: number
  title: string
  description?: string | null
  teacher: TeacherOwnerSummary
  level: LearningLevel
  category: LearningCategory
  learningType?: LearningType | null
  accessType: AccessType
  price: number
  contentStatus: ContentStatus
  rejectionReason?: string | null
  createdAt: string
  updatedAt: string
}

export interface ContentSubmissionDetailResponse {
  contentType: ManagedContentType
  id: number
  title: string
  description?: string | null
  basicContent?: string | null
  moduleId?: number | null
  moduleTitle?: string | null
  teacher: TeacherOwnerSummary
  level: LearningLevel
  category: LearningCategory
  learningType?: LearningType | null
  accessType: AccessType
  price: number
  coverImageUrl?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  packageItems: PackageItemResponse[]
  createdAt: string
  updatedAt: string
}

export interface ModerationRecordResponse {
  id: number
  targetType: string
  targetId: number
  targetTitle: string
  actionTaken: string
  note?: string | null
  adminUserId: number
  adminName: string
  relatedTeacherId?: number | null
  relatedTeacherName?: string | null
  createdAt: string
}

export interface AuditLogResponse {
  id: number
  actorUserId?: number | null
  actorEmail?: string | null
  actionType: string
  targetType: string
  targetId?: number | null
  description: string
  ipAddress?: string | null
  requestId?: string | null
  createdAt: string
}

export interface AiAvailabilityResponse {
  available: boolean
  provider?: string | null
  model?: string | null
  status: string
  message?: string | null
  textGenerationAvailable: boolean
  audioAnalysisAvailable: boolean
  imageAnalysisAvailable: boolean
}

export interface AiChatResponse {
  sessionId?: number | null
  userMessage: string
  aiResponse: string
  language?: string | null
  createdAt: string
  status: string
}

export interface AiVocabularyHelperResponse {
  chineseWord: string
  pinyin?: string | null
  englishMeaning?: string | null
  bahasaMelayuMeaning?: string | null
  simpleExplanation?: string | null
  exampleSentence?: string | null
  exampleSentencePinyin?: string | null
  exampleSentenceTranslation?: string | null
  imagePromptOrDescription?: string | null
  miniPracticeQuestion?: string | null
  encouragement?: string | null
  status: string
}

export interface AiSentencePracticeResponse {
  originalSentence: string
  correctedSentence?: string | null
  pinyin?: string | null
  englishMeaning?: string | null
  bahasaMelayuMeaning?: string | null
  improvementExplanation?: string | null
  naturalSentenceVersion?: string | null
  practiceTask?: string | null
  encouragement?: string | null
  status: string
}

export interface AiStoryVocabularyItemResponse {
  chineseWord: string
  pinyin?: string | null
  englishMeaning?: string | null
  bahasaMelayuMeaning?: string | null
}

export interface AiStoryComprehensionQuestionResponse {
  question: string
  options: string[]
  answer: string
  explanation?: string | null
}

export interface AiStoryGeneratorResponse {
  title: string
  chineseStory: string
  pinyin?: string | null
  englishTranslation?: string | null
  bahasaMelayuTranslation?: string | null
  vocabularyList: AiStoryVocabularyItemResponse[]
  comprehensionQuestions: AiStoryComprehensionQuestionResponse[]
  encouragement?: string | null
  usageMode?: string | null
  status: string
}

export interface AiGeneratedQuizQuestionResponse {
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
  level?: string | null
  category?: string | null
  usageMode?: string | null
  questions: AiGeneratedQuizQuestionResponse[]
  status: string
}

export interface AiLearningRecommendationResponse {
  recommendedNextStep?: string | null
  recommendedLessonOrActivity?: string | null
  weakAreaSummary?: string | null
  revisionSuggestion?: string | null
  shortEncouragement?: string | null
  suggestedPracticeTypes: string[]
  status: string
}

export interface AiPronunciationFeedbackResponse {
  feedback?: string | null
  pronunciationComment?: string | null
  toneComment?: string | null
  improvementTips?: string | null
  encouragement?: string | null
  status: string
}

export interface AiWritingFeedbackResponse {
  feedback?: string | null
  clarityComment?: string | null
  improvementTips?: string | null
  encouragement?: string | null
  status: string
}

export interface StudentAiResponseReportResponse {
  id: number
  aiUsageLogId?: number | null
  chatSessionId?: number | null
  chatMessageId?: number | null
  featureKey: string
  reasonCategory: AiResponseReportReason
  description?: string | null
  promptSnapshot?: string | null
  aiResponseSnapshot?: string | null
  status: AiResponseReportStatus
  createdAt: string
  reviewedAt?: string | null
  updatedAt: string
}

export interface AdminAiResponseReportResponse extends StudentAiResponseReportResponse {
  studentUserId?: number | null
  studentName?: string | null
  studentEmail?: string | null
  reviewerUserId?: number | null
  reviewerName?: string | null
  reviewerNote?: string | null
}

export interface AdminAiUsageLogResponse {
  id: number
  userId?: number | null
  userName?: string | null
  userEmail?: string | null
  userRole?: string | null
  featureKey: string
  providerName?: string | null
  modelName?: string | null
  status: string
  tokenCount?: number | null
  languagePreference?: string | null
  inputSummary?: string | null
  outputSummary?: string | null
  errorSummary?: string | null
  relatedEntityType?: string | null
  relatedEntityId?: number | null
  createdAt: string
}

export interface CartItemResponse {
  cartItemId: number
  itemType: ManagedContentType
  itemId: number
  title: string
  unitPrice: number
  currencyCode: string
  quantity: number
  subtotal: number
  teacherOwned: boolean
}

export interface CartResponse {
  cartId: number
  items: CartItemResponse[]
  totalAmount: number
  currencyCode: string
  empty: boolean
}

export interface CheckoutResponse {
  orderId: number
  orderNumber: string
  paymentId: number
  paymentStatus: string
  totalAmount: number
  currencyCode: string
  checkoutUrl?: string | null
  providerName: string
  providerStatus?: string | null
  paymentAvailable: boolean
  message?: string | null
  items: CartItemResponse[]
}

export interface StudentPaymentRecordResponse {
  paymentId: number
  orderId: number
  orderNumber: string
  providerName: string
  amount: number
  currencyCode: string
  paymentStatus: string
  paymentMethod?: string | null
  providerStatus?: string | null
  paidAt?: string | null
  createdAt: string
  items: CartItemResponse[]
}

export interface AdminPaymentRecordResponse extends StudentPaymentRecordResponse {
  studentId: number
  studentName: string
  studentEmail: string
}

export interface StudentRefundRequestResponse {
  id: number
  orderId: number
  orderNumber: string
  amount: number
  reason: string
  decisionReason?: string | null
  refundStatus: string
  createdAt: string
}

export interface AdminRefundRequestResponse extends StudentRefundRequestResponse {
  studentId: number
  studentName: string
  studentEmail: string
}

export interface CommissionSettingResponse {
  commissionRate: number
  settingKey: string
  description: string
}

export interface AdminPlatformSettingsResponse {
  platformName: string
  aiDisclaimerText: string
  emailNotifications: boolean
  inAppNotifications: boolean
  dailySummary: boolean
  strictContentFiltering: boolean
  sensitiveWordFilter: boolean
  aiSafetyCheck: boolean
}

export interface AdminPlatformSettingsUpdateRequest {
  aiDisclaimerText: string
  emailNotifications: boolean
  inAppNotifications: boolean
  dailySummary: boolean
  strictContentFiltering: boolean
  sensitiveWordFilter: boolean
  aiSafetyCheck: boolean
}

export interface PublicPlatformSettingsResponse {
  platformName: string
  aiDisclaimerText: string
  strictContentFiltering: boolean
  sensitiveWordFilter: boolean
  aiSafetyCheck: boolean
}
