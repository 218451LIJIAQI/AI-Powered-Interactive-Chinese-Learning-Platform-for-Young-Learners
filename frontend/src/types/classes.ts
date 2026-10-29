/**
 * Teacher-made classes: what the class endpoints return (dto/classes on the backend). Dates are ISO strings; live
 * session times (`startsAt`, `endsAt`, …) carry their offset, so the browser shows them in the viewer's own time.
 */
import type { ContentStatus, LearningStage, LessonMastery } from './api'

export type LearningType = 'RECORDED' | 'LIVE' | 'HYBRID'
export type ClassActivityType = 'FLASHCARDS' | 'TRACING' | 'MATCHING'
/** State of a pending change to a published class or lesson. */
export type RevisionStatus = 'DRAFT' | 'SUBMITTED' | 'REJECTED'
export type SessionStatus = 'SCHEDULED' | 'RESCHEDULED' | 'FULL' | 'CANCELLED' | 'COMPLETED'
export type FeedbackType = 'ENCOURAGING' | 'LESSON' | 'WEAK_AREA' | 'CORRECTIVE' | 'PROGRESS'
export type FeedbackAbout = 'QUIZ_ATTEMPT' | 'LESSON' | 'GENERAL'
export type QuizSkill = 'WORD_MEANINGS' | 'PINYIN_TONES' | 'LISTENING' | 'SENTENCES'
export type MeetingPlatform = 'ZOOM' | 'GOOGLE_MEET' | 'MICROSOFT_TEAMS' | 'OTHER'

// ----- catalogue and the student's classes -----

export interface ClassTeacher {
  id: number
  name: string
  verified: boolean
  bio: string | null
}

export interface ClassCard {
  id: number
  title: string
  description: string | null
  levelName: string | null
  topicName: string | null
  learningType: LearningType | null
  teacher: ClassTeacher
  unitCount: number
  lessonCount: number
  upcomingLiveSessionCount: number
}

export interface CatalogueOption {
  id: number
  name: string
}

export interface CatalogueSummary {
  topics: CatalogueOption[]
  levels: CatalogueOption[]
}

export interface CatalogueQuery {
  keyword?: string
  topicId?: number
  levelId?: number
  learningType?: LearningType
  sort?: 'RECENT' | 'TITLE'
  page?: number
  size?: number
}

interface LessonPreview {
  id: number
  title: string
  activityCount: number
  quizCount: number
}

export interface UnitPreview {
  id: number
  title: string
  lessons: LessonPreview[]
}

export interface SessionBrief {
  id: number
  title: string
  classId: number
  classTitle: string
  lessonId: number | null
  lessonTitle: string | null
  teacherName: string
  startsAt: string
  endsAt: string
  platform: MeetingPlatform
  status: SessionStatus
  booked: boolean
  bookedCount: number
  capacity: number | null
}

export interface ClassPreview {
  summary: ClassCard
  teacherNote: string | null
  units: UnitPreview[]
  liveSessions: SessionBrief[]
}

export interface LessonProgressRow {
  id: number
  title: string
  number: number
  status: LessonMastery
  percent: number
  bestQuizPercent: number | null
  activityCount: number
  available: boolean
}

export interface UnitProgress {
  id: number
  title: string
  lessonCount: number
  completedCount: number
  lessons: LessonProgressRow[]
}

export interface NextStep {
  lessonId: number
  lessonTitle: string
  lessonNumber: number
  activitiesDone: number
  activityCount: number
  quizDone: boolean
}

export interface MyClassCard {
  summary: ClassCard
  joinedAt: string
  completedLessons: number
  progressPercent: number
  lessons: LessonProgressRow[]
  nextStep: NextStep | null
  nextLiveSession: SessionBrief | null
  archived: boolean
}

export interface MyClasses {
  classes: MyClassCard[]
  liveToday: SessionBrief | null
  moreClassCount: number
  moreTeachers: ClassTeacher[]
  upcomingLiveSessionCount: number
}

