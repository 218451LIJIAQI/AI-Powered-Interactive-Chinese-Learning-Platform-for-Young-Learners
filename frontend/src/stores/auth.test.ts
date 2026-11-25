import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUserResponse } from '@/types/api'
import { AUTH_INVALIDATED_EVENT, getAccessToken, getAuthSessionVersion, setAccessToken } from '@/api/http'
import { setLocale } from '@/plugins/i18n'
import { deferred } from '@/test/helpers'
import { storageKeys } from '@/utils/storage'

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
  logout: vi.fn(),
  login: vi.fn(),
  updateProfile: vi.fn(),
}))

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return {
    ...actual,
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
    logout: mocks.logout,
    login: mocks.login,
  },
}))

vi.mock('@/api/ops', () => ({ accountOpsApi: { updateProfile: mocks.updateProfile } }))

import { useAuthStore } from './auth'

beforeEach(() => {
  setActivePinia(createPinia())
  for (const mock of Object.values(mocks)) mock.mockReset()
  setAccessToken(null)
  mocks.setAccessToken.mockClear()
})

describe('auth store token storage', () => {
  beforeEach(() => {
    mocks.refresh.mockResolvedValue(null)
  })

  it('forgets the in-memory access token when the session is cleared', () => {
    const auth = useAuthStore()
    setAccessToken('signed-in-token')
    auth.currentUser = studentUser
    auth.clearSession()

    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })

  it('stays signed out when the refresh cookie does not restore a session', async () => {
    const auth = useAuthStore()
    await auth.bootstrap()

    expect(mocks.refresh).toHaveBeenCalledTimes(1)
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.me).not.toHaveBeenCalled()
  })

  it('clears the refreshed access token when current-user hydration fails', async () => {
    mocks.refresh.mockResolvedValue('rotated-access-token')
    mocks.me.mockRejectedValue(new Error('me failed'))

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })

  it('hydrates the current user after a successful cookie refresh', async () => {
    mocks.refresh.mockResolvedValue('rotated-access-token')
    mocks.me.mockResolvedValue(studentUser)

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.currentUser).toEqual(studentUser)
    expect(auth.activeRole).toBe('STUDENT')
    // The refreshed token stays in http.ts memory: the store never clears it on the success path.
    expect(mocks.setAccessToken).not.toHaveBeenCalledWith(null)
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

  it('clears the session even when backend logout fails', async () => {
    mocks.logout.mockRejectedValue(new Error('logout endpoint unavailable'))

    const auth = useAuthStore()
    auth.currentUser = studentUser

    await auth.logout()

    expect(mocks.logout).toHaveBeenCalledOnce()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
  })
})

describe('auth store account management (FRAUTH07, FRAUTH08)', () => {
  it('replaces the in-memory access token after a password change', () => {
    const auth = useAuthStore()
    auth.currentUser = studentUser

    auth.replaceAccessToken('new-access-token', getAuthSessionVersion())

    expect(auth.isAuthenticated).toBe(true)
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith('new-access-token')
    expect(auth.currentUser).toEqual(studentUser)
  })

  it('ignores a token replacement without a signed-in session', () => {
    const auth = useAuthStore()
    auth.replaceAccessToken('new-access-token', getAuthSessionVersion())
    expect(auth.isAuthenticated).toBe(false)
    expect(mocks.setAccessToken).not.toHaveBeenCalled()
  })

  it('applies a name edit without switching the interface language', () => {
    const auth = useAuthStore()
    auth.currentUser = studentUser

    auth.applySavedName({ ...studentUser, fullName: 'Aina Rahman', preferredLanguage: 'ms' }, getAuthSessionVersion())

    expect(auth.currentUser?.fullName).toBe('Aina Rahman')
    expect(document.documentElement.lang).toBe('en')
  })
})

