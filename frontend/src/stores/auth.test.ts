import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from './auth'

const setAccessToken = vi.fn()

vi.mock('@/api/http', () => ({
  setAccessToken: (token: string | null) => setAccessToken(token),
}))

vi.mock('@/config/env', () => ({
  appEnv: {
    useDemoData: false,
    enablePreviewMode: false,
  },
}))

vi.mock('@/demo/data', () => ({
  demoUsers: {},
}))

vi.mock('@/plugins/pwa', () => ({
  clearOfflineCaches: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/api/services', () => ({
  authApi: {
    refresh: vi.fn().mockRejectedValue(new Error('no refresh cookie')),
    me: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
  },
}))

describe('auth store token storage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    window.sessionStorage.clear()
    setAccessToken.mockClear()
  })

  it('clears legacy persisted bearer tokens and keeps the access token in memory only', () => {
    window.localStorage.setItem('mandarin-spark.token', 'legacy-local')
    window.sessionStorage.setItem('mandarin-spark.token', 'legacy-session')

    const auth = useAuthStore()
    auth.token = 'fresh-access-token'
    auth.clearSession()

    expect(window.localStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(window.sessionStorage.getItem('mandarin-spark.token')).toBeNull()
    expect(setAccessToken).toHaveBeenLastCalledWith(null)
  })
})
