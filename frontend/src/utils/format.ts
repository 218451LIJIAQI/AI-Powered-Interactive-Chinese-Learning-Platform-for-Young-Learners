import type { AppLanguage } from '@/types/api'
import { documentLanguage, normalizeAppLanguage } from '@/utils/locale'

/** British English: the day comes before the month (UI rules · Dates). */
const DISPLAY_LOCALES: Record<AppLanguage, string> = { en: 'en-GB', ms: 'ms-MY', 'zh-CN': 'zh-CN' }

const numberFormats = new Map<string, Intl.NumberFormat>()
const listFormats = new Map<string, Intl.ListFormat>()
const relativeTimeFormats = new Map<string, Intl.RelativeTimeFormat>()
const dateFormats = new Map<string, (date: Date) => string>()

function cached<T>(cache: Map<string, T>, key: string, create: () => T): T {
  const existing = cache.get(key)
  if (existing) return existing
  const built = create()
  cache.set(key, built)
  return built
}

function getNumberFormat(
  locale: string | undefined,
  keySuffix = '',
  options?: Intl.NumberFormatOptions,
): Intl.NumberFormat {
  const displayLocale = getDisplayLocale(locale)
  const key = keySuffix ? `${displayLocale}:${keySuffix}` : displayLocale
  return cached(numberFormats, key, () => new Intl.NumberFormat(displayLocale, options))
}

/**
 * The Intl locale of an interface language. Without one, the page's language (`<html lang>`, which the language switch sets
 * together with the vue-i18n locale). A `computed` that formats without calling `t` passes the i18n `locale.value`, so
 * it re-runs on a language switch.
 */
function getDisplayLocale(locale?: string): string {
  const language = locale === undefined ? documentLanguage() : normalizeAppLanguage(locale)
  return DISPLAY_LOCALES[language ?? 'en']
}

function toFiniteNumber(value: number | null | undefined): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
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

/** Progress through `total` steps on step `index` (0-based); the current step counts once its answer is checked. */
export function stepProgress(index: number, total: number, checked: boolean): number {
  return ratioPercent(index + (checked ? 1 : 0), total)
}

/** "80%", or "33.3%" with `digits: 1` (Intl, so the sign follows the interface language). */
export function formatPercent(value: number | null | undefined, options: PercentOptions = {}, locale?: string): string {
  const digits = options.digits ?? 0
  return getNumberFormat(locale, `pct:${digits}`, {
    style: 'percent',
    maximumFractionDigits: digits,
  }).format(clampPercent(value, options) / 100)
}

export function formatNumber(value: number | null | undefined, locale?: string): string {
  return getNumberFormat(locale).format(toFiniteNumber(value))
}

/** The visible badge label; accessible text can still give the exact count. */
export function formatBadgeCount(count: number): string {
  return count > 99 ? '99+' : String(count)
}

/** Joins items with the conjunction and punctuation of the interface language. */
export function formatList(items: readonly string[], locale: string): string {
  const displayLocale = getDisplayLocale(locale)
  const formatter = cached(
    listFormats,
    displayLocale,
    () => new Intl.ListFormat(displayLocale, { style: 'long', type: 'conjunction' }),
  )
  return formatter.format(items)
}

