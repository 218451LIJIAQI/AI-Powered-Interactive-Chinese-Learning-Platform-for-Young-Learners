import { onUnmounted, ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import type { GoalDraft } from '@/components/plan/goalDraft'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import type { LearningPlan } from '@/types/plan'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'

/** The building screen stays at least this long, so its steps can be read. */
const MIN_BUILDING_MS = 2400

export interface BuiltPlan {
  readonly plan: LearningPlan
  readonly draft?: GoalDraft
}

interface PlanBuilderOptions {
  readonly isBuilding: () => boolean
  readonly canStart: () => boolean
  readonly beforeRequest: () => Promise<unknown>
  readonly onStart: () => void
  readonly onBuilt: (built: BuiltPlan) => void
  readonly fallbackError: () => string
}

/**
 * Runs a learning-plan build or goal rebuild while enforcing the minimum reading delay, auth-session isolation,
 * atomic state commitment, and clean timer/promise teardown when the learner skips mid-build.
 */
export function usePlanBuilder(options: PlanBuilderOptions) {
  const isUnmounted = useIsUnmounted()
  const buildError = ref<string | null>(null)
  const buildErrorStatus = ref<number | null>(null)

  let lastRequest: (() => Promise<BuiltPlan>) | null = null
  let pendingBuild: Promise<BuiltPlan | null> | null = null
  let buildTimer: ReturnType<typeof setTimeout> | null = null
  let buildTimerDone: (() => void) | null = null

  function clearBuildTimer(): void {
    if (buildTimer !== null) {
      clearTimeout(buildTimer)
      buildTimer = null
    }
    const done = buildTimerDone
    buildTimerDone = null
    done?.()
  }

  onUnmounted(clearBuildTimer)

  async function run(request: () => Promise<BuiltPlan>): Promise<void> {
    if (pendingBuild || !options.canStart()) return
    lastRequest = request
    buildError.value = null
    buildErrorStatus.value = null
    options.onStart()

    const started = Date.now()
    const session = getAuthSessionVersion()
    const stillBuilding = () => !isUnmounted() && options.isBuilding() && session === getAuthSessionVersion()

    try {
      pendingBuild = options.beforeRequest().then(() => (stillBuilding() ? request() : null))
      const built = await pendingBuild
      if (!built || !stillBuilding()) return

      const waitMs = Math.max(0, MIN_BUILDING_MS - (Date.now() - started))
      if (waitMs > 0) {
        await new Promise<void>((resolve) => {
          buildTimerDone = resolve
          buildTimer = setTimeout(() => {
            buildTimer = null
            buildTimerDone = null
            resolve()
          }, waitMs)
        })
      }
      if (!stillBuilding()) return
      options.onBuilt(built)
    } catch (reason) {
      if (stillBuilding()) {
        buildError.value = resolveApiError(reason, options.fallbackError())
        buildErrorStatus.value = apiErrorStatus(reason)
      }
    } finally {
      pendingBuild = null
    }
  }

  function retry(): void {
    if (lastRequest) void run(lastRequest)
  }

  async function abortAndAwait(): Promise<BuiltPlan | null> {
    clearBuildTimer()
    if (!pendingBuild) return null
    return pendingBuild.catch(() => null)
  }

  return { buildError, buildErrorStatus, run, retry, abortAndAwait }
}
