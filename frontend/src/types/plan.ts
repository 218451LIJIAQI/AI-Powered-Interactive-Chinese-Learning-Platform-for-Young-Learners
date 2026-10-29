/**
 * The learning plan module: the setup choices, the plan (built with SparkBot or from rules), the plan at a glance for
 * Home / Progress / Learn / Find classes, the teacher's read-only goal and the e-mail confirmation of a new account.
 * Texts SparkBot wrote are null in a rule-based plan; the pages write them from the goals instead.
 */
import type { SessionBrief } from '@/types/classes'
import type { LessonKind, LevelCheckSkill, MandarinLevel, PracticeType, StopState, TodaySummary } from '@/types/learn'

export type PlanSetupState = 'NOT_STARTED' | 'SKIPPED' | 'COMPLETED'
export type PlanReason =
  | 'TRAVEL'
  | 'WORK'
  | 'STUDY'
  | 'EVERYDAY'
  | 'FRIENDS_FAMILY'
  | 'CULTURE'
  | 'JUST_FOR_ME'
  | 'OTHER'
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
  | 'TALK_ABOUT_FAMILY'
  | 'ENJOY_MEDIA'
  | 'NOT_SURE'
export type PlanPace = 'LIGHT' | 'REGULAR' | 'SERIOUS' | 'INTENSE'
export type PlanFocus = 'SPEAKING' | 'LISTENING' | 'READING' | 'WRITING' | 'VOCABULARY' | 'CONVERSATION' | 'BALANCED'
export type PlanSelfLevel = 'NEW_TO_MANDARIN' | 'A_LITTLE' | 'THE_BASICS' | 'NOT_SURE'
export type PlanStepKind = 'LEARN_UNIT' | 'CLASS'
export type PlanStatus = 'STARTS_TODAY' | 'ON_TRACK' | 'BEHIND' | 'COMPLETE'
export type StepStatus = 'DONE' | 'IN_PROGRESS' | 'NEXT' | 'LATER' | 'IN_MY_CLASSES' | 'OPTIONAL' | 'UNAVAILABLE'
type TaskKind = 'LEARN_DONE' | 'LEARN_NEXT' | 'PRACTICE' | 'XIAOMEI'

// ===== Setup =====

export interface PlanOptions {
  readonly reasons: readonly { readonly key: PlanReason; readonly goals: readonly PlanGoal[] }[]
  readonly paces: readonly {
    readonly key: PlanPace
    readonly minutesPerDay: number
    readonly daysPerWeek: number
    readonly dailyGoalXp: number
  }[]
  readonly focuses: readonly PlanFocus[]
  readonly goalTextMax: number
}

/** The goal as the setup (or "Update my goal") sends it; `deadline` is an ISO date or null for no deadline. */
export interface GoalInput {
  reason: PlanReason
  reasonText: string | null
  goals: PlanGoal[]
  goalText: string | null
  deadline: string | null
  pace: PlanPace
  focus: PlanFocus[]
}

export interface LevelCheckAnswer {
  questionId: number
  optionId: number | null
}

export interface BuildPlanPayload {
  goal: GoalInput
  levelCheck: { selfLevel: PlanSelfLevel; skipped: boolean; answers: LevelCheckAnswer[] }
  language: string
}

// ===== The plan =====

export interface PlanStep {
  readonly position: number
  readonly kind: PlanStepKind
  readonly refId: number
  readonly title: string
  readonly titleZh: string | null
  readonly unitNumber: number | null
  readonly hskLevel: number | null
  readonly iconKey: string | null
  readonly lessonCount: number
  readonly completedCount: number
  readonly percent: number
  readonly status: StepStatus
  readonly joined: boolean
  readonly quickReview: boolean
  readonly weekFrom: number
  readonly weekTo: number
  /** SparkBot's "Why"; null in a rule-based plan, which explains the step with `goals`. */
  readonly reason: string | null
  readonly goals: readonly PlanGoal[]
  readonly teacherName: string | null
  readonly nextLesson: { readonly id: number; readonly title: string } | null
  readonly nextSessionAt: string | null
}

export interface WeekTask {
  readonly kind: TaskKind
  readonly title: string | null
  readonly done: boolean
  readonly doneOn: string | null
  readonly minutes: number | null
  readonly lessonId: number | null
  readonly practiceType: PracticeType | null
  readonly practiceSetId: number | null
}

