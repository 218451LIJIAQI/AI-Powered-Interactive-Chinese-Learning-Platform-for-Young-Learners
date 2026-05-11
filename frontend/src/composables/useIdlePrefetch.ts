import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useAuthStore, type UserRole } from '../stores/authStore'

/**
 * Resolves the browser's idle scheduler with a `setTimeout` polyfill so the
 * code path works inside the SSR/JSDOM environments used by Vitest.
 */
function scheduleIdle(callback: () => void): number {
  type RIC = (cb: IdleRequestCallback, opts?: IdleRequestOptions) => number
  const ric = (window as unknown as { requestIdleCallback?: RIC }).requestIdleCallback
  if (typeof ric === 'function') {
    return ric(() => callback(), { timeout: 2000 })
  }
  return window.setTimeout(callback, 1500)
}

function cancelIdle(id: number): void {
  type CIC = (id: number) => void
  const cic = (window as unknown as { cancelIdleCallback?: CIC }).cancelIdleCallback
  if (typeof cic === 'function') {
    cic(id)
    return
  }
  window.clearTimeout(id)
}

/**
 * Skip prefetching when the user has explicit data-saver enabled or is on a
 * 2g/slow-2g cellular connection — fetching ~30 small JS chunks for a 7-year
 * old's metered phone plan is rude.
 */
function shouldPrefetch(): boolean {
  type Connection = {
    saveData?: boolean
    effectiveType?: '4g' | '3g' | '2g' | 'slow-2g'
  }
  const connection = (navigator as unknown as { connection?: Connection }).connection
  if (!connection) {
    return true
  }
  if (connection.saveData) {
    return false
  }
  return connection.effectiveType !== '2g' && connection.effectiveType !== 'slow-2g'
}

/**
 * Returns the lazy-import factories for the dashboard pages of every role,
 * matching the registration in `src/router/index.ts`. Calling each factory
 * downloads the chunk; Vite then caches it until the next deploy.
 */
const DASHBOARD_LOADERS: Record<UserRole, () => Promise<unknown>> = {
  STUDENT: () => import('../pages/student/StudentDashboardPage.vue'),
  TEACHER: () => import('../pages/teacher/TeacherDashboardPage.vue'),
  ADMIN: () => import('../pages/admin/AdminDashboardPage.vue'),
}

/**
 * Picks the most relevant dashboard for a user. Accounts with multiple roles
 * default to the most "active" role (TEACHER > ADMIN > STUDENT) — TEACHER is
 * highest because we want creator dashboards warm for content authors.
 */
function pickPrimaryRole(roles: readonly UserRole[]): UserRole | null {
  if (roles.includes('TEACHER')) return 'TEACHER'
  if (roles.includes('ADMIN')) return 'ADMIN'
  if (roles.includes('STUDENT')) return 'STUDENT'
  return null
}

/**
 * Idle-prefetches the high-traffic page chunks the signed-in user is most
 * likely to navigate to next, smoothing out the post-login transition without
 * blocking the initial paint.
 *
 * Currently: the role-specific dashboard. Other pages remain on-demand so we
 * don't bloat the prefetch budget.
 */
export function useIdlePrefetch() {
  const authStore = useAuthStore()
  let pendingId: number | null = null

  const prefetchForCurrentRole = () => {
    const role = pickPrimaryRole(authStore.roles)
    if (!role || !shouldPrefetch()) {
      return
    }
    if (pendingId !== null) {
      cancelIdle(pendingId)
    }
    pendingId = scheduleIdle(() => {
      pendingId = null
      // Fire-and-forget; failures aren't critical because the same loader
      // runs again synchronously when the user actually navigates.
      DASHBOARD_LOADERS[role]().catch(() => {})
    })
  }

  onMounted(prefetchForCurrentRole)

  // Re-run when the user signs in mid-session so the dashboard is warm by the
  // time they finish reading the success message.
  watch(() => authStore.roles.join(','), (joined) => {
    if (joined) {
      prefetchForCurrentRole()
    }
  })

  onBeforeUnmount(() => {
    if (pendingId !== null) {
      cancelIdle(pendingId)
      pendingId = null
    }
  })
}
