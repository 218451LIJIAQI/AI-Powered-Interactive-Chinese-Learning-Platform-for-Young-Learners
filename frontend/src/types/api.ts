/* Frontend mirror of backend REST API request and response contracts. */
/** BCP-47 interface languages (D18). Old accounts may still return a legacy Chinese code; see utils/locale.ts. */
export type AppLanguage = 'en' | 'ms' | 'zh-CN'
export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'
export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type LearningType = 'LIVE' | 'RECORDED' | 'HYBRID'
export type ActivityDifficulty = 'EASY' | 'MEDIUM' | 'HARD'
/**
 * Review workflow states (D4, D14, D24): DRAFT -> SUBMITTED -> PUBLISHED | REJECTED, PUBLISHED -> ARCHIVED -> PUBLISHED.
 * `APPROVED` only exists for database compatibility; the backend never assigns it and the UI treats it as ARCHIVED.
 */
export type ContentStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'ARCHIVED'
/** Optional scaffolding stage of a quiz or practice activity (D23). */
export type LearningStage = 'GUIDED' | 'SCAFFOLDED' | 'INDEPENDENT'
/** Content types handled by the admin review queue (`/admin/content-review/{type}/{id}`). PACKAGE is the "Course". */
export type ReviewContentType =
  | 'PACKAGE'
  | 'MODULE'
  | 'LESSON'
  | 'QUIZ'
  | 'FLASHCARD'
  | 'MATCHING_GAME'
  | 'LISTENING_EXERCISE'
  | 'TRACING_EXERCISE'
  | 'DRAG_DROP_ACTIVITY'
  | 'STORY_ACTIVITY'
export type LiveClassStatus = 'SCHEDULED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED'
export type AiResponseReportStatus = 'PENDING_REVIEW' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED'
export type AiResponseReportReviewStatus = Exclude<AiResponseReportStatus, 'PENDING_REVIEW'>
export type AiResponseReportReason =
  | 'INCORRECT_CONTENT'
  | 'HALLUCINATION'
  | 'UNSAFE_CONTENT'
  | 'INAPPROPRIATE_CONTENT'
  | 'OTHER'
export type AiFeatureKey =
  | 'CHAT_TUTOR'
  | 'VOCABULARY_HELPER'
  | 'SENTENCE_PRACTICE'
  | 'QUIZ_GENERATOR'
  | 'STORY_GENERATOR'
  | 'PRONUNCIATION_FEEDBACK'
  | 'WRITING_FEEDBACK'
type AiFeedbackStatus = 'SUCCESS' | 'FAILED' | 'UNAVAILABLE'
/** Lesson mastery indicator (D25): MASTERED = lesson completed and best quiz percentage >= 80 %. */
export type LessonMastery = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'MASTERED'
export type NotificationStatus = 'UNREAD' | 'READ'
export type TeacherFeedbackType =
  | 'GENERAL'
  | 'QUIZ'
  | 'LESSON'
  | 'ENCOURAGING'
  | 'CORRECTIVE'
  | 'PROGRESS'
  | 'WEAK_AREA'
/** Why a learner appears in the teacher's "Needs support" list (quiz average < 60 % / inactive for 14 days). */
export type StudentSupportReason = 'LOW_QUIZ_SCORE' | 'INACTIVE'
/** Practice submissions (D7 / D7a). */
export type PracticeSubmissionType = 'PRONUNCIATION_AUDIO' | 'HANDWRITING_IMAGE'
type PracticeAiFeedbackStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'FAILED'
export type PracticeReviewStatus = 'SUBMITTED' | 'REVIEWED'
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

interface PublicTeacherSummary {
  teacherId: number
  displayName: string
  verified: boolean
}

