import { describe, expect, it, vi } from 'vitest'
import type { RouteMeta } from 'vue-router'

import type { UserRole } from '@/types/api'

import { type GuardAuthState, resolveNavigation } from './guard'

function route(name: string, fullPath: string, meta: RouteMeta = {}) {
  return { name, fullPath, meta }
}

function authState(overrides: Partial<GuardAuthState> & { roles?: UserRole[] } = {}): GuardAuthState {
  const roles = overrides.roles ?? []
  return {
    bootstrap: vi.fn(),
    isAuthenticated: false,
    isPreviewSession: false,
    previewRole: null,
    activeRole: roles[0] ?? null,
    hasRole: (role: UserRole) => roles.includes(role),
    ...overrides,
  }
}

const studentHome = route('student-home', '/student/home', { roles: ['STUDENT'], previewRole: 'STUDENT' })
const adminUsers = route('admin-users', '/admin/users', { roles: ['ADMIN'], previewRole: 'ADMIN' })
const teacherContent = route('teacher-content', '/teacher/content', { roles: ['TEACHER'], previewRole: 'TEACHER' })

describe('router role guard (FR-RBAC-02..05)', () => {
  it('lets anyone open public pages after bootstrapping the session', async () => {
    const auth = authState()
    await expect(resolveNavigation(route('catalog', '/courses'), auth)).resolves.toBe(true)
    expect(auth.bootstrap).toHaveBeenCalledOnce()
  })

  it('sends guests to sign in with a redirect back to the protected page', async () => {
    await expect(resolveNavigation(studentHome, authState())).resolves.toEqual({
      name: 'auth',
      query: { redirect: '/student/home' },
    })
  })

  it('allows a signed-in user with the required role', async () => {
    const auth = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await expect(resolveNavigation(adminUsers, auth)).resolves.toBe(true)
  })

  it('redirects a signed-in user without the role to the unauthorized page', async () => {
    const student = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(adminUsers, student)).resolves.toEqual({ name: 'unauthorized' })
    await expect(resolveNavigation(teacherContent, student)).resolves.toEqual({ name: 'unauthorized' })

    const teacher = authState({ isAuthenticated: true, roles: ['TEACHER'] })
    await expect(resolveNavigation(studentHome, teacher)).resolves.toEqual({ name: 'unauthorized' })
  })

  it('sends a signed-in user away from the auth pages to their role home', async () => {
    const teacher = authState({ isAuthenticated: true, roles: ['TEACHER'] })
    await expect(resolveNavigation(route('auth', '/auth'), teacher)).resolves.toBe('/teacher/dashboard')

    const admin = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await expect(resolveNavigation(route('forgot-password', '/forgot-password'), admin)).resolves.toBe(
      '/admin/dashboard',
    )
  })

  it('limits a preview session to the pages of its preview role', async () => {
    const preview = authState({ isPreviewSession: true, previewRole: 'STUDENT', roles: [] })
    await expect(resolveNavigation(studentHome, preview)).resolves.toBe(true)
    await expect(resolveNavigation(adminUsers, preview)).resolves.toEqual({
      name: 'auth',
      query: { redirect: '/admin/users' },
    })
  })
})
