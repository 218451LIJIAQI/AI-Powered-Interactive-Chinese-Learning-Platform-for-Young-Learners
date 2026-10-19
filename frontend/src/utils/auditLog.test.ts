import { describe, expect, it } from 'vitest'

import { auditLogQuery, isValidAuditDateRange } from './auditLog'

describe('audit-log server filters (D16)', () => {
  it('sends only the filled filters with page and size', () => {
    expect(auditLogQuery({ from: '', to: '', actionType: '', actor: '' }, 0, 10)).toEqual({ page: 0, size: 10 })
    expect(
      auditLogQuery({ from: '2026-05-01', to: '2026-05-31', actionType: ' content approve ', actor: 'admin@' }, 2, 10),
    ).toEqual({
      page: 2,
      size: 10,
      from: '2026-05-01',
      to: '2026-05-31',
      actionType: 'CONTENT_APPROVE',
      actor: 'admin@',
    })
  })

  it('sends a numeric actor as actorId and ignores malformed dates', () => {
    expect(auditLogQuery({ from: '05/01/2026', to: '', actionType: '', actor: ' 103 ' }, -1, 10)).toEqual({
      page: 0,
      size: 10,
      actorId: 103,
    })
  })

  it('rejects a start date after the end date', () => {
    expect(isValidAuditDateRange({ from: '2026-05-10', to: '2026-05-01' })).toBe(false)
    expect(isValidAuditDateRange({ from: '2026-05-01', to: '2026-05-01' })).toBe(true)
    expect(isValidAuditDateRange({ from: '2026-05-01', to: '' })).toBe(true)
  })
})
