import { defineStore } from 'pinia'

import { authApi } from '@/api/services'
import { appEnv } from '@/config/env'
import { demoUsers } from '@/demo/data'
import type { CurrentUserResponse, UserRole } from '@/types/api'
import { safeGetItem, safeRemoveItem, safeSetItem, storageKeys } from '@/utils/storage'

function canUsePreviewMode() {
  return appEnv.useDemoData && appEnv.enablePreviewMode
}

function isUserRole(value: string | null): value is UserRole {
  return value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN'
}

function readPersistedToken() {
  const sessionToken = safeGetItem(storageKeys.token, window.sessionStorage)
  if (sessionToken) {
    return sessionToken
  }

  // One-time migration: older builds persisted bearer tokens in localStorage.
  // Read once so an existing signed-in user is not abruptly logged out during
  // upgrade, then immediately rely on session storage going forward.
  return safeGetItem(storageKeys.token)
}

function readPersistedPreviewRole() {
  if (!canUsePreviewMode()) {
    return null
  }

  const role = safeGetItem(storageKeys.previewRole, window.sessionStorage)
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
  safeRemoveItem(storageKeys.token, window.sessionStorage)

  if (token) {
    safeSetItem(storageKeys.token, token, window.sessionStorage)
  }
}

function persistPreviewRole(role: UserRole | null) {
  safeRemoveItem(storageKeys.previewRole)
  safeRemoveItem(storageKeys.previewRole, window.sessionStorage)

  if (role && canUsePreviewMode()) {
    safeSetItem(storageKeys.previewRole, role, window.sessionStorage)
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: readPersistedToken() as string | null,
    currentUser: null as CurrentUserResponse | null,
    previewRole: readPersistedPreviewRole(),
    bootstrapped: false,
    loading: false,
  }),
  getters: {
    activeRole(state): UserRole | null {
      return state.currentUser?.roles[0] ?? (canUsePreviewMode() ? state.previewRole : null) ?? null
    },
    isPreviewSession(state): boolean {
      return canUsePreviewMode() && !state.token && state.previewRole !== null
    },
    isAuthenticated(state): boolean {
      return Boolean(state.token)
    },
    displayName(state): string {
      return state.currentUser?.fullName ?? (state.previewRole && canUsePreviewMode() ? demoUsers[state.previewRole].fullName : 'Guest Learner')
    },
    hasRole: (state) => (role: UserRole) => (state.currentUser?.roles ?? []).includes(role) || (canUsePreviewMode() && state.previewRole === role),
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
        persistToken(this.token)
        try {
          this.currentUser = await authApi.me()
        } catch {
          this.clearSession()
        }
      } else if (canUsePreviewMode() && this.previewRole) {
        this.currentUser = demoUsers[this.previewRole]
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
