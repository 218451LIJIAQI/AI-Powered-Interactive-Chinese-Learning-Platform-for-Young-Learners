/**
 * Which optional class of a plan the learner wants first ("Pick this first" on the road map). The pick is kept with the
 * plan (the setup's draft too, which "Start my path" makes the plan), so Home shows the same class on any device
 * (S3.19/S3.20). It counts only while the plan offers that class among others.
 */
import { computed, onScopeDispose, ref, watch, type Ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { planApi } from '@/api/plan'
import type { LearningPlan, PlanStep } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'

/** The classes a student can still choose between. */
export function optionalClassSteps(plan: LearningPlan): PlanStep[] {
  return plan.steps.filter((step) => step.status === 'OPTIONAL')
}

/** Those classes in the order they come: a picked one takes the first place. */
export function optionalClassesInOrder(plan: LearningPlan, picked: number | null): PlanStep[] {
  return optionalClassSteps(plan).sort((a, b) => Number(b.refId === picked) - Number(a.refId === picked))
}

/**
 * The Learn unit a class is best taken after ("Best after Unit 4"): the class's own setting when the plan sends it,
 * else the last unit before the class in the plan.
 */
export function bestAfterUnit(plan: LearningPlan, step: PlanStep): number | null {
  if (step.bestAfterUnit != null) return step.bestAfterUnit
  for (let index = plan.steps.length - 1; index >= 0; index--) {
    const other = plan.steps[index]!
    if (other.kind === 'LEARN_UNIT' && other.position < step.position) return other.unitNumber
  }
  return null
}

/**
 * The pick shows at once and is saved behind it, one save at a time and in order; each answer is the plan as the
 * server has it. A failed save goes back to the plan's own pick and says so (`pickError`, else `failedMessage`). The
 * page passes its own message: a composable must not open a second local i18n scope in the page's component.
 */
export function usePlanPick(plan: Ref<LearningPlan | null>, failedMessage: () => string) {
  /** The pick on screen while its save runs; undefined once the plan has it. */
  const pending = ref<number | null | undefined>(undefined)
  const pickError = ref<string | null>(null)
  let latest = 0
  let planEpoch = 0
  let saving = Promise.resolve()
  let active = true
  let patching = false

  onScopeDispose(() => {
    active = false
    latest += 1
    planEpoch += 1
  })

  watch(
    plan,
    () => {
      if (patching) return
      planEpoch += 1
      latest += 1
      pending.value = undefined
      pickError.value = null
    },
    { flush: 'sync' },
  )

  /** Only a class of this plan, and only when there is a choice to make. */
  const picked = computed<number | null>(() => {
    const value = pending.value === undefined ? (plan.value?.pickedClassId ?? null) : pending.value
    const options = plan.value ? optionalClassSteps(plan.value) : []
    return options.length > 1 && options.some((step) => step.refId === value) ? value : null
  })

  function togglePick(classId: number): void {
    const next = picked.value === classId ? null : classId
    const request = ++latest
    const epoch = planEpoch
    const session = getAuthSessionVersion()
    pending.value = next
    pickError.value = null
    saving = saving.then(async () => {
      try {
        if (!active || request !== latest || epoch !== planEpoch || session !== getAuthSessionVersion()) return
        const saved = await planApi.pickClass(next)
        if (active && epoch === planEpoch && session === getAuthSessionVersion() && plan.value) {
          patching = true
          try {
            plan.value = { ...plan.value, pickedClassId: saved.pickedClassId }
          } finally {
            patching = false
          }
        }
      } catch (reason) {
        if (active && request === latest && epoch === planEpoch && session === getAuthSessionVersion()) {
          pickError.value = resolveApiError(reason, failedMessage())
        }
      } finally {
        if (active && request === latest && epoch === planEpoch) pending.value = undefined
      }
    })
  }

  return { picked, togglePick, pickError, whenSaved: () => saving }
}
