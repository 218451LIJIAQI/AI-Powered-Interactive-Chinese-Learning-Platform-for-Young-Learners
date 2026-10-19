import { afterEach, describe, expect, it } from 'vitest'

import {
  clampNumber,
  clampPercent,
  formatDate,
  formatDateTime,
  formatNumber,
  formatPercent,
  ratioPercent,
} from './format'

afterEach(() => {
  document.documentElement.lang = 'en'
})

describe('format numeric helpers', () => {
  it('formats non-finite numbers with stable fallbacks', () => {
    expect(formatNumber(Number.NaN)).toBe('0')
    expect(formatNumber(Number.POSITIVE_INFINITY)).toBe('0')
    expect(formatNumber(Number.NEGATIVE_INFINITY)).toBe('0')
  })

  it('clamps percentages before display or progress-bar usage', () => {
    expect(clampPercent(105.4)).toBe(100)
    expect(clampPercent(-8)).toBe(0)
    expect(clampPercent(Number.NaN)).toBe(0)
    expect(formatPercent(79.6)).toBe('80%')
  })

  it('computes safe ratio percentages', () => {
    expect(ratioPercent(3, 4)).toBe(75)
    expect(ratioPercent(5, 4)).toBe(100)
    expect(ratioPercent(-1, 4)).toBe(0)
    expect(ratioPercent(4, 0)).toBe(0)
    expect(ratioPercent(Number.NaN, 4)).toBe(0)
  })

  it('clamps arbitrary numeric ranges', () => {
    expect(clampNumber(90, 10, 78)).toBe(78)
    expect(clampNumber(4, 10, 78)).toBe(10)
    expect(clampNumber(42, 78, 10)).toBe(42)
    expect(clampNumber(Number.POSITIVE_INFINITY, 10, 78)).toBe(10)
  })
})

describe('format date helpers', () => {
  it('returns placeholders for missing or invalid dates', () => {
    expect(formatDate(null)).toBe('--')
    expect(formatDate('')).toBe('--')
    expect(formatDate('not-a-date')).toBe('--')
    expect(formatDateTime(undefined)).toBe('--')
  })

  it('formats valid date strings with default and custom options', () => {
    expect(formatDate('2026-05-20T12:00:00.000Z')).toBe('May 20, 2026')
    expect(formatDate('2026-05-20T12:00:00.000Z', { month: 'short', day: 'numeric' })).toBe('May 20')
    expect(formatDateTime('2026-05-20T12:00:00.000Z')).toMatch(/^May 20, \d{1,2}:00 (AM|PM)$/)
  })

  it('formats a calendar date (yyyy-MM-dd) as that day in every time zone and rejects impossible dates', () => {
    expect(formatDate('2026-05-18')).toBe('May 18, 2026')
    expect(formatDate('2026-02-30')).toBe('--')
  })

  it('uses the active interface locale for dates and numbers', () => {
    document.documentElement.lang = 'zh-CN'
    expect(formatNumber(1234)).toBe('1,234')
    expect(formatDate('2026-05-20T12:00:00.000Z')).toBe('2026年5月20日')

    document.documentElement.lang = 'ms'
    expect(formatDate('2026-05-20T12:00:00.000Z')).toBe('20 Mei 2026')
  })
})
