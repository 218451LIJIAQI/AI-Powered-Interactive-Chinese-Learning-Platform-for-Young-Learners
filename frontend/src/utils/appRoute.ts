import { createMemoryHistory, createRouter } from 'vue-router'

import { routes } from '@/router/routes'
import type { UserRole } from '@/types/api'
import { hasInvalidRecordId } from '@/utils/recordId'
import { defaultRouteForRole } from '@/utils/role'

/** Side-effect-free matcher so route checks never trigger browser-history setup or a router import cycle. */
const routeMatcher = createRouter({ history: createMemoryHistory(), routes })

const UNSAFE_PATH_SEGMENTS = new Set(['.', '..'])
/** No place to land after signing in: the sign-in pages themselves and the system pages. */
const NOT_A_SIGN_IN_DESTINATION: ReadonlySet<string> = new Set([
  'auth',
  'forgot-password',
  'reset-password',
  'confirm-email',
  'unauthorized',
  'maintenance',
])

function containsUnsafePathSegment(basePath: string): boolean {
  // A segment that cannot be decoded counts as unsafe; `%2e%2e` decodes to `..`.
  return basePath.split('/').some((segment) => {
    try {
      return UNSAFE_PATH_SEGMENTS.has(decodeURIComponent(segment))
    } catch {
      return true
    }
  })
}

function resolveAppRoute(path: string | null) {
  const trimmed = path?.trim()
  if (!trimmed || !trimmed.startsWith('/') || trimmed.startsWith('//')) return null
  // eslint-disable-next-line no-control-regex
  if (trimmed.includes('\\') || /[\u0000-\u001f\u007f]/u.test(trimmed)) return null

  const basePath = trimmed.split(/[?#]/, 1)[0]
  if (containsUnsafePathSegment(basePath)) return null
  // A path that is none of the router's pages lands on its catch-all.
  const resolved = routeMatcher.resolve(trimmed)
  return resolved.name === 'not-found' || hasInvalidRecordId(resolved.params) ? null : { path: trimmed, resolved }
}

/**
 * A safe in-app target for a redirect or a notification link: the trimmed path when it is a first-party route
 * (no other origin, no traversal, no control characters), otherwise null.
 */
export function normalizeAppRoute(path: string | null): string | null {
  return resolveAppRoute(path)?.path ?? null
}

/**
 * Returns a safe redirect when any granted role can open it. Without an accessible target, the first role determines
 * the account's home, in the order returned by the server.
 */
export function signedInDestination(redirect: unknown, grantedRoles: readonly UserRole[]): string {
  const match = resolveAppRoute(typeof redirect === 'string' ? redirect : null)
  if (match) {
    const roles = match.resolved.meta.roles ?? []
    const isDestination = typeof match.resolved.name !== 'string' || !NOT_A_SIGN_IN_DESTINATION.has(match.resolved.name)
    if (isDestination && (!roles.length || roles.some((role) => grantedRoles.includes(role)))) return match.path
  }
  return defaultRouteForRole(grantedRoles[0] ?? null)
}
