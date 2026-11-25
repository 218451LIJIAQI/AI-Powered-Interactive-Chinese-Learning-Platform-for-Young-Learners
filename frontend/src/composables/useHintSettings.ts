import { readonly, ref } from 'vue'

import { getAccessToken, getAuthSessionVersion } from '@/api/http'
import { hintApi } from '@/api/learn'
import type { HintSettings } from '@/types/learn'

const settings = ref<HintSettings | null>(null)
const failed = ref(false)
const readonlySettings = readonly(settings)
const readonlyFailed = readonly(failed)
let pending: Promise<void> | null = null
let loadedForSession: number | null = null

/** Clears the cached hint settings so the next reader fetches fresh rules (e.g. after a level-check retake). */
export function invalidateHintSettings(): void {
  settings.value = null
  failed.value = false
  pending = null
}

function syncSession(session: number): void {
  if (session === loadedForSession) return
  invalidateHintSettings()
  loadedForSession = session
}

function fetchSettings(targetSession: number, allowRetry = true): Promise<void> {
  if (pending) return pending
  loadedForSession = targetSession
  failed.value = false
  // Without settings no hint is offered; the next player to mount asks again.
  const request = hintApi
    .settings()
    .catch(() => null)
    .then((loaded) => {
      if (pending !== request) return
      pending = null
      const current = getAuthSessionVersion()
      if (current === targetSession) {
        if (loaded !== null) settings.value = loaded
        else failed.value = true
      } else if (allowRetry && settings.value === null && getAccessToken() !== null) {
        // Same-account token rotation bumped the session version while this initial read was in flight.
        void fetchSettings(current, false)
      }
    })
  pending = request
  return request
}

function reload(): Promise<void> {
  const current = getAuthSessionVersion()
  syncSession(current)
  return fetchSettings(current)
}

function rememberSettings(changed: HintSettings, expectedSession: number): boolean {
  const currentSession = getAuthSessionVersion()
  if (currentSession !== expectedSession) return false
  // A saved setting supersedes an earlier GET even if its response is still in flight.
  pending = null
  failed.value = false
  loadedForSession = currentSession
  settings.value = changed
  return true
}

/**
 * The student's hint setting with the rule of their level, loaded once per sign-in and shared by every Practice
 * player; the Account page writes a changed setting back with `remember`. Another sign-in (a different student on
 * this browser) loads its own, and an answer for an earlier sign-in is dropped. A failed load leaves hints off for
 * this page (Practice works without them), sets `failed` for a page that says so, and tries again on the next use.
 */
export function useHintSettings() {
  const session = getAuthSessionVersion()
  syncSession(session)
  if (!settings.value) void fetchSettings(session)

  return {
    settings: readonlySettings,
    failed: readonlyFailed,
    remember: (changed: HintSettings, expectedSession = session) => rememberSettings(changed, expectedSession),
    reload,
  }
}
