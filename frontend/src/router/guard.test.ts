import { describe, expect, it, vi } from 'vitest'
import type { LocationQuery, RouteMeta } from 'vue-router'

import { type GuardAuthState, resolveNavigation } from './guard'

function route(name: string, fullPath: string, meta: RouteMeta = {}, query: LocationQuery = {}) {
  return { name, fullPath, meta, query, params: {} }
}

function authState(overrides: Partial<GuardAuthState> = {}): GuardAuthState {
  return {
    bootstrap: vi.fn(),
    isAuthenticated: false,
    roles: [],
    ...overrides,
  }
}

const studentHome = route('student-home', '/student/home', { roles: ['STUDENT'] })
const adminUsers = route('admin-users', '/admin/users', { roles: ['ADMIN'] })
const adminClass = route('admin-class', '/admin/content/classes/4', { roles: ['ADMIN'] })

describe('router role guard (FRAUTH11)', () => {
  it('lets anyone open public pages after bootstrapping the session', async () => {
    const auth = authState()
    await expect(resolveNavigation(route('classes', '/classes'), auth)).resolves.toBe(true)
    expect(auth.bootstrap).toHaveBeenCalledOnce()
  })

  it('opens the maintenance page to everyone, signed in or not, without bootstrapping auth', async () => {
    const maintenance = route('maintenance', '/maintenance')

    const guest = authState()
    await expect(resolveNavigation(maintenance, guest)).resolves.toBe(true)
    expect(guest.bootstrap).not.toHaveBeenCalled()

    const learner = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(maintenance, learner)).resolves.toBe(true)
    expect(learner.bootstrap).not.toHaveBeenCalled()
  })

  it('opens the missing page when a numeric route segment would lose precision', async () => {
    const target = { ...adminClass, params: { classId: '9007199254740993' } }
    await expect(resolveNavigation(target, authState({ isAuthenticated: true, roles: ['ADMIN'] }))).resolves.toBe(
      '/not-found',
    )
  })

  it('sends guests to sign in with a redirect back to the protected page', async () => {
    await expect(resolveNavigation(studentHome, authState())).resolves.toEqual({
      name: 'auth',
      query: { redirect: '/student/home' },
    })
  })

  it('allows a signed-in user with the required role', async () => {
    const admin = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await expect(resolveNavigation(adminUsers, admin)).resolves.toBe(true)
    await expect(resolveNavigation(adminClass, admin)).resolves.toBe(true)

    const learner = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(studentHome, learner)).resolves.toBe(true)
  })

  it('redirects a signed-in user without the role to the unauthorized page, which keeps the refused page', async () => {
    const learner = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(adminUsers, learner)).resolves.toEqual({
      name: 'unauthorized',
      query: { redirect: '/admin/users' },
    })
    await expect(resolveNavigation(adminClass, learner)).resolves.toEqual({
      name: 'unauthorized',
      query: { redirect: '/admin/content/classes/4' },
    })

    const admin = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await expect(resolveNavigation(studentHome, admin)).resolves.toEqual({
      name: 'unauthorized',
      query: { redirect: '/student/home' },
    })
  })

  it('sends a signed-in user away from the auth pages to their role home', async () => {
    const learner = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(route('auth', '/auth'), learner)).resolves.toBe('/student/home')

    const admin = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await expect(resolveNavigation(route('forgot-password', '/forgot-password'), admin)).resolves.toBe(
      '/admin/dashboard',
    )
  })

  it('sends a signed-in user from the sign-in page on to a page of their role it was going to', async () => {
    // E.g. a tab left on "Your session ended" is reloaded after signing in in another tab.
    const signInTo = (redirect: string) =>
      route('auth', `/auth?redirect=${encodeURIComponent(redirect)}`, {}, { redirect, reason: 'expired' })
    const learner = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(signInTo('/student/classes/7'), learner)).resolves.toBe('/student/classes/7')

    // A page of another role, an outside address or a sign-in page itself leads home instead.
    await expect(resolveNavigation(signInTo('/admin/users'), learner)).resolves.toBe('/student/home')
    await expect(resolveNavigation(signInTo('//evil.example/student/home'), learner)).resolves.toBe('/student/home')
    await expect(resolveNavigation(signInTo('/auth'), learner)).resolves.toBe('/student/home')
  })

  it('opens an e-mailed reset or invite link to a signed-in user too', async () => {
    const admin = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await expect(resolveNavigation(route('reset-password', '/reset-password?token=t'), admin)).resolves.toBe(true)
  })
})
