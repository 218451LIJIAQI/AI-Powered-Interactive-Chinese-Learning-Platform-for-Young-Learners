import { describe, expect, it } from 'vitest'

import {
  formatAnalyticsKey,
  hasBreakdownData,
  hasTrendData,
  normalizeBreakdown,
  translateAnalyticsKey,
} from './analytics'

describe('analytics utilities', () => {
  const t = (key: string, named?: Record<string, unknown>) => {
    if (key === 'analytics.ratingBucket') {
      return `${named?.count} star`
    }

    return key
  }

  it('normalizes a breakdown to the requested key order', () => {
    const result = normalizeBreakdown(
      [
        { key: 'APPROVED', value: 5 },
        { key: 'PENDING', value: 2 },
      ],
      ['PENDING', 'APPROVED', 'REJECTED'],
    )

    expect(result).toEqual([
      { key: 'PENDING', value: 2 },
      { key: 'APPROVED', value: 5 },
      { key: 'REJECTED', value: 0 },
    ])
  })

  it('detects trend and breakdown emptiness safely', () => {
    expect(hasTrendData([{ label: 'Mon', value: 0 }])).toBe(false)
    expect(hasTrendData([{ label: 'Tue', value: 3 }])).toBe(true)
    expect(hasBreakdownData([{ key: 'OPEN', value: 0 }])).toBe(false)
    expect(hasBreakdownData([{ key: 'OPEN', value: 1 }])).toBe(true)
  })

  it('translates numeric and enum analytics keys', () => {
    expect(translateAnalyticsKey('4', t)).toBe('4 star')
    expect(translateAnalyticsKey('APPROVED', t)).toBe('analytics.enums.APPROVED')
    expect(translateAnalyticsKey('custom_value', t)).toBe('Custom Value')
    expect(formatAnalyticsKey('support_ticket_status')).toBe('Support Ticket Status')
  })
})
