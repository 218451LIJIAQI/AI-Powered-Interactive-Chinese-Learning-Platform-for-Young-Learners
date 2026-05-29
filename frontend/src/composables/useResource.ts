/**
 * Reactive {@code data / loading / error / reload} helper for any
 * "load a single API resource into the page" pattern.
 *
 * <h2>Why a composable</h2>
 * <p>Every dashboard page has the same shape: declare a loader,
 * show a spinner while it runs, render data on success, surface a
 * user-friendly error on failure. Inlining that everywhere drifts
 * over time — this composable bakes in:</p>
 * <ul>
 *   <li>The {@code resolveApiError} translation so error messaging
 *       is consistent across the app.</li>
 *   <li>Preview-mode fallbacks: when the QA preview deployment is
 *       active and the API is unreachable or returns 404, the
 *       supplied {@code fallback} fixture is shown instead of an
 *       error so the demo flow stays usable.</li>
 *   <li>{@code immediate} toggle so pages with deferred fetches
 *       (e.g. fetch-on-tab-open) can opt out of the on-mount call.</li>
 * </ul>
 *
 * <h2>Why guarded fallbacks</h2>
 * <p>{@code canUseFallback} requires a configured fallback and the
 * explicit demo-preview build flags or an active preview session.
 * This prevents a real backend outage during a normal production
 * session from silently rendering demo data and masking an incident,
 * while keeping QA preview pages fast and network-clean.</p>
 */
import { computed, onMounted, ref } from 'vue'

import { appEnv } from '@/config/env'
import { useAuthStore } from '@/stores/auth'
import { resolveApiError } from '@/utils/apiError'

type FallbackFactory<T> = (() => T) | T
type ApiFailure = {
  response?: {
    status?: number
  }
  request?: unknown
}

export function useResource<T>(loader: () => Promise<T>, fallback?: FallbackFactory<T>, immediate = true) {
  const auth = useAuthStore()

  function readFallback() {
    if (typeof fallback === 'function') {
      return (fallback as () => T)()
    }

    return fallback as T
  }

  const hasFallback = computed(() => fallback !== undefined)
  const canUseFallback = computed(
    () => hasFallback.value && appEnv.useDemoData && (appEnv.enablePreviewMode || auth.isPreviewSession),
  )
  // Demo data remains gated behind explicit preview configuration. Normal
  // builds still surface backend outages instead of silently masking them.
  const data = ref<T | null>(canUseFallback.value ? readFallback() : null)
  const error = ref<string | null>(null)
  const loading = ref(immediate && !canUseFallback.value)
  const usingFallback = ref(canUseFallback.value)
  let executionSequence = 0

  function shouldUseFailureFallback(reason: unknown) {
    if (!canUseFallback.value) return false

    const failure = reason as ApiFailure | null
    const status = failure?.response?.status
    return status === 404 || (status == null && Boolean(failure?.request))
  }

  async function execute() {
    const sequence = ++executionSequence

    if (canUseFallback.value) {
      data.value = readFallback()
      usingFallback.value = true
      error.value = null
      loading.value = false
      return
    }

    loading.value = data.value === null
    error.value = null

    try {
      const result = await loader()
      if (sequence !== executionSequence) return
      data.value = result
      usingFallback.value = false
    } catch (reason) {
      if (sequence !== executionSequence) return
      const message = resolveApiError(reason, 'Request failed')
      error.value = message

      if (shouldUseFailureFallback(reason)) {
        if (data.value === null) {
          data.value = readFallback()
        }
        usingFallback.value = true
        error.value = null
      }
    } finally {
      if (sequence === executionSequence) {
        loading.value = false
      }
    }
  }

  onMounted(() => {
    if (immediate) {
      void execute()
    }
  })

  return {
    data,
    error,
    loading,
    usingFallback,
    hasData: computed(() => data.value !== null),
    reload: execute,
  }
}