interface TeacherOwnerSummary {
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

interface AuthUserSummary {
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
  /** en | ms | zh-CN; legacy accounts may return the old Chinese code (normalise with normalizeAppLanguage). */
  preferredLanguage?: string | null
  roles: UserRole[]
  teacherStatus?: TeacherStatus | null
}

export interface CurrentUserResponse {
  id: number
  fullName: string
  email: string
  /** en | ms | zh-CN; legacy accounts may return the old Chinese code (normalise with normalizeAppLanguage). */
  preferredLanguage?: string | null
  roles: UserRole[]
  studentProfile?: {
    id: number
    displayName: string
    currentLevel?: string | null
    totalPoints: number
    /** Optional institution (stored in the legacy school_name column). */
    institution?: string | null
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
  coverImageUrl?: string | null
  status: ContentStatus
}

interface PublicCourseItemSummary {
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
  coverImageUrl?: string | null
  status: ContentStatus
  basicContent?: string | null
  packageItems: PublicCourseItemSummary[]
}

export interface NotificationResponse {
  id: number
  title: string
  message: string
  type: string
  status: NotificationStatus
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
  mastery: LessonMastery
  bestQuizPercentage?: number | null
}

export interface StudentProgressSummaryResponse {
  totalLessonsStarted: number
  completedLessons: number
  quizAverage: number
  overallCompletionPercent: number
  /** Lesson titles of recent wrong answers. */
  weakAreas: string[]
  lastActivitySummary?: string | null
  recentLessons: StudentProgressLessonResponse[]
  /** Every lesson with progress, newest first. */
  lessons: StudentProgressLessonResponse[]
}

/** `POST /students/lessons/{lessonId}/viewed`. */
export interface StudentLessonProgressResponse {
  lessonId: number
  completionPercent: number
  completed: boolean
  mastery: LessonMastery
  bestQuizPercentage?: number | null
  contentViewed: boolean
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

interface LiveClassTeacherSummaryResponse {
  teacherId: number
  userId: number
  displayName: string
}

interface LiveClassLessonSummaryResponse {
  lessonId: number
  lessonTitle: string
  learningType: string
}

interface LiveClassPackageSummaryResponse {
  packageId: number
  packageTitle: string
  learningType: string
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
  /** Seats taken (ENROLLED + ATTENDED for students). */
  enrolledCount: number
  /** Seats still open under maxParticipants; null/absent when the class has no limit (FR-LIVE-05). */
  remainingSeats?: number | null
  /** True when a limit is set and every seat is taken. */
  full?: boolean
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
  learningType: string
  liveClasses: LiveClassResponse[]
  recordedVideos: RecordedLessonVideoResponse[]
  contentViewed: boolean
  mastery: LessonMastery
  bestQuizPercentage?: number | null
  /** Published quizzes of the lesson in stage order. */
  quizzes: StudentQuizSummaryResponse[]
  /** Optional lesson cover image (FR-CONT-05); render only http(s) URLs. */
  coverImageUrl?: string | null
  /** Where the lesson sits (FR-LRN-02): its module and the first published course listing it. */
  moduleId?: number | null
  moduleTitle?: string | null
  courseId?: number | null
  courseTitle?: string | null
}

export interface VideoProgressResponse {
  /** null before the first save (the server returns a zero record). */
  id: number | null
  lessonId: number
  recordedVideoId: number
  watchedSeconds: number
  totalSeconds?: number | null
  progressPercentage: number
  completed: boolean
  lastWatchedAt?: string | null
  /** null on the zero record returned before the first save. */
  updatedAt?: string | null
}

export interface StudentActivitySummaryResponse {
  id: number
  type: string
  title: string
  hskLevel?: number | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  updatedAt: string
  completed: boolean
}

/** One graded item of a completion attempt; `key` identifies the item (left item id, question index, ...). */
export interface StudentActivityItemResult {
  key: string
  correct: boolean
  /** Story explanations, returned only when the attempt passed. */
  explanation?: string | null
}

export interface StudentActivityCompletionResponse {
  activityId: number
  type: string
  passed: boolean
  correctCount: number
  totalCount: number
  earnedPoints: number
  totalPoints: number
  firstCompletion: boolean
  results: StudentActivityItemResult[]
}

/* Completion request bodies (`POST /students/activities/{slug}/{id}/complete`). Flashcards send no body. */
export interface MatchingCompletionRequest {
  matches: Array<{ leftItemId: number; rightText: string }>
}

export interface DragDropCompletionRequest {
  placements: Array<{ itemId: number; zone: string }>
}

export type ListeningCompletionRequest =
  | { answers: Array<{ questionIndex: number; answerText: string }> }
  | { responseText: string }

export interface TracingCompletionRequest {
  strokeCount: number
}

export interface StoryCompletionRequest {
  answers: Array<{ questionId: number; optionIndex: number }>
}

export interface StudentFlashcardResponse {
  id: number
  /** Optional on flashcards (nullable column). */
  title?: string | null
  frontText: string
  backText: string
  pinyin?: string | null
  englishMeaning?: string | null
  malayMeaning?: string | null
  imageUrl?: string | null
  audioUrl?: string | null
  sortOrder?: number | null
  hskLevel?: number | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  completed: boolean
}

export interface StudentMatchingLeftItemResponse {
  id: number
  text: string
  pinyin?: string | null
  imageUrl?: string | null
  sortOrder?: number | null
}

/** Right-hand options are shuffled on every request and carry no ids or pair links. */
export interface StudentMatchingRightOptionResponse {
  text: string
  imageUrl?: string | null
}

export interface StudentMatchingGameResponse {
  id: number
  title: string
  hskLevel?: number | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  completed: boolean
  leftItems: StudentMatchingLeftItemResponse[]
  rightOptions: StudentMatchingRightOptionResponse[]
}

export interface StudentListeningQuestionResponse {
  index: number
  questionText: string
  /** Empty = the learner types the answer. */
  options: string[]
}

export interface StudentListeningExerciseResponse {
  id: number
  title: string
  audioUrl: string | null
  prompt: string | null
  questions: StudentListeningQuestionResponse[]
  /** True when the item has no answer key (legacy free-text prompt). */
  freeResponse: boolean
  hskLevel?: number | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  completed: boolean
}

export interface StudentTracingExerciseResponse {
  id: number
  title: string
  worksheetUrl: string | null
  targetCharacter: string | null
  pinyin?: string | null
  meaning?: string | null
  instructions?: string | null
  hskLevel?: number | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  completed: boolean
}

/** Shuffled, without the target zone (the answer key is never sent before an attempt). */
export interface StudentDragDropItemResponse {
  id: number
  dragText: string
  pinyin?: string | null
  imageUrl?: string | null
  sortOrder?: number | null
}

export interface StudentDragDropActivityResponse {
  id: number
  title: string
  instructions?: string | null
  hskLevel?: number | null
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  completed: boolean
  items: StudentDragDropItemResponse[]
  /** Distinct zones in alphabetical order. */
  zones: string[]
}

/** Story questions carry no explanation or correct flag before answering. */
export interface StudentStoryQuestionResponse {
  id: number
  questionText: string
  options: Array<{ index: number; text: string }>
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
  difficulty?: string | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  completed: boolean
  questions: StudentStoryQuestionResponse[]
}

export interface AdminActivitySummaryResponse {
  id: number
  type: AdminActivityType
  title: string
  status: ContentStatus
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

interface AdminMatchingGameItemResponse {
  id: number
  leftText: string
  rightText: string
  leftImageUrl?: string | null
  rightImageUrl?: string | null
  pinyin?: string | null
  sortOrder?: number | null
}

interface AdminDragDropItemResponse {
  id: number
  dragText: string
  targetZone: string
  pinyin?: string | null
  imageUrl?: string | null
  sortOrder?: number | null
}

interface AdminStoryQuestionResponse {
  id: number
  questionText: string
  optionsPayload: string
  explanation?: string | null
  sortOrder?: number | null
  /** 0-based index of the correct option (null when not set yet). */
  correctOptionIndex?: number | null
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
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminMatchingGameResponse {
  id: number
  title: string
  status: ContentStatus
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
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
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
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
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
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
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
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
  rejectionReason?: string | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
  learningStage?: LearningStage | null
  createdByUserId?: number | null
  createdByDisplayName?: string | null
  createdAt: string
  updatedAt: string
  questions: AdminStoryQuestionResponse[]
}

interface QuizOptionResponse {
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
  /** A teacher hint exists (always false on INDEPENDENT quizzes); the text is fetched on request. */
  hintAvailable: boolean
}

/** `GET /students/quizzes/{quizId}/questions/{questionId}/hint`. */
export interface QuizHintResponse {
  questionId: number
  hint: string
}

/** One answer of `POST /students/quizzes/{quizId}/attempts`: optionId for option types, answerText for text types. */
export interface QuizAnswerSubmission {
  questionId: number
  optionId?: number
  answerText?: string
  hintUsed?: boolean
}

export interface StudentQuizResponse {
  id: number
  lessonId: number
  lessonTitle: string
  title: string
  description?: string | null
  timeLimitMinutes?: number | null
  totalPoints: number
  learningStage?: LearningStage | null
  questions: StudentQuizQuestionResponse[]
  /** Module of the quiz's lesson (FR-LRN-02 breadcrumb). */
  moduleId?: number | null
  moduleTitle?: string | null
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
  learningStage?: LearningStage | null
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
  selectedOptionId?: number | null
  correctOptionId?: number | null
  hintUsed: boolean
  options: QuizOptionResponse[]
}

export interface QuizAttemptResultResponse {
  attemptId: number
  quizId: number
  quizTitle: string
  score: number
  maxScore: number
  /** 0..100 */
  percentage: number
  learningStage?: LearningStage | null
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
  quizId?: number | null
  lessonId?: number | null
  lessonTitle?: string | null
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
    upcomingLiveClasses: number
    totalLiveEnrolments: number
  }
  quizScoreTrend: AnalyticsTrendPoint[]
  contentStatusBreakdown: AnalyticsBreakdownItem[]
}

interface TeacherVerificationDocumentSummaryResponse {
  id: number
  documentType: TeacherVerificationDocumentType
  description?: string | null
  originalFileName: string
  contentType: string
  fileSize: number
  submittedAt: string
  currentVersion: boolean
}

interface TeacherVerificationReviewSummaryResponse {
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

export interface TeacherStudentLessonMasteryResponse {
  lessonId: number
  lessonTitle: string
  completionPercent: number
  mastery: LessonMastery
  bestQuizPercentage?: number | null
}

export interface TeacherStudentSummaryResponse {
  studentId: number
  userId: number
  fullName: string
  displayName?: string | null
  currentLevel?: string | null
  progressPercent: number
  quizAveragePercent?: number | null
  atRisk: boolean
  /** Same value as atRisk; driven only by supportReasons (feedback never flags a learner). */
  needsSupport: boolean
  supportReasons: StudentSupportReason[]
  /** Lesson titles of wrong answers on this teacher's quizzes. */
  weakAreas: string[]
  /** The teacher's lessons only. */
  lessonMastery: TeacherStudentLessonMasteryResponse[]
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
  coverImageUrl?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  teacher: TeacherOwnerSummary
  packageItems: PackageItemResponse[]
  createdAt: string
  updatedAt: string
}

interface TeacherQuizOptionResponse {
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
  /** Optional teacher hint (D8); never shown to students on INDEPENDENT quizzes. */
  hint?: string | null
  options: TeacherQuizOptionResponse[]
}

export interface TeacherQuizResponse {
  id: number
  lessonId: number
  lessonTitle: string
  title: string
  description?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  learningStage?: LearningStage | null
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
  percentage: number
  hintsUsed: number
  submittedAt: string
}

export interface LiveClassEnrolmentResponse {
  id: number
  liveClassId: number
  studentId: number
  studentName: string
  enrolmentStatus: string
  enrolledAt: string
  cancelledAt?: string | null
  attendedAt?: string | null
}

export interface LessonVideoProgressSummaryResponse {
  studentId: number
  studentName: string
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
  practiceSubmissionId?: number | null
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
  practiceSubmissionId?: number | null
  feedbackType?: string | null
  message: string
  createdAt: string
  updatedAt: string
}

interface PracticeSubmissionFeedbackResponse {
  id: number
  teacherName: string
  feedbackType: string
  message: string
  createdAt: string
}

/** Stored pronunciation / handwriting upload (D7a). AI feedback is "AI-assisted, not a score". */
export interface PracticeSubmissionResponse {
  id: number
  submissionType: PracticeSubmissionType
  studentId: number
  studentName: string
  lessonId: number | null
  lessonTitle: string | null
  targetText: string | null
  aiFeedbackStatus: PracticeAiFeedbackStatus
  aiFeedbackText: string | null
  aiNotice: string
  reviewStatus: PracticeReviewStatus
  submittedAt: string
  reviewedAt: string | null
  fileName: string
  contentType: string
  fileSize: number
  /** Role-specific authorised stream path; fetch it with the bearer token (never put tokens in URLs). */
  fileUrl: string
  teacherFeedback: PracticeSubmissionFeedbackResponse[]
}

export interface AdminDashboardResponse {
  fullName: string
  totalStudents: number
  totalTeachers: number
  pendingTeacherApprovals: number
  aiUsageSummary: {
    totalRequests: number
    successfulRequests: number
  }
}

export interface AdminAnalyticsResponse {
  overview: {
    totalStudents: number
    totalTeachers: number
    pendingTeacherApprovals: number
    approvedTeachers: number
    rejectedTeachers: number
    publishedContentItems: number
    moderationActions: number
    auditLogEntries: number
    adminUnreadNotifications: number
    aiRequests: number
  }
  userRegistrationTrend: AnalyticsTrendPoint[]
  teacherApprovalBreakdown: AnalyticsBreakdownItem[]
  contentStatusBreakdown: AnalyticsBreakdownItem[]
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

interface AdminAuditSummaryResponse {
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

export interface ContentSubmissionResponse {
  contentType: ManagedContentType
  id: number
  title: string
  description?: string | null
  teacher: TeacherOwnerSummary
  level: LearningLevel
  category: LearningCategory
  learningType?: LearningType | null
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
  coverImageUrl?: string | null
  contentStatus: ContentStatus
  rejectionReason?: string | null
  packageItems: PackageItemResponse[]
  createdAt: string
  updatedAt: string
}

/** One row of the admin content review queue (`GET /admin/content-review`). */
export interface ContentReviewItemResponse {
  type: ReviewContentType
  id: number
  title: string
  contentStatus: ContentStatus
  rejectionReason?: string | null
  learningStage?: LearningStage | null
  ownerUserId?: number | null
  ownerName?: string | null
  ownerTeacherStatus?: TeacherStatus | null
  /** Lesson title for a QUIZ, module title for a LESSON. */
  parentTitle?: string | null
  createdAt: string
  updatedAt: string
}

/** Type-specific reviewer detail: course/module/lesson body, quiz with answers, or the activity record. */
type ContentReviewDetail =
  | ContentSubmissionDetailResponse
  | TeacherQuizResponse
  | AdminFlashcardResponse
  | AdminMatchingGameResponse
  | AdminListeningExerciseResponse
  | AdminTracingExerciseResponse
  | AdminDragDropActivityResponse
  | AdminStoryActivityResponse

export interface ContentReviewDetailResponse {
  item: ContentReviewItemResponse
  detail: ContentReviewDetail
}

export interface ModerationRecordResponse {
  id: number
  targetType: string
  targetId: number
  /** null on records created before the title snapshot was stored. */
  targetTitle?: string | null
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
  /** null when the AI is unavailable or its reply could not be parsed. */
  chineseWord?: string | null
  pinyin?: string | null
  englishMeaning?: string | null
  bahasaMelayuMeaning?: string | null
  simpleExplanation?: string | null
  exampleSentence?: string | null
  exampleSentenceTranslation?: string | null
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
  encouragement?: string | null
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
  level?: string | null
  category?: string | null
  usageMode?: string | null
  questions: AiGeneratedQuizQuestionResponse[]
  status: string
}

export interface AiPronunciationFeedbackResponse {
  feedback?: string | null
  pronunciationComment?: string | null
  toneComment?: string | null
  improvementTips?: string | null
  encouragement?: string | null
  status: AiFeedbackStatus | string
  notice?: string | null
  /** Stored practice submission created by the upload (D7a). */
  submissionId?: number | null
}

export interface AiWritingFeedbackResponse {
  feedback?: string | null
  clarityComment?: string | null
  improvementTips?: string | null
  encouragement?: string | null
  status: AiFeedbackStatus | string
  notice?: string | null
  submissionId?: number | null
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

export interface AdminPlatformSettingsResponse {
  platformName: string
  aiDisclaimerText: string
  sensitiveWordFilter: boolean
  aiSafetyCheck: boolean
}

export interface AdminPlatformSettingsUpdateRequest {
  aiDisclaimerText: string
  sensitiveWordFilter: boolean
  aiSafetyCheck: boolean
}

export interface PublicPlatformSettingsResponse {
  platformName: string
  aiDisclaimerText: string
}