export interface StudentClass {
  joined: boolean
  archived: boolean
  summary: ClassCard
  teacherNote: string | null
  preview: UnitPreview[]
  progressPercent: number
  completedLessons: number
  lessonCount: number
  nextStep: NextStep | null
  units: UnitProgress[]
  liveSessions: SessionBrief[]
  quizAttemptCount: number
  feedbackCount: number
}

export interface AttemptRow {
  attemptId: number
  quizId: number
  quizTitle: string
  lessonTitle: string
  completedAt: string
  score: number
  maxScore: number
  percent: number
}

export interface QuizResults {
  averagePercent: number | null
  attempts: AttemptRow[]
}

export interface FeedbackItem {
  id: number
  teacherName: string
  type: FeedbackType
  about: FeedbackAbout
  lessonTitle: string | null
  message: string
  createdAt: string
  editedAt: string | null
}

// ----- the lesson, its activities and quizzes -----

export interface KeyPhrase {
  chinese: string
  pinyin: string
  meaning: string
}

export interface StudentVideo {
  title: string
  externalUrl: string | null
  streamPath: string | null
  durationSeconds: number | null
  watched: boolean
  percentWatched: number
}

export interface ActivityStep {
  type: ClassActivityType
  id: number
  title: string
  number: number
  stage: LearningStage | null
  done: boolean
}

export interface QuizCard {
  id: number
  title: string
  stage: LearningStage | null
  questionCount: number
  marks: number
  timeLimitMinutes: number | null
  hintsAvailable: boolean
  bestPercent: number | null
  attemptCount: number
  inProgress: boolean
}

export interface StudentLesson {
  classId: number
  classTitle: string
  teacherName: string
  unitTitle: string
  title: string
  number: number
  summary: string | null
  minutes: number | null
  available: boolean
  video: StudentVideo | null
  keyPhrases: KeyPhrase[]
  activities: ActivityStep[]
  quizzes: QuizCard[]
  stepsDone: number
  stepsTotal: number
  percent: number
  status: LessonMastery
  bestQuizPercent: number | null
  liveSessions: SessionBrief[]
}

export interface ActivityRef {
  type: ClassActivityType
  id: number
  title: string
}

export interface MatchWord {
  id: number
  chinese: string
  pinyin: string | null
}

export interface TraceCharacter {
  character: string
  pinyin: string
  meaning: string | null
}

export interface ActivityPlay {
  type: ClassActivityType
  id: number
  title: string
  stage: LearningStage | null
  number: number
  activityCount: number
  lessonTitle: string
  done: boolean
  previous: ActivityRef | null
  next: ActivityRef | null
  cards: KeyPhrase[]
  characters: TraceCharacter[]
  words: MatchWord[]
  meanings: string[]
}

export interface PairResult {
  wordId: number
  correct: boolean
}

export interface ActivityOutcome {
  done: boolean
  pairs: PairResult[]
  stepsDone: number
  stepsTotal: number
  lessonStatus: LessonMastery
}

export interface MatchHint {
  wordId: number
  meaning: string
}

export interface QuizIntro {
  id: number
  title: string
  stage: LearningStage | null
  questionCount: number
  marks: number
  timeLimitMinutes: number | null
  hintsAvailable: boolean
  classId: number
  classTitle: string
  lessonId: number
  lessonTitle: string
  bestPercent: number | null
  openAttemptId: number | null
}

interface QuizOption {
  id: number
  text: string
}

export interface QuizQuestion {
  id: number
  type: string
  text: string
  audioUrl: string | null
  imageUrl: string | null
  hasHint: boolean
  options: QuizOption[]
}

export interface SavedAnswer {
  questionId: number
  optionId: number | null
  answerText: string | null
}

export interface QuizSession {
  attemptId: number
  quiz: QuizIntro
  expiresAt: string | null
  secondsLeft: number | null
  questions: QuizQuestion[]
  answers: SavedAnswer[]
}

export interface ReviewedQuestion {
  questionId: number
  number: number
  text: string
  yourAnswer: string | null
  correctAnswer: string | null
  correct: boolean
  explanation: string | null
  hintUsed: boolean
}

export interface PracticeSuggestion {
  skill: QuizSkill
  activity: ActivityRef | null
}

