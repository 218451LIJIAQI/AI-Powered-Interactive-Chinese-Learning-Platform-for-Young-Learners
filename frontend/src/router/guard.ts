import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'

import type { UserRole } from '@/types/api'
import { defaultRouteForRole } from '@/utils/role'

/** The part of the auth store the navigation guard depends on (kept small so it can be unit tested). */
export interface GuardAuthState {
  bootstrap(): Promise<void> | void
  readonly isAuthenticated: boolean
  readonly activeRole: UserRole | null
  hasRole(role: UserRole): boolean
  /** True for a teacher whose application an admin approved. */
  readonly isApprovedTeacher: boolean
  /** Re-reads the account of a teacher who is not approved yet, so an approval given meanwhile takes effect. */
  refreshTeacherApproval(): Promise<void> | void
}

const AUTH_ROUTE_NAMES: ReadonlySet<string> = new Set(['auth', 'forgot-password', 'reset-password'])

type GuardRoute = Pick<RouteLocationNormalized, 'fullPath' | 'name' | 'meta'>

/**
 * Role-based access (FRAUTH11): signed-in users are sent from the auth pages to their role
 * home, guests are sent to sign in (with a redirect back), and a user without the route's role
 * is sent to /unauthorized, and a teacher who is not approved yet is sent from the teaching tools to the Locked page.
 */
export async function resolveNavigation(to: GuardRoute, auth: GuardAuthState): Promise<RouteLocationRaw | true> {
  await auth.bootstrap()

  const requiredRoles = to.meta.roles ?? []

  if (typeof to.name === 'string' && AUTH_ROUTE_NAMES.has(to.name) && auth.isAuthenticated) {
    return defaultRouteForRole(auth.activeRole)
  }

  if (requiredRoles.length === 0) {
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }

  if (!requiredRoles.some((role) => auth.hasRole(role))) {
    return { name: 'unauthorized' }
  }

  if (requiredRoles.includes('TEACHER')) {
    await auth.refreshTeacherApproval()
    // Teaching tools stay locked until an admin approves the teacher; the Locked page explains why.
    if (to.meta.requiresApprovedTeacher && !auth.isApprovedTeacher) return { name: 'teacher-locked' }
  }

  return true
}
