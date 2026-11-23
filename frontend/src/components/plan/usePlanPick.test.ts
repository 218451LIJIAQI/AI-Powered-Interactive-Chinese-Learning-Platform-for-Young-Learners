import { effectScope, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { learningPlan, planStep } from '@/test/planFixtures'
import type { LearningPlan } from '@/types/plan'

const mocks = vi.hoisted(() => ({
  pickClass: vi.fn(),
}))

vi.mock('@/api/plan', () => ({
  planApi: { pickClass: mocks.pickClass },
}))

import { usePlanPick } from './usePlanPick'

function makePlan(pickedClassId: number | null = null): LearningPlan {
  return learningPlan({
    pickedClassId,
    steps: [
      planStep({ position: 1, kind: 'CLASS', refId: 10, status: 'OPTIONAL', joined: false }),
      planStep({ position: 2, kind: 'CLASS', refId: 20, status: 'OPTIONAL', joined: false }),
    ],
  })
}

describe('usePlanPick', () => {
  beforeEach(() => {
    mocks.pickClass.mockReset()
  })

  it('preserves pickError when a follow-up pick fails right after an earlier pick patched the plan', async () => {
    const first = deferred<LearningPlan>()
    const second = deferred<LearningPlan>()
    mocks.pickClass.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const plan = ref<LearningPlan | null>(makePlan(null))
    const scope = effectScope()
    const state = scope.run(() => usePlanPick(plan, () => 'Could not save pick.'))!

    state.togglePick(10)
    await Promise.resolve()
    state.togglePick(20)
    expect(state.picked.value).toBe(20)

    first.resolve(makePlan(10))
    await nextTick()
    second.reject(new Error('network'))
    await state.whenSaved()
    await nextTick()

    expect(mocks.pickClass).toHaveBeenCalledTimes(2)
    expect(state.pickError.value).toBe('Could not save pick.')
    expect(state.picked.value).toBe(10)
    scope.stop()
  })

  it('coalesces intermediate rapid toggles before the first request starts', async () => {
    mocks.pickClass.mockResolvedValueOnce(makePlan(20))
    const plan = ref<LearningPlan | null>(makePlan(null))
    const scope = effectScope()
    const state = scope.run(() => usePlanPick(plan, () => 'Could not save pick.'))!

    state.togglePick(10)
    state.togglePick(20)
    await state.whenSaved()

    expect(mocks.pickClass).toHaveBeenCalledExactlyOnceWith(20)
    expect(plan.value?.pickedClassId).toBe(20)
    scope.stop()
  })

  it('ignores an in-flight pick response after the plan is replaced externally', async () => {
    const inFlight = deferred<LearningPlan>()
    mocks.pickClass.mockReturnValueOnce(inFlight.promise)

    const plan = ref<LearningPlan | null>(makePlan(null))
    const scope = effectScope()
    const state = scope.run(() => usePlanPick(plan, () => 'Could not save pick.'))!

    state.togglePick(10)
    expect(state.picked.value).toBe(10)
    await Promise.resolve()
    expect(mocks.pickClass).toHaveBeenCalledExactlyOnceWith(10)

    // Parent replaces the plan (e.g. after updating goal) while pickClass(10) is still in flight.
    plan.value = makePlan(20)
    expect(state.picked.value).toBe(20)

    inFlight.resolve(makePlan(10))
    await state.whenSaved()
    await nextTick()

    expect(plan.value?.pickedClassId).toBe(20)
    expect(state.picked.value).toBe(20)
    scope.stop()
  })
})
