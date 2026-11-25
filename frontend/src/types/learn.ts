/**
 * Types of the Learn module: the Learn path and lesson player, Practice, Mistakes, XP / daily goal / streak, the
 * Progress page, the level check and the admin Learning path and Practice library. They hold the fields of the backend
 * DTOs in `dto/learn` that the frontend reads.
 */
import type { PracticeType } from '@/config/practiceActivities'

import type { ContentStatus, PageResponse } from './api'

/** Optional scaffolding stage of a practice set (FRCONT10). */
type LearningStage = 'GUIDED' | 'SCAFFOLDED' | 'INDEPENDENT'

export type StopState = 'LOCKED' | 'AVAILABLE' | 'DONE'
type UnitState = 'LOCKED' | 'ACTIVE' | 'DONE'
export type LessonKind = 'LESSON' | 'BONUS' | 'REVIEW' | 'CHECKPOINT'
type QuestionType = 'MEANING' | 'PINYIN' | 'HANZI' | 'LISTEN_CHOOSE' | 'LISTEN_BUILD' | 'TRANSLATE_BUILD'
type AnswerKind = 'CHOICE' | 'BUILD'
export type MistakeSource = 'LEARN' | 'PRACTICE'
export type { PracticeType } from '@/config/practiceActivities'
/** How sure the learner was when submitting an answer (Practice asks for Listening, Drag & Drop and Typing). */
export type AnswerConfidence = 'GUESS' | 'SURE'
/**
 * How a learner rated a flashcard, sent as the card's answer when the set is finished: a card still being learned
 * becomes a mistake, reviewed later as a multiple-choice question.
 */
export type FlashcardRating = 'GOT_IT' | 'STILL_LEARNING'
/** When hints are offered: after being stuck, only on request, or never (Account page). */
export type HintMode = 'AUTOMATIC' | 'ON_REQUEST' | 'OFF'
export type HintTier = 'HSK1' | 'HSK2' | 'HSK3'
export type PublishStatus = 'DRAFT' | 'PUBLISHED'
export type BadgeKey =
  | 'FIRST_STEPS'
  | 'STREAK_EXPLORER'
  | 'XP_STARTER'
  | 'QUIZ_STAR'
  | 'LESSON_FINISHER'
  | 'TEN_DAY_STREAK'
  | 'UNIT_CHAMPION'
  | 'XP_300'

interface ChoiceOption {
  readonly text: string
  readonly pinyin: string | null
}

/** A question without its answer; fields that would give the answer away are null for its type. */
export interface QuestionView {
  readonly id: number
  readonly answerKind: AnswerKind
  readonly type: QuestionType | null
  readonly prompt: string | null
  readonly hanzi: string | null
  readonly pinyin: string | null
  readonly meaning: string | null
  readonly audioText: string | null
  readonly options: readonly ChoiceOption[]
  readonly tiles: readonly string[]
}

export interface AnswerCheck {
  readonly correct: boolean
  readonly correctAnswer: string
  readonly pinyin: string | null
  readonly meaning: string | null
  readonly explanation: string | null
  /** Practice confidence as recorded by the server (e.g. GUESS when an earlier hint was served). */
  readonly effectiveConfidence?: AnswerConfidence | null
}

/**
 * A rank the learner passed with a result (every 100 XP opens the next rank, `RewardRules.levelFor`): the rank
 * reached, the total XP it opens at and the total XP the rank after it opens at.
 */
export interface RankUp {
  readonly rank: number
  readonly rankXp: number
  readonly nextRankXp: number
}

export interface XpResult {
  readonly xpEarned: number
  readonly bonusXp: number
  readonly todayXp: number
  readonly dailyGoalXp: number
  readonly goalReached: boolean
  readonly currentStreak: number
  readonly newBadges: readonly BadgeKey[]
  /** Null unless this result took the learner past a rank threshold (the rank-up dialog then opens). */
  readonly rankUp: RankUp | null
}

export interface TodaySummary {
  readonly dailyGoalXp: number
  readonly todayXp: number
  readonly goalReached: boolean
  readonly week: readonly { readonly date: string; readonly goalReached: boolean }[]
  readonly mistakesWaiting: number
}

export interface DailyGoal {
  readonly dailyGoalXp: number
}

// Learn path

