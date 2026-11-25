/**
 * Classes made by the MandarinSpark team: what the public and learner class endpoints return (dto/classes on the
 * backend). Dates are ISO strings. The admin authoring shapes are in types/adminClasses.ts.
 */
import type { LessonProgressStatus } from './api'

/** Every class lesson has exactly these three activities, in this order. */
export const CLASS_ACTIVITY_TYPES = ['FLASHCARDS', 'TRACING', 'MATCHING'] as const
export type ClassActivityType = (typeof CLASS_ACTIVITY_TYPES)[number]

// The catalogue and the learner's classes

export interface ClassCard {
  id: number
  title: string
  description: string | null
  topicName: string | null
  /** "Best after Unit N": a soft label, never a lock. */
  bestAfterUnit: number | null
  lessonCount: number
}

export interface CatalogueQuery {
  page: number
  size: number
}

export interface CatalogueMembership {
  joinedCount: number
  plannedClassIds: number[]
}

interface LessonPreview {
  id: number
  title: string
}

interface UnitPreview {
  id: number
  title: string
  lessons: LessonPreview[]
}

export interface ClassPreview {
  summary: ClassCard
  units: UnitPreview[]
}

export interface LessonProgressRow {
  id: number
  title: string
  status: LessonProgressStatus
  available: boolean
}

interface UnitProgress {
  id: number
  title: string
  lessonCount: number
  completedCount: number
  lessons: LessonProgressRow[]
}

/** The lesson to continue with, and where the learner is in it (its activities and its quiz are its steps). */
interface NextStep {
  lessonId: number
  lessonTitle: string
  lessonNumber: number
  stepsDone: number
  stepsTotal: number
}

export interface MyClassCard {
  summary: ClassCard
  completedLessons: number
  progressPercent: number
  lessons: LessonProgressRow[]
  nextStep: NextStep | null
  /** Read-only: its lessons and results stay open, nothing new can be done in it. */
  archived: boolean
  archivedAt: string | null
}

export interface MyClasses {
  classes: MyClassCard[]
  /** Catalogue classes the learner has not joined: the count of the Find classes tab. */
  moreClassCount: number
}

/** A class page: before joining only `summary` and `preview` are filled; after joining `preview` is empty and progress and `units` are filled. */
export interface StudentClass {
  joined: boolean
  archived: boolean
  archivedAt: string | null
  summary: ClassCard
  preview: UnitPreview[]
  completedLessons: number
  nextStep: NextStep | null
  units: UnitProgress[]
}

// The lesson, its activities and quizzes

export interface KeyPhrase {
  chinese: string
  pinyin: string
  meaning: string
}

/** A flashcard or matching pair read from the server, where historical or draft items may omit pinyin. */
export interface PhraseCard {
  chinese: string
  pinyin: string | null
  meaning: string
}

/** A lesson of an archived class keeps the title and length, with neither path: it no longer plays. */
interface StudentVideo {
  title: string
  externalUrl: string | null
  streamPath: string | null
  durationSeconds: number | null
  watched: boolean
}

interface ActivityStep {
  type: ClassActivityType
  id: number
  title: string
  number: number
  done: boolean
}

interface QuizCard {
  id: number
  questionCount: number
  bestPercent: number | null
  attemptCount: number
  /** The attempt with the best score (the newest of equal ones), behind "See best result"; null before the first one. */
  bestAttemptId: number | null
  inProgress: boolean
}

export interface StudentLesson {
  classId: number
  classTitle: string
  title: string
  available: boolean
  video: StudentVideo | null
  keyPhrases: KeyPhrase[]
  activities: ActivityStep[]
  quizzes: QuizCard[]
  stepsDone: number
  stepsTotal: number
  status: LessonProgressStatus
  /** The class is archived: the lesson is read-only (phrases and results; no video, activity or quiz opens). */
  archived: boolean
}

interface ActivityRef {
  type: ClassActivityType
  id: number
}

export interface MatchWord {
  id: number
  chinese: string
  pinyin: string | null
}

export interface TraceCharacter {
  character: string
  pinyin: string | null
  meaning: string | null
}

export interface ActivityPlay {
  type: ClassActivityType
  id: number
  classId: number
  lessonId: number
  title: string
  /** The lesson's "Allow SparkBot hints": a learner stuck on the matching gets a hint. */
  hintsAllowed: boolean
  number: number
  activityCount: number
  lessonTitle: string
  next: ActivityRef | null
  cards: PhraseCard[]
  characters: TraceCharacter[]
  words: MatchWord[]
  meanings: string[]
}

interface PairResult {
  wordId: number
  correct: boolean
}

export interface ActivityOutcome {
  done: boolean
  pairs: PairResult[]
  /** True only when this completion transitioned the lesson to COMPLETED. */
  lessonCompletedNow: boolean
}

export interface MatchHint {
  wordId: number
  meaning: string
}

export interface QuizIntro {
  id: number
  classId: number
  classTitle: string
  lessonId: number
  lessonTitle: string
}

interface QuizOption {
  id: number
  text: string
}

interface QuizQuestion {
  id: number
  type: string
  text: string
  audioUrl: string | null
  imageUrl: string | null
  /** The lesson allows SparkBot hints and this question has one. */
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
  questions: QuizQuestion[]
  answers: SavedAnswer[]
}

interface ReviewedQuestion {
  questionId: number
  number: number
  text: string
  yourAnswer: string | null
  correctAnswer: string | null
  correct: boolean
  explanation: string | null
  /** The admin's feedback on the answer the learner picked, when it was wrong ("That's 面"); else null. */
  optionFeedback: string | null
}

export interface QuizResult {
  attemptId: number
  quiz: QuizIntro
  submittedAt: string
  /** This attempt's score. */
  percent: number
  /** Over the quiz's submitted attempts, this one included. */
  bestPercent: number
  /** The right answers of that best attempt, out of its questions: "Your best is 4 out of 5 (80%)". */
  bestCorrect: number
  bestTotal: number
  correctCount: number
  wrongCount: number
  questions: ReviewedQuestion[]
  /** The lesson's status after this attempt. */
  lessonStatus: LessonProgressStatus
  /** Activities of the lesson still to do before it is completed. */
  activitiesLeft: number
}