export interface QuizResult {
  quiz: QuizIntro
  submittedAt: string
  score: number
  maxScore: number
  percent: number
  bestPercent: number | null
  hintsUsed: number
  correctCount: number
  wrongCount: number
  questions: ReviewedQuestion[]
  lessonStatus: LessonMastery
  activitiesLeft: number
  suggestion: PracticeSuggestion | null
  feedback: FeedbackItem[]
}

// ----- live sessions -----

export interface StudentSession {
  id: number
  title: string
  classId: number
  classTitle: string
  lessonId: number | null
  lessonTitle: string | null
  teacherName: string
  startsAt: string
  endsAt: string
  joinOpensAt: string
  platform: MeetingPlatform
  status: SessionStatus
  rescheduledFrom: string | null
  cancelledAt: string | null
  capacity: number | null
  bookedCount: number
  seatsLeft: number | null
  booked: boolean
  bookingCancelled: boolean
  attended: boolean
  canBook: boolean
  canJoin: boolean
  recordingUrl: string | null
}

export interface StudentSessions {
  upcoming: StudentSession[]
  past: StudentSession[]
  bookedCount: number
}

export interface TeacherSession {
  id: number
  title: string
  classId: number
  classTitle: string
  lessonId: number | null
  lessonTitle: string | null
  startsAt: string
  endsAt: string
  /** Wall-clock start and end in the session's own time zone (for the edit form). */
  localStart: string
  localEnd: string
  timeZone: string
  platform: MeetingPlatform
  meetingLink: string
  status: SessionStatus
  rescheduledFrom: string | null
  cancelledAt: string | null
  capacity: number | null
  bookedCount: number
  attendedCount: number
  recordingUrl: string | null
}

export interface BookedStudent {
  studentId: number
  name: string
  xpLevel: number
  classProgressPercent: number | null
  /** BOOKED until the session ends, then ATTENDED or DID_NOT_JOIN. */
  attendance: 'BOOKED' | 'ATTENDED' | 'DID_NOT_JOIN'
}

export interface TeacherSessionDetail {
  session: TeacherSession
  joinOpensAt: string
  students: BookedStudent[]
}

export interface TeacherSessions {
  upcoming: TeacherSession[]
  past: TeacherSession[]
}

export interface ScheduleOptions {
  classes: { id: number; title: string; lessons: { id: number; title: string }[] }[]
}

export interface LiveSessionPayload {
  title: string
  classId: number
  lessonId?: number | null
  startTime: string
  endTime: string
  timeZone?: string
  capacity?: number | null
  meetingLink: string
  meetingPlatform: MeetingPlatform
}

// ----- teacher: classes, units, lesson editor -----

export interface ClassListItem {
  id: number
  title: string
  status: ContentStatus
  revisionStatus: RevisionStatus | null
  levelName: string | null
  topicName: string | null
  learningType: LearningType | null
  unitCount: number
  lessonCount: number
  publishedLessons: number
  lessonsInReview: number
  draftLessons: number
  lessonsWithChangesRequested: number
  revisionsInReview: number
  revisionsWithChangesRequested: number
  studentCount: number
  updatedAt: string
}

export interface TeacherLessonRow {
  id: number
  title: string
  status: ContentStatus
  revisionStatus: RevisionStatus | null
  reviewNote: string | null
  reviewNoteBy: string | null
  activityCount: number
  questionCount: number
  updatedAt: string
}

export interface TeacherUnit {
  id: number
  title: string
  archived: boolean
  lessons: TeacherLessonRow[]
}

export interface DetailsRevision {
  status: RevisionStatus
  title: string
  description: string | null
  levelId: number | null
  topicId: number | null
  learningType: LearningType | null
  rejectionReason: string | null
}

export interface TeacherClassPage {
  id: number
  title: string
  description: string | null
  teacherNote: string | null
  levelId: number | null
  levelName: string | null
  topicId: number | null
  topicName: string | null
  learningType: LearningType | null
  status: ContentStatus
  rejectionReason: string | null
  revision: DetailsRevision | null
  units: TeacherUnit[]
  studentCount: number
  canDelete: boolean
}