export interface PathStop {
  readonly id: number
  readonly position: number
  readonly title: string
  readonly kind: LessonKind
  readonly minutes: number
  readonly xpReward: number
  readonly state: StopState
  readonly current: boolean
  readonly unlockAfter: string | null
}

export interface PathUnit {
  readonly id: number
  readonly number: number
  readonly title: string
  readonly titleZh: string
  /** The unit's line icon (an `IconGlyph` name). */
  readonly iconKey: string
  readonly state: UnitState
  readonly lessonCount: number
  readonly completedCount: number
  readonly unlockAfterUnit: number | null
  readonly lessons: readonly PathStop[]
}

export interface PathCheckpoint {
  readonly id: number
  readonly questionCount: number
  readonly xpReward: number
  readonly passPercent: number
  readonly state: StopState
}

export interface PathLevel {
  readonly level: number
  readonly hskLevel: number
  readonly locked: boolean
  readonly units: readonly PathUnit[]
  readonly checkpoint: PathCheckpoint | null
}

export interface LearnPath {
  readonly levels: readonly PathLevel[]
  readonly nextLesson: { readonly unitId: number; readonly xpReward: number } | null
}

export interface Guidebook {
  readonly phrases: readonly { readonly hanzi: string; readonly pinyin: string; readonly meaning: string }[]
  readonly tips: readonly string[]
}

/**
 * Identifies a single play of a lesson, practice set or mistakes review. The player sends both fields with each
 * hint request so the server can verify the run and count hints toward its allowance.
 */
export interface HintRun {
  readonly runStartedAt: string
  readonly runSignature: string
}

export interface LessonPlay extends HintRun {
  readonly title: string
  readonly kind: LessonKind
  readonly passPercent: number | null
  readonly questions: readonly QuestionView[]
}

/** An answer to a question; a null answer means the question was skipped. */
export interface QuestionAnswer {
  readonly questionId: number
  readonly answer: string | null
}

export interface LessonResult {
  readonly kind: LessonKind
  readonly passed: boolean
  readonly passPercent: number | null
  readonly accuracy: number
  readonly nextLesson: { readonly title: string; readonly unlockedNow: boolean } | null
  readonly xp: XpResult
}

// Mistakes

export interface MistakeRow {
  readonly id: number
  readonly source: MistakeSource
  readonly type: QuestionType | null
  readonly prompt: string | null
  readonly hanzi: string | null
  readonly meaning: string | null
  readonly yourAnswer: string | null
  readonly correctAnswer: string
  /** Answered wrong while "Sure": it leads the list and the review. */
  readonly sureWrong: boolean
  /** A flashcard the learner marked "Still learning" (reviewed as a multiple-choice question): nothing was answered. */
  readonly stillLearning: boolean
}

export interface MistakeList {
  readonly page: PageResponse<MistakeRow>
  readonly all: number
  readonly learn: number
  readonly practice: number
}

export interface MistakeReview extends HintRun {
  readonly questions: readonly QuestionView[]
  readonly context: readonly {
    readonly sourceTitle: string
    readonly fromLearn: boolean
    readonly yourAnswer: string | null
    /** A flashcard marked "Still learning" in Flashcards. */
    readonly stillLearning: boolean
  }[]
}

export interface MistakeReviewResult {
  readonly cleared: number
  readonly left: number
  readonly xp: XpResult
}

// Practice

export interface PracticeSetSummary {
  readonly id: number
  readonly title: string
  readonly hskLevel: number | null
  readonly itemCount: number
  readonly xpReward: number
  readonly done: boolean
  /** A run was finished but not every item was right yet. */
  readonly started: boolean
}

export interface PracticeItem {
  readonly key: string
  readonly prompt: string | null
  readonly hanzi: string | null
  readonly pinyin: string | null
  readonly meaning: string | null
  readonly audioText: string | null
  readonly example: string | null
  readonly exampleMeaning: string | null
  readonly options: readonly ChoiceOption[]
  readonly tiles: readonly string[]
}

export interface PracticeSet extends HintRun {
  readonly id: number
  readonly type: PracticeType
  readonly stage: LearningStage | null
  readonly xpReward: number
  readonly done: boolean
  readonly items: readonly PracticeItem[]
  readonly matchingChoices: readonly string[]
  readonly strokeCount: number | null
  /** Tracing: the strokes of each character in order, adding up to `strokeCount`; null when the set does not say. */
  readonly characterStrokes: readonly number[] | null
}