describe('the language chosen with the switch is kept on the account (its sign-in and e-mails use it)', () => {
  function signedIn(preferredLanguage: CurrentUserResponse['preferredLanguage'] = 'en') {
    const auth = useAuthStore()
    auth.currentUser = { ...studentUser, preferredLanguage }
    return auth
  }

  it('saves the language alone, so a name changed elsewhere is never written back', async () => {
    mocks.updateProfile.mockResolvedValue({ ...studentUser, fullName: 'Name from the server', preferredLanguage: 'ms' })
    const auth = signedIn()

    await auth.savePreferredLanguage('ms')

    expect(mocks.updateProfile).toHaveBeenCalledExactlyOnceWith({ preferredLanguage: 'ms' })
    expect(auth.currentUser).toEqual({ ...studentUser, preferredLanguage: 'ms' })
  })

  it('sends one save at a time and keeps the last choice', async () => {
    const first = deferred<CurrentUserResponse>()
    mocks.updateProfile
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce({ ...studentUser, preferredLanguage: 'en' })
    const auth = signedIn()

    const saving = auth.savePreferredLanguage('ms')
    void auth.savePreferredLanguage('zh-CN')
    void auth.savePreferredLanguage('en')
    expect(mocks.updateProfile).toHaveBeenCalledTimes(1)
    first.resolve({ ...studentUser, preferredLanguage: 'ms' })
    await saving
    await flushPromises()

    // "ms" was stored meanwhile, so the last choice goes out after it; the one in between is skipped.
    expect(mocks.updateProfile.mock.calls).toEqual([[{ preferredLanguage: 'ms' }], [{ preferredLanguage: 'en' }]])
    expect(auth.currentUser?.preferredLanguage).toBe('en')
  })

  it('sends nothing when signed out or when the account has that language already', async () => {
    await useAuthStore().savePreferredLanguage('ms')
    await signedIn('ms').savePreferredLanguage('ms')

    expect(mocks.updateProfile).not.toHaveBeenCalled()
  })

  it('keeps a new choice queued behind a selection that needed no save', async () => {
    mocks.updateProfile.mockResolvedValueOnce({ ...studentUser, preferredLanguage: 'ms' })
    const auth = signedIn()

    await Promise.all([auth.savePreferredLanguage('en'), auth.savePreferredLanguage('ms')])

    expect(mocks.updateProfile).toHaveBeenCalledExactlyOnceWith({ preferredLanguage: 'ms' })
    expect(auth.currentUser?.preferredLanguage).toBe('ms')
  })

  it('processes the new account’s choice when a save from the previous session finishes', async () => {
    const first = deferred<CurrentUserResponse>()
    const otherUser = { ...studentUser, id: 2, email: 'other@example.com', preferredLanguage: 'en' }
    mocks.login.mockResolvedValueOnce({ token: 'other-token' })
    mocks.me.mockResolvedValueOnce(otherUser)
    mocks.updateProfile
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce({ ...otherUser, preferredLanguage: 'zh-CN' })
    const auth = signedIn()

    const oldChoice = auth.savePreferredLanguage('ms')
    await auth.login(otherUser.email, 'password', false)
    const newChoice = auth.savePreferredLanguage('zh-CN')
    first.resolve({ ...studentUser, preferredLanguage: 'ms' })
    await Promise.all([oldChoice, newChoice])

    expect(mocks.updateProfile.mock.calls).toEqual([[{ preferredLanguage: 'ms' }], [{ preferredLanguage: 'zh-CN' }]])
    expect(auth.currentUser).toEqual({ ...otherUser, preferredLanguage: 'zh-CN' })
  })

  it('leaves the account as it was when the save fails, and the next switch tries again', async () => {
    mocks.updateProfile.mockRejectedValueOnce(new Error('offline'))
    const auth = signedIn()

    await auth.savePreferredLanguage('zh-CN')
    expect(auth.currentUser?.preferredLanguage).toBe('en')

    mocks.updateProfile.mockResolvedValueOnce({ ...studentUser, preferredLanguage: 'zh-CN' })
    await auth.savePreferredLanguage('zh-CN')
    expect(mocks.updateProfile).toHaveBeenCalledTimes(2)
    expect(auth.currentUser?.preferredLanguage).toBe('zh-CN')
  })

  it('ignores a save answered after the account signed out', async () => {
    const save = deferred<CurrentUserResponse>()
    mocks.updateProfile.mockReturnValueOnce(save.promise)
    const auth = signedIn()

    const saving = auth.savePreferredLanguage('ms')
    auth.clearSession()
    save.resolve({ ...studentUser, preferredLanguage: 'ms' })
    await saving

    expect(auth.currentUser).toBeNull()
    expect(mocks.updateProfile).toHaveBeenCalledTimes(1)
  })
})

