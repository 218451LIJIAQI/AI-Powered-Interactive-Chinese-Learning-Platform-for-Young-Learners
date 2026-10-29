/** The goal while the student edits it (setup, "Edit my goal", "Update my goal"), and what is sent to the server. */
import type { GoalInput, LearningPlan, PlanFocus, PlanGoal, PlanPace, PlanReason } from '@/types/plan'

import { addDays, addMonths, isoDay } from './planFormat'

export type DeadlineChoice = '2w' | '1m' | '2m' | '3m' | '6m' | 'none' | 'date'

export const DEADLINE_CHOICES: readonly DeadlineChoice[] = ['2w', '1m', '2m', '3m', '6m', 'none', 'date']

const MAX_FOCUS = 2

export interface GoalDraft {
  reason: PlanReason | null
  reasonText: string
  goals: PlanGoal[]
  goalText: string
  deadlineChoice: DeadlineChoice | null
  customDate: string
  pace: PlanPace | null
  focus: PlanFocus[]
}

export function emptyDraft(): GoalDraft {
  return {
    reason: null,
    reasonText: '',
    goals: [],
    goalText: '',
    deadlineChoice: null,
    customDate: '',
    pace: null,
    focus: [],
  }
}

/** The plan's goal as a draft: its deadline becomes a picked date. */
export function draftFromPlan(plan: LearningPlan): GoalDraft {
  return {
    reason: plan.goal.reason,
    reasonText: plan.goal.reasonText ?? '',
    goals: [...plan.goal.picks],
    goalText: plan.goal.ownWords ?? '',
    deadlineChoice: plan.goal.deadline ? 'date' : 'none',
    customDate: plan.goal.deadline ?? '',
    pace: plan.pace.key,
    focus: [...plan.goal.focus],
  }
}

/** The deadline of a choice as an ISO date (null for no deadline, or an unpicked date). */
export function deadlineFor(choice: DeadlineChoice | null, customDate: string, today = new Date()): string | null {
  switch (choice) {
    case '2w':
      return isoDay(addDays(today, 14))
    case '1m':
      return isoDay(addMonths(today, 1))
    case '2m':
      return isoDay(addMonths(today, 2))
    case '3m':
      return isoDay(addMonths(today, 3))
    case '6m':
      return isoDay(addMonths(today, 6))
    case 'date':
      return customDate || null
    default:
      return null
  }
}

export function deadlineReady(draft: GoalDraft): boolean {
  return draft.deadlineChoice !== null && (draft.deadlineChoice !== 'date' || draft.customDate !== '')
}

export function goalInput(draft: GoalDraft): GoalInput {
  return {
    reason: draft.reason ?? 'OTHER',
    reasonText: draft.reason === 'OTHER' ? draft.reasonText.trim() || null : null,
    goals: [...draft.goals],
    goalText: draft.goalText.trim() || null,
    deadline: deadlineFor(draft.deadlineChoice, draft.customDate),
    pace: draft.pace ?? 'REGULAR',
    focus: [...draft.focus],
  }
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

/** Today plus one day: the earliest date a goal can have. */
export function earliestDeadline(today = new Date()): string {
  return isoDay(addDays(today, 1))
}

/** Two years ahead: the latest date the server accepts. */
export function latestDeadline(today = new Date()): string {
  return isoDay(addDays(today, 730))
}
