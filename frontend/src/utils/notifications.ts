import type { NotificationQuery } from '@/api/services'
import type { NotificationResponse, NotificationStatus, PageResponse } from '@/types/api'
import { normalizeAppRoute } from '@/utils/routeCompat'

/** Filter tabs of the notifications page (D20); `ALL` omits the server `status` parameter. */
export type NotificationFilter = 'ALL' | NotificationStatus

const NOTIFICATION_PAGE_SIZE = 10
/** Window event the topbar listens to so the unread badge refreshes after read actions. */
export const NOTIFICATION_READ_EVENT = 'mandarin-spark:notification-read'

export function notificationQuery(
  filter: NotificationFilter,
  page: number,
  size = NOTIFICATION_PAGE_SIZE,
): NotificationQuery {
  const query: NotificationQuery = { page: Math.max(0, Math.floor(page)), size }
  if (filter !== 'ALL') query.status = filter
  return query
}

/** Client-side equivalent of the server filter + paging, used for preview fixtures. */
export function pagePreviewNotifications(
  items: readonly NotificationResponse[],
  filter: NotificationFilter,
  page: number,
  size = NOTIFICATION_PAGE_SIZE,
): PageResponse<NotificationResponse> {
  const filtered = filter === 'ALL' ? [...items] : items.filter((item) => item.status === filter)
  const totalPages = Math.max(1, Math.ceil(filtered.length / size))
  const safePage = Math.min(Math.max(0, page), totalPages - 1)
  return {
    items: filtered.slice(safePage * size, safePage * size + size),
    page: safePage,
    size,
    totalItems: filtered.length,
    totalPages,
  }
}

/** Only first-party, known app routes may be opened from a notification. */
export function notificationTarget(item: Pick<NotificationResponse, 'targetPath'>): string | null {
  return normalizeAppRoute(item.targetPath ?? null)
}

/** Marks one notification read in a list without refetching (optimistic local update). */
export function markNotificationRead(
  items: readonly NotificationResponse[],
  id: number,
  readAt = new Date().toISOString(),
): NotificationResponse[] {
  return items.map((item) => (item.id === id && item.status !== 'READ' ? { ...item, status: 'READ', readAt } : item))
}

export function announceNotificationsRead(notificationId?: number): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(NOTIFICATION_READ_EVENT, { detail: { notificationId } }))
  }
}
