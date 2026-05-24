import type { AppLanguage } from '@/types/api'

/**
 * Single source of truth for `localStorage` keys used by the frontend,
 * plus `safe*` wrappers that swallow exceptions so a Safari Private
 * mode or quota-exceeded error never crashes the calling code.
 *
 * Keep keys prefixed with `mandarin-spark.` so they are easy to find
 * in DevTools and do not clash with other apps on the same origin.
 */
export const storageKeys = {
  token: 'mandarin-spark.token',
  language: 'mandarin-spark.language',
  previewRole: 'mandarin-spark.preview-role',
} as const

export function safeGetItem(key: string, storage: Storage = window.localStorage): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

export function safeSetItem(key: string, value: string, storage: Storage = window.localStorage) {
  try {
    storage.setItem(key, value)
  } catch {
  }
}

export function safeRemoveItem(key: string, storage: Storage = window.localStorage) {
  try {
    storage.removeItem(key)
  } catch {
  }
}

const validLocales: ReadonlySet<string> = new Set<AppLanguage>(['en', 'ms', 'zh'])

export function readPersistedLocale(): AppLanguage {
  const raw = safeGetItem(storageKeys.language)
  return raw !== null && validLocales.has(raw) ? (raw as AppLanguage) : 'en'
}
