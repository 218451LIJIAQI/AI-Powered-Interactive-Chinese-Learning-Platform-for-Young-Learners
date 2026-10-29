import type { NotificationQuery } from '@/api/services'
import type { NotificationResponse, NotificationStatus, UserRole } from '@/types/api'

/** Filter tabs of the notifications page (FRNOTIF02); `ALL` omits the server `status` parameter. */
export type NotificationFilter = 'ALL' | NotificationStatus

const NOTIFICATION_PAGE_SIZE = 20
/** Window event the topbar listens to so the unread badge refreshes after read actions. */
export const NOTIFICATION_READ_EVENT = 'mandarin-spark:notification-read'

export function notificationQuery(filter: NotificationFilter, page: number): NotificationQuery {
  const query: NotificationQuery = { page: Math.max(0, Math.floor(page)), size: NOTIFICATION_PAGE_SIZE }
  if (filter !== 'ALL') query.status = filter
  return query
}

/** Marks one notification read in a list without refetching (optimistic local update). */
export function markNotificationRead(
  items: readonly NotificationResponse[],
  id: number,
  readAt = new Date().toISOString(),
): NotificationResponse[] {
  return items.map((item) => (item.id === id && item.status !== 'READ' ? { ...item, status: 'READ', readAt } : item))
}

export function announceNotificationsRead(): void {
  window.dispatchEvent(new CustomEvent(NOTIFICATION_READ_EVENT))
}

/** The tag and icon of a notification type (namespace `notifications`); an unknown type reads as Account. */
interface NotificationKind {
  readonly labelKey: string
  readonly icon: string
}

const ACCOUNT_KIND: NotificationKind = { labelKey: 'notifications.typeAccount', icon: 'account' }

const NOTIFICATION_KINDS: Readonly<Record<string, NotificationKind>> = {
  LIVE_CLASS_REMINDER: { labelKey: 'notifications.typeLiveSession', icon: 'live' },
  TEACHER_FEEDBACK: { labelKey: 'notifications.typeFeedback', icon: 'chat' },
  SUPPORT_REQUEST: { labelKey: 'notifications.typeSupport', icon: 'help' },
  AI_REPORT_REVIEW: { labelKey: 'notifications.typeAiReport', icon: 'flag' },
  CONTENT_REVIEW: { labelKey: 'notifications.typeContentReview', icon: 'checklist' },
  CLASS_ENROLMENT: { labelKey: 'notifications.typeStudents', icon: 'students' },
  MODERATION_ACTION: { labelKey: 'notifications.typeModeration', icon: 'shield' },
  PROGRESS_UPDATE: { labelKey: 'notifications.typeProgress', icon: 'progress' },
  ADMIN_TASK: { labelKey: 'notifications.typeAdminTask', icon: 'audit' },
  SYSTEM: ACCOUNT_KIND,
}

/** Teacher decisions read as "Verification" for the teacher and "Teacher application" for admins. */
export function notificationKind(type: string, role: UserRole | null): NotificationKind {
  if (type === 'TEACHER_APPROVAL') {
    return role === 'ADMIN'
      ? { labelKey: 'notifications.typeTeacherApplication', icon: 'verification' }
      : { labelKey: 'notifications.typeVerification', icon: 'verified' }
  }
  return NOTIFICATION_KINDS[type] ?? ACCOUNT_KIND
}
