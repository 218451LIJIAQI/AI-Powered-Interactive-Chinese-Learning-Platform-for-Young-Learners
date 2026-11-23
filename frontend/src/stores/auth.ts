import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { CanceledError } from 'axios'

import {
  getAccessToken,
  getAuthSessionVersion,
  invalidateAuthSession,
  refreshAccessToken,
  setAccessToken,
} from '@/api/http'
import { accountOpsApi } from '@/api/ops'
import { authApi } from '@/api/services'
import { applyPreferredLanguage } from '@/plugins/i18n'
import type { AppLanguage, CurrentUserResponse, UserRole } from '@/types/api'
import { announceSessionOwner, sessionOwner } from '@/utils/storage'

interface LanguageChoice {
  locale: AppLanguage
  sessionVersion: number
}

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUserResponse | null>(null)
  // A token alone is insufficient until /auth/me confirms the account.
  const isAuthenticated = computed(() => currentUser.value !== null)
  const bootstrapped = ref(false)
  const loading = ref(false)
  /**
   * The address of the account whose session ended in this tab without a sign-out (it expired, or another tab signed
   * in): the sign-in page that follows starts with it. Kept in memory only; a sign-out forgets it.
   */
  const endedSessionEmail = ref<string | null>(null)

  // Scoped to the Pinia store instance so separate apps or test runs never share in-flight promises.
  let restoreInFlight: Promise<void> | null = null
  let queuedLanguage: LanguageChoice | null = null
  let languageSave: Promise<void> | null = null
  // Increments only on actual session boundaries (clear/login/restore), never on in-place token rotation.
  let sessionGeneration = 0

  const roles = computed<readonly UserRole[]>(() => currentUser.value?.roles ?? [])
  const activeRole = computed<UserRole | null>(() => roles.value[0] ?? null)

  function clearSession(): void {
    sessionGeneration += 1
    if (currentUser.value) endedSessionEmail.value = currentUser.value.email
    currentUser.value = null
    loading.value = false
    bootstrapped.value = true
    restoreInFlight = null
    queuedLanguage = null
    setAccessToken(null)
  }

  /**
   * Ends the session here and in the browser's other tabs, which share the refresh cookie: after a sign-out, or when
   * the server has ended the account's sessions itself (the account deleted, its password reset, its e-mail changed).
   * Without the announcement the other tabs would go on showing the account until their next refresh fails.
   */
  function clearSessionInAllTabs(): void {
    clearSession()
    // Signed out on purpose (or the account changed): the next sign-in starts empty, as on a shared computer.
    endedSessionEmail.value = null
    announceSessionOwner(null)
  }

  async function runRestore(): Promise<void> {
    // The refresh cookie restores a session; refreshAccessToken() serialises it across tabs.
    const version = getAuthSessionVersion()
    // On startup an unreachable server leaves the tab signed out. The maintenance page can retry.
    const token = await refreshAccessToken().catch(() => null)
    if (version !== getAuthSessionVersion()) return
    if (token) {
      try {
        const user = await authApi.me()
        if (version === getAuthSessionVersion()) {
          const owner = sessionOwner()
          if (owner === null || (typeof owner === 'number' && owner !== user.id)) {
            clearSession()
            if (owner === null) endedSessionEmail.value = null
          } else {
            currentUser.value = user
            sessionGeneration += 1
          }
        }
      } catch {
        if (version === getAuthSessionVersion()) clearSession()
      }
    }
    bootstrapped.value = true
  }

  async function restoreSession(): Promise<void> {
    // Concurrent navigations or maintenance retries share one refresh and one /auth/me hydration.
    if (!restoreInFlight) {
      const pending = runRestore().finally(() => {
        if (restoreInFlight === pending) restoreInFlight = null
      })
      restoreInFlight = pending
    }
    return restoreInFlight
  }

  async function bootstrap(): Promise<void> {
    if (bootstrapped.value) return
    return restoreSession()
  }

  async function login(email: string, password: string, keepSignedIn: boolean): Promise<void> {
    clearSession()
    let version = getAuthSessionVersion()
    loading.value = true

    try {
      const response = await authApi.login({ email, password, keepSignedIn })
      if (version !== getAuthSessionVersion()) throw new CanceledError('The sign-in session has ended.')
      setAccessToken(response.token)
      version = getAuthSessionVersion()

      const user = await authApi.me()
      if (version !== getAuthSessionVersion()) throw new CanceledError('The sign-in session has ended.')
      currentUser.value = user
      endedSessionEmail.value = null
      announceSessionOwner(user.id)

      // FRAUTH07: an explicit sign-in switches the interface to the account's preferred language.
      applyPreferredLanguage(user.preferredLanguage)
    } catch (reason) {
      if (version !== getAuthSessionVersion()) throw new CanceledError('The sign-in session has ended.')
      clearSession()
      throw reason
    } finally {
      if (version === getAuthSessionVersion()) loading.value = false
    }
  }

  /** A name save can arrive after a language switch or a new sign-in. */
  function applySavedName(user: CurrentUserResponse, expectedVersion: number): void {
    if (expectedVersion === getAuthSessionVersion() && currentUser.value?.id === user.id) {
      currentUser.value = { ...currentUser.value, fullName: user.fullName }
    }
  }

  async function persistLanguageChoice({ locale, sessionVersion }: LanguageChoice): Promise<void> {
    const user = currentUser.value
    if (!user || user.preferredLanguage === locale || sessionVersion !== getAuthSessionVersion()) return

    // The switch applies to this tab immediately. A failed account save can be retried by the next choice.
    const saved = await accountOpsApi.updateProfile({ preferredLanguage: locale }).catch(() => null)
    if (!saved || sessionVersion !== getAuthSessionVersion() || currentUser.value?.id !== saved.id) return

    // A name saved meanwhile on the Account page stays.
    currentUser.value = { ...currentUser.value, preferredLanguage: saved.preferredLanguage }
  }

  /** Serialize language saves and coalesce pending choices to the last selection in the current session. */
  async function savePreferredLanguage(locale: AppLanguage): Promise<void> {
    if (!isAuthenticated.value) return
    queuedLanguage = { locale, sessionVersion: getAuthSessionVersion() }
    if (languageSave) return languageSave

    const saveQueuedChoices = async () => {
      try {
        while (queuedLanguage) {
          const choice = queuedLanguage
          queuedLanguage = null
          await persistLanguageChoice(choice)
        }
      } finally {
        languageSave = null
      }
    }
    languageSave = saveQueuedChoices()
    return languageSave
  }

  /**
   * After a password change the server revokes every other session and returns a fresh access token for this
   * tab (the refresh cookie is rotated by the response). Replace the in-memory token so requests keep working.
   */
  function replaceAccessToken(token: string, expectedVersion: number): boolean {
    if (!isAuthenticated.value || expectedVersion !== getAuthSessionVersion()) return false
    setAccessToken(token)
    return true
  }

  /**
   * Whether the refresh cookie still opens a session, after the server ended an account's sessions that may not be
   * this browser's (a confirmed e-mail change link can belong to another account than the one signed in).
   */
  async function hasLiveSession(): Promise<boolean> {
    return (await refreshAccessToken()) !== null
  }

  async function logout(): Promise<boolean> {
    const version = getAuthSessionVersion()
    const generation = sessionGeneration
    const signingOutUserId = currentUser.value?.id ?? null

    function announceSignOutIfStillOwner(): void {
      const owner = sessionOwner()
      if (owner === undefined || owner === signingOutUserId) {
        announceSessionOwner(null)
      }
    }

    function finishAfterSessionChange(): boolean {
      if (isAuthenticated.value || loading.value) return false
      // If a concurrent background 401 expired the session while logout was in flight (and no new login started),
      // still honour the user's explicit sign-out by clearing the remembered email and notifying other tabs unless
      // another tab already claimed the browser for a different account.
      endedSessionEmail.value = null
      announceSignOutIfStillOwner()
      return true
    }

    if (isAuthenticated.value || getAccessToken() !== null) {
      await authApi.logout().catch(() => undefined)
    }
    if (generation !== sessionGeneration) return finishAfterSessionChange()

    // An in-place password change rotated the token without switching sessions: revoke the rotated session too.
    if (version !== getAuthSessionVersion() && (isAuthenticated.value || getAccessToken() !== null)) {
      await authApi.logout().catch(() => undefined)
      if (generation !== sessionGeneration) return finishAfterSessionChange()
    }

    clearSession()
    endedSessionEmail.value = null
    announceSignOutIfStillOwner()
    return true
  }

  /**
   * Another tab of this browser signed in or out (`storage` event of `storageKeys.sessionOwner`, wired in main.ts).
   * The tabs share one refresh cookie, and sign-out ends the browser's shared session, so a tab that
   * still shows the previous account ends its session: otherwise its next refresh would act as whoever signed in
   * elsewhere while it shows (and writes for) this account. The same account signing in again changes nothing. A
   * sign-out there (or an account change, announced the same way) forgets the address here too, as a sign-out in this
   * tab does; another account signing in leaves it for the sign-in page.
   */
  function followOtherTabSession(announcement: string | null): void {
    const userId = sessionOwner(announcement)
    if (userId === undefined) return
    if (currentUser.value && userId !== currentUser.value.id) {
      // The invalidation event ends the session synchronously (main.ts), which keeps the address of the account.
      invalidateAuthSession(getAuthSessionVersion())
    }
    if (userId === null) endedSessionEmail.value = null
  }

  return {
    isAuthenticated,
    currentUser,
    bootstrapped,
    loading,
    endedSessionEmail,
    roles,
    activeRole,
    bootstrap,
    restoreSession,
    login,
    applySavedName,
    savePreferredLanguage,
    replaceAccessToken,
    clearSession,
    clearSessionInAllTabs,
    hasLiveSession,
    logout,
    followOtherTabSession,
  }
})
