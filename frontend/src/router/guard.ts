import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'

import type { UserRole } from '@/types/api'
import { normalizeAppRoute } from '@/utils/routeCompat'
import { defaultRouteForRole } from '@/utils/role'

/** The part of the auth store the navigation guard depends on (kept small so it can be unit tested). */
export interface GuardAuthState {
  bootstrap(): Promise<void> | void
  readonly isAuthenticated: boolean
  readonly isPreviewSession: boolean
  readonly previewRole: UserRole | null
  readonly activeRole: UserRole | null
  hasRole(role: UserRole): boolean
}

const AUTH_ROUTE_NAMES: ReadonlySet<string> = new Set(['auth', 'forgot-password', 'reset-password'])

type GuardRoute = Pick<RouteLocationNormalized, 'fullPath' | 'name' | 'meta'>

/**
 * Role-based access (FR-RBAC-02..05): signed-in users are sent from the auth pages to their role
 * home, guests are sent to sign in (with a redirect back), and a user without the route's role
 * is sent to /unauthorized. Preview sessions may open only their preview role's pages.
 */
export async function resolveNavigation(to: GuardRoute, auth: GuardAuthState): Promise<RouteLocationRaw | true> {
  const normalizedRoute = normalizeAppRoute(to.fullPath)
  if (normalizedRoute && normalizedRoute !== to.fullPath) {
    return normalizedRoute
  }

  await auth.bootstrap()

  const requiredRoles = to.meta.roles ?? []

  if (typeof to.name === 'string' && AUTH_ROUTE_NAMES.has(to.name) && auth.isAuthenticated) {
    return defaultRouteForRole(auth.activeRole)
  }

  if (requiredRoles.length === 0) {
    return true
  }

  if (
    auth.isPreviewSession &&
    auth.previewRole &&
    to.meta.previewRole === auth.previewRole &&
    requiredRoles.includes(auth.previewRole)
  ) {
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }

  if (!requiredRoles.some((role) => auth.hasRole(role))) {
    return { name: 'unauthorized' }
  }

  return true
}
