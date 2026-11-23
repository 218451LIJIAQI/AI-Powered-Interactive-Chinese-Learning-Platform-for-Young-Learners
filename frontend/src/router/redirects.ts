import type { RouteLocationNormalizedLoaded, RouteLocationRaw, Router } from 'vue-router'

import type { HealthStatusResponse } from '@/types/api'

/**
 * Redirects the app makes by itself, outside a navigation: main.ts wires them to the events of api/http.ts.
 */

/**
 * A signed-in session has ended (AUTH_INVALIDATED_EVENT): the sign-in page, with the way back to the page the user was
 * on and the reason, so the page can say why they are signing in again. Null on a public page, which stays open.
 */
export function sessionExpiredRedirect(
  route: Pick<RouteLocationNormalizedLoaded, 'fullPath' | 'meta'>,
): RouteLocationRaw | null {
  if (!route.meta.roles?.length) return null
  return { name: 'auth', query: { redirect: route.fullPath, reason: 'expired' } }
}

/**
 * The API answered 503 (SERVICE_UNAVAILABLE_EVENT). The returned function asks the health endpoint and opens the
 * maintenance page only when that does not answer UP either: a 503 about one feature (the service itself is up) leaves
 * the user on their page, with that page's own error. Requests that fail together share one check, and nothing happens
 * on the maintenance page itself, whose "Try again" runs the same check.
 */
export function createMaintenanceCheck(
  router: Pick<Router, 'currentRoute' | 'replace'>,
  health: () => Promise<HealthStatusResponse>,
): () => Promise<void> {
  let pending: Promise<void> | null = null
  const onMaintenancePage = () => router.currentRoute.value.name === 'maintenance'

  async function check(): Promise<void> {
    const answer = await health().catch(() => null)
    if (answer?.status === 'UP' || onMaintenancePage()) return
    // A failed navigation (the page's chunk could not be loaded either) leaves the user where they are.
    await router.replace({ name: 'maintenance' }).catch(() => undefined)
  }

  return () => {
    if (onMaintenancePage()) return Promise.resolve()
    pending ??= check().finally(() => {
      pending = null
    })
    return pending
  }
}
