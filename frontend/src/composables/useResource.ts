import { computed, onMounted, ref, shallowRef } from 'vue'
import type { ComputedRef, Ref } from 'vue'

import { appEnv } from '@/config/env'
import { useAuthStore } from '@/stores/auth'
import { defaultRequestError, resolveApiError } from '@/utils/apiError'

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

/**
 * How a resource may use its demo fixture:
 * - `none`: always live data (no fixture, or the build is not a preview build);
 * - `immediate`: preview build with no real session (anonymous visitor or preview role) -> fixture, no request;
 * - `onUnreachable`: preview build with a real signed-in session -> live data; the fixture is used only when the
 *   API cannot be reached at all (network failure, no HTTP response). HTTP errors (4xx/5xx) are shown as errors.
 * Outside preview builds (VITE_USE_DEMO_DATA and VITE_ENABLE_PREVIEW_MODE both true) a real user never sees fixtures.
 */
export type FallbackMode = 'none' | 'immediate' | 'onUnreachable'

export interface FallbackRuleInput {
  hasFallback: boolean
  useDemoData: boolean
  enablePreviewMode: boolean
  hasRealSession: boolean
}

export function resolveFallbackMode(input: FallbackRuleInput): FallbackMode {
  if (!input.hasFallback || !input.useDemoData || !input.enablePreviewMode) return 'none'
  return input.hasRealSession ? 'onUnreachable' : 'immediate'
}

/** True when the request never reached the API (no HTTP response), e.g. the backend is down. */
export function isUnreachableFailure(reason: unknown): boolean {
  const failure = reason as ApiFailure | null
  return failure?.response?.status == null && Boolean(failure?.request)
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

  const fallbackMode = computed(() =>
    resolveFallbackMode({
      hasFallback: fallback !== undefined,
      useDemoData: appEnv.useDemoData,
      enablePreviewMode: appEnv.enablePreviewMode,
      hasRealSession: Boolean(auth.isAuthenticated),
    }),
  )
  const canUseFallback = computed(() => fallbackMode.value === 'immediate')
  const data = shallowRef<T | null>(canUseFallback.value ? readFallback() : null)
  const error = ref<string | null>(null)
  const loading = ref(immediate && !canUseFallback.value)
  const usingFallback = ref(canUseFallback.value)
  let executionSequence = 0

  function shouldUseFailureFallback(reason: unknown): boolean {
    return fallbackMode.value === 'onUnreachable' && isUnreachableFailure(reason)
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
        error.value = resolveApiError(reason, defaultRequestError())
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
