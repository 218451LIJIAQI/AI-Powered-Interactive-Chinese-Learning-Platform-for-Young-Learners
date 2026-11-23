/**
 * Admin class authoring (Admin › Content › Classes): what `/admin/classes`, `/admin/units`, `/admin/lessons` and the
 * AI quiz draft return and accept. Classes are themed courses the MandarinSpark team makes and publishes; there is no
 * review step. Dates are ISO strings. The lesson shapes learners share (key phrases, tracing characters, activity
 * types) stay in types/classes.ts.
 */
import type { ClassTopic, ContentStatus } from './api'
import type { ClassActivityType, KeyPhrase, PhraseCard, TraceCharacter } from './classes'

/** What a row of Content › Classes is: a class, or a lesson of a class. */
export type AdminContentType = 'CLASS' | 'LESSON'

/** One row of Content › Classes (A3.01): Title · Type · Class · Status · Updated. */
export interface AdminContentRow {
  type: AdminContentType
  id: number
  title: string
  /** The class itself for a class row; the lesson's class for a lesson row. */
  classId: number
  /** The lesson's class; null for a class row. */
  classTitle: string | null
  status: ContentStatus
  updatedAt: string
}

export interface AdminContentQuery {
  keyword?: string
  type?: AdminContentType
  status?: ContentStatus
  page?: number
  size?: number
}

interface AdminLessonRow {
  id: number
  title: string
  status: ContentStatus
  /** A published lesson with saved changes that learners don't see until it is published again. */
  hasUnpublishedChanges: boolean
}

export interface AdminUnit {
  id: number
  title: string
  lessons: AdminLessonRow[]
}

/** The class page (A3.05–A3.13): its details, units and lessons. */
export interface AdminClassPage {
  id: number
  title: string
  description: string | null
  category: ClassTopic | null
  /** "Best after Unit N" of the Learn path: a soft label for learners, never a lock. */
  bestAfterUnit: number | null
  status: ContentStatus
  lessonCount: number
  units: AdminUnit[]
  /** The class has what publishing needs: 2 units with 4–6 published lessons. */
  canPublish: boolean
}

/** New class and the Details tab; saved at once, also on a published class. */
export interface ClassDetailsPayload {
  title: string
  description: string | null
  categoryId: number | null
  /** 1 to 8; null clears it. */
  bestAfterUnit: number | null
}

export type ClassDetailsForm = Omit<ClassDetailsPayload, 'description'> & { description: string }

// Lesson editor (A3.14–A3.35)

interface EditorVideo {
  title: string
  durationSeconds: number | null
}

export interface EditorActivity {
  type: ClassActivityType
  id: number
  title: string
  number: number
  characters: TraceCharacter[]
  pairs: PhraseCard[]
  /** The Flashcards cards (Chinese · Pinyin · Meaning); none on an older activity, whose learners review the key phrases. */
  cards: PhraseCard[]
}

interface EditorQuestionOption {
  id: number
  text: string
  correct: boolean
  /** What a learner who picks this answer reads in the review ("That’s 面"); null when there is none. */
  feedback: string | null
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
  /** SparkBot drafted it and nobody has kept or edited it yet; publishing waits for that check. */
  aiDraft: boolean
}

export interface EditorQuiz {
  id: number
  /** "Allow SparkBot hints" (A3.25): learners who are stuck get a hint. New lessons start with hints off. */
  hintsAllowed: boolean
  /** Learners have taken the quiz, so its questions stay as they are to keep their results true. */
  questionsLocked: boolean
  questions: EditorQuestion[]
}

/** What publishing checks, on the version the editor shows (the saved working copy). */
interface EditorChecklist {
  videoAdded: boolean
  keyPhrasesAdded: boolean
  activitiesComplete: boolean
  /** The quiz has its 5 questions, each with a correct answer. */
  quizzesReady: boolean
  /** Every key phrase, flashcard, tracing character and matching pair has its pinyin. */
  pinyinComplete: boolean
}

/** A lesson as the editor shows it: the working copy, with the changes saved since it was last published. */
export interface LessonEditor {
  lessonId: number
  classId: number
  classTitle: string
  classStatus: ContentStatus
  unitId: number
  unitTitle: string
  /** The class's units in order, for the lesson's Unit field. */
  units: { id: number; title: string }[]
  status: ContentStatus
  /** A published lesson whose saved changes learners don't see yet: Publish applies them. */
  hasUnpublishedChanges: boolean
  /** False while the class or the lesson is archived. */
  editable: boolean
  title: string
  minutes: number | null
  summary: string | null
  keyPhrases: KeyPhrase[]
  video: EditorVideo | null
  videoUploadMaxBytes?: number
  activities: EditorActivity[]
  quizzes: EditorQuiz[]
  checklist: EditorChecklist
}

export interface LessonContentPayload {
  title: string
  unitId: number | null
  minutes: number | null
  summary: string | null
  keyPhrases: KeyPhrase[]
}

interface TraceCharacterInput {
  character: string
  pinyin: string
  meaning: string | null
}

export interface ActivityPayload {
  type: ClassActivityType
  title: string
  characters?: TraceCharacterInput[]
  pairs?: KeyPhrase[]
  /** Flashcards: up to 20 cards, each with its Chinese, pinyin and meaning. */
  cards?: KeyPhrase[]
}

/** The lesson's quiz as it is made; hints start off unless `hintsAllowed`. */
export interface QuizPayload {
  title: string
  hintsAllowed: boolean
}

/** The quiz's settings: "Allow SparkBot hints". */
export interface QuizSettingsPayload {
  hintsAllowed: boolean
}

export interface QuestionPayload {
  questionType: string
  questionText: string
  points: number
  /** 1-based place in the quiz (Undo of a delete puts the question back there); a new question goes last without it. */
  position?: number
  correctAnswerText?: string | null
  explanation?: string | null
  hint?: string | null
  audioUrl?: string | null
  imageUrl?: string | null
  /** `feedback` (up to 300 characters) is what a learner who picks the answer reads in the review. */
  options?: { optionText: string; correctOption: boolean; feedback?: string | null }[]
  /** A new question SparkBot drafted: it waits for Keep or Edit before the lesson can be published. */
  aiDraft?: boolean
}
