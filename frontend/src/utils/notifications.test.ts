import { describe, expect, it } from 'vitest'

import type { NotificationResponse } from '@/types/api'

import { markNotificationRead, notificationQuery, notificationTarget, pagePreviewNotifications } from './notifications'

function notification(id: number, status: 'UNREAD' | 'READ', targetPath: string | null = null): NotificationResponse {
  return {
    id,
    title: `Notice ${id}`,
    message: 'Message',
    type: 'SYSTEM',
    status,
    targetType: null,
    targetId: null,
    targetPath,
    createdAt: '2026-05-18T08:00:00Z',
    readAt: status === 'READ' ? '2026-05-18T09:00:00Z' : null,
  }
}

describe('notifications page helpers (D20)', () => {
  it('sends the server status filter only for the unread / read tabs', () => {
    expect(notificationQuery('ALL', 0)).toEqual({ page: 0, size: 10 })
    expect(notificationQuery('UNREAD', 2)).toEqual({ status: 'UNREAD', page: 2, size: 10 })
    expect(notificationQuery('READ', -3, 5)).toEqual({ status: 'READ', page: 0, size: 5 })
  })

  it('filters and pages preview notifications like the server', () => {
    const items = [1, 2, 3, 4, 5].map((id) => notification(id, id % 2 ? 'UNREAD' : 'READ'))

    const unread = pagePreviewNotifications(items, 'UNREAD', 0, 2)
    expect(unread.items.map((item) => item.id)).toEqual([1, 3])
    expect(unread).toMatchObject({ totalItems: 3, totalPages: 2, page: 0 })

    expect(pagePreviewNotifications(items, 'UNREAD', 9, 2).items.map((item) => item.id)).toEqual([5])
    expect(pagePreviewNotifications(items, 'READ', 0, 10).items.map((item) => item.id)).toEqual([2, 4])
    expect(pagePreviewNotifications([], 'ALL', 0)).toMatchObject({ items: [], totalPages: 1 })
  })

  it('opens only known first-party target paths', () => {
    expect(notificationTarget(notification(1, 'UNREAD', '/student/practice-submissions'))).toBe(
      '/student/practice-submissions',
    )
    expect(notificationTarget(notification(1, 'UNREAD', 'https://evil.example'))).toBeNull()
    expect(notificationTarget(notification(1, 'UNREAD', null))).toBeNull()
  })

  it('marks a single notification as read locally', () => {
    const items = [notification(1, 'UNREAD'), notification(2, 'UNREAD')]
    const updated = markNotificationRead(items, 2, '2026-05-19T00:00:00Z')
    expect(updated.map((item) => item.status)).toEqual(['UNREAD', 'READ'])
    expect(updated[1].readAt).toBe('2026-05-19T00:00:00Z')
    expect(items[1].status).toBe('UNREAD')
  })
})
