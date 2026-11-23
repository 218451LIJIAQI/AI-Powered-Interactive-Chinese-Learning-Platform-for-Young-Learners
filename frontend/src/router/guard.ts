import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'

import type { UserRole } from '@/types/api'
import { signedInDestination } from '@/utils/appRoute'
import { hasInvalidRecordId } from '@/utils/recordId'

/** The part of the auth store the navigation guard depends on (kept small so it can be unit tested). */
export interface GuardAuthState {
  bootstrap(): Promise<void> | void
  readonly isAuthenticated: boolean
  readonly roles: readonly UserRole[]
}

/**
 * The pages only a signed-out visitor needs. The reset page is not one of them: its e-mailed link (or an admin invite)
 * works whoever is signed in on this browser.
 */
const SIGNED_OUT_ROUTE_NAMES: ReadonlySet<string> = new Set(['auth', 'forgot-password'])

type GuardRoute = Pick<RouteLocationNormalized, 'fullPath' | 'name' | 'meta' | 'query' | 'params'>

/**
 * Role-based access (FRAUTH11): signed-in users are sent from the sign-in pages on to where they were going (a safe
 * `redirect` their role may open) or their role home, guests are sent to sign in (with a redirect back), a user without
 * the route's role is sent to /unauthorized (which keeps the page, for signing in with another account).
 */
export async function resolveNavigation(to: GuardRoute, auth: GuardAuthState): Promise<RouteLocationRaw | true> {
  // Maintenance opens immediately even when the API is unreachable (no refresh call blocking the status page).
  if (to.name === 'maintenance') return true
  await auth.bootstrap()
  if (hasInvalidRecordId(to.params)) return '/not-found'

  const requiredRoles = to.meta.roles ?? []

  if (typeof to.name === 'string' && SIGNED_OUT_ROUTE_NAMES.has(to.name) && auth.isAuthenticated) {
    return signedInDestination(to.query.redirect, auth.roles)
  }

  if (requiredRoles.length === 0) {
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }

  if (!requiredRoles.some((role) => auth.roles.includes(role))) {
    return { name: 'unauthorized', query: { redirect: to.fullPath } }
  }

  return true
}