export interface LearningPlan {
  readonly aiAssisted: boolean
  readonly goal: {
    readonly title: string | null
    readonly summary: string | null
    readonly reason: PlanReason
    readonly reasonText: string | null
    readonly picks: readonly PlanGoal[]
    /** The goals the plan works towards: the picks, or the reason's defaults. */
    readonly goals: readonly PlanGoal[]
    readonly ownWords: string | null
    readonly deadline: string | null
    readonly focus: readonly PlanFocus[]
    readonly workOn: readonly string[]
  }
  readonly level: PlanLevel
  readonly weeks: { readonly current: number; readonly total: number; readonly content: number }
  readonly progress: {
    readonly percent: number
    readonly status: PlanStatus
    readonly daysThisWeek: number
    readonly paceDays: number
  }
  readonly pace: {
    readonly key: PlanPace
    readonly minutesPerDay: number
    readonly daysPerWeek: number
    readonly dailyGoalXp: number
  }
  readonly steps: readonly PlanStep[]
  /** SparkBot's "By the end you should be able to"; empty in a rule-based plan. */
  readonly outcomes: readonly string[]
  readonly coverage: readonly {
    readonly goal: PlanGoal
    readonly learn: boolean
    readonly classId: number | null
    readonly classTitle: string | null
  }[]
  readonly milestone: {
    readonly date: string
    readonly text: string | null
    readonly stepPositions: readonly number[]
  } | null
  readonly thisWeek: readonly WeekTask[]
  readonly kept: { readonly lessons: number; readonly practiceSets: number; readonly classes: number }
}

export interface PlanLevel {
  readonly placed: MandarinLevel
  readonly target: MandarinLevel
  readonly headStart: boolean
  readonly knownSkills: readonly LevelCheckSkill[]
  readonly newSkills: readonly LevelCheckSkill[]
  /** Null when a brand-new student skipped the check. */
  readonly checkedAt: string | null
}

// ===== At a glance =====

export interface PlanSummary {
  readonly goalTitle: string | null
  readonly reason: PlanReason
  readonly deadline: string | null
  readonly week: number
  readonly totalWeeks: number
  readonly percent: number
  readonly status: PlanStatus
  readonly level: MandarinLevel
  readonly targetLevel: MandarinLevel
  readonly goalUnit: { readonly number: number; readonly title: string; readonly titleZh: string } | null
  readonly classSteps: readonly { readonly classId: number; readonly position: number }[]
  readonly optionalClass: { readonly classId: number; readonly title: string; readonly weekFrom: number } | null
  readonly firstStep: {
    readonly title: string
    readonly lessonsLeft: number
    readonly minutesLeft: number
    readonly quickReview: boolean
  } | null
  readonly practice: PracticeSuggestion | null
}

interface PracticeSuggestion {
  readonly type: PracticeType
  readonly setId: number
  readonly title: string
}

export interface PlanOverview {
  readonly setupState: PlanSetupState
  readonly plan: PlanSummary | null
}

export interface RetakeResult {
  readonly level: MandarinLevel
  readonly headStart: boolean
  readonly knownSkills: readonly LevelCheckSkill[]
  readonly newSkills: readonly LevelCheckSkill[]
  readonly answered: number
  readonly changed: boolean
}

/** What a teacher sees of a student's plan: never their own words or SparkBot's summary. */
export interface TeacherLearningGoal {
  readonly goalTitle: string | null
  readonly reason: PlanReason
  readonly deadline: string | null
  readonly focus: readonly PlanFocus[]
  readonly level: MandarinLevel
  readonly targetLevel: MandarinLevel
  readonly updatedAt: string
  readonly yourClasses: readonly { readonly classId: number; readonly title: string; readonly position: number }[]
  readonly stepCount: number
}

// ===== Home =====

export interface EmailConfirmation {
  readonly email: string
  readonly confirmed: boolean
}

export interface HomeNextLesson {
  readonly lessonId: number
  readonly title: string
  readonly kind: LessonKind
  readonly unitNumber: number
  readonly unitTitle: string
  readonly unitTitleZh: string
  readonly position: number
  readonly unitLessonCount: number
  readonly completedInUnit: number
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
}

export interface HomeView {
  readonly firstName: string
  readonly today: TodaySummary
  /** No XP earned yet: the first day. */
  readonly firstTime: boolean
  readonly email: EmailConfirmation
  readonly setupState: PlanSetupState
  readonly next: HomeNextLesson | null
  readonly liveToday: SessionBrief | null
  readonly plan: PlanSummary | null
}
