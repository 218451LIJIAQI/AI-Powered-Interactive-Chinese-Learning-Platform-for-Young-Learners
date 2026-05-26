/**
 * Pinia store for authentication state — token, current user profile,
 * and the optional read-only "preview role" used by the QA preview
 * deployment.
 *
 * <h2>Three session modes</h2>
 * <ul>
 *   <li><b>Authenticated:</b> {@code token} present, {@code currentUser}
 *       hydrated from {@code /api/v1/auth/me}, {@code previewRole}
 *       always null.</li>
 *   <li><b>Preview:</b> only available when both
 *       {@code VITE_USE_DEMO_DATA=true} and
 *       {@code VITE_ENABLE_PREVIEW_MODE=true}. A synthetic role
 *       (STUDENT / TEACHER / ADMIN) is selected from the role picker
 *       and the dashboards render against {@code demoUsers} fixtures.
 *       Preview cannot coexist with a real token.</li>
 *   <li><b>Anonymous:</b> neither token nor preview role; the public
 *       catalogue is the only accessible surface.</li>
 * </ul>
 *
 * <h2>Storage hardening</h2>
 * <p>The access token lives in memory only and is restored by calling
 * {@code /api/v1/auth/refresh} with the HttpOnly refresh-token cookie.
 * Legacy {@code localStorage}/{@code sessionStorage} bearer tokens are
 * cleared on every auth state change. Preview role remains
 * session-scoped because it is an explicit synthetic state that should
 * not silently persist.</p>
 *
 * <h2>Failure-tolerant logout</h2>
 * <p>{@code logout} falls back to local session clearing when the
 * backend is unreachable so sign-out stays responsive — the backend
 * still enforces true revocation via session-version when it can
 * be reached.</p>
 */
import { defineStore } from 'pinia'

import { setAccessToken } from '@/api/http'
import { authApi } from '@/api/services'
import { appEnv } from '@/config/env'
import { demoUsers } from '@/demo/data'
import { clearOfflineCaches } from '@/plugins/pwa'
import type { CurrentUserResponse, UserRole } from '@/types/api'
import { getSessionStorage, safeGetItem, safeRemoveItem, safeSetItem, storageKeys } from '@/utils/storage'

function canUsePreviewMode() {
  return appEnv.useDemoData && appEnv.enablePreviewMode
}

function isUserRole(value: string | null): value is UserRole {
  return value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN'
}

function readPersistedPreviewRole() {
  if (!canUsePreviewMode()) {
    return null
  }

  const role = safeGetItem(storageKeys.previewRole, getSessionStorage())
  if (isUserRole(role)) {
    return role
  }

  // Preview mode is intentionally ephemeral: it represents a synthetic
  // read-only role simulation, not a real authenticated session. Remove any
  // legacy localStorage value so preview access does not silently survive a
  // browser restart after the session-only hardening pass.
  safeRemoveItem(storageKeys.previewRole)
  return null
}

function persistToken(token: string | null) {
  safeRemoveItem(storageKeys.token)
  safeRemoveItem(storageKeys.token, getSessionStorage())
  setAccessToken(token)
}

function persistPreviewRole(role: UserRole | null) {
  safeRemoveItem(storageKeys.previewRole)
  safeRemoveItem(storageKeys.previewRole, getSessionStorage())

  if (role && canUsePreviewMode()) {
    safeSetItem(storageKeys.previewRole, role, getSessionStorage())
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null as string | null,
    currentUser: null as CurrentUserResponse | null,
    previewRole: readPersistedPreviewRole(),
    bootstrapped: false,
    loading: false,
  }),
  getters: {
    activeRole(state): UserRole | null {
      if (state.currentUser) {
        return state.currentUser.roles[0] ?? null
      }
      if (canUsePreviewMode() && !state.token) {
        return state.previewRole
      }
      return null
    },
    isPreviewSession(state): boolean {
      return canUsePreviewMode() && !state.token && state.previewRole !== null
    },
    isAuthenticated(state): boolean {
      return Boolean(state.token)
    },
    displayName(state): string {
      if (state.currentUser) {
        return state.currentUser.fullName
      }
      if (state.previewRole && canUsePreviewMode() && !state.token) {
        return demoUsers[state.previewRole].fullName
      }
      return 'Guest Learner'
    },
    hasRole: (state) => (role: UserRole) =>
      (state.currentUser?.roles ?? []).includes(role) ||
      (canUsePreviewMode() && !state.token && state.previewRole === role),
  },
  actions: {
    async bootstrap() {
      if (this.bootstrapped) return

      this.loading = true

      if (!canUsePreviewMode()) {
        this.previewRole = null
        persistPreviewRole(null)
      }

      if (this.token) {
        if (this.previewRole) {
          this.previewRole = null
          persistPreviewRole(null)
        }

        persistToken(this.token)
        try {
          this.currentUser = await authApi.me()
        } catch {
          this.clearSession()
        }
      } else {
        if (canUsePreviewMode()) {
          persistToken(null)
          this.currentUser = this.previewRole ? demoUsers[this.previewRole] : null
          this.bootstrapped = true
          this.loading = false
          return
        }

        try {
          const response = await authApi.refresh()
          this.token = response.token
          persistToken(response.token)
          this.previewRole = null
          persistPreviewRole(null)
          this.currentUser = await authApi.me()
        } catch {
          persistToken(null)
          if (canUsePreviewMode() && this.previewRole) {
            this.currentUser = demoUsers[this.previewRole]
          }
        }
      }

      this.bootstrapped = true
      this.loading = false
    },
    async login(email: string, password: string) {
      this.loading = true

      try {
        const response = await authApi.login({ email, password })
        this.token = response.token
        this.previewRole = null
        persistToken(response.token)
        persistPreviewRole(null)

        try {
          this.currentUser = await authApi.me()
        } catch (reason) {
          this.clearSession()
          throw reason
        }
      } finally {
        this.loading = false
      }
    },
    setPreviewRole(role: UserRole | null) {
      if (this.token) {
        this.previewRole = null
        persistPreviewRole(null)
        return
      }

      this.previewRole = role && canUsePreviewMode() ? role : null
      this.currentUser = this.previewRole ? demoUsers[this.previewRole] : null
      persistPreviewRole(this.previewRole)
    },
    clearSession() {
      this.token = null
      this.currentUser = null
      this.previewRole = null
      persistToken(null)
      persistPreviewRole(null)
      void clearOfflineCaches()
    },
    async logout() {
      if (this.token) {
        try {
          await authApi.logout()
        } catch {
          // Fall back to local session clearing when the backend is already
          // unavailable or the token has expired. This keeps sign-out
          // responsive for the current browser while the backend enforces
          // true token invalidation whenever it can be reached.
        }
      }

      this.clearSession()
    },
  },
})
