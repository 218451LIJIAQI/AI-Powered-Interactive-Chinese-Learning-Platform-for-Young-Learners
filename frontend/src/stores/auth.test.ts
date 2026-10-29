import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUserResponse } from '@/types/api'

const studentUser: CurrentUserResponse = {
  id: 1,
  email: 'student@example.com',
  fullName: 'Student User',
  roles: ['STUDENT'],
}

const mocks = vi.hoisted(() => ({
  setAccessToken: vi.fn(),
  refresh: vi.fn(),
  me: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return {
    // Spies on the real function, which also removes bearer tokens stored by older builds.
    setAccessToken: (token: string | null) => {
      mocks.setAccessToken(token)
      actual.setAccessToken(token)
    },
    // The cross-tab refresh itself is covered in api/http.test.ts; it resolves to the new token or null.
    refreshAccessToken: mocks.refresh,
  }
})

vi.mock('@/api/services', () => ({
  authApi: {
    me: mocks.me,
    login: mocks.login,
    logout: mocks.logout,
  },
}))

import { useAuthStore } from './auth'

describe('auth store token storage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.refresh.mockReset()
    mocks.refresh.mockResolvedValue(null)
    mocks.me.mockReset()
    mocks.login.mockReset()
    mocks.logout.mockReset()
  })

  it('clears legacy persisted bearer tokens and keeps the access token in memory only', () => {
    window.localStorage.setItem('mandarin-spark.token', 'legacy-local')
    window.sessionStorage.setItem('mandarin-spark.token', 'legacy-session')

    const auth = useAuthStore()
    auth.hasSession = true
    auth.clearSession()

    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })

  it('stays signed out when the refresh cookie does not restore a session', async () => {
    const auth = useAuthStore()
    await auth.bootstrap()

    expect(mocks.refresh).toHaveBeenCalledTimes(1)
    expect(auth.hasSession).toBe(false)
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.me).not.toHaveBeenCalled()
  })

  it('clears the refreshed access token when current-user hydration fails', async () => {
    mocks.refresh.mockResolvedValue('rotated-access-token')
    mocks.me.mockRejectedValue(new Error('me failed'))

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.hasSession).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
  })

  it('hydrates the current user after a successful cookie refresh', async () => {
    mocks.refresh.mockResolvedValue('rotated-access-token')
    mocks.me.mockResolvedValue(studentUser)

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.hasSession).toBe(true)
    expect(auth.currentUser).toEqual(studentUser)
    expect(auth.activeRole).toBe('STUDENT')
    expect(auth.isAuthenticated).toBe(true)
    // The refreshed token stays in http.ts memory: the store never clears it on the success path.
    expect(mocks.setAccessToken).not.toHaveBeenCalledWith(null)
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
  })

  it('presents the refresh cookie once when two navigations bootstrap at the same time', async () => {
    mocks.refresh.mockResolvedValue('rotated-access-token')
    mocks.me.mockResolvedValue(studentUser)

    const auth = useAuthStore()
    await Promise.all([auth.bootstrap(), auth.bootstrap()])

    // The second navigation reuses the first restore: one refresh and one /auth/me request.
    expect(mocks.refresh).toHaveBeenCalledTimes(1)
    expect(mocks.me).toHaveBeenCalledTimes(1)
    expect(auth.currentUser).toEqual(studentUser)
  })

  it('clears the session and any legacy bearer token even when backend logout fails', async () => {
    mocks.logout.mockRejectedValue(new Error('logout endpoint unavailable'))
    window.localStorage.setItem('mandarin-spark.token', 'legacy-local')
    window.sessionStorage.setItem('mandarin-spark.token', 'legacy-session')

    const auth = useAuthStore()
    auth.hasSession = true
    auth.currentUser = studentUser

    await auth.logout()

    expect(mocks.logout).toHaveBeenCalledOnce()
    expect(auth.hasSession).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })
})

describe('auth store account management (FRAUTH07, FRAUTH08)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('replaces the in-memory access token after a password change', () => {
    const auth = useAuthStore()
    auth.hasSession = true
    auth.currentUser = studentUser

    auth.replaceAccessToken('new-access-token')

    expect(auth.hasSession).toBe(true)
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith('new-access-token')
    expect(auth.currentUser).toEqual(studentUser)
  })

  it('ignores a token replacement without a signed-in session', () => {
    const auth = useAuthStore()
    auth.replaceAccessToken('new-access-token')
    expect(auth.hasSession).toBe(false)
    expect(mocks.setAccessToken).not.toHaveBeenCalled()
  })

  it('re-reads a pending teacher so an approval given during the session unlocks the teaching pages', async () => {
    const pendingTeacher: CurrentUserResponse = {
      ...studentUser,
      roles: ['TEACHER'],
      teacherProfile: { teacherStatus: 'PENDING', verificationState: 'IN_REVIEW' },
    }
    const approvedTeacher = {
      ...pendingTeacher,
      teacherProfile: { teacherStatus: 'APPROVED', verificationState: 'APPROVED' },
    }
    mocks.me.mockReset()
    mocks.me.mockResolvedValue(approvedTeacher)
    const auth = useAuthStore()
    auth.hasSession = true
    auth.currentUser = pendingTeacher

    await auth.refreshTeacherApproval()
    expect(auth.currentUser).toEqual(approvedTeacher)

    // An approved teacher (and any other account) is not re-read.
    await auth.refreshTeacherApproval()
    auth.currentUser = studentUser
    await auth.refreshTeacherApproval()
    expect(mocks.me).toHaveBeenCalledTimes(1)
  })

  it('applies a profile update to the current user and switches the interface language', () => {
    const auth = useAuthStore()
    auth.hasSession = true
    auth.currentUser = studentUser

    auth.applyProfileUpdate({ ...studentUser, fullName: 'Aina Rahman', preferredLanguage: 'ms' })

    expect(auth.currentUser?.fullName).toBe('Aina Rahman')
    expect(document.documentElement.lang).toBe('ms')
  })
})
