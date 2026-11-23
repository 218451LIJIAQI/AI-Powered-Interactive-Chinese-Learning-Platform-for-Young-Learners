/** The goal while the learner edits it (setup, "Edit my goal", "Update my goal"), and what is sent to the server. */
import type { GoalInput, LearningPlan, PlanFocus, PlanGoal, PlanPace, PlanReason } from '@/types/plan'
import { shiftDay } from '@/utils/format'

/** "1–2 hours", the band most learners gave in Shen & Wu (2026): chosen until the learner picks another. */
export const DEFAULT_PACE: PlanPace = 'HOURS_1_TO_2'

const MAX_FOCUS = 2

/** The latest goal date the server accepts, in days after the platform's today (two years). */
const LATEST_DEADLINE_DAYS = 730

export interface GoalDraft {
  reason: PlanReason | null
  reasonText: string
  goals: PlanGoal[]
  goalText: string
  /** Undefined until chosen, null for “No date”, or a calendar date (yyyy-MM-dd). */
  deadline: string | null | undefined
  pace: PlanPace
  focus: PlanFocus[]
}

export function emptyDraft(): GoalDraft {
  return {
    reason: null,
    reasonText: '',
    goals: [],
    goalText: '',
    deadline: undefined,
    pace: DEFAULT_PACE,
    focus: [],
  }
}

/**
 * "Today" in the goal's rules is the platform's day (`today`, yyyy-MM-dd, as the plan and the setup options send it),
 * the one the server checks a goal date against: the browser's own day can be a day behind or ahead of it.
 * A goal date that is not after today has passed: the server plans no more towards it.
 */
export function deadlinePassed(deadline: string | null, today: string): boolean {
  return deadline !== null && deadline <= today
}

/**
 * The plan's goal as a draft. A date that has passed (on the plan's `today`) is left unpicked, so the learner chooses a
 * new date (or no date) before rebuilding.
 */
export function draftFromPlan(plan: LearningPlan): GoalDraft {
  const { deadline } = plan.goal
  return {
    reason: plan.goal.reason,
    reasonText: plan.goal.reasonText ?? '',
    goals: [...plan.goal.picks],
    goalText: plan.goal.ownWords ?? '',
    deadline: deadlinePassed(deadline, plan.today) ? undefined : deadline,
    pace: plan.pace.key,
    focus: [...plan.goal.focus],
  }
}

/** A date or "No date" is chosen. */
export function deadlineReady(draft: GoalDraft): boolean {
  return draft.deadline !== undefined
}

function goalFields(draft: GoalDraft) {
  return {
    reason: draft.reason,
    reasonText: draft.reason === 'OTHER' ? draft.reasonText.trim() || null : null,
    goals: draft.goals,
    goalText: draft.goalText.trim() || null,
    deadline: draft.deadline,
    pace: draft.pace,
    focus: draft.focus,
  }
}

/** Only a completed draft can be sent to the server. */
export function goalInput(draft: GoalDraft): GoalInput {
  const { reason, deadline, ...fields } = goalFields(draft)
  if (reason === null || deadline === undefined) throw new Error('A goal needs a reason and a deadline choice.')
  return { ...fields, reason, deadline }
}

/**
 * The two drafts ask for the same goal (the order of the picks aside). "Update my goal" opens on the plan's own goal and
 * offers "Rebuild my plan" only once something differs. "No date" counts as a change from a date that has passed.
 */
export function sameGoal(a: GoalDraft, b: GoalDraft): boolean {
  const key = (draft: GoalDraft) => {
    return JSON.stringify({
      ...goalFields(draft),
      goals: [...draft.goals].sort(),
      focus: [...draft.focus].sort(),
    })
  }
  return key(a) === key(b)
}

/** "Not sure yet" stands alone: picking it clears the others, and picking another goal clears it. */
export function toggledGoals(goals: readonly PlanGoal[], goal: PlanGoal): PlanGoal[] {
  if (goals.includes(goal)) return goals.filter((picked) => picked !== goal)
  if (goal === 'NOT_SURE') return ['NOT_SURE']
  return [...goals.filter((picked) => picked !== 'NOT_SURE'), goal]
}

/** Up to two focus skills; null when the limit stops a third. */
export function toggledFocus(focus: readonly PlanFocus[], picked: PlanFocus): PlanFocus[] | null {
  if (focus.includes(picked)) return focus.filter((item) => item !== picked)
  if (focus.length >= MAX_FOCUS) return null
  return [...focus, picked]
}

/** The platform's today plus one day: the earliest date a goal can have. */
export function earliestDeadline(today: string): string {
  return shiftDay(today, 1)
}

/** The latest date the server accepts. */
export function latestDeadline(today: string): string {
  return shiftDay(today, LATEST_DEADLINE_DAYS)
}
