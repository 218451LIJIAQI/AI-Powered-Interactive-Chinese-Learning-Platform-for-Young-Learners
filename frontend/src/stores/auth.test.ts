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
    TEACHER: mocks.teacherPreviewUser,
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

  it('attempts cookie refresh during anonymous preview bootstrap before staying anonymous', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(mocks.refresh).toHaveBeenCalledOnce()
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
})