export interface ClassDetailsPayload {
  title: string
  description?: string | null
  levelId?: number | null
  topicId?: number | null
  learningType?: LearningType | null
}

/** One change of a revision; the frontend words it from `kind`. */
export interface ContentChange {
  kind: string
  title: string | null
  count: number | null
  total: number | null
}

export interface ContentEvent {
  id: number
  type: string
  subjectTitle: string | null
  actorName: string | null
  actorRole: 'TEACHER' | 'ADMIN' | string | null
  note: string | null
  changes: ContentChange[]
  createdAt: string
}

export interface EditorVideo {
  title: string
  durationSeconds: number | null
  watchedCount: number
  studentCount: number
}

export interface MatchPair {
  chinese: string
  pinyin: string
  meaning: string
}

export interface EditorActivity {
  type: ClassActivityType
  id: number
  title: string
  stage: LearningStage | null
  number: number
  characters: TraceCharacter[]
  pairs: MatchPair[]
}

export interface EditorQuestionOption {
  id: number
  text: string
  correct: boolean
}

export interface EditorQuestion {
  id: number
  number: number
  type: string
  text: string
  points: number
  correctAnswerText: string | null
  explanation: string | null
  hint: string | null
  audioUrl: string | null
  imageUrl: string | null
  options: EditorQuestionOption[]
}

export interface EditorQuiz {
  id: number
  title: string
  description: string | null
  stage: LearningStage | null
  timeLimitMinutes: number | null
  totalPoints: number
  questionsLocked: boolean
  attemptCount: number
  averagePercent: number | null
  recentAttempts: { studentName: string; completedAt: string; percent: number }[]
  questions: EditorQuestion[]
}

export interface EditorChecklist {
  activityCount: number
  quizCount: number
  questionCount: number
  quizzesReady: boolean
  pinyinComplete: boolean
}

export interface LessonEditor {
  lessonId: number
  classId: number
  classTitle: string
  classStatus: ContentStatus
  unitId: number
  unitTitle: string
  units: { id: number; title: string }[]
  status: ContentStatus
  revisionStatus: RevisionStatus | null
  editable: boolean
  reviewNote: string | null
  reviewNoteBy: string | null
  reviewNoteAt: string | null
  title: string
  minutes: number | null
  summary: string | null
  keyPhrases: KeyPhrase[]
  video: EditorVideo | null
  activities: EditorActivity[]
  quizzes: EditorQuiz[]
  checklist: EditorChecklist
}

export interface LessonContentPayload {
  title: string
  unitId?: number | null
  minutes?: number | null
  summary?: string | null
  keyPhrases: KeyPhrase[]
}

export interface ActivityPayload {
  type: ClassActivityType
  title: string
  stage?: LearningStage | null
  characters?: TraceCharacter[]
  pairs?: MatchPair[]
}

export interface QuizPayload {
  title: string
  description?: string | null
  timeLimitMinutes?: number | null
  learningStage?: LearningStage | null
}

export interface QuestionPayload {
  questionType: string
  questionText: string
  points: number
  correctAnswerText?: string | null
  explanation?: string | null
  hint?: string | null
  audioUrl?: string | null
  imageUrl?: string | null
  options?: { optionText: string; correctOption: boolean }[]
}

// ----- teacher: students, feedback, dashboard -----

export interface RosterRow {
  studentId: number
  name: string
  xpLevel: number
  progressPercent: number
  quizAverage: number | null
  needsSupport: boolean
  lastActiveAt: string | null
}

export interface Roster {
  students: RosterRow[]
  total: number
  needsSupportCount: number
}

export interface StudentLessonProgress {
  lessonId: number
  title: string
  status: LessonMastery
  percent: number
  videoWatched: boolean | null
  bestQuizPercent: number | null
}

export interface WeakArea {
  skill: QuizSkill
  quizId: number
  quizTitle: string
  correct: number
  total: number
  percent: number
  lastSeenAt: string | null
  examples: string[]
}