/** "3:42" for 222 seconds (whole seconds, rounded down like a running timer); "0:00" when unknown. */
export function formatClock(totalSeconds: number | null | undefined): string {
  const seconds = Math.max(0, Math.floor(toFiniteNumber(totalSeconds)))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** "512 B", "210 KB", "1.4 MB": a file size for a person, in the interface language. */
export function formatFileSize(bytes: number | null | undefined, locale?: string): string {
  const units = ['B', 'KB', 'MB', 'GB']
  let value = Math.max(0, toFiniteNumber(bytes))
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  // Values just under 1024 (e.g. 1023.999 KB) would round up to "1,024 KB": promote them cleanly to the next unit.
  if (unit < units.length - 1 && Math.round(value) >= 1024) {
    value /= 1024
    unit += 1
  }
  const digits = unit === 0 || value >= 10 ? 0 : 1
  return `${getNumberFormat(locale, `dec:${digits}`, { maximumFractionDigits: digits }).format(value)} ${units[unit]}`
}

// Dates and times

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
    const local = new Date(0)
    local.setHours(0, 0, 0, 0)
    // The multi-argument Date constructor would treat years 00–99 as 1900–1999.
    local.setFullYear(year, month - 1, day)
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
const WEEKDAY_DATE: Intl.DateTimeFormatOptions = { weekday: 'short', ...SHORT_DATE }
const WEEKDAY_LONG_DATE: Intl.DateTimeFormatOptions = { weekday: 'short', ...LONG_DATE }
const DATE_TIME: Intl.DateTimeFormatOptions = { ...SHORT_DATE, ...TIME }
const FULL_DATE_TIME: Intl.DateTimeFormatOptions = { ...LONG_DATE, ...TIME }

/** Fast cache keys for the hoisted presets so common formatting calls avoid JSON.stringify. */
const DATE_PRESET_KEYS = new WeakMap<Intl.DateTimeFormatOptions, string>([
  [LONG_DATE, 'long'],
  [SHORT_DATE, 'short'],
  [WEEKDAY_DATE, 'weekday'],
  [WEEKDAY_LONG_DATE, 'weekday-long'],
  [DATE_TIME, 'datetime'],
  [FULL_DATE_TIME, 'full-datetime'],
])

const ENGLISH_DATE_PARTS = ['weekday', 'day', 'month', 'year'] as const

/**
 * The house style of English dates (UI rules · Dates): "Mon 18 May 2026", "18 May, 3:30 PM". Intl has no English
 * locale that writes exactly this (en-GB puts a comma after the weekday and uses a 24-hour clock, en-MY a lower-case
 * "pm"), so the parts are assembled here: day before month, no comma inside the date, a 12-hour clock with AM / PM.
 */
function formatEnglishDate(date: Date, formatter: Intl.DateTimeFormat, hasTime: boolean): string {
  const parts = formatter.formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value
  const datePart = ENGLISH_DATE_PARTS.map(part).filter(Boolean).join(' ')
  if (!hasTime) return datePart

  const clock = [part('hour'), part('minute')].filter(Boolean).join(':')
  const timePart = [clock, part('dayPeriod')?.toUpperCase()].filter(Boolean).join(' ')
  return datePart ? `${datePart}, ${timePart}` : timePart
}

/** Creates a formatter for several dates that share the same shape and interface language. */
export function createDateFormatter(options: Intl.DateTimeFormatOptions, locale?: string): (date: Date) => string {
  const displayLocale = getDisplayLocale(locale)
  const key = `${displayLocale}:${DATE_PRESET_KEYS.get(options) ?? JSON.stringify(options)}`
  return cached(dateFormats, key, () => {
    const useNative =
      displayLocale !== DISPLAY_LOCALES.en ||
      options.dateStyle !== undefined ||
      options.timeStyle !== undefined ||
      options.second !== undefined
    if (useNative) {
      const nativeFormatter = new Intl.DateTimeFormat(displayLocale, options)
      return (date) => nativeFormatter.format(date)
    }

    const hasTime = options.hour !== undefined || options.minute !== undefined
    const formatter = new Intl.DateTimeFormat(displayLocale, hasTime ? { ...options, hour12: true } : options)
    return (date) => formatEnglishDate(date, formatter, hasTime)
  })
}

/** Short and accessible column labels for the app's Monday-first calendars. January 1, 2024 was a Monday. */
export function calendarWeekdays(locale: string): { short: string; long: string }[] {
  const shortDay = createDateFormatter({ weekday: 'narrow' }, locale)
  const longDay = createDateFormatter({ weekday: 'long' }, locale)
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(2024, 0, 1 + index)
    return { short: shortDay(date), long: longDay(date) }
  })
}

