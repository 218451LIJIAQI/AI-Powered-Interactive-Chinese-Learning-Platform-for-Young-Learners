<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useNotificationUnreadCount } from '../composables/useNotificationUnreadCount'

const { unreadCount } = useNotificationUnreadCount(true)

const navGroups = computed(() => [
  {
    key: 'home',
    labelKey: 'kid.navGroupHome',
    emoji: '\ud83c\udfe0',
    items: [
      { key: 'admin-dashboard', labelKey: 'nav.adminDashboard', routeName: 'admin-dashboard', icon: 'House' },
    ],
  },
  {
    key: 'people',
    labelKey: 'kid.navGroupPeople',
    emoji: '\ud83d\udc65',
    items: [
      { key: 'admin-users', labelKey: 'nav.userManagement', routeName: 'admin-users', icon: 'UserFilled' },
      { key: 'admin-students', labelKey: 'nav.students', routeName: 'admin-students', icon: 'Avatar' },
      {
        key: 'admin-teacher-approval',
        labelKey: 'nav.teacherApproval',
        routeName: 'admin-teacher-approval',
        icon: 'CircleCheck',
      },
    ],
  },
  {
    key: 'approvals',
    labelKey: 'kid.navGroupApprovals',
    emoji: '\u2705',
    items: [
      {
        key: 'admin-content-approvals',
        labelKey: 'nav.contentApproval',
        routeName: 'admin-content-approvals',
        icon: 'DocumentCopy',
      },
      {
        key: 'admin-content-moderation',
        labelKey: 'nav.contentModeration',
        routeName: 'admin-content-moderation',
        icon: 'Finished',
      },
      {
        key: 'admin-moderation',
        labelKey: 'phase11.moderationNav',
        routeName: 'admin-moderation',
        icon: 'Operation',
      },
      {
        key: 'admin-ai-usage-logs',
        labelKey: 'nav.aiUsageLogs',
        routeName: 'admin-ai-usage-logs',
        icon: 'DataLine',
      },
    ],
  },
  {
    key: 'classes',
    labelKey: 'kid.navGroupClasses',
    emoji: '\ud83c\udf93',
    items: [
      { key: 'admin-live-classes', labelKey: 'nav.liveClasses', routeName: 'admin-live-classes', icon: 'VideoCameraFilled' },
      { key: 'admin-schedule', labelKey: 'nav.schedule', routeName: 'admin-schedule', icon: 'Calendar' },
    ],
  },
  {
    key: 'finance',
    labelKey: 'kid.navGroupFinance',
    emoji: '\ud83d\udcb0',
    items: [
      { key: 'admin-payments', labelKey: 'nav.paymentManagement', routeName: 'admin-payments', icon: 'CreditCard' },
      { key: 'admin-refunds', labelKey: 'nav.refundManagement', routeName: 'admin-refunds', icon: 'RefreshLeft' },
      { key: 'admin-payouts', labelKey: 'nav.payoutManagement', routeName: 'admin-payouts', icon: 'Wallet' },
      {
        key: 'admin-subscription-plans',
        labelKey: 'nav.subscriptionPlans',
        routeName: 'admin-subscription-plans',
        icon: 'Tickets',
      },
      {
        key: 'admin-commission-settings',
        labelKey: 'commerce.commissionTitle',
        routeName: 'admin-commission-settings',
        icon: 'Setting',
      },
    ],
  },
  {
    key: 'inbox',
    labelKey: 'kid.navGroupInbox',
    emoji: '\ud83d\udc8c',
    items: [
      {
        key: 'admin-notifications',
        labelKey: 'nav.notifications',
        routeName: 'admin-notifications',
        icon: 'Bell',
        badgeCount: unreadCount.value,
      },
      {
        key: 'admin-support-tickets',
        labelKey: 'nav.supportTickets',
        routeName: 'admin-support-tickets',
        icon: 'Service',
      },
      { key: 'admin-reports', labelKey: 'nav.reports', routeName: 'admin-reports', icon: 'Warning' },
      { key: 'admin-reviews', labelKey: 'nav.reviewsModeration', routeName: 'admin-reviews', icon: 'ChatRound' },
      {
        key: 'admin-audit-logs',
        labelKey: 'nav.activityLogs',
        routeName: 'admin-audit-logs',
        icon: 'Memo',
      },
    ],
  },
  {
    key: 'system',
    labelKey: 'kid.navGroupSystem',
    emoji: '\u2699\ufe0f',
    items: [
      { key: 'admin-settings', labelKey: 'nav.systemSettings', routeName: 'admin-settings', icon: 'Setting' },
    ],
  },
])
</script>

<template>
  <AppShell variant="admin" :nav-groups="navGroups" />
</template>
