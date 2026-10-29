import { describe, expect, it } from 'vitest'

import {
  clampPercent,
  formatClock,
  formatDate,
  formatDateTime,
  formatFileSize,
  formatFullDateTime,
  formatNumber,
  formatPercent,
  formatRelativeTime,
  formatShortDate,
  formatTime,
  formatWeekdayDate,
  parseDate,
  ratioPercent,
  shareOfMax,
  stepProgress,
} from './format'

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
    expect(clampPercent(33.333, { digits: 1 })).toBe(33.3)
    expect(formatPercent(79.6)).toBe('80%')
  })

  it('formats a percentage with the decimals asked for, without trailing zeros', () => {
    expect(formatPercent(100 / 3, { digits: 1 })).toBe('33.3%')
    expect(formatPercent(50, { digits: 1 })).toBe('50%')
    expect(formatPercent(0.04, { digits: 1 })).toBe('0%')
    expect(formatPercent(null, { digits: 1 })).toBe('0%')
    expect(formatPercent(120, { digits: 1 })).toBe('100%')
  })

  it('computes safe ratio percentages', () => {
    expect(ratioPercent(3, 4)).toBe(75)
    expect(ratioPercent(5, 4)).toBe(100)
    expect(ratioPercent(-1, 4)).toBe(0)
    expect(ratioPercent(4, 0)).toBe(0)
    expect(ratioPercent(Number.NaN, 4)).toBe(0)
    expect(ratioPercent(1, 3)).toBe(33)
    expect(ratioPercent(1, 3, { digits: 1 })).toBe(33.3)
    expect(ratioPercent(2, 3, { digits: 2 })).toBe(66.67)
  })

  it('scales a value against the largest one, and an empty or all-zero list to 0', () => {
    expect(shareOfMax(5, [5, 10, 2])).toBe(50)
    expect(shareOfMax(10, [5, 10, 2])).toBe(100)
    expect(shareOfMax(0, [0, 0])).toBe(0)
    expect(shareOfMax(0, [])).toBe(0)
  })

  it('counts the current step once its answer is checked', () => {
    expect(stepProgress(0, 4, false)).toBe(0)
    expect(stepProgress(0, 4, true)).toBe(25)
    expect(stepProgress(3, 4, true)).toBe(100)
    expect(stepProgress(1, 3, false)).toBe(33)
    expect(stepProgress(0, 0, true)).toBe(0)
  })
})

describe('formatClock', () => {
  it('shows minutes and two-digit seconds, rounding down like a running timer', () => {
    expect(formatClock(0)).toBe('0:00')
    expect(formatClock(7)).toBe('0:07')
    expect(formatClock(75)).toBe('1:15')
    expect(formatClock(222.9)).toBe('3:42')
    expect(formatClock(3725)).toBe('62:05')
  })

  it('shows 0:00 for a negative, missing or non-numeric time', () => {
    expect(formatClock(-5)).toBe('0:00')
    expect(formatClock(null)).toBe('0:00')
    expect(formatClock(undefined)).toBe('0:00')
    expect(formatClock(Number.NaN)).toBe('0:00')
    expect(formatClock(Number.POSITIVE_INFINITY)).toBe('0:00')
  })
})

describe('formatFileSize', () => {
  it('picks B, KB, MB or GB, with one decimal below 10 of a unit', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(48_213)).toBe('47 KB')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2 MB')
    expect(formatFileSize(1.44 * 1024 * 1024)).toBe('1.4 MB')
    expect(formatFileSize(14.2 * 1024 * 1024)).toBe('14 MB')
    expect(formatFileSize(3 * 1024 ** 3)).toBe('3 GB')
    expect(formatFileSize(5 * 1024 ** 4)).toBe('5,120 GB')
  })

  it('treats a negative, missing or non-numeric size as empty', () => {
    expect(formatFileSize(-10)).toBe('0 B')
    expect(formatFileSize(null)).toBe('0 B')
    expect(formatFileSize(Number.NaN)).toBe('0 B')
  })

  it('writes the number in the interface language', () => {
    document.documentElement.lang = 'zh-CN'
    expect(formatFileSize(1536)).toBe('1.5 KB')
    document.documentElement.lang = 'ms'
    expect(formatFileSize(1.5 * 1024 * 1024)).toBe('1.5 MB')
  })
})

