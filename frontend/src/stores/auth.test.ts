import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthResponse, CurrentUserResponse } from '@/types/api'

const studentUser: CurrentUserResponse = {
  id: 1,
  email: 'student@example.com',
  fullName: 'Student User',
  roles: ['STUDENT'],
}

const teacherPreviewUser: CurrentUserResponse = {
  id: 2,
  email: 'preview.teacher@example.com',
  fullName: 'Preview Teacher',
  roles: ['TEACHER'],
}

const mocks = vi.hoisted(() => ({
  setAccessToken: vi.fn(),
  refresh: vi.fn(),
  me: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  appEnv: {
    useDemoData: false,
    enablePreviewMode: false,
  },
  teacherPreviewUser: {
    id: 2,
    email: 'preview.teacher@example.com',
    fullName: 'Preview Teacher',
    roles: ['TEACHER'] as const,
  },
}))

vi.mock('@/api/http', () => ({
  setAccessToken: (token: string | null) => mocks.setAccessToken(token),
}))

vi.mock('@/config/env', () => ({
  appEnv: mocks.appEnv,
}))

vi.mock('@/demo/data', () => ({
  demoUsers: {
    STUDENT: {
      id: 1,
      email: 'preview.student@example.com',
      fullName: 'Preview Student',
      roles: ['STUDENT'] as const,
    },
    TEACHER: mocks.teacherPreviewUser,
    ADMIN: {
      id: 3,
      email: 'preview.admin@example.com',
      fullName: 'Preview Admin',
      roles: ['ADMIN'] as const,
    },
  },
}))

vi.mock('@/plugins/pwa', () => ({
  clearOfflineCaches: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/api/services', () => ({
  authApi: {
    refresh: (...args: unknown[]) => mocks.refresh(...args),
    me: (...args: unknown[]) => mocks.me(...args),
    login: (...args: unknown[]) => mocks.login(...args),
    logout: (...args: unknown[]) => mocks.logout(...args),
  },
}))

import { useAuthStore } from './auth'

describe('auth store token storage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    window.sessionStorage.clear()
    mocks.setAccessToken.mockClear()
    mocks.refresh.mockReset()
    mocks.refresh.mockRejectedValue(new Error('no refresh cookie'))
    mocks.me.mockReset()
    mocks.login.mockReset()
    mocks.logout.mockReset()
    mocks.appEnv.useDemoData = false
    mocks.appEnv.enablePreviewMode = false
  })

  it('clears legacy persisted bearer tokens and keeps the access token in memory only', () => {
    window.localStorage.setItem('mandarin-spark.token', 'legacy-local')
    window.sessionStorage.setItem('mandarin-spark.token', 'legacy-session')

    const auth = useAuthStore()
    auth.token = 'fresh-access-token'
    auth.clearSession()

    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })

  it('removes legacy tokens during anonymous bootstrap refresh failure', async () => {
    window.localStorage.setItem('mandarin-spark.token', 'legacy-local')
    window.sessionStorage.setItem('mandarin-spark.token', 'legacy-session')

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.token).toBeNull()
    expect(auth.currentUser).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })

  it('clears the refreshed access token when current-user hydration fails', async () => {
    const session: AuthResponse = {
      token: 'rotated-access-token',
      user: {
        id: studentUser.id,
        email: studentUser.email,
        fullName: studentUser.fullName,
        roles: studentUser.roles,
      },
    }
    mocks.refresh.mockResolvedValue(session)
    mocks.me.mockRejectedValue(new Error('me failed'))

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.token).toBeNull()
    expect(auth.currentUser).toBeNull()
    expect(auth.previewRole).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
    expect(mocks.setAccessToken).toHaveBeenCalledWith('rotated-access-token')
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
  })

  it('hydrates the current user after a successful cookie refresh', async () => {
    const session: AuthResponse = {
      token: 'rotated-access-token',
      user: {
        id: studentUser.id,
        email: studentUser.email,
        fullName: studentUser.fullName,
        roles: studentUser.roles,
      },
    }
    mocks.refresh.mockResolvedValue(session)
    mocks.me.mockResolvedValue(studentUser)

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.token).toBe('rotated-access-token')
    expect(auth.currentUser).toEqual(studentUser)
    expect(auth.activeRole).toBe('STUDENT')
    expect(auth.isAuthenticated).toBe(true)
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith('rotated-access-token')
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
  })

  it('skips cookie refresh during anonymous preview bootstrap to avoid preview startup network failures', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(mocks.refresh).not.toHaveBeenCalled()
    expect(auth.token).toBeNull()
    expect(auth.currentUser).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })

  it('uses an explicit preview role without calling refresh', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true
    window.sessionStorage.setItem('mandarin-spark.preview-role', 'TEACHER')

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(mocks.refresh).not.toHaveBeenCalled()
    expect(auth.token).toBeNull()
    expect(auth.previewRole).toBe('TEACHER')
    expect(auth.currentUser).toEqual(teacherPreviewUser)
  })

  it('refuses preview role selection when preview mode is not explicitly enabled', () => {
    const auth = useAuthStore()

    auth.setPreviewRole('TEACHER')

    expect(auth.previewRole).toBeNull()
    expect(auth.currentUser).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.preview-role')).toBeNull()
  })

  it('removes invalid persisted preview roles from every browser storage area', () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true
    window.localStorage.setItem('mandarin-spark.preview-role', 'ADMIN')
    window.sessionStorage.setItem('mandarin-spark.preview-role', 'OWNER')

    const auth = useAuthStore()

    expect(auth.previewRole).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.preview-role')).toBeNull()
  })

  it('keeps preview role in session storage only and never alongside a real token', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true

    const auth = useAuthStore()
    auth.setPreviewRole('TEACHER')

    expect(auth.previewRole).toBe('TEACHER')
    expect(auth.currentUser).toEqual(teacherPreviewUser)
    expect(window.localStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.preview-role')).toBe('TEACHER')

    const session: AuthResponse = {
      token: 'real-access-token',
      user: {
        id: studentUser.id,
        email: studentUser.email,
        fullName: studentUser.fullName,
        roles: studentUser.roles,
      },
    }
    mocks.login.mockResolvedValue(session)
    mocks.me.mockResolvedValue(studentUser)

    await auth.login('student@example.com', 'Password123!')

    expect(auth.previewRole).toBeNull()
    expect(auth.currentUser).toEqual(studentUser)
    expect(window.localStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith('real-access-token')
  })

  it('clears preview state and local bearer token even when backend logout fails', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true
    mocks.logout.mockRejectedValue(new Error('logout endpoint unavailable'))
    window.localStorage.setItem('mandarin-spark.token', 'legacy-local')
    window.sessionStorage.setItem('mandarin-spark.token', 'legacy-session')

    const auth = useAuthStore()
    auth.setPreviewRole('ADMIN')
    auth.token = 'real-access-token'
    auth.currentUser = studentUser

    await auth.logout()

    expect(mocks.logout).toHaveBeenCalledOnce()
    expect(auth.token).toBeNull()
    expect(auth.currentUser).toBeNull()
    expect(auth.previewRole).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.localStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.preview-role')).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })
})
