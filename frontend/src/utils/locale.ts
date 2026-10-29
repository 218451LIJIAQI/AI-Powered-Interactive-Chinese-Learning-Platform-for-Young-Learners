import type { AppLanguage } from '@/types/api'

/**
 * Legacy Chinese locale code stored by earlier builds (localStorage) and returned by
 * older accounts' preferredLanguage. It is only ever read and mapped to zh-CN.
 */
const LEGACY_CHINESE_CODE = 'zh'

/** Maps any stored/returned language value to a supported interface language, or null when unknown. */
export function normalizeAppLanguage(value: string | null | undefined): AppLanguage | null {
  if (!value) return null
  const trimmed = value.trim()
  if (!trimmed) return null

  const lower = trimmed.toLowerCase()
  if (lower === LEGACY_CHINESE_CODE || lower === 'zh-cn' || lower === 'zh-hans' || lower === 'zh-hans-cn') {
    return 'zh-CN'
  }
  if (lower === 'ms' || lower === 'ms-my') return 'ms'
  if (lower === 'en' || lower === 'en-us' || lower === 'en-gb') return 'en'
  return null
}

/**
 * Interface language of the current document (`<html lang>`, kept in sync with the UI locale), for code outside
 * the Vue i18n scope such as API-layer error messages. Any `zh*` tag maps to zh-CN, any `ms*` tag to ms.
 */
export function documentLanguage(): AppLanguage {
  const language = document.documentElement.lang.toLowerCase()
  if (/^zh\b/.test(language)) return 'zh-CN'
  if (language.startsWith('ms')) return 'ms'
  return 'en'
}
