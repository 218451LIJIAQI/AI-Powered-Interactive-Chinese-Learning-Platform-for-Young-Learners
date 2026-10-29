import { defineStore } from 'pinia'

import { refreshAccessToken, setAccessToken } from '@/api/http'
import { authApi } from '@/api/services'
import { useUiStore } from '@/stores/ui'
import type { CurrentUserResponse, UserRole } from '@/types/api'

let bootstrapInFlight: Promise<void> | null = null

export const useAuthStore = defineStore('auth', {
  state: () => ({
    /** True while this tab holds an access token; the token itself lives in memory in api/http.ts only. */
    hasSession: false,
    currentUser: null as CurrentUserResponse | null,
    bootstrapped: false,
    loading: false,
  }),
  getters: {
    activeRole(state): UserRole | null {
      return state.currentUser?.roles[0] ?? null
    },
    isAuthenticated(state): boolean {
      return state.hasSession
    },
    /** No hard-coded English name: callers fall back to their translated default label. */
    displayName(state): string {
      return state.currentUser?.fullName ?? ''
    },
    isApprovedTeacher(state): boolean {
      return state.currentUser?.teacherProfile?.teacherStatus === 'APPROVED'
    },
    hasRole: (state) => (role: UserRole) => (state.currentUser?.roles ?? []).includes(role),
  },
  actions: {
    async bootstrap(): Promise<void> {
      if (this.bootstrapped) return
      // Two navigations during start-up share one session restore (one refresh and one /auth/me request).
      bootstrapInFlight ??= this.restoreSession().finally(() => {
        bootstrapInFlight = null
      })
      return bootstrapInFlight
    },
    async restoreSession(): Promise<void> {
      // The refresh cookie restores a session; refreshAccessToken() serialises it across tabs.
      if (await refreshAccessToken()) {
        this.hasSession = true
        try {
          this.currentUser = await authApi.me()
        } catch {
          this.clearSession()
        }
      }
      this.bootstrapped = true
    },
    async login(email: string, password: string, keepSignedIn = true): Promise<void> {
      this.loading = true

      try {
        const response = await authApi.login({ email, password, keepSignedIn })
        this.hasSession = true
        setAccessToken(response.token)

        try {
          this.currentUser = await authApi.me()
        } catch (reason) {
          this.clearSession()
          throw reason
        }

        // FRAUTH07: an explicit sign-in switches the interface to the account's preferred language (zh -> zh-CN).
        useUiStore().applyPreferredLanguage(this.currentUser.preferredLanguage)
      } finally {
        this.loading = false
      }
    },
    /**
     * The teacher status is read once at sign-in. A pending or rejected teacher re-reads the account before each
     * teaching page, so an approval given during the session unlocks the pages without a reload.
     */
    async refreshTeacherApproval(): Promise<void> {
      const user = this.currentUser
      if (!user?.roles.includes('TEACHER') || user.teacherProfile?.teacherStatus === 'APPROVED') return
      await this.refreshCurrentUser()
    },
    /** Re-reads the signed-in account (after a change made on another page, e.g. the teaching profile's name). */
    async refreshCurrentUser(): Promise<void> {
      const user = this.currentUser
      if (!this.hasSession || !user) return
      try {
        const refreshed = await authApi.me()
        // A sign-out (or another account signing in) while the request ran must not bring this account back.
        if (this.hasSession && this.currentUser?.id === user.id) this.currentUser = refreshed
      } catch {
        // Keep the current account; the page's own requests report any problem.
      }
    },
    /** Applies `PUT /users/me/profile` (FRAUTH07): refreshes the signed-in user and switches the UI language. */
    applyProfileUpdate(user: CurrentUserResponse): void {
      if (!this.hasSession) return
      this.currentUser = user
      useUiStore().applyPreferredLanguage(user.preferredLanguage)
    },
    /**
     * After a password change the server revokes every other session and returns a fresh access token for this
     * tab (the refresh cookie is rotated by the response). Replace the in-memory token so requests keep working.
     */
    replaceAccessToken(token: string): void {
      if (!this.hasSession || !token) return
      setAccessToken(token)
    },
    clearSession(): void {
      this.hasSession = false
      this.currentUser = null
      setAccessToken(null)
    },
    async logout(): Promise<void> {
      if (this.hasSession) {
        await authApi.logout().catch(() => undefined)
      }

      this.clearSession()
    },
  },
})
