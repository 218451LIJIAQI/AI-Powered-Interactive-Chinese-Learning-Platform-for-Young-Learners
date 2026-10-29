import type { AppLanguage } from '@/types/api'
import { normalizeAppLanguage } from '@/utils/locale'

export const storageKeys = {
  token: 'mandarin-spark.token',
  recoveryEmail: 'mandarin-spark.recovery-email',
  language: 'mandarin-spark.language',
  /** Written when a tab starts an access-token refresh, so the other tabs learn the refresh cookie may rotate. */
  refreshMarker: 'mandarin-spark.refresh-started',
} as const

/** The browser's storage, or null where reading it throws (e.g. blocked site data). */
function browserStorage(name: 'localStorage' | 'sessionStorage'): Storage | null {
  try {
    return window[name]
  } catch {
    return null
  }
}

export function getSessionStorage(): Storage | null {
  return browserStorage('sessionStorage')
}

/* The helpers below use localStorage unless given another storage, and never throw (blocked or full storage). */

export function safeGetItem(key: string, storage = browserStorage('localStorage')): string | null {
  try {
    return storage?.getItem(key) ?? null
  } catch {
    return null
  }
}

export function safeSetItem(key: string, value: string, storage = browserStorage('localStorage')): void {
  try {
    storage?.setItem(key, value)
  } catch {}
}

export function safeRemoveItem(key: string, storage = browserStorage('localStorage')): void {
  try {
    storage?.removeItem(key)
  } catch {}
}

/**
 * Reads the stored interface language. A value saved by an earlier build with the legacy
 * Chinese code is mapped to zh-CN and written back so the old value disappears.
 */
export function readPersistedLocale(): AppLanguage {
  const raw = safeGetItem(storageKeys.language)
  const locale = normalizeAppLanguage(raw)
  if (!locale) return 'en'
  if (raw !== locale) safeSetItem(storageKeys.language, locale)
  return locale
}