/** A Monday-first month grid for the month containing `monthDate`. Cells outside the month are empty. */
export function calendarWeeks<T>(monthDate: Date, cell: (date: Date, day: number) => T): (T | null)[][] {
  const year = monthDate.getFullYear()
  const month = monthDate.getMonth()
  const firstOfMonth = new Date(year, month, 1)
  const days = new Date(year, month + 1, 0).getDate()
  const leading = (firstOfMonth.getDay() + 6) % 7
  return Array.from({ length: Math.ceil((leading + days) / 7) }, (_, week) =>
    Array.from({ length: 7 }, (_, weekday) => {
      const day = week * 7 + weekday - leading + 1
      return day >= 1 && day <= days ? cell(new Date(year, month, day), day) : null
    }),
  )
}

/**
 * A date in the interface language ("18 May 2026" by default), or "--" when there is none. The presets below cover
 * the shapes the pages repeat; `locale` is the i18n locale of a `computed` that must follow a language switch.
 */
export function formatDate(value: DateValue, options: Intl.DateTimeFormatOptions = LONG_DATE, locale?: string): string {
  const date = parseDate(value)
  if (!date) return '--'

  return createDateFormatter(options, locale)(date)
}

/** "18 May" */
export function formatShortDate(value: DateValue, locale?: string): string {
  return formatDate(value, SHORT_DATE, locale)
}

/** "Mon 18 May" */
export function formatWeekdayDate(value: DateValue, locale?: string): string {
  return formatDate(value, WEEKDAY_DATE, locale)
}

/** "Mon 18 May 2026" */
export function formatWeekdayLongDate(value: DateValue, locale?: string): string {
  return formatDate(value, WEEKDAY_LONG_DATE, locale)
}

/** "18 May, 3:05 PM" */
export function formatDateTime(value: DateValue, locale?: string): string {
  return formatDate(value, DATE_TIME, locale)
}

/** "18 May 2026, 3:05 PM" */
export function formatFullDateTime(value: DateValue, locale?: string): string {
  return formatDate(value, FULL_DATE_TIME, locale)
}

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

/**
 * "10 min ago", "2 hr ago", "yesterday" in the interface language (Intl.RelativeTimeFormat), or "--" when there is no
 * time; after a week the caller usually shows the date instead. `now` is injectable for tests.
 */
export function formatRelativeTime(value: DateValue, now: Date = new Date(), locale?: string): string {
  const date = parseDate(value)
  if (!date) return '--'
  const elapsed = date.getTime() - now.getTime()
  const displayLocale = getDisplayLocale(locale)
  const format = cached(
    relativeTimeFormats,
    displayLocale,
    () => new Intl.RelativeTimeFormat(displayLocale, { numeric: 'auto', style: 'short' }),
  )
  const absolute = Math.abs(elapsed)
  if (absolute < MINUTE_MS) return format.format(0, 'second')
  const minutes = Math.round(elapsed / MINUTE_MS)
  if (Math.abs(minutes) < 60) return format.format(minutes, 'minute')
  const hours = Math.round(elapsed / HOUR_MS)
  if (Math.abs(hours) < 24) return format.format(hours, 'hour')
  return format.format(Math.round(elapsed / DAY_MS), 'day')
}

/** The local calendar date as yyyy-MM-dd. */
export function isoDay(date: Date): string {
  const year = String(date.getFullYear()).padStart(4, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** The calendar day `days` after (or before) a yyyy-MM-dd day. */
export function shiftDay(day: string, days: number): string {
  const date = parseDate(day)
  if (!date) return day
  date.setDate(date.getDate() + days)
  return isoDay(date)
}

/** Whole days from one yyyy-MM-dd day to another; negative when `to` comes first. */
export function daysBetween(from: string, to: string): number {
  const start = parseDate(from)
  const end = parseDate(to)
  if (!start || !end) return 0
  // Rounded: a daylight-saving change makes one day 23 or 25 hours long.
  return Math.round((end.getTime() - start.getTime()) / DAY_MS)
}
