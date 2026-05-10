import { i18n } from '../i18n'

const LOCALE_MAP: Record<string, string> = {
  en: 'en-MY',
  ms: 'ms-MY',
  zh: 'zh-CN',
}

function resolveBrowserLocale() {
  const current = i18n.global.locale.value as string | undefined
  if (current && LOCALE_MAP[current]) {
    return LOCALE_MAP[current]
  }
  return 'en-MY'
}

const DEFAULT_DATETIME_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
}

const DEFAULT_DATE_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
}

function safeDate(value: string | Date | null | undefined) {
  if (!value) {
    return null
  }

  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(
  value: string | Date | null | undefined,
  fallback = '-',
  options: Intl.DateTimeFormatOptions = DEFAULT_DATETIME_OPTIONS,
) {
  const date = safeDate(value)
  if (!date) {
    return fallback
  }

  try {
    return new Intl.DateTimeFormat(resolveBrowserLocale(), options).format(date)
  } catch {
    return date.toLocaleString()
  }
}

export function formatDate(
  value: string | Date | null | undefined,
  fallback = '-',
  options: Intl.DateTimeFormatOptions = DEFAULT_DATE_OPTIONS,
) {
  const date = safeDate(value)
  if (!date) {
    return fallback
  }

  try {
    return new Intl.DateTimeFormat(resolveBrowserLocale(), options).format(date)
  } catch {
    return date.toLocaleDateString()
  }
}
