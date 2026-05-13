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

  it('fills in zero for keys the backend did not return', () => {
    // Sanity check that the dashboard never crashes on a partial response —
    // e.g. an admin payment-status breakdown that includes only the statuses
    // currently in use should still surface the full enum set with zeros.
    const result = normalizeBreakdown([], ['PENDING', 'PAID', 'REFUNDED'])
    expect(result).toEqual([
      { key: 'PENDING', value: 0 },
      { key: 'PAID', value: 0 },
      { key: 'REFUNDED', value: 0 },
    ])
  })

  it('drops unknown keys not in the desired set so the donut chart cannot drift', () => {
    const result = normalizeBreakdown(
      [
        { key: 'PENDING', value: 1 },
        { key: 'UNEXPECTED_LEGACY_KEY', value: 99 },
      ],
      ['PENDING', 'PAID'],
    )
    expect(result.map((item) => item.key)).toEqual(['PENDING', 'PAID'])
    expect(result.find((item) => item.key === 'PENDING')?.value).toBe(1)
    // The unexpected key did not leak through, and PAID is still padded with 0.
    expect(result.find((item) => item.key === 'PAID')?.value).toBe(0)
  })

  it('detects trend and breakdown emptiness safely', () => {
    expect(hasTrendData([{ label: 'Mon', value: 0 }])).toBe(false)
    expect(hasTrendData([{ label: 'Tue', value: 3 }])).toBe(true)
    expect(hasBreakdownData([{ key: 'OPEN', value: 0 }])).toBe(false)
    expect(hasBreakdownData([{ key: 'OPEN', value: 1 }])).toBe(true)
  })

  it('treats fully empty trend/breakdown arrays as having no data', () => {
    expect(hasTrendData([])).toBe(false)
    expect(hasBreakdownData([])).toBe(false)
  })

  it('translates numeric and enum analytics keys', () => {
    expect(translateAnalyticsKey('4', t)).toBe('4 star')
    expect(translateAnalyticsKey('APPROVED', t)).toBe('analytics.enums.APPROVED')
    expect(translateAnalyticsKey('custom_value', t)).toBe('Custom Value')
    expect(formatAnalyticsKey('support_ticket_status')).toBe('Support Ticket Status')
  })

  it('falls back to title-cased text when an enum is not in the shared label map', () => {
    // Whenever a backend enum lands in a dashboard before the i18n map
    // catches up, the chart still renders something readable instead of
    // showing the raw UPPER_SNAKE token to end users.
    expect(translateAnalyticsKey('BRAND_NEW_STATUS', t)).toBe('Brand New Status')
  })
})