export interface FeedbackTarget {
  kind: 'QUIZ_ATTEMPT' | 'LESSON'
  id: number
  label: string
  percent: number | null
  at: string | null
}

export interface FeedbackRow {
  id: number
  studentName: string
  classTitle: string | null
  type: FeedbackType
  about: FeedbackAbout
  lessonTitle: string | null
  attemptPercent: number | null
  message: string
  createdAt: string
  editedAt: string | null
}

export interface TeacherStudentDetail {
  studentId: number
  name: string
  xpLevel: number
  classes: { id: number; title: string }[]
  classId: number
  classTitle: string
  lastActiveAt: string | null
  needsSupport: boolean
  progressPercent: number
  lessonsCompleted: number
  lessonCount: number
  mastered: number
  quizAverage: number | null
  lessons: StudentLessonProgress[]
  quizResults: AttemptRow[]
  weakAreas: WeakArea[]
  feedback: FeedbackRow[]
  feedbackTargets: FeedbackTarget[]
}

export interface FeedbackPayload {
  studentId: number
  classId: number
  lessonId?: number | null
  quizAttemptId?: number | null
  type: FeedbackType
  message: string
}

export interface TodoItem {
  kind: string
  needsYou: boolean
  classId: number | null
  lessonId: number | null
  sessionId: number | null
  title: string
  note: string | null
  noteBy: string | null
  at: string | null
  detail: string | null
}

export interface TeachingDashboard {
  todo: TodoItem[]
  studentCount: number
  needsSupportCount: number
  publishedClasses: number
  publishedLessons: number
  inReviewCount: number
  upcomingSessionCount: number
  upcomingBookings: number
  quizAverage: number | null
  studentsNeedingSupport: RosterRow[]
  upcomingSessions: TeacherSession[]
}

// ----- admin -----

export type ReviewKind = 'NEW_CLASS' | 'CLASS_DETAILS' | 'NEW_LESSON' | 'LESSON_REVISION'

export interface ReviewQueueItem {
  kind: ReviewKind
  type: 'CLASS' | 'LESSON'
  classId: number
  lessonId: number | null
  title: string
  classTitle: string
  teacherName: string
  submittedAt: string | null
  lessonTitles: string[]
  changesQuiz: boolean
}

export interface ReviewQueue {
  items: ReviewQueueItem[]
  total: number
}

export interface ReviewLessonPreview {
  lessonId: number
  title: string
  classTitle: string
  unitTitle: string
  summary: string | null
  keyPhrases: KeyPhrase[]
  videoTitle: string | null
  videoDurationSeconds: number | null
  activities: { type: ClassActivityType; title: string; stage: LearningStage | null; isNew: boolean }[]
  quizzes: {
    title: string
    stage: LearningStage | null
    questionCount: number
    everyQuestionExplained: boolean
    isNew: boolean
  }[]
}

export interface ReviewDetail {
  item: ReviewQueueItem
  classPreview: {
    title: string
    description: string | null
    levelName: string | null
    topicName: string | null
    learningType: LearningType | null
    teacherName: string
    unitCount: number
    lessonCount: number
    lessons: { id: number; title: string; unitTitle: string }[]
  } | null
  lessons: ReviewLessonPreview[]
  changes: ContentChange[]
  notInReview: string[]
  history: ContentEvent[]
}

export interface TeacherContentRow {
  type: 'CLASS' | 'LESSON'
  id: number
  title: string
  classTitle: string | null
  teacherName: string
  status: ContentStatus
  revisionStatus: RevisionStatus | null
  updatedAt: string
}

export interface TeacherContentDetail {
  row: TeacherContentRow
  reviewNote: string | null
  reviewNoteBy: string | null
  reviewNoteAt: string | null
  unitTitle: string | null
  unitCount: number
  lessonCount: number
  activityCount: number
  questionCount: number
  studentCount: number
  history: ContentEvent[]
  canArchive: boolean
  canRestore: boolean
}

export interface TeacherContentQuery {
  keyword?: string
  type?: 'CLASS' | 'LESSON'
  status?: string
  teacherId?: number
  page?: number
  size?: number
}
