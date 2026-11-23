import { readonly, ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { hintApi } from '@/api/learn'
import type { HintSettings } from '@/types/learn'

const settings = ref<HintSettings | null>(null)
const failed = ref(false)
let pending: Promise<void> | null = null
let loadedForSession: number | null = null

/** Clears the cached hint settings so the next reader fetches fresh rules (e.g. after a level-check retake). */
export function invalidateHintSettings(): void {
  settings.value = null
  failed.value = false
  pending = null
}

/**
 * The student's hint setting with the rule of their level, loaded once per sign-in and shared by every Practice
 * player; the Account page writes a changed setting back with `remember`. Another sign-in (a different student on
 * this browser) loads its own, and an answer for an earlier sign-in is dropped. A failed load leaves hints off for
 * this page (Practice works without them), sets `failed` for a page that says so, and tries again on the next use.
 */
export function useHintSettings() {
  const session = getAuthSessionVersion()
  if (session !== loadedForSession) {
    settings.value = null
    pending = null
    loadedForSession = session
  }

  function fetchSettings(): Promise<void> {
    if (pending) return pending
    failed.value = false
    const request: Promise<void> = hintApi
      .settings()
      .then((loaded) => {
        if (pending === request && getAuthSessionVersion() === session) settings.value = loaded
      })
      .catch(() => {
        // Without settings no hint is offered; the next player to mount asks again.
        if (pending === request && getAuthSessionVersion() === session) failed.value = true
      })
      .finally(() => {
        if (pending === request) pending = null
      })
    pending = request
    return request
  }

  if (!settings.value && !pending) void fetchSettings()

  function remember(changed: HintSettings, expectedSession = session): boolean {
    const currentSession = getAuthSessionVersion()
    if (currentSession !== expectedSession) return false
    // A saved setting supersedes an earlier GET even if its response is still in flight.
    pending = null
    failed.value = false
    loadedForSession = currentSession
    settings.value = changed
    return true
  }

  return { settings: readonly(settings), failed: readonly(failed), remember }
}
