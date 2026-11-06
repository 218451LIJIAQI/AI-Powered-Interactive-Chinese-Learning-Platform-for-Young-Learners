/**
 * Which optional class of a plan the student wants first ("Pick this first" on the road map). The API has no field for
 * it: the server orders a plan's classes itself. So the pick is the student's own preference in this browser: it orders
 * the road map and the weeks, and is dropped when a rebuilt plan no longer has that class.
 */
import { computed, ref, type Ref } from 'vue'

import type { LearningPlan, PlanStep } from '@/types/plan'

const STORAGE_KEY = 'mandarinspark.planClassPick'

function readPick(): number | null {
  try {
    const value = Number(window.localStorage.getItem(STORAGE_KEY))
    return Number.isInteger(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

function writePick(classId: number | null): void {
  try {
    if (classId === null) window.localStorage.removeItem(STORAGE_KEY)
    else window.localStorage.setItem(STORAGE_KEY, String(classId))
  } catch {
    // Storage off: the pick lasts until the page is left.
  }
}

/** The classes a student can still choose between: optional, not joined, and still available. */
export function optionalClassSteps(plan: LearningPlan): PlanStep[] {
  return plan.steps.filter((step) => step.kind === 'CLASS' && step.status === 'OPTIONAL' && !step.joined)
}

export function usePlanPick(plan: Readonly<Ref<LearningPlan | null>>) {
  const stored = ref<number | null>(readPick())

  /** Only a class of this plan, and only when there is a choice to make. */
  const picked = computed<number | null>(() => {
    const options = plan.value ? optionalClassSteps(plan.value) : []
    return options.length > 1 && options.some((step) => step.refId === stored.value) ? stored.value : null
  })

  function togglePick(classId: number): void {
    stored.value = stored.value === classId ? null : classId
    writePick(stored.value)
  }

  return { picked, togglePick }
}
