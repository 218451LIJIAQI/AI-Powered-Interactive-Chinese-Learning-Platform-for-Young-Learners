/**
 * Types of the Learn module: the Learn path and lesson player, Practice, Mistakes, XP / daily goal / streak, the
 * Progress page, the level check and the admin Learning path and Practice library. They mirror the backend DTOs in
 * `dto/learn`.
 */
import type { ContentStatus, LearningStage, PageResponse } from './api'

export type StopState = 'LOCKED' | 'AVAILABLE' | 'DONE'
export type UnitState = 'LOCKED' | 'ACTIVE' | 'DONE'
export type LessonKind = 'LESSON' | 'BONUS' | 'REVIEW' | 'CHECKPOINT'
export type QuestionType = 'MEANING' | 'PINYIN' | 'HANZI' | 'LISTEN_CHOOSE' | 'LISTEN_BUILD' | 'TRANSLATE_BUILD'
export type AnswerKind = 'CHOICE' | 'BUILD'
export type MistakeSource = 'LEARN' | 'PRACTICE'
export type PracticeType = 'FLASHCARDS' | 'QUIZZES' | 'LISTENING' | 'TRACING' | 'DRAG_DROP' | 'MATCHING' | 'STORY'
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

export interface ChoiceOption {
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
}

export interface XpResult {
  readonly xpEarned: number
  readonly bonusXp: number
  readonly totalXp: number
  readonly todayXp: number
  readonly dailyGoalXp: number
  readonly goalReached: boolean
  readonly goalJustReached: boolean
  readonly currentStreak: number
  readonly newBadges: readonly BadgeKey[]
}

export interface TodaySummary {
  readonly date: string
  readonly dailyGoalXp: number
  readonly todayXp: number
  readonly goalReached: boolean
  readonly currentStreak: number
  readonly week: readonly { readonly date: string; readonly goalReached: boolean }[]
  readonly setsDoneToday: number
  readonly mistakesWaiting: number
}

export interface DailyGoal {
  readonly dailyGoalXp: number
}

// ===== Learn path =====

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
  readonly state: UnitState
  readonly lessonCount: number
  readonly completedCount: number
  readonly unlockAfterUnit: number | null
  readonly lessons: readonly PathStop[]
}

export interface PathCheckpoint {
  readonly id: number
  readonly title: string
  readonly questionCount: number
  readonly xpReward: number
  readonly passPercent: number
  readonly state: StopState
  readonly unlockAfter: string | null
}

export interface PathLevel {
  readonly level: number
  readonly hskLevel: number
  readonly lessonCount: number
  readonly locked: boolean
  readonly units: readonly PathUnit[]
  readonly checkpoint: PathCheckpoint | null
}

export interface NextLesson {
  readonly id: number
  readonly title: string
  readonly kind: LessonKind
  readonly unitId: number
  readonly unitNumber: number
  readonly unitTitle: string
  readonly unitTitleZh: string
  readonly position: number
  readonly unitLessonCount: number
  readonly minutes: number
  readonly xpReward: number
}

export interface LearnPath {
  readonly totalLessons: number
  readonly completedLessons: number
  readonly levels: readonly PathLevel[]
  readonly nextLesson: NextLesson | null
  readonly learnMistakesWaiting: number
}

export interface Guidebook {
  readonly unitId: number
  readonly number: number
  readonly title: string
  readonly titleZh: string
  readonly phrases: readonly { readonly hanzi: string; readonly pinyin: string; readonly meaning: string }[]
  readonly tips: readonly string[]
}

export interface LessonPlay {
  readonly id: number
  readonly title: string
  readonly kind: LessonKind
  readonly unitId: number
  readonly unitNumber: number
  readonly unitTitle: string
  readonly position: number
  readonly unitLessonCount: number
  readonly minutes: number
  readonly xpReward: number
  readonly passPercent: number | null
  readonly questions: readonly QuestionView[]
}

/** An answer to a question; a null answer means the question was skipped. */
export interface QuestionAnswer {
  readonly questionId: number
  readonly answer: string | null
}

export interface LessonResult {
  readonly lessonId: number
  readonly title: string
  readonly kind: LessonKind
  readonly unitNumber: number
  readonly position: number
  readonly passed: boolean
  readonly passPercent: number | null
  readonly correctCount: number
  readonly questionCount: number
  readonly accuracy: number
  readonly durationSeconds: number | null
  readonly nextLesson: { readonly id: number; readonly title: string; readonly unlockedNow: boolean } | null
  readonly xp: XpResult
}

// ===== Mistakes =====

export interface MistakeRow {
  readonly id: number
  readonly source: MistakeSource
  readonly type: QuestionType | null
  readonly prompt: string | null
  readonly hanzi: string | null
  readonly meaning: string | null
  readonly yourAnswer: string | null
  readonly correctAnswer: string
  readonly lastWrongAt: string
}

export interface MistakeList {
  readonly page: PageResponse<MistakeRow>
  readonly all: number
  readonly learn: number
  readonly practice: number
}

export interface MistakeReview {
  readonly waiting: number
  readonly questions: readonly QuestionView[]
  readonly context: readonly {
    readonly sourceTitle: string
    readonly fromLearn: boolean
    readonly yourAnswer: string | null
  }[]
}

export interface MistakeReviewResult {
  readonly answered: number
  readonly cleared: number
  readonly left: number
  readonly xp: XpResult
}

// ===== Practice =====