export interface PracticeItemAnswer {
  readonly itemKey: string
  readonly answer: string | null
  /**
   * Sent with a check; an answer after a hint always counts as a guess (the server knows which hints it gave for the
   * item).
   */
  readonly confidence?: AnswerConfidence | null
  /** Listening sets mark skipped items when browser speech playback failed. */
  readonly audioFailed?: boolean
}

export interface HintPolicyLevel {
  readonly tier: HintTier
  readonly delaySeconds: number
  /** Hints a set may use; 0 means every question may have one. */
  readonly perSet: number
}

/** The hint rule of the student's level with their setting. A hint is one step; how much it says follows the tier. */
export interface HintSettings extends HintPolicyLevel {
  readonly mode: HintMode
}

type HintKind =
  | 'RULE_OUT'
  | 'CLUE'
  | 'FIRST_SOUND'
  | 'FIRST_LETTER'
  | 'LISTEN_SLOW'
  | 'TWO_LEFT'
  | 'FIRST_TILE'
  | 'LEAVE_OUT'
  | 'ORDER_START'
  | 'INITIALS'
  | 'TYPE_THIS'
  | 'PAIR'
  | 'STROKE_COUNT'
  | 'STROKE_ORDER'

/** An item's one hint; the interface writes its sentence from the kind and fields. */
export interface HintStep {
  readonly kind: HintKind
  readonly words: readonly string[]
  readonly pinyin: readonly string[]
  readonly text: string | null
  readonly count: number | null
  /** Every answer ruled out so far (greyed out). */
  readonly ruledOut: readonly string[]
}

/** The Progress page's answers of the last 7 days. */
export interface AnswerInsights {
  readonly sureAndRight: number
  readonly luckyGuesses: number
  readonly sureButWrong: number
  readonly wrongGuesses: number
  readonly hintsUsed: number
  readonly mostHintsIn: PracticeType | null
}

export interface PracticeResult {
  readonly type: PracticeType
  readonly correctCount: number
  readonly itemCount: number
  readonly allCorrect: boolean
  readonly nextSet: { readonly id: number; readonly title: string } | null
  readonly xp: XpResult
}

// Progress

export interface ProgressOverview {
  readonly totalXp: number
  readonly level: number
  readonly xpIntoLevel: number
  readonly nextLevelXp: number
  readonly currentStreak: number
  readonly bestStreak: number
  readonly calendar: {
    readonly month: string
    readonly today: string
    readonly joinedOn: string
    readonly goalDays: readonly string[]
    readonly previousMonth: string | null
    readonly nextMonth: string | null
  }
  readonly badges: readonly { readonly key: BadgeKey; readonly earned: boolean; readonly earnedAt: string | null }[]
}

export type ProgressSkill = 'VOCABULARY' | 'LISTENING' | 'CHARACTERS' | 'SENTENCES'

/** The Skills card of the Progress page, computed from the student's real completions. */
export interface SkillProgress {
  readonly skills: readonly {
    readonly skill: ProgressSkill
    /** The share of the skill's published items the student completed. */
    readonly percent: number
    /** Percentage points gained from completions in the last 7 days. */
    readonly weeklyChange: number
  }[]
  /** Null until the student has completed anything. */
  readonly weakest: ProgressSkill | null
}

// Admin

export interface AdminUnitRow {
  readonly id: number
  readonly number: number
  readonly title: string
  readonly titleZh: string
  readonly hskLevel: number
  /** The unit's lessons and reviews: neither its bonus chests nor a level checkpoint (as on the student path). */
  readonly lessonCount: number
  /** The unit's bonus chests, named beside its lessons. */
  readonly bonusCount: number
  readonly studentsStarted: number
  readonly status: PublishStatus
}

export interface AdminLearningPath {
  readonly unitCount: number
  readonly levels: readonly {
    readonly level: number
    readonly hskLevel: number
    readonly units: readonly AdminUnitRow[]
  }[]
}

export interface AdminUnitDetail {
  readonly unit: AdminUnitRow
  readonly lessons: readonly {
    readonly id: number
    readonly title: string
    readonly kind: LessonKind
    readonly minutes: number
    readonly xpReward: number
  }[]
}

export type LevelCheckSkill =
  | 'VOCABULARY'
  | 'LISTENING'
  | 'CHARACTERS'
  | 'NUMBERS'
  | 'PINYIN'
  | 'GRAMMAR'
  | 'SENTENCES'
  | 'IDIOMS'
