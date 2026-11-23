/**
 * The learning plan module: the setup choices, the plan (built with SparkBot or from rules), the plan at a glance for
 * Home / Progress and the e-mail confirmation of a new account.
 * Texts SparkBot wrote are null in a rule-based plan; the pages write them from the goals instead.
 */
import type { AppLanguage } from '@/types/api'
import type {
  LessonKind,
  LevelCheckAnswer,
  LevelCheckSkill,
  MandarinLevel,
  PracticeType,
  StopState,
  TodaySummary,
} from '@/types/learn'

export type { LevelCheckAnswer }

export type PlanReason = 'TRAVEL' | 'WORK' | 'STUDY' | 'EVERYDAY' | 'JUST_FOR_ME' | 'OTHER'
export type PlanGoal =
  | 'INTRODUCE_MYSELF'
  | 'GREET_CUSTOMERS'
  | 'ORDER_FOOD'
  | 'ASK_DIRECTIONS'
  | 'GET_AROUND'
  | 'GO_SHOPPING'
  | 'UNDERSTAND_PRICES'
  | 'READ_SIGNS'
  | 'SHORT_CHATS'
  | 'SMALL_TALK'
  | 'TALK_ABOUT_WORK'
  | 'WRITE_MESSAGES'
  | 'TALK_IN_CLASS'
  | 'TELL_TIME'
  | 'ENJOY_MEDIA'
  | 'NOT_SURE'
/** "Time I can give each day", the study-time bands of Shen & Wu (2026); HOURS_1_TO_2 is the default. */
export type PlanPace = 'UNDER_30_MIN' | 'MINUTES_30_TO_60' | 'HOURS_1_TO_2' | 'HOURS_2_PLUS'
export type PlanFocus = 'LISTENING' | 'SPEAKING' | 'READING' | 'WRITING'
export type PlanSelfLevel = 'NEW_TO_MANDARIN' | 'A_LITTLE' | 'THE_BASICS' | 'NOT_SURE'
export type PlanStatus = 'STARTS_TODAY' | 'ON_TRACK' | 'BEHIND' | 'COMPLETE'
export type StepStatus = 'DONE' | 'IN_PROGRESS' | 'NEXT' | 'LATER' | 'IN_MY_CLASSES' | 'OPTIONAL' | 'UNAVAILABLE'

/** A study-time band in minutes a day; the open-ended "2+ hours" has no maximum. */
export interface PlanPaceBand {
  readonly key: PlanPace
  readonly minMinutesPerDay: number
  readonly maxMinutesPerDay: number | null
}

// Setup

export interface PlanOptions {
  readonly reasons: readonly { readonly key: PlanReason; readonly goals: readonly PlanGoal[] }[]
  readonly paces: readonly PlanPaceBand[]
  /** Single source of truth from the server for where each self-level estimate starts the adaptive check. */
  readonly checkStarts: Readonly<Record<PlanSelfLevel, MandarinLevel>>
  readonly goalTextMax: number
  /** The platform's day (yyyy-MM-dd), which the server checks a goal date against: not the browser's. */
  readonly today: string
}

/** The goal as the setup (or "Update my goal") sends it; `deadline` is an ISO date or null for no date. */
export interface GoalInput {
  reason: PlanReason
  reasonText: string | null
  goals: PlanGoal[]
  goalText: string | null
  deadline: string | null
  pace: PlanPace
  focus: PlanFocus[]
}

export interface BuildPlanPayload {
  goal: GoalInput
  levelCheck: { selfLevel: PlanSelfLevel; skipped: boolean; answers: LevelCheckAnswer[] }
  language: AppLanguage
}

// The plan

export interface PlanStep {
  readonly position: number
  readonly kind: 'LEARN_UNIT' | 'CLASS'
  readonly refId: number
  readonly title: string
  readonly titleZh: string | null
  readonly unitNumber: number | null
  readonly lessonCount: number
  readonly completedCount: number
  readonly percent: number
  readonly status: StepStatus
  readonly joined: boolean
  readonly quickReview: boolean
  readonly weekFrom: number
  readonly weekTo: number
  /**
   * A class: the Learn unit it is best taken after (the class's own setting); null when the class has none, and then
   * the unit the class follows in the plan stands in.
   */
  readonly bestAfterUnit: number | null
  /** SparkBot's "Why"; null in a rule-based plan, which explains the step with `goals`. */
  readonly reason: string | null
  readonly goals: readonly PlanGoal[]
  /**
   * A Learn unit: a lesson of the unit, or, for the unit behind an open level checkpoint, that checkpoint (kind
   * CHECKPOINT). A joined class: its next lesson.
   */
  readonly nextLesson: { readonly id: number; readonly title: string; readonly kind: LessonKind } | null
}

/** One of this week's to-dos: what each kind holds in the server's flat record. */
type WeekTask =
  | { readonly kind: 'LEARN_DONE'; readonly title: string; readonly doneOn: string }
  | { readonly kind: 'LEARN_NEXT'; readonly title: string; readonly minutes: number; readonly lessonId: number }
  | {
      readonly kind: 'PRACTICE'
      readonly title: string
      readonly practiceType: PracticeType
      readonly practiceSetId: number
    }
  | { readonly kind: 'XIAOMEI'; readonly done: boolean }

