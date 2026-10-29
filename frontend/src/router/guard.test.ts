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
    refreshTeacherApproval: vi.fn(),
    isAuthenticated: false,
    isApprovedTeacher: false,
    activeRole: roles[0] ?? null,
    hasRole: (role: UserRole) => roles.includes(role),
    ...overrides,
  }
}

const studentHome = route('student-home', '/student/home', { roles: ['STUDENT'] })
const adminUsers = route('admin-users', '/admin/users', { roles: ['ADMIN'] })
const teacherClasses = route('teacher-classes', '/teacher/classes', {
  roles: ['TEACHER'],
  requiresApprovedTeacher: true,
})
const teacherProfile = route('teacher-profile', '/teacher/profile', { roles: ['TEACHER'] })

describe('router role guard (FRAUTH11)', () => {
  it('lets anyone open public pages after bootstrapping the session', async () => {
    const auth = authState()
    await expect(resolveNavigation(route('classes', '/classes'), auth)).resolves.toBe(true)
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

  it('re-reads the teacher approval before opening a teaching page only', async () => {
    const teacher = authState({ isAuthenticated: true, isApprovedTeacher: true, roles: ['TEACHER'] })
    await expect(resolveNavigation(teacherClasses, teacher)).resolves.toBe(true)
    expect(teacher.refreshTeacherApproval).toHaveBeenCalledTimes(1)

    const admin = authState({ isAuthenticated: true, roles: ['ADMIN'] })
    await resolveNavigation(adminUsers, admin)
    expect(admin.refreshTeacherApproval).not.toHaveBeenCalled()
  })

  it('sends a teacher who is not approved yet from the teaching tools to the Locked page', async () => {
    const applicant = authState({ isAuthenticated: true, roles: ['TEACHER'] })
    await expect(resolveNavigation(teacherClasses, applicant)).resolves.toEqual({ name: 'teacher-locked' })
    // The teaching profile (documents, verification) stays open to applicants.
    await expect(resolveNavigation(teacherProfile, applicant)).resolves.toBe(true)
  })

  it('redirects a signed-in user without the role to the unauthorized page', async () => {
    const student = authState({ isAuthenticated: true, roles: ['STUDENT'] })
    await expect(resolveNavigation(adminUsers, student)).resolves.toEqual({ name: 'unauthorized' })
    await expect(resolveNavigation(teacherClasses, student)).resolves.toEqual({ name: 'unauthorized' })

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
})
