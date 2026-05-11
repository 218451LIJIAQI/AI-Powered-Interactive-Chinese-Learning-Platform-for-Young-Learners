/**
 * Centralised list of the localStorage keys used by the frontend.
 *
 * Keeping them in one place avoids subtle bugs where two modules drift out of
 * sync (e.g. `apiClient` reading `'ai-chinese-learning-token'` while
 * `authStore` writes to a slightly different key).
 */
export const STORAGE_KEYS = {
  /** JWT bearer token used for authenticated API requests. */
  AUTH_TOKEN: 'ai-chinese-learning-token',
  /** Preferred UI language code, one of the SupportedLocale values. */
  LOCALE: 'ai-chinese-learning-locale',
  /** Prefix for the per-variant sidebar group expand/collapse state. */
  NAV_COLLAPSE_PREFIX: 'kid-nav-collapse',
} as const

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS]
