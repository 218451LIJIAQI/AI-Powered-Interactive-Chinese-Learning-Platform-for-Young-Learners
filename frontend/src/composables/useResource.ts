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
  const canUseFallback = computed(() => hasFallback.value && appEnv.useDemoData && auth.isPreviewSession)
  // Preview data is an explicit user-visible mode. We should never silently
  // swap live API failures for demo data during a normal anonymous session,
  // otherwise real backend outages look like working product surfaces.
  const data = ref<T | null>(canUseFallback.value ? readFallback() : null)
  const error = ref<string | null>(null)
  const loading = ref(immediate && !canUseFallback.value)
  const usingFallback = ref(canUseFallback.value)

  function shouldUseFailureFallback(reason: unknown) {
    if (!canUseFallback.value) return false

    const failure = reason as ApiFailure | null
    const status = failure?.response?.status
    return status === 404 || (status == null && Boolean(failure?.request))
  }

  async function execute() {
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
      data.value = await loader()
      usingFallback.value = false
    } catch (reason) {
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
      loading.value = false
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