export interface LearningPlan {
  readonly aiAssisted: boolean
  readonly goal: {
    readonly title: string | null
    readonly summary: string | null
    readonly reason: PlanReason
    readonly reasonText: string | null
    readonly picks: readonly PlanGoal[]
    /** The goals the plan works towards: the picks without "Not sure yet", or the reason's defaults. */
    readonly goals: readonly Exclude<PlanGoal, 'NOT_SURE'>[]
    readonly ownWords: string | null
    readonly deadline: string | null
    readonly focus: readonly PlanFocus[]
    readonly workOn: readonly string[]
  }
  readonly level: {
    readonly placed: MandarinLevel
    readonly target: MandarinLevel
    readonly knownSkills: readonly LevelCheckSkill[]
    readonly newSkills: readonly LevelCheckSkill[]
  }
  /**
   * Calendar weeks: `total` is the days from the plan's start to the goal date divided by 7, rounded up (from the daily
   * time when there is no date); `current` counts 7-day blocks from the start day; `content` is the week the last Learn
   * step should end.
   */
  readonly weeks: { readonly current: number; readonly total: number; readonly content: number }
  readonly progress: {
    readonly percent: number
    readonly status: PlanStatus
    /** Days of this plan week with the daily goal reached. */
    readonly daysThisWeek: number
    /** Days of this plan week before today without the daily goal reached. */
    readonly daysBehind: number
  }
  readonly pace: PlanPaceBand
  readonly steps: readonly PlanStep[]
  /** SparkBot's "By the end you should be able to"; empty in a rule-based plan. */
  readonly outcomes: readonly string[]
  readonly milestone: {
    readonly date: string
    readonly text: string | null
    readonly stepPositions: readonly number[]
  } | null
  readonly thisWeek: readonly WeekTask[]
  readonly kept: { readonly lessons: number; readonly practiceSets: number }
  /** The platform's day the plan was read on (yyyy-MM-dd): a goal date not after it has passed. */
  readonly today: string
  /**
   * The optional class the learner picked to take first ("Pick this first"), kept with the plan; null without a pick,
   * or once it no longer applies (joined, gone, or the only class left).
   */
  readonly pickedClassId: number | null
}

// At a glance

export interface PlanSummary {
  readonly goalTitle: string | null
  readonly reason: PlanReason
  readonly deadline: string | null
  /** The first milestone of a plan whose date is too close (null without one). */
  readonly milestoneDate: string | null
  readonly week: number
  readonly totalWeeks: number
  readonly percent: number
  readonly status: PlanStatus
  /** Days of this plan week before today without the daily goal reached ("You're 2 study days behind"). */
  readonly daysBehind: number
  readonly level: MandarinLevel
  readonly targetLevel: MandarinLevel
  readonly optionalClass: {
    readonly title: string
    readonly weekFrom: number
    /** The Learn unit the class is best taken after (the class's own setting); null when it has none. */
    readonly bestAfterUnit: number | null
  } | null
  readonly practice: { readonly type: PracticeType; readonly setId: number; readonly title: string } | null
  /** The platform's day the summary was read on (yyyy-MM-dd), matching `LearningPlan.today`. */
  readonly today: string
}

export interface PlanOverview {
  readonly plan: PlanSummary | null
}

export interface RetakeResult {
  readonly level: MandarinLevel
  readonly changed: boolean
}

// Home

export interface EmailConfirmation {
  readonly email: string
  readonly confirmed: boolean
}

/**
 * Home's next step. A level checkpoint is shown with its level: `level` is the level it closes and `stops` are that
 * level's units (`position` = unit number).
 */
export interface HomeNextLesson {
  readonly lessonId: number
  readonly title: string
  readonly kind: LessonKind
  /** The path level of its unit. */
  readonly level: number
  readonly unitNumber: number
  readonly unitTitle: string
  readonly unitTitleZh: string
  readonly minutes: number
  readonly xpReward: number
  /** The first hanzi of the key phrase, for the 田字格 tile. */
  readonly character: string | null
  readonly phrase: { readonly hanzi: string; readonly meaning: string } | null
  readonly stops: readonly {
    readonly id: number
    readonly position: number
    readonly state: StopState
    readonly current: boolean
  }[]
  readonly quickReview: boolean
  readonly unitMinutesLeft: number
  /**
   * Its unit is one of the plan's Learn units (false without a plan, and once the path goes on past them); for a
   * checkpoint, the plan has a unit behind it to do.
   */
  readonly inPlan: boolean
}

export interface HomeView {
  readonly firstName: string
  /** Today's XP against the daily goal; its last `week` day is the platform's today. */
  readonly today: TodaySummary
  /** No XP earned yet: the first day. */
  readonly firstTime: boolean
  readonly email: EmailConfirmation
  readonly setupState: 'NOT_STARTED' | 'SKIPPED' | 'COMPLETED'
  readonly next: HomeNextLesson | null
  readonly plan: PlanSummary | null
}