export type LevelCheckLevel = 'BEGINNER_1' | 'BEGINNER_2'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface LevelCheckQuestionRow {
  readonly id: number
  readonly prompt: string
  readonly skill: LevelCheckSkill
  readonly difficulty: Difficulty
  readonly level: LevelCheckLevel
  readonly status: PublishStatus
}

export interface LevelCheckQuestionDetail extends LevelCheckQuestionRow {
  readonly hanzi: string | null
  readonly pinyin: string | null
  readonly audioText: string | null
  readonly answeredCount: number
  /** Null while nobody answered the question. */
  readonly correctPercent: number | null
  readonly options: readonly {
    readonly text: string
    readonly correct: boolean
    readonly explanation: string | null
  }[]
}

export interface LevelCheckQuestionPayload {
  prompt: string
  hanzi: string | null
  pinyin: string | null
  audioText: string | null
  skill: LevelCheckSkill
  difficulty: Difficulty
  level: LevelCheckLevel
  status: PublishStatus
  options: { text: string; correct: boolean; explanation: string | null }[]
}

export interface AdminPracticeSetRow {
  readonly id: number
  readonly type: PracticeType
  readonly title: string
  readonly hskLevel: number | null
  readonly status: ContentStatus
  readonly updatedAt: string
}

/** One item of a set as admins edit it (fields used depend on the type). */
export interface PracticeItemData {
  hanzi: string | null
  pinyin: string | null
  meaning: string | null
  prompt: string | null
  audioText: string | null
  example: string | null
  exampleMeaning: string | null
  options: { text: string; pinyin: string | null }[]
  correctIndex: number | null
  explanation: string | null
  distractors: string | null
}

/** A practice set's status changes; `unarchive` is the Undo of Archive (back to the status before), `restore` a draft. */
export type PracticeSetAction = 'publish' | 'unpublish' | 'archive' | 'restore' | 'unarchive'
export type PracticeHistoryAction = 'CREATED' | 'UPDATED' | 'PUBLISHED' | 'UNPUBLISHED' | 'ARCHIVED' | 'RESTORED'

export interface AdminPracticeSetDetail {
  readonly id: number
  readonly type: PracticeType
  readonly title: string
  readonly stage: LearningStage | null
  readonly hskLevel: number | null
  readonly xpReward: number
  readonly status: ContentStatus
  /** Null for seeded platform content. */
  readonly createdBy: string | null
  readonly playedBy: number
  readonly items: readonly PracticeItemData[]
  readonly strokeCount: number | null
  /** Tracing: the strokes of each character in order, adding up to `strokeCount`; null when not given. */
  readonly characterStrokes: readonly number[] | null
  readonly history: readonly {
    readonly action: PracticeHistoryAction
    readonly actor: string | null
    readonly at: string
  }[]
}

/** A new or edited set; the server works out its XP from its type, level and items. */
export interface PracticeSetPayload {
  title: string
  stage: LearningStage | null
  hskLevel: number
  items: PracticeItemData[]
  strokeCount: number | null
  /** Tracing: one number per character, adding up to `strokeCount`; empty when not given. */
  characterStrokes: number[]
}

// Level check (student; the plan module's setup uses it)

export type MandarinLevel = 'NEW_TO_MANDARIN' | 'BEGINNER_1' | 'BEGINNER_2'

export interface LevelCheckAnswer {
  questionId: number
  optionId: number | null
}

export interface LevelCheckStep {
  readonly feedback: {
    readonly correct: boolean
    readonly skipped: boolean
    readonly correctAnswer: string | null
    readonly explanation: string | null
  } | null
  readonly next: {
    readonly id: number
    readonly prompt: string
    readonly hanzi: string | null
    readonly pinyin: string | null
    readonly audioText: string | null
    /** The skill this question tests: the check asks one question per skill. */
    readonly skill: LevelCheckSkill
    readonly options: readonly { readonly id: number; readonly text: string }[]
  } | null
  readonly done: boolean
  readonly result: {
    /** The starting level the check placed the learner at. */
    readonly level: MandarinLevel
    readonly newSkills: readonly LevelCheckSkill[]
  } | null
  readonly answeredCount: number
  readonly maxQuestions: number
  /** Server-signed token binding this check's issued questions and answers to the current learner. */
  readonly runToken?: string | null
}