describe('auth operation ownership', () => {
  beforeEach(() => {
    mocks.login.mockResolvedValue({ token: 'login-token' })
    mocks.me.mockResolvedValue(studentUser)
    mocks.logout.mockResolvedValue(undefined)
    mocks.refresh.mockResolvedValue('restored-token')
  })

  function signedIn() {
    const auth = useAuthStore()
    auth.currentUser = studentUser
    return auth
  }

  function announcedUserId(): number | null | undefined {
    return JSON.parse(window.localStorage.getItem(storageKeys.sessionOwner) ?? 'null')?.userId
  }

  async function withAuthInvalidated<T>(listener: EventListener, run: () => T | Promise<T>): Promise<T> {
    window.addEventListener(AUTH_INVALIDATED_EVENT, listener)
    try {
      return await run()
    } finally {
      window.removeEventListener(AUTH_INVALIDATED_EVENT, listener)
    }
  }

  it('does not restore a session after sign-out while refresh is pending', async () => {
    const refresh = deferred<string>()
    mocks.refresh.mockReturnValueOnce(refresh.promise)
    const auth = useAuthStore()
    const restore = auth.bootstrap()
    auth.clearSession()
    refresh.resolve('old-token')
    await restore
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.me).not.toHaveBeenCalled()
    await auth.bootstrap()
    expect(mocks.refresh).toHaveBeenCalledTimes(1)
  })

  it.each([true, false])('ignores old bootstrap hydration after explicit sign-in (success=%s)', async (succeeds) => {
    const hydration = deferred<CurrentUserResponse>()
    mocks.me.mockReturnValueOnce(hydration.promise)
    const auth = useAuthStore()
    const restore = auth.bootstrap()
    await flushPromises()
    const current = { ...studentUser, id: 2, fullName: 'New account' }
    mocks.me.mockResolvedValueOnce(current)
    await auth.login('new@example.com', 'password', false)
    if (succeeds) hydration.resolve(studentUser)
    else hydration.reject(new Error('old hydration failed'))
    await restore
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.currentUser).toEqual(current)
  })

  it('ignores a login response after the session is cleared', async () => {
    const signIn = deferred<{ token: string }>()
    mocks.login.mockReturnValueOnce(signIn.promise)
    const auth = useAuthStore()
    const result = expect(auth.login('old@example.com', 'password', false)).rejects.toMatchObject({
      code: 'ERR_CANCELED',
    })
    auth.clearSession()
    signIn.resolve({ token: 'old-token' })
    await result
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(auth.loading).toBe(false)
    expect(mocks.me).not.toHaveBeenCalled()
  })

  it.each([true, false])('keeps a new login while old login hydration settles (success=%s)', async (succeeds) => {
    const hydration = deferred<CurrentUserResponse>()
    mocks.me.mockReturnValueOnce(hydration.promise)
    const auth = useAuthStore()
    const oldLogin = auth.login('old@example.com', 'password', false)
    const oldResult = expect(oldLogin).rejects.toBeDefined()
    await flushPromises()
    const current = { ...studentUser, id: 2, preferredLanguage: 'zh-CN' as const }
    mocks.me.mockResolvedValueOnce(current)
    await auth.login('new@example.com', 'password', false)
    if (succeeds) hydration.resolve({ ...studentUser, preferredLanguage: 'ms' })
    else hydration.reject(new Error('old hydration failed'))
    await oldResult
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.currentUser).toEqual(current)
    expect(document.documentElement.lang).toBe('zh-CN')
    expect(auth.loading).toBe(false)
  })

  it('does not clear a new login when an earlier logout completes', async () => {
    const signOut = deferred<void>()
    mocks.logout.mockReturnValueOnce(signOut.promise)
    const auth = signedIn()
    const logout = auth.logout()
    await auth.login('new@example.com', 'password', false)
    signOut.resolve()
    await expect(logout).resolves.toBe(false)
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.currentUser).toEqual(studentUser)
  })

  it('tells the other tabs which account signed in here, and that it signed out', async () => {
    const auth = useAuthStore()

    await auth.login(studentUser.email, 'password', false)
    expect(announcedUserId()).toBe(studentUser.id)

    await auth.logout()
    expect(announcedUserId()).toBeNull()
  })

  it('remembers the account of a session that ended by itself, for the sign-in page, until someone signs in', async () => {
    const auth = signedIn()

    // An expired session (main.ts clears it when the refresh fails).
    auth.clearSession()
    expect(auth.endedSessionEmail).toBe(studentUser.email)
    // Clearing an already empty session keeps the address.
    auth.clearSession()
    expect(auth.endedSessionEmail).toBe(studentUser.email)

    await auth.login('other@example.com', 'password', false)
    expect(auth.endedSessionEmail).toBeNull()
  })

  it('forgets the account on a sign-out, so a shared computer does not show it to the next person', async () => {
    const auth = signedIn()

    await auth.logout()

    expect(auth.endedSessionEmail).toBeNull()
  })

  it('tells the other tabs when the server has ended the session itself (account deleted, password reset)', () => {
    const announcement = () => JSON.parse(window.localStorage.getItem(storageKeys.sessionOwner) ?? 'null')
    const auth = signedIn()

    auth.clearSessionInAllTabs()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
    expect(mocks.logout).not.toHaveBeenCalled()
    expect(announcement()).toMatchObject({ userId: null })
  })

  it('knows whether the refresh cookie still opens a session', async () => {
    const auth = signedIn()

    mocks.refresh.mockResolvedValueOnce('rotated-access-token')
    expect(await auth.hasLiveSession()).toBe(true)
    mocks.refresh.mockResolvedValueOnce(null)
    expect(await auth.hasLiveSession()).toBe(false)
  })

  it.each([
    ['another account signed in', { userId: 2, at: 1 }, true],
    ['the account signed out', { userId: null, at: 1 }, true],
    ['the same account signed in again', { userId: studentUser.id, at: 1 }, false],
  ])('ends this tab’s session when, in another tab, %s: %s', async (_case, announcement, ends) => {
    // Otherwise this tab's next refresh would run on the other tab's cookie while it still shows this account.
    const invalidated = vi.fn()
    await withAuthInvalidated(invalidated, () => {
      const auth = signedIn()
      setAccessToken('student-token')

      auth.followOtherTabSession(JSON.stringify(announcement))

      expect(invalidated).toHaveBeenCalledTimes(ends ? 1 : 0)
      expect(getAccessToken()).toBe(ends ? null : 'student-token')
    })
  })

  it.each([
    ['another account signed in', { userId: 2, at: 1 }, studentUser.email],
    ['the account signed out', { userId: null, at: 1 }, null],
  ])('when, in another tab, %s, leaves this address for the sign-in page: %s', async (_case, announcement, address) => {
    const auth = signedIn()
    // main.ts ends the session when the invalidation event arrives.
    await withAuthInvalidated(
      () => auth.clearSession(),
      () => {
        auth.followOtherTabSession(JSON.stringify(announcement))

        expect(auth.isAuthenticated).toBe(false)
        expect(auth.endedSessionEmail).toBe(address)
      },
    )
  })

  it('ignores another tab’s announcement while signed out, mid sign-in, or when it cannot be read', async () => {
    const invalidated = vi.fn()
    await withAuthInvalidated(invalidated, async () => {
      const auth = useAuthStore()
      auth.followOtherTabSession(JSON.stringify({ userId: 2, at: 1 }))
      const hydration = deferred<CurrentUserResponse>()
      mocks.me.mockReturnValueOnce(hydration.promise)
      const login = auth.login(studentUser.email, 'password', false)
      await flushPromises()
      expect(auth.loading).toBe(true)
      expect(auth.isAuthenticated).toBe(false)
      auth.followOtherTabSession(JSON.stringify({ userId: 2, at: 1 }))
      hydration.resolve(studentUser)
      await login
      auth.followOtherTabSession('not json')
      auth.followOtherTabSession(JSON.stringify({ userId: '2' }))
      auth.followOtherTabSession(null)

      expect(invalidated).not.toHaveBeenCalled()
    })
  })

  it('rejects profile and password responses belonging to an earlier session', async () => {
    const auth = signedIn()
    const oldVersion = getAuthSessionVersion()
    auth.clearSession()
    await auth.login(studentUser.email, 'password', false)
    mocks.setAccessToken.mockClear()
    auth.applySavedName({ ...studentUser, fullName: 'Old name', preferredLanguage: 'ms' }, oldVersion)
    auth.replaceAccessToken('old-password-token', oldVersion)
    expect(auth.currentUser?.fullName).toBe(studentUser.fullName)
    expect(document.documentElement.lang).toBe('en')
    expect(mocks.setAccessToken).not.toHaveBeenCalled()
  })

  it('ignores profile updates while signed out or for another user id', () => {
    const auth = useAuthStore()
    auth.applySavedName({ ...studentUser, fullName: 'Ghost' }, getAuthSessionVersion())
    expect(auth.currentUser).toBeNull()

    auth.currentUser = studentUser
    auth.applySavedName({ ...studentUser, id: 999, fullName: 'Other' }, getAuthSessionVersion())
    expect(auth.currentUser?.fullName).toBe(studentUser.fullName)
  })

  it('completes logout cleanly after a concurrent 401 expiry without clobbering another tab’s new login', async () => {
    // Case 1: concurrent 401 expiry while logout is in flight still returns true and announces sign-out.
    const expiryLogout = deferred<void>()
    mocks.logout.mockReturnValueOnce(expiryLogout.promise)
    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: studentUser.id, at: 1 }))
    const auth = signedIn()
    const first = auth.logout()
    auth.clearSession()
    expect(auth.endedSessionEmail).toBe(studentUser.email)
    expiryLogout.resolve()
    await expect(first).resolves.toBe(true)
    expect(auth.endedSessionEmail).toBeNull()
    expect(announcedUserId()).toBeNull()

    // Case 2: another tab signs in as user 2 mid-logout; logout still returns true but preserves user 2's ownership.
    const switchLogout = deferred<void>()
    mocks.logout.mockReturnValueOnce(switchLogout.promise)
    const auth2 = signedIn()
    const second = auth2.logout()
    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: 2, at: 2 }))
    auth2.clearSession()
    switchLogout.resolve()
    await expect(second).resolves.toBe(true)
    expect(auth2.endedSessionEmail).toBeNull()
    expect(announcedUserId()).toBe(2)
  })

  it.each([
    ['signed out', null],
    ['switched to another user', 2],
  ] as const)('aborts restoreSession when another tab %s during /auth/me hydration', async (_label, otherOwner) => {
    const hydration = deferred<CurrentUserResponse>()
    mocks.refresh.mockResolvedValueOnce('restored-token')
    mocks.me.mockReturnValueOnce(hydration.promise)
    const auth = useAuthStore()

    const restoring = auth.restoreSession()
    await flushPromises()
    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: otherOwner, at: Date.now() }))
    hydration.resolve(studentUser)
    await restoring

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenLastCalledWith(null)
    if (otherOwner === null) expect(auth.endedSessionEmail).toBeNull()
  })

  it('starts a fresh restoreSession if clearSession invalidated an in-flight restore', async () => {
    const staleRefresh = deferred<string | null>()
    const freshRefresh = deferred<string | null>()
    mocks.refresh.mockReturnValueOnce(staleRefresh.promise).mockReturnValueOnce(freshRefresh.promise)
    mocks.me.mockResolvedValueOnce(studentUser)
    const auth = useAuthStore()

    const first = auth.restoreSession()
    auth.clearSession()
    const second = auth.restoreSession()

    staleRefresh.resolve(null)
    freshRefresh.resolve('fresh-token')
    await Promise.all([first, second])

    expect(mocks.refresh).toHaveBeenCalledTimes(2)
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.currentUser).toEqual(studentUser)
  })

  it('deduplicates concurrent restoreSession and bootstrap calls', async () => {
    const refresh = deferred<string>()
    mocks.refresh.mockReturnValueOnce(refresh.promise)
    mocks.me.mockResolvedValueOnce(studentUser)
    const auth = useAuthStore()

    const direct = auth.restoreSession()
    const viaBootstrap = auth.bootstrap()
    refresh.resolve('shared-token')
    await Promise.all([direct, viaBootstrap])

    expect(mocks.refresh).toHaveBeenCalledTimes(1)
    expect(mocks.me).toHaveBeenCalledTimes(1)
    expect(auth.isAuthenticated).toBe(true)
  })

  it('clears loading and token when login fails at login or hydration', async () => {
    const auth = useAuthStore()
    mocks.login.mockRejectedValueOnce(new Error('bad password'))
    await expect(auth.login(studentUser.email, 'wrong', false)).rejects.toThrow('bad password')
    expect(auth.loading).toBe(false)
    expect(auth.isAuthenticated).toBe(false)
    expect(getAccessToken()).toBeNull()

    mocks.login.mockResolvedValueOnce({ token: 'temp-token' })
    mocks.me.mockRejectedValueOnce(new Error('me unavailable'))
    await expect(auth.login(studentUser.email, 'correct', false)).rejects.toThrow('me unavailable')
    expect(auth.loading).toBe(false)
    expect(auth.isAuthenticated).toBe(false)
    expect(getAccessToken()).toBeNull()
    expect(mocks.logout).toHaveBeenCalledOnce()
  })

  it('refreshes once and retries backend logout when the access token expired before sign-out', async () => {
    const unauthorized = { isAxiosError: true, response: { status: 401 } }
    mocks.logout.mockRejectedValueOnce(unauthorized).mockResolvedValueOnce(undefined)
    mocks.refresh.mockResolvedValueOnce('fresh-logout-token')
    const auth = signedIn()
    setAccessToken('expired-token')

    await expect(auth.logout()).resolves.toBe(true)

    expect(mocks.refresh).toHaveBeenCalledOnce()
    expect(mocks.logout).toHaveBeenCalledTimes(2)
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('does not let an older profile update revert a concurrent language switch', async () => {
    const auth = useAuthStore()
    auth.currentUser = { ...studentUser, preferredLanguage: 'en' }
    const version = getAuthSessionVersion()

    const languageRequest = deferred<CurrentUserResponse>()
    mocks.updateProfile.mockReturnValueOnce(languageRequest.promise)

    setLocale('zh-CN')
    const savingLanguage = auth.savePreferredLanguage('zh-CN')
    // The name response carries a snapshot from before the language change.
    auth.applySavedName({ ...studentUser, fullName: 'Aina Updated', preferredLanguage: 'en' }, version)
    expect(auth.currentUser?.fullName).toBe('Aina Updated')
    expect(document.documentElement.lang).toBe('zh-CN')

    languageRequest.resolve({ ...studentUser, preferredLanguage: 'zh-CN' })
    await savingLanguage
    await flushPromises()

    expect(auth.currentUser?.fullName).toBe('Aina Updated')
    expect(auth.currentUser?.preferredLanguage).toBe('zh-CN')
    expect(document.documentElement.lang).toBe('zh-CN')
    expect(mocks.updateProfile).toHaveBeenCalledExactlyOnceWith({ preferredLanguage: 'zh-CN' })
  })

  it('clears endedSessionEmail on cross-tab sign-out even when this tab is already signed out', () => {
    const auth = useAuthStore()
    auth.endedSessionEmail = studentUser.email
    auth.currentUser = null

    auth.followOtherTabSession(JSON.stringify({ userId: null, at: Date.now() }))

    expect(auth.endedSessionEmail).toBeNull()
  })

  it('still signs out and revokes a rotated token when replaceAccessToken fires mid-logout', async () => {
    const firstLogout = deferred<void>()
    mocks.logout.mockReturnValueOnce(firstLogout.promise).mockResolvedValueOnce(undefined)
    const auth = signedIn()
    const version = getAuthSessionVersion()

    const pendingLogout = auth.logout()
    // Password-change response rotates the in-memory token while logout is still awaiting the server.
    auth.replaceAccessToken('rotated-after-password-change', version)
    expect(getAccessToken()).toBe('rotated-after-password-change')

    firstLogout.resolve()
    await expect(pendingLogout).resolves.toBe(true)

    expect(mocks.logout).toHaveBeenCalledTimes(2)
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('preserves another tab’s new login when it arrives during a rotated-token logout retry or before storage sync', async () => {
    // Case 1: rotated-token second logout is in flight when another tab signs in as user 2.
    const firstLogout = deferred<void>()
    const retryLogout = deferred<void>()
    mocks.logout.mockReturnValueOnce(firstLogout.promise).mockReturnValueOnce(retryLogout.promise)
    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: studentUser.id, at: 1 }))
    const auth = signedIn()
    await withAuthInvalidated(
      () => auth.clearSession(),
      async () => {
        const version = getAuthSessionVersion()
        const pendingLogout = auth.logout()
        auth.replaceAccessToken('rotated-after-password-change', version)
        firstLogout.resolve()
        await flushPromises()

        const otherTabAnnouncement = JSON.stringify({ userId: 2, at: 2 })
        window.localStorage.setItem(storageKeys.sessionOwner, otherTabAnnouncement)
        auth.followOtherTabSession(otherTabAnnouncement)
        retryLogout.resolve()

        await expect(pendingLogout).resolves.toBe(true)
        expect(announcedUserId()).toBe(2)
        expect(auth.endedSessionEmail).toBeNull()
      },
    )

    // Case 2: another tab already wrote user 2 to localStorage before its storage event reached this tab.
    const singleLogout = deferred<void>()
    mocks.logout.mockReturnValueOnce(singleLogout.promise)
    const auth2 = signedIn()
    const pendingSecond = auth2.logout()
    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: 2, at: 3 }))
    singleLogout.resolve()
    await expect(pendingSecond).resolves.toBe(true)
    expect(announcedUserId()).toBe(2)
    expect(auth2.isAuthenticated).toBe(false)
  })
})
