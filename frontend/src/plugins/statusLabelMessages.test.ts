import { describe, expect, it } from 'vitest'

import { humanizeEnumValue, statusLabelKey, statusLabelMessages } from './statusLabelMessages'
import { auditActionKey } from '@/utils/auditActions'

describe('status label messages', () => {
  it('keeps names only for audit actions the audit-action table does not name (older entries)', () => {
    const fallbackCodes = Object.keys(statusLabelMessages.en)
      .filter((key) => key.startsWith('auditAction_'))
      .map((key) => key.slice('auditAction_'.length))
    expect(fallbackCodes).toContain('TEACHER_LESSON_CREATE')
    for (const code of fallbackCodes) expect(auditActionKey(code), code).toBeNull()
  })

  it('returns null for unknown values and humanises them as a fallback', () => {
    expect(statusLabelKey('auditAction', 'ABANDONED')).toBeNull()
    expect(statusLabelKey('auditAction', '')).toBeNull()
    expect(humanizeEnumValue('WAITLISTED_LATE')).toBe('Waitlisted Late')
    expect(humanizeEnumValue(null)).toBe('')
  })
})
