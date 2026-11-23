import { onDeactivated, onScopeDispose, readonly, ref } from 'vue'

interface Options {
  /**
   * Reads the permission at each request instead of once, and shows our step when the browser cannot report it either
   * (the lesson's Say step).
   */
  checkOnRequest?: boolean
}

async function queryPermission(): Promise<PermissionStatus | null> {
  try {
    return (await navigator.permissions?.query({ name: 'microphone' as PermissionName })) ?? null
  } catch {
    return null
  }
}

/**
 * Our own microphone step before the browser's prompt: `request` shows it (`asking`, answered with `confirm` or
 * `cancel`) while the browser has still to ask, and otherwise calls `start` at once, so a granted or blocked microphone
 * goes straight to the activity. The permission is read once and followed while the component lives (the AI Helper's
 * tabs), unless `checkOnRequest`.
 */
export function useMicPermission(start: () => void | Promise<void>, { checkOnRequest = false }: Options = {}) {
  const asking = ref(false)
  /** Allowed in our step (or reported granted on a `checkOnRequest` check): our step is not shown again. */
  let allowed = false
  let cached: PermissionStatus | null = null
  let initialCheck: Promise<void> | undefined
  if (!checkOnRequest && navigator.permissions) {
    initialCheck = queryPermission().then((result) => {
      cached = result
      initialCheck = undefined
    })
  }
  let requestVersion = 0

  function invalidateRequest(): void {
    requestVersion++
  }
  onDeactivated(invalidateRequest)
  onScopeDispose(invalidateRequest)

  async function request(): Promise<void> {
    if (!allowed) {
      const version = ++requestVersion
      if (initialCheck) await initialCheck
      const permission = checkOnRequest ? await queryPermission() : cached
      if (version !== requestVersion) return
      if (permission?.state === 'prompt' || (!permission && checkOnRequest)) {
        asking.value = true
        return
      }
      allowed = checkOnRequest && permission?.state === 'granted'
    }
    return start()
  }

  function confirm(): void {
    invalidateRequest()
    asking.value = false
    allowed = true
    void start()
  }

  function cancel(): void {
    invalidateRequest()
    asking.value = false
  }

  return { asking: readonly(asking), request, confirm, cancel }
}
