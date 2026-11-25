import { computed, ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { planApi } from '@/api/plan'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useResource } from '@/composables/useResource'
import type { PlanOptions } from '@/types/plan'

/**
 * Loads learning-plan setup/goal options via `useResource` and supports silent background refreshes (e.g. on platform
 * day rollover or after a 400 deadline rejection) with an explicit `refreshing` state, deduplicated in-flight refresh
 * requests, and auth-session isolation.
 */
export function usePlanOptions(immediate = true) {
  const resource = useResource(planApi.options, immediate)
  const isUnmounted = useIsUnmounted()
  const refreshing = ref(false)
  const busy = computed(() => resource.loading.value || refreshing.value)

  let refreshSeq = 0
  let inFlightRefresh: { session: number; promise: Promise<PlanOptions | null> } | null = null

  function refresh(): Promise<PlanOptions | null> {
    const session = getAuthSessionVersion()
    if (inFlightRefresh && inFlightRefresh.session === session) {
      return inFlightRefresh.promise
    }
    const seq = ++refreshSeq
    refreshing.value = true
    const promise = planApi
      .options()
      .then((fresh) => {
        if (isUnmounted() || seq !== refreshSeq || session !== getAuthSessionVersion()) return null
        resource.replace(fresh)
        return fresh
      })
      .catch(() => null)
      .finally(() => {
        if (inFlightRefresh?.promise === promise) inFlightRefresh = null
        if (!isUnmounted() && seq === refreshSeq) refreshing.value = false
      })
    inFlightRefresh = { session, promise }
    return promise
  }

  return {
    ...resource,
    refreshing,
    busy,
    refresh,
  }
}
