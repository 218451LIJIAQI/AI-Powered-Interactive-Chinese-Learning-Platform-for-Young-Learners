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
export function formatNumber(value: number | null | undefined) {
  return new Intl.NumberFormat('en-US').format(value ?? 0)
}

export function formatCurrency(value: number | null | undefined, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(value ?? 0)
}

function parseDate(value: string | null | undefined) {
  if (!value) return null

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value: string | null | undefined, options?: Intl.DateTimeFormatOptions) {
  const date = parseDate(value)
  if (!date) return '--'

  return new Intl.DateTimeFormat('en-US', options ?? { month: 'short', day: 'numeric', year: 'numeric' }).format(
    date,
  )
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
  const raw = value ?? 0
  const safe = Number.isFinite(raw) ? Math.min(100, Math.max(0, raw)) : 0
  return `${Math.round(safe)}%`
}