describe('parseDate', () => {
  it('reads a calendar date (yyyy-MM-dd) as local midnight of that day', () => {
    const date = parseDate('2026-05-18')!
    expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes()]).toEqual([
      2026, 4, 18, 0, 0,
    ])
  })

  it('reads timestamps and Date objects, and rejects missing, invalid and impossible dates', () => {
    expect(parseDate('2026-05-20T12:00:00.000Z')?.toISOString()).toBe('2026-05-20T12:00:00.000Z')
    const now = new Date(2026, 0, 2)
    expect(parseDate(now)).toBe(now)
    expect(parseDate(new Date(Number.NaN))).toBeNull()
    expect(parseDate(null)).toBeNull()
    expect(parseDate('')).toBeNull()
    expect(parseDate('not-a-date')).toBeNull()
    expect(parseDate('2026-02-30')).toBeNull()
  })
})

describe('format date helpers', () => {
  const NOON_UTC = '2026-05-20T12:00:00.000Z'

  it('returns placeholders for missing or invalid dates', () => {
    expect(formatDate(null)).toBe('--')
    expect(formatDate('')).toBe('--')
    expect(formatDate('not-a-date')).toBe('--')
    expect(formatDateTime(undefined)).toBe('--')
    expect(formatShortDate(null)).toBe('--')
    expect(formatTime('nope')).toBe('--')
  })

  it('formats valid date strings with default and custom options', () => {
    expect(formatDate(NOON_UTC)).toBe('May 20, 2026')
    expect(formatDate(NOON_UTC, { month: 'long', year: 'numeric' })).toBe('May 2026')
    expect(formatDateTime(NOON_UTC)).toMatch(/^May 20, \d{1,2}:00 (AM|PM)$/)
  })

  it('has a preset for each shape the pages repeat', () => {
    const local = new Date(2026, 4, 18, 15, 5)
    expect(formatShortDate(local)).toBe('May 18')
    expect(formatTime(local)).toBe('3:05 PM')
    expect(formatWeekdayDate(local)).toBe('Mon, May 18')
    expect(formatDate(local)).toBe('May 18, 2026')
    expect(formatDateTime(local)).toBe('May 18, 3:05 PM')
    expect(formatFullDateTime(local)).toBe('May 18, 2026, 3:05 PM')
  })

  it('formats a calendar date (yyyy-MM-dd) as that day in every time zone and rejects impossible dates', () => {
    expect(formatDate('2026-05-18')).toBe('May 18, 2026')
    expect(formatWeekdayDate('2026-05-18')).toBe('Mon, May 18')
    expect(formatDate('2026-02-30')).toBe('--')
  })

  it('uses the active interface locale for dates and numbers', () => {
    document.documentElement.lang = 'zh-CN'
    expect(formatNumber(1234)).toBe('1,234')
    expect(formatDate(NOON_UTC)).toBe('2026年5月20日')

    document.documentElement.lang = 'ms'
    expect(formatDate(NOON_UTC)).toBe('20 Mei 2026')
  })

  it('uses the locale passed in over the page language', () => {
    document.documentElement.lang = 'en'
    expect(formatShortDate('2026-05-18', 'zh-CN')).toBe('5月18日')
    expect(formatDate('2026-05-18', { weekday: 'long' }, 'ms')).toBe('Isnin')
  })
})

describe('formatRelativeTime', () => {
  const now = new Date('2026-05-20T12:00:00.000Z')

  it('says how long ago in the largest whole unit up to days', () => {
    expect(formatRelativeTime('2026-05-20T11:59:30.000Z', now)).toBe('now')
    expect(formatRelativeTime('2026-05-20T11:50:00.000Z', now)).toBe('10 min. ago')
    expect(formatRelativeTime('2026-05-20T09:00:00.000Z', now)).toBe('3 hr. ago')
    expect(formatRelativeTime('2026-05-19T12:00:00.000Z', now)).toBe('yesterday')
    expect(formatRelativeTime('2026-05-15T12:00:00.000Z', now)).toBe('5 days ago')
    expect(formatRelativeTime('2026-05-20T14:00:00.000Z', now)).toBe('in 2 hr.')
  })

  it('returns a placeholder for a missing or invalid time', () => {
    expect(formatRelativeTime(null, now)).toBe('--')
    expect(formatRelativeTime('soon', now)).toBe('--')
  })

  it('uses the interface language', () => {
    document.documentElement.lang = 'zh-CN'
    expect(formatRelativeTime('2026-05-19T12:00:00.000Z', now)).toBe('昨天')
  })
})