export interface PracticeSetSummary {
  readonly id: number
  readonly title: string
  readonly stage: LearningStage | null
  readonly hskLevel: number | null
  readonly itemCount: number
  readonly xpReward: number
  readonly done: boolean
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

export interface StoryLine {
  readonly hanzi: string
  readonly pinyin: string | null
  readonly translation: string | null
}

export interface PracticeSet {
  readonly id: number
  readonly type: PracticeType
  readonly title: string
  readonly stage: LearningStage | null
  readonly hskLevel: number | null
  readonly xpReward: number
  readonly done: boolean
  readonly items: readonly PracticeItem[]
  readonly storyLines: readonly StoryLine[]
  readonly matchingChoices: readonly string[]
  readonly strokeCount: number | null
}

export interface PracticeItemAnswer {
  readonly itemKey: string
  readonly answer: string | null
}

export interface PracticeResult {
  readonly type: PracticeType
  readonly setId: number
  readonly title: string
  readonly correctCount: number
  readonly itemCount: number
  readonly allCorrect: boolean
  readonly results: readonly { readonly key: string; readonly correct: boolean }[]
  readonly nextSet: { readonly id: number; readonly title: string } | null
  readonly xp: XpResult
}

// ===== Progress =====

export type XpSourceKind =
  | 'LEARN_LESSON'
  | 'PRACTICE_SET'
  | 'QUIZ_STREAK'
  | 'MISTAKES_REVIEW'
  | 'DAILY_GOAL'
  | 'EARLIER'

export interface ProgressOverview {
  readonly totalXp: number
  readonly level: number
  readonly xpIntoLevel: number
  readonly nextLevelXp: number
  readonly dailyGoalXp: number
  readonly todayXp: number
  readonly goalReachedToday: boolean
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
  readonly sources: {
    readonly learn: number
    readonly practice: number
    readonly reviewsAndGoals: number
    readonly earlier: number
  }
  readonly recent: readonly {
    readonly kind: XpSourceKind
    readonly title: string
    readonly xp: number
    readonly at: string
  }[]
  readonly badges: readonly { readonly key: BadgeKey; readonly earned: boolean; readonly earnedAt: string | null }[]
}

// ===== Admin =====

export interface AdminUnitRow {
  readonly id: number
  readonly number: number
  readonly title: string
  readonly titleZh: string
  readonly hskLevel: number
  readonly lessonCount: number
  readonly studentsStarted: number
  readonly status: PublishStatus
}

export interface AdminLearningPath {
  readonly unitCount: number
  readonly lessonCount: number
  readonly levels: readonly {
    readonly level: number
    readonly hskLevel: number
    readonly lessonCount: number
    readonly units: readonly AdminUnitRow[]
  }[]
}

export interface AdminUnitDetail {
  readonly unit: AdminUnitRow
  readonly lessons: readonly {
    readonly id: number
    readonly position: number
    readonly title: string
    readonly kind: LessonKind
    readonly minutes: number
    readonly xpReward: number
    readonly questionCount: number
  }[]
}

export type LevelCheckSkill = 'VOCABULARY' | 'LISTENING' | 'CHARACTERS' | 'SENTENCES' | 'PINYIN'
export type LevelCheckLevel = 'BEGINNER_1' | 'BEGINNER_2'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface LevelCheckQuestionRow {
  readonly id: number
  readonly prompt: string
  readonly hanzi: string | null
  readonly skill: LevelCheckSkill
  readonly difficulty: Difficulty
  readonly level: LevelCheckLevel
  readonly status: PublishStatus
  readonly answeredCount: number
  readonly correctPercent: number | null
}

export interface LevelCheckOption {
  readonly id?: number | null
  readonly text: string
  readonly correct: boolean
  readonly explanation: string | null
}

export interface LevelCheckQuestionDetail extends LevelCheckQuestionRow {
  readonly pinyin: string | null
  readonly audioText: string | null
  readonly options: readonly LevelCheckOption[]
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
  readonly stage: LearningStage | null
  readonly hskLevel: number | null
  readonly itemCount: number
  readonly status: ContentStatus
  readonly updatedAt: string
}

/** One item of a set as admins edit it (fields used depend on the type). */
export interface PracticeItemData {
  id?: number | null
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

export type PracticeHistoryAction = 'CREATED' | 'UPDATED' | 'PUBLISHED' | 'UNPUBLISHED' | 'ARCHIVED' | 'RESTORED'

export interface AdminPracticeSetDetail {
  readonly id: number
  readonly type: PracticeType
  readonly title: string
  readonly stage: LearningStage | null
  readonly hskLevel: number | null
  readonly xpReward: number
  readonly status: ContentStatus
  readonly createdBy: string | null
  readonly createdAt: string
  readonly updatedAt: string
  readonly playedBy: number
  readonly items: readonly PracticeItemData[]
  readonly storyLines: readonly StoryLine[]
  readonly strokeCount: number | null
  readonly history: readonly {
    readonly action: PracticeHistoryAction
    readonly actor: string | null
    readonly at: string
  }[]
}

export interface PracticeSetPayload {
  title: string
  stage: LearningStage | null
  hskLevel: number | null
  xpReward: number | null
  items: PracticeItemData[]
  storyLines: { hanzi: string; pinyin: string | null; translation: string | null }[]
  strokeCount: number | null
}

// ===== Level check (student; the plan module's setup uses it) =====

export type MandarinLevel = 'NEW_TO_MANDARIN' | 'BEGINNER_1' | 'BEGINNER_2'

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
    readonly options: readonly { readonly id: number; readonly text: string }[]
  } | null
  readonly done: boolean
  readonly result: {
    readonly knownSkills: readonly LevelCheckSkill[]
    readonly newSkills: readonly LevelCheckSkill[]
    readonly answeredCount: number
  } | null
  readonly answeredCount: number
  readonly maxQuestions: number
}
