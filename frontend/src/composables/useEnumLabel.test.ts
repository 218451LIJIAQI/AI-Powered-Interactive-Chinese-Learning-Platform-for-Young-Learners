import { describe, expect, it } from 'vitest'

import { formatEnumValue } from './useEnumLabel'

describe('formatEnumValue', () => {
  it('converts UPPER_SNAKE_CASE to Title Case', () => {
    expect(formatEnumValue('ACTION_TAKEN')).toBe('Action Taken')
    expect(formatEnumValue('IN_PROGRESS')).toBe('In Progress')
    expect(formatEnumValue('PENDING')).toBe('Pending')
  })

  it('handles single-word values', () => {
    expect(formatEnumValue('OPEN')).toBe('Open')
    expect(formatEnumValue('LOW')).toBe('Low')
  })

  it('handles double-underscore and trailing underscore values gracefully', () => {
    expect(formatEnumValue('FOO__BAR')).toBe('Foo Bar')
    expect(formatEnumValue('FOO_')).toBe('Foo')
  })

  it('handles already-lowercase or mixed values', () => {
    expect(formatEnumValue('mixed_value')).toBe('Mixed Value')
  })
})
