import { describe, expect, it } from 'vitest'

import { formatDate, formatDateTime } from './datetime'

describe('datetime helpers', () => {
  it('returns the fallback for null, undefined, and empty values', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
    expect(formatDate(null)).toBe('-')
  })

  it('returns a non-empty formatted string for valid dates', () => {
    const result = formatDateTime('2026-05-10T08:30:00Z')
    expect(typeof result).toBe('string')
    expect(result).not.toBe('-')
    expect(result.length).toBeGreaterThan(0)
  })

  it('returns a non-empty formatted string for a Date instance', () => {
    const result = formatDate(new Date('2026-05-10T00:00:00Z'))
    expect(typeof result).toBe('string')
    expect(result).not.toBe('-')
  })

  it('returns a custom fallback for invalid input', () => {
    expect(formatDateTime('not-a-real-date', '—')).toBe('—')
  })
})
