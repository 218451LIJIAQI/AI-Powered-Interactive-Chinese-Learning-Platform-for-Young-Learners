import { describe, expect, it } from 'vitest'

import { formatMoney } from './money'

describe('formatMoney', () => {
  it('formats a positive number to RM with two decimals', () => {
    expect(formatMoney(0)).toBe('RM 0.00')
    expect(formatMoney(12)).toBe('RM 12.00')
    expect(formatMoney(12.5)).toBe('RM 12.50')
    expect(formatMoney(1234.5678)).toBe('RM 1234.57')
  })

  it('treats null and undefined as zero so missing fields render safely', () => {
    expect(formatMoney(null)).toBe('RM 0.00')
    expect(formatMoney(undefined)).toBe('RM 0.00')
  })

  it('falls back to zero for non-finite numbers', () => {
    expect(formatMoney(Number.NaN)).toBe('RM 0.00')
    expect(formatMoney(Number.POSITIVE_INFINITY)).toBe('RM 0.00')
    expect(formatMoney(Number.NEGATIVE_INFINITY)).toBe('RM 0.00')
  })

  it('preserves negative amounts (refunds, payout reversals)', () => {
    expect(formatMoney(-15.5)).toBe('RM -15.50')
  })
})
