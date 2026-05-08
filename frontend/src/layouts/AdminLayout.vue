<script setup lang="ts">
import AppShell from '../components/common/AppShell.vue'

const navItems = [
  { key: 'admin-dashboard', labelKey: 'nav.adminDashboard', routeName: 'admin-dashboard', icon: 'House' },
  { key: 'admin-users', labelKey: 'nav.userManagement', routeName: 'admin-users', icon: 'UserFilled' },
  { key: 'admin-students', labelKey: 'nav.students', routeName: 'admin-students', icon: 'Avatar' },
  {
    key: 'admin-teacher-approval',
    labelKey: 'nav.teacherApproval',
    routeName: 'admin-teacher-approval',
    icon: 'CircleCheck',
  },
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
    key: 'admin-ai-usage-logs',
    labelKey: 'nav.aiUsageLogs',
    routeName: 'admin-ai-usage-logs',
    icon: 'DataLine',
  },
  { key: 'admin-payments', labelKey: 'nav.paymentManagement', routeName: 'admin-payments', icon: 'CreditCard' },
  { key: 'admin-refunds', labelKey: 'nav.refundManagement', routeName: 'admin-refunds', icon: 'RefreshLeft' },
  { key: 'admin-payouts', labelKey: 'nav.payoutManagement', routeName: 'admin-payouts', icon: 'Wallet' },
  {
    key: 'admin-subscription-plans',
    labelKey: 'nav.subscriptionPlans',
    routeName: 'admin-subscription-plans',
    icon: 'Tickets',
  },
  { key: 'admin-reports', labelKey: 'nav.reports', routeName: 'admin-reports', icon: 'Warning' },
  { key: 'admin-reviews', labelKey: 'nav.reviewsModeration', routeName: 'admin-reviews', icon: 'ChatRound' },
  {
    key: 'admin-notifications',
    labelKey: 'nav.notifications',
    routeName: 'admin-notifications',
    icon: 'Bell',
  },
  {
    key: 'admin-support-tickets',
    labelKey: 'nav.supportTickets',
    routeName: 'admin-support-tickets',
    icon: 'Service',
  },
  {
    key: 'admin-activity-logs',
    labelKey: 'nav.activityLogs',
    routeName: 'admin-activity-logs',
    icon: 'Memo',
  },
  { key: 'admin-settings', labelKey: 'nav.systemSettings', routeName: 'admin-settings', icon: 'Setting' },
]
</script>

<template>
  <AppShell
    variant="admin"
    title="Admin Control Center"
    subtitle="Track platform health, approvals, and operational signals from a dashboard-ready workspace."
    :nav-items="navItems"
  />
</template>
