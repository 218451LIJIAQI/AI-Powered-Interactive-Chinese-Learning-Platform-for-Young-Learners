const DEFAULT_DATE_OPTIONS: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric', year: 'numeric' }
const DISPLAY_LOCALES = { en: 'en-US', ms: 'ms-MY', zh: 'zh-CN' } as const

export function getDisplayLocale(locale?: string): string {
  if (!locale && typeof document === 'undefined') return DISPLAY_LOCALES.en
  const language = (locale ?? document.documentElement.lang).trim().toLowerCase().split('-')[0]
  return DISPLAY_LOCALES[language as keyof typeof DISPLAY_LOCALES] ?? DISPLAY_LOCALES.en
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

export function ratioPercent(numerator: number | null | undefined, denominator: number | null | undefined): number {
  const divisor = toFiniteNumber(denominator)
  if (divisor <= 0) return 0

  return clampPercent((toFiniteNumber(numerator) / divisor) * 100)
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat(getDisplayLocale()).format(toFiniteNumber(value))
}

const DEFAULT_CURRENCY_CODE = 'USD'
const WELL_FORMED_CURRENCY_CODE = /^[A-Z]{3}$/
let supportedCurrencyCodes: ReadonlySet<string> | null | undefined

function getSupportedCurrencyCodes(): ReadonlySet<string> | null {
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

function normalizeCurrencyCode(currency: string | null | undefined): string {
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

export function formatCurrency(value: number | null | undefined, currency = DEFAULT_CURRENCY_CODE): string {
  return new Intl.NumberFormat(getDisplayLocale(), {
    style: 'currency',
    currency: normalizeCurrencyCode(currency),
    maximumFractionDigits: 2,
  }).format(toFiniteNumber(value))
}

function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null

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
