import { describe, expect, it } from 'vitest'

import type { NotificationResponse } from '@/types/api'

import { markNotificationRead, notificationKind, notificationQuery } from './notifications'

function notification(id: number, status: 'UNREAD' | 'READ'): NotificationResponse {
  return {
    id,
    title: `Notice ${id}`,
    message: 'Message',
    type: 'SYSTEM',
    status,
    targetPath: null,
    createdAt: '2026-05-18T08:00:00Z',
    readAt: status === 'READ' ? '2026-05-18T09:00:00Z' : null,
  }
}

describe('notifications page helpers (FRNOTIF02)', () => {
  it('sends the server status filter only for the unread / read tabs, 20 per page', () => {
    expect(notificationQuery('ALL', 0)).toEqual({ page: 0, size: 20 })
    expect(notificationQuery('UNREAD', 2)).toEqual({ status: 'UNREAD', page: 2, size: 20 })
    expect(notificationQuery('READ', -3)).toEqual({ status: 'READ', page: 0, size: 20 })
  })

  it('marks a single notification as read locally', () => {
    const items = [notification(1, 'UNREAD'), notification(2, 'UNREAD')]
    const updated = markNotificationRead(items, 2, '2026-05-19T00:00:00Z')
    expect(updated.map((item) => item.status)).toEqual(['UNREAD', 'READ'])
    expect(updated[1].readAt).toBe('2026-05-19T00:00:00Z')
    expect(items[1].status).toBe('UNREAD')
  })

  it('names each notification type, by role for teacher decisions, and falls back to Account', () => {
    expect(notificationKind('LIVE_CLASS_REMINDER', 'STUDENT').labelKey).toBe('notifications.typeLiveSession')
    expect(notificationKind('SUPPORT_REQUEST', 'TEACHER').labelKey).toBe('notifications.typeSupport')
    expect(notificationKind('TEACHER_APPROVAL', 'ADMIN').labelKey).toBe('notifications.typeTeacherApplication')
    expect(notificationKind('TEACHER_APPROVAL', 'TEACHER').labelKey).toBe('notifications.typeVerification')
    expect(notificationKind('SOMETHING_NEW', 'STUDENT').labelKey).toBe('notifications.typeAccount')
  })
})
