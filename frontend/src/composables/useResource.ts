import { computed, onMounted, ref, shallowRef } from 'vue'
import type { ComputedRef, Ref } from 'vue'

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

interface ResourceState<T> {
  readonly data: Ref<T | null>
  readonly error: Ref<string | null>
  readonly loading: Ref<boolean>
  readonly usingFallback: Ref<boolean>
  readonly hasData: ComputedRef<boolean>
  readonly reload: () => Promise<void>
}

export function useResource<T>(
  loader: () => Promise<T>,
  fallback?: FallbackFactory<T>,
  immediate = true,
): ResourceState<T> {
  const auth = useAuthStore()

  function readFallback(): T {
    if (fallback === undefined) {
      throw new Error('Fallback is not configured')
    }

    if (typeof fallback === 'function') {
      return (fallback as () => T)()
    }

    return fallback as T
  }

  const hasFallback = computed(() => fallback !== undefined)
  const canUseFallback = computed(
    () => hasFallback.value && appEnv.useDemoData && (appEnv.enablePreviewMode || auth.isPreviewSession),
  )
  const data = shallowRef<T | null>(canUseFallback.value ? readFallback() : null)
  const error = ref<string | null>(null)
  const loading = ref(immediate && !canUseFallback.value)
  const usingFallback = ref(canUseFallback.value)
  let executionSequence = 0

  function shouldUseFailureFallback(reason: unknown): boolean {
    if (!canUseFallback.value) return false

    const failure = reason as ApiFailure | null
    const status = failure?.response?.status
    return status === 404 || (status == null && Boolean(failure?.request))
  }

  async function execute(): Promise<void> {
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

      if (shouldUseFailureFallback(reason)) {
        if (data.value === null) {
          data.value = readFallback()
        }
        usingFallback.value = true
        error.value = null
      } else {
        error.value = resolveApiError(reason, 'Request failed')
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
