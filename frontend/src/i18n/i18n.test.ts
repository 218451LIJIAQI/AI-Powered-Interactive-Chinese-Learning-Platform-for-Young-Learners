import { describe, expect, it } from 'vitest'

import { i18n, type SupportedLocale } from './index'

type MessageTree = Record<string, unknown>

const LOCALES: SupportedLocale[] = ['en', 'ms', 'zh']

const DEMO_FACING_KEYS = [
  'common.appName',
  'common.forbidden',
  'common.yes',
  'common.no',
  'common.cancel',
  'nav.home',
  'nav.login',
  'nav.register',
  'nav.studentDashboard',
  'nav.teacherDashboard',
  'nav.adminDashboard',
  'auth.logout',
  'auth.loginHeading',
  'auth.messages.loginSuccess',
  'dashboard.completed',
  'notFound.title',
  'notFound.description',
  'notFound.backHome',
  'phase11.notificationsTitle',
  'phase11.markAllAsRead',
  'phase11.emptyNotifications',
  'notification.status.UNREAD',
  'notification.status.READ',
  'notification.type.GENERAL',
  'notification.savedItem.LESSON',
  'enums.ticketStatus.OPEN',
  'enums.ticketPriority.HIGH',
  'enums.paymentStatus.PAID',
  'enums.refundStatus.APPROVED',
  'enums.payoutStatus.PENDING',
  'enums.reviewStatus.VISIBLE',
  'enums.reportStatus.ACTION_TAKEN',
  'enums.reportTargetType.LESSON',
  'enums.contentStatus.PUBLISHED',
  'enums.managedContentType.MODULE',
  'enums.liveClassStatus.ONGOING',
  'enums.moderationAction.REQUEST_REVISION',
  'enums.moderationTargetType.TEACHER_PROFILE',
  'enums.meetingPlatform.GOOGLE_MEET',
  'enums.teacherStatus.APPROVED',
  'enums.accessType.FREE',
  'enums.aiStatus.SUCCESS',
]

function resolveKey(messages: MessageTree, key: string): unknown {
  return key.split('.').reduce<unknown>((value, part) => {
    if (value && typeof value === 'object' && part in (value as Record<string, unknown>)) {
      return (value as Record<string, unknown>)[part]
    }
    return undefined
  }, messages)
}

describe('i18n coverage for demo-facing keys', () => {
  it.each(LOCALES)('locale "%s" provides a non-empty string for every demo-facing key', (locale) => {
    const messages = (i18n.global.getLocaleMessage(locale) ?? {}) as MessageTree

    for (const key of DEMO_FACING_KEYS) {
      const value = resolveKey(messages, key)
      expect(
        typeof value === 'string' && value.trim().length > 0,
        `Missing i18n key "${key}" in locale "${locale}"`,
      ).toBe(true)
    }
  })
})
