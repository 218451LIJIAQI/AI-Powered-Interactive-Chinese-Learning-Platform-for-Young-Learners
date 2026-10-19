import type { AppLanguage } from '@/types/api'
import { normalizeAppLanguage } from '@/utils/locale'

const DEFAULT_DATE_OPTIONS: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric', year: 'numeric' }
const DISPLAY_LOCALES: Record<AppLanguage, string> = { en: 'en-US', ms: 'ms-MY', 'zh-CN': 'zh-CN' }

export function getDisplayLocale(locale?: string): string {
  if (!locale && typeof document === 'undefined') return DISPLAY_LOCALES.en
  const language = normalizeAppLanguage(locale ?? document.documentElement.lang)
  return language ? DISPLAY_LOCALES[language] : DISPLAY_LOCALES.en
}

export function toFiniteNumber(value: number | null | undefined, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export function nonNegativeInteger(value: number | null | undefined, fallback = 0): number {
  return Math.max(0, Math.round(toFiniteNumber(value, fallback)))
}

export function clampNumber(value: number | null | undefined, min: number, max: number, fallback = min): number {
  const low = Math.min(min, max)
  const high = Math.max(min, max)
  return Math.min(high, Math.max(low, toFiniteNumber(value, fallback)))
}

export function clampPercent(value: number | null | undefined, fallback = 0): number {
  return Math.round(clampNumber(value, 0, 100, fallback))
}

/** "+5" for gains, "-3" for deductions, "0" otherwise (rounded). */
export function formatSignedInteger(value: number | null | undefined): string {
  const rounded = Math.round(toFiniteNumber(value))
  return rounded > 0 ? `+${rounded}` : String(rounded)
}

export function ratioPercent(numerator: number | null | undefined, denominator: number | null | undefined): number {
  const divisor = toFiniteNumber(denominator)
  if (divisor <= 0) return 0

  return clampPercent((toFiniteNumber(numerator) / divisor) * 100)
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat(getDisplayLocale()).format(toFiniteNumber(value))
}

const DATE_ONLY_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null

  // A calendar date (backend LocalDate, e.g. the last learning day) is parsed as local midnight: `new Date('yyyy-MM-dd')`
  // means UTC midnight and would show the previous day in time zones west of UTC.
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

export function formatDate(value: string | null | undefined, options?: Intl.DateTimeFormatOptions): string {
  const date = parseDate(value)
  if (!date) return '--'

  return new Intl.DateTimeFormat(getDisplayLocale(), options ?? DEFAULT_DATE_OPTIONS).format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  const date = parseDate(value)
  if (!date) return '--'

  return new Intl.DateTimeFormat(getDisplayLocale(), {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

export function formatPercent(value: number | null | undefined): string {
  return `${clampPercent(value)}%`
}
