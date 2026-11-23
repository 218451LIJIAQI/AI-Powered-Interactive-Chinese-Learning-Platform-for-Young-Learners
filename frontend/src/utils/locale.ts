import { APP_LANGUAGES, type AppLanguage } from '@/types/api'

/** The interface language a stored or returned value names, or null when it names none. */
export function normalizeAppLanguage(value: string | null | undefined): AppLanguage | null {
  return APP_LANGUAGES.find((language) => language === value) ?? null
}

/**
 * Interface language of the current document (`<html lang>`, kept in sync with the UI locale), for code outside
 * the Vue i18n scope such as API-layer error messages. Safe in non-DOM environments.
 */
export function documentLanguage(): AppLanguage {
  if (typeof document === 'undefined') return 'en'
  return normalizeAppLanguage(document.documentElement.lang) ?? 'en'
}
