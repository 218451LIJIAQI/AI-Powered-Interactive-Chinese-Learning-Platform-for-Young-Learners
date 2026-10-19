import { defineStore } from 'pinia'

import { setAccessToken } from '@/api/http'
import { authApi } from '@/api/services'
import { appEnv } from '@/config/env'
import { demoUsers } from '@/demo/users'
import { clearOfflineCaches } from '@/plugins/pwa'
import { useUiStore } from '@/stores/ui'
import type { CurrentUserResponse, UserRole } from '@/types/api'
import { getSessionStorage, safeGetItem, safeRemoveItem, safeSetItem, storageKeys } from '@/utils/storage'

function canUsePreviewMode(): boolean {
  return appEnv.useDemoData && appEnv.enablePreviewMode
}

function isUserRole(value: string | null): value is UserRole {
  return value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN'
}

function readPersistedPreviewRole(): UserRole | null {
  if (!canUsePreviewMode()) {
    return null
  }

  const sessionStorage = getSessionStorage()
  const role = safeGetItem(storageKeys.previewRole, sessionStorage)
  if (isUserRole(role)) {
    return role
  }

  safeRemoveItem(storageKeys.previewRole)
  safeRemoveItem(storageKeys.previewRole, sessionStorage)
  return null
}

function persistToken(token: string | null): void {
  const sessionStorage = getSessionStorage()

  safeRemoveItem(storageKeys.token)
  safeRemoveItem(storageKeys.token, sessionStorage)
  setAccessToken(token)
}

function persistPreviewRole(role: UserRole | null): void {
  const sessionStorage = getSessionStorage()

  safeRemoveItem(storageKeys.previewRole)
  safeRemoveItem(storageKeys.previewRole, sessionStorage)

  if (role && canUsePreviewMode()) {
    safeSetItem(storageKeys.previewRole, role, sessionStorage)
  }
}

function clearPersistedPreviewRole(): void {
  persistPreviewRole(null)
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
      // No hard-coded English name: callers fall back to their translated default label.
      return ''
    },
    hasRole: (state) => (role: UserRole) =>
      (state.currentUser?.roles ?? []).includes(role) ||
      (canUsePreviewMode() && !state.token && state.previewRole === role),
  },
  actions: {
    async bootstrap(): Promise<void> {
      if (this.bootstrapped) return

      this.loading = true

      if (!canUsePreviewMode()) {
        this.previewRole = null
        clearPersistedPreviewRole()
      }

      if (this.token) {
        if (this.previewRole) {
          this.previewRole = null
          clearPersistedPreviewRole()
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
          if (this.previewRole) {
            this.currentUser = demoUsers[this.previewRole]
          }
          this.bootstrapped = true
          this.loading = false
          return
        }

        try {
          const response = await authApi.refresh()
          this.token = response.token
          persistToken(response.token)
          this.previewRole = null
          clearPersistedPreviewRole()
          try {
            this.currentUser = await authApi.me()
          } catch {
            this.clearSession()
          }
        } catch {
          persistToken(null)
        }
      }

      this.bootstrapped = true
      this.loading = false
    },
    async login(email: string, password: string): Promise<void> {
      this.loading = true

      try {
        const response = await authApi.login({ email, password })
        this.token = response.token
        this.previewRole = null
        persistToken(response.token)
        clearPersistedPreviewRole()

        try {
          this.currentUser = await authApi.me()
        } catch (reason) {
          this.clearSession()
          throw reason
        }

        // D18: an explicit sign-in switches the interface to the account's preferred language (legacy zh -> zh-CN).
        useUiStore().applyPreferredLanguage(this.currentUser.preferredLanguage)
      } finally {
        this.loading = false
      }
    },
    /** Applies `PUT /users/me/profile` (D12): refreshes the signed-in user and switches the UI language. */
    applyProfileUpdate(user: CurrentUserResponse): void {
      if (!this.token) return
      this.currentUser = user
      useUiStore().applyPreferredLanguage(user.preferredLanguage)
    },
    /**
     * After a password change the server revokes every other session and returns a fresh access token for this
     * tab (the refresh cookie is rotated by the response). Replace the in-memory token so requests keep working.
     */
    replaceAccessToken(token: string): void {
      if (!this.token || !token) return
      this.token = token
      persistToken(token)
    },
    setPreviewRole(role: UserRole | null): void {
      if (this.token) {
        this.previewRole = null
        clearPersistedPreviewRole()
        return
      }

      this.previewRole = role && canUsePreviewMode() ? role : null
      this.currentUser = this.previewRole ? demoUsers[this.previewRole] : null
      persistPreviewRole(this.previewRole)
    },
    clearSession(): void {
      this.token = null
      this.currentUser = null
      this.previewRole = null
      persistToken(null)
      clearPersistedPreviewRole()
      void clearOfflineCaches()
    },
    async logout(): Promise<void> {
      if (this.token) {
        await authApi.logout().catch(() => undefined)
      }

      this.clearSession()
    },
  },
})
