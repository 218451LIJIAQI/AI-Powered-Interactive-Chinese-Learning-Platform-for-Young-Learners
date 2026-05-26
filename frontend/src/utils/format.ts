/**
 * Locale-aware display formatters used across every persona surface
 * for numbers, currency, dates and percentages. Centralising avoids
 * the classic "TypeError: Cannot read properties of null (reading
 * 'toFixed')" by null-coalescing every input to a sensible default
 * and returning the `'--'` placeholder for invalid date strings so
 * the UI never shows raw `Invalid Date` to a learner.
 *
 * The locale is fixed to `en-US` because the platform's authored
 * content is English-first; persona-specific labels are translated
 * separately via i18n.
 */
export function toFiniteNumber(value: number | null | undefined, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export function nonNegativeInteger(value: number | null | undefined, fallback = 0) {
  return Math.max(0, Math.round(toFiniteNumber(value, fallback)))
}

export function clampNumber(value: number | null | undefined, min: number, max: number, fallback = min) {
  const low = Math.min(min, max)
  const high = Math.max(min, max)
  return Math.min(high, Math.max(low, toFiniteNumber(value, fallback)))
}

export function clampPercent(value: number | null | undefined, fallback = 0) {
  return Math.round(clampNumber(value, 0, 100, fallback))
}

export function ratioPercent(numerator: number | null | undefined, denominator: number | null | undefined) {
  const divisor = toFiniteNumber(denominator)
  if (divisor <= 0) return 0

  return clampPercent((toFiniteNumber(numerator) / divisor) * 100)
}

export function formatNumber(value: number | null | undefined) {
  return new Intl.NumberFormat('en-US').format(toFiniteNumber(value))
}

const DEFAULT_CURRENCY_CODE = 'USD'
const WELL_FORMED_CURRENCY_CODE = /^[A-Z]{3}$/
let supportedCurrencyCodes: ReadonlySet<string> | null | undefined

function getSupportedCurrencyCodes() {
  if (supportedCurrencyCodes !== undefined) {
    return supportedCurrencyCodes
  }

  const supportedValuesOf = (Intl as typeof Intl & { supportedValuesOf?: (key: 'currency') => string[] })
    .supportedValuesOf
  if (typeof supportedValuesOf !== 'function') {
    supportedCurrencyCodes = null
    return supportedCurrencyCodes
  }

  try {
    supportedCurrencyCodes = new Set(supportedValuesOf.call(Intl, 'currency'))
  } catch {
    supportedCurrencyCodes = null
  }

  return supportedCurrencyCodes
}

function normalizeCurrencyCode(currency: string | null | undefined) {
  const normalized = typeof currency === 'string' ? currency.trim().toUpperCase() : ''
  if (!WELL_FORMED_CURRENCY_CODE.test(normalized)) {
    return DEFAULT_CURRENCY_CODE
  }

  const supportedCodes = getSupportedCurrencyCodes()
  if (supportedCodes && !supportedCodes.has(normalized)) {
    return DEFAULT_CURRENCY_CODE
  }

  return normalized
}

export function formatCurrency(value: number | null | undefined, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: normalizeCurrencyCode(currency),
    maximumFractionDigits: 2,
  }).format(toFiniteNumber(value))
}

function parseDate(value: string | null | undefined) {
  if (!value) return null

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value: string | null | undefined, options?: Intl.DateTimeFormatOptions) {
  const date = parseDate(value)
  if (!date) return '--'

  return new Intl.DateTimeFormat('en-US', options ?? { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
}

export function formatDateTime(value: string | null | undefined) {
  const date = parseDate(value)
  if (!date) return '--'

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

export function formatPercent(value: number | null | undefined) {
  return `${clampPercent(value)}%`
}
