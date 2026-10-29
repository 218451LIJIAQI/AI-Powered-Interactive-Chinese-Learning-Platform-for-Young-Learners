import type { AppLanguage } from '@/types/api'
import { documentLanguage, normalizeAppLanguage } from '@/utils/locale'

const DISPLAY_LOCALES: Record<AppLanguage, string> = { en: 'en-US', ms: 'ms-MY', 'zh-CN': 'zh-CN' }

/**
 * The Intl locale of an interface language. Without one, the page's language (`<html lang>`, which the UI store sets
 * together with the vue-i18n locale). A `computed` that formats without calling `t` passes the i18n `locale.value`, so
 * it re-runs on a language switch.
 */
function getDisplayLocale(locale?: string): string {
  const language = locale === undefined ? documentLanguage() : normalizeAppLanguage(locale)
  return DISPLAY_LOCALES[language ?? 'en']
}

function toFiniteNumber(value: number | null | undefined, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

/** Decimal places of a percentage (whole numbers by default). */
interface PercentOptions {
  digits?: number
}

/** 0-100, rounded to `digits` decimals. */
export function clampPercent(value: number | null | undefined, { digits = 0 }: PercentOptions = {}): number {
  const factor = 10 ** digits
  return Math.round(Math.min(100, Math.max(0, toFiniteNumber(value))) * factor) / factor
}

/** `numerator` as a percentage of `denominator` (0-100); 0 when there is nothing to divide by. */
export function ratioPercent(
  numerator: number | null | undefined,
  denominator: number | null | undefined,
  options?: PercentOptions,
): number {
  const divisor = toFiniteNumber(denominator)
  if (divisor <= 0) return 0

  return clampPercent((toFiniteNumber(numerator) / divisor) * 100, options)
}

/** `value` as a share of the largest of `values` (0-100), for bars scaled to the longest one. */
export function shareOfMax(value: number, values: readonly number[]): number {
  return ratioPercent(value, Math.max(0, ...values))
}

/** Progress through `total` steps on step `index` (0-based); the current step counts once its answer is checked. */
export function stepProgress(index: number, total: number, checked: boolean): number {
  return ratioPercent(index + (checked ? 1 : 0), total)
}

/** "80%", or "33.3%" with `digits: 1` (Intl, so the sign follows the interface language). */
export function formatPercent(value: number | null | undefined, options: PercentOptions = {}): string {
  return new Intl.NumberFormat(getDisplayLocale(), {
    style: 'percent',
    maximumFractionDigits: options.digits ?? 0,
  }).format(clampPercent(value, options) / 100)
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat(getDisplayLocale()).format(toFiniteNumber(value))
}

/** "3:42" for 222 seconds (whole seconds, rounded down like a running timer); "0:00" when unknown. */
export function formatClock(totalSeconds: number | null | undefined): string {
  const seconds = Math.max(0, Math.floor(toFiniteNumber(totalSeconds)))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** "512 B", "210 KB", "1.4 MB": a file size for a person, in the interface language. */
export function formatFileSize(bytes: number | null | undefined): string {
  const units = ['B', 'KB', 'MB', 'GB']
  let value = Math.max(0, toFiniteNumber(bytes))
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  const digits = unit === 0 || value >= 10 ? 0 : 1
  return `${new Intl.NumberFormat(getDisplayLocale(), { maximumFractionDigits: digits }).format(value)} ${units[unit]}`
}

// ----- dates and times -----

const DATE_ONLY_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

/**
 * The Date of an API value, or null when it is missing or invalid. A calendar date (a backend LocalDate such as the
 * last learning day, `yyyy-MM-dd`) is local midnight: `new Date('yyyy-MM-dd')` means UTC midnight and would show the
 * previous day in time zones west of UTC.
 */
export function parseDate(value: string | Date | null | undefined): Date | null {
  if (!value) return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value

  const dateOnly = DATE_ONLY_PATTERN.exec(value)
  if (dateOnly) {
    const [year, month, day] = dateOnly.slice(1).map(Number)
    const local = new Date(year, month - 1, day)
    // Reject impossible dates such as 2026-02-30 instead of letting them roll over.
    return local.getMonth() === month - 1 && local.getDate() === day ? local : null
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

type DateValue = string | Date | null | undefined

const LONG_DATE: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric', year: 'numeric' }
const SHORT_DATE: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' }
const TIME: Intl.DateTimeFormatOptions = { hour: 'numeric', minute: '2-digit' }

/**
 * A date in the interface language ("May 18, 2026" by default), or "--" when there is none. The presets below cover
 * the shapes the pages repeat; `locale` is the i18n locale of a `computed` that must follow a language switch.
 */
export function formatDate(value: DateValue, options: Intl.DateTimeFormatOptions = LONG_DATE, locale?: string): string {
  const date = parseDate(value)
  if (!date) return '--'

  return new Intl.DateTimeFormat(getDisplayLocale(locale), options).format(date)
}

/** "May 18" */
export function formatShortDate(value: DateValue, locale?: string): string {
  return formatDate(value, SHORT_DATE, locale)
}

/** "3:05 PM" */
export function formatTime(value: DateValue, locale?: string): string {
  return formatDate(value, TIME, locale)
}

/** "Mon, May 18" */
export function formatWeekdayDate(value: DateValue, locale?: string): string {
  return formatDate(value, { weekday: 'short', ...SHORT_DATE }, locale)
}

/** "May 18, 3:05 PM" */
export function formatDateTime(value: DateValue, locale?: string): string {
  return formatDate(value, { ...SHORT_DATE, ...TIME }, locale)
}

/** "May 18, 2026, 3:05 PM" */
export function formatFullDateTime(value: DateValue, locale?: string): string {
  return formatDate(value, { ...LONG_DATE, ...TIME }, locale)
}

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

/**
 * "10 min ago", "2 hr. ago", "yesterday" in the interface language (Intl.RelativeTimeFormat), or "--" when there is no
 * time; after a week the caller usually shows the date instead. `now` is injectable for tests.
 */
export function formatRelativeTime(value: DateValue, now: Date = new Date()): string {
  const date = parseDate(value)
  if (!date) return '--'
  const elapsed = date.getTime() - now.getTime()
  const format = new Intl.RelativeTimeFormat(getDisplayLocale(), { numeric: 'auto', style: 'short' })
  const absolute = Math.abs(elapsed)
  if (absolute < MINUTE_MS) return format.format(0, 'second')
  if (absolute < HOUR_MS) return format.format(Math.round(elapsed / MINUTE_MS), 'minute')
  if (absolute < DAY_MS) return format.format(Math.round(elapsed / HOUR_MS), 'hour')
  return format.format(Math.round(elapsed / DAY_MS), 'day')
}
