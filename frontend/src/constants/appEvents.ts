/**
 * Centralised list of app-level CustomEvent names dispatched on `window`.
 *
 * Keeping them in one place avoids magic strings drifting between dispatchers
 * and listeners (e.g. a typo like `'notifications:refersh'` in one file would
 * silently fail the listener subscribed to `'notifications:refresh'`).
 */
export const APP_EVENTS = {
  /**
   * Fired when something in the UI has changed the user's notification state
   * (e.g. reading a notification, opening a support ticket). Listeners
   * re-fetch the unread count so the sidebar badge stays accurate.
   */
  NOTIFICATIONS_REFRESH: 'notifications:refresh',
  /**
   * Fired by the apiClient response interceptor on a 403 response so the
   * top-level App.vue can render a translated "permission denied" toast.
   */
  FORBIDDEN: 'app:forbidden',
} as const

export type AppEventName = (typeof APP_EVENTS)[keyof typeof APP_EVENTS]

/**
 * Convenience wrapper for dispatching a known app event on `window`.
 *
 * Compared to inlining `window.dispatchEvent(new CustomEvent('...'))`, this
 * helper:
 *   - Removes the repeated boilerplate at call sites.
 *   - Guards against `window` being undefined in SSR/test environments.
 *   - Keeps the strongly-typed event name as the only public contract.
 */
export function dispatchAppEvent<T = unknown>(name: AppEventName, detail?: T): void {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent(name, detail === undefined ? undefined : { detail }))
}
