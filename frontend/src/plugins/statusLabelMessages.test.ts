import { describe, expect, it } from 'vitest'

import { humanizeEnumValue, statusLabelKey, statusLabelMessages } from './statusLabelMessages'
import { auditActionKey } from '@/utils/auditActions'

describe('status label messages', () => {
  it('labels every lesson mastery value in every language', () => {
    for (const value of ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'MASTERED']) {
      expect(statusLabelKey('mastery', value), value).toBe(`mastery_${value}`)
    }
    expect(statusLabelKey('mastery', 'in_progress')).toBe('mastery_IN_PROGRESS')
    expect(statusLabelMessages.ms.mastery_MASTERED).toBe('Dikuasai')
    expect(statusLabelMessages['zh-CN'].mastery_NOT_STARTED).toBe('未开始')
  })

  it('keeps names only for audit actions the audit-action table does not name (older entries)', () => {
    const fallbackCodes = Object.keys(statusLabelMessages.en)
      .filter((key) => key.startsWith('auditAction_'))
      .map((key) => key.slice('auditAction_'.length))
    expect(fallbackCodes).toContain('TEACHER_LESSON_CREATE')
    for (const code of fallbackCodes) expect(auditActionKey(code), code).toBeNull()
  })

  it('returns null for unknown values and humanises them as a fallback', () => {
    expect(statusLabelKey('mastery', 'ABANDONED')).toBeNull()
    expect(statusLabelKey('mastery', '')).toBeNull()
    expect(humanizeEnumValue('WAITLISTED_LATE')).toBe('Waitlisted Late')
    expect(humanizeEnumValue(null)).toBe('')
  })
})
