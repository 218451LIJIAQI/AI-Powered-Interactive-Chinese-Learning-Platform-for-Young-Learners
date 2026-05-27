<template>
  <div class="page-stack admin-dashboard-page">
    <PageHeaderBlock
      title="Admin Control Center"
      description="Review platform state, teacher approvals, moderation queues, finance signals, and system health."
      icon="verification"
    >
      <template #actions>
        <div class="admin-dashboard-date-chip">
          <IconGlyph name="calendar" :size="18" />
          <span>{{ todayLabel }}</span>
        </div>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" :error="pageError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-dashboard-kpis">
        <article v-for="card in statCards" :key="card.label" class="admin-dashboard-kpi">
          <div class="admin-dashboard-kpi-icon" :class="`is-${card.tone}`">
            <IconGlyph :name="card.icon" :size="22" />
          </div>
          <div class="admin-dashboard-kpi-copy">
            <small>{{ card.label }}</small>
            <strong>{{ card.value }}</strong>
            <span>{{ card.copy }}</span>
          </div>
        </article>
      </section>

      <section class="admin-dashboard-grid">
        <article class="panel admin-dashboard-panel">
          <div class="row-between admin-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Teacher Verification Queue</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/teacher-verification')">
              View All
            </button>
          </div>

          <div v-if="teacherQueueItems.length" class="admin-dashboard-list">
            <article v-for="item in teacherQueueItems" :key="item.teacherId" class="admin-dashboard-row">
              <PortraitAvatar :size="50" :kind="item.avatarKind" />
              <div class="admin-dashboard-copy">
                <strong>{{ item.fullName }}</strong>
                <span>{{ item.email }}</span>
                <small>{{ item.appliedLabel }}</small>
              </div>
              <StatusPill label="Pending" tone="warning" />
            </article>
          </div>
          <StatePanel
            v-else
            icon="verification"
            title="No pending teachers"
            message="Teacher applications waiting for review will appear here."
          />
        </article>

        <article class="panel admin-dashboard-panel">
          <div class="row-between admin-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Moderation and AI Reports</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/moderation')">
              Open Queue
            </button>
          </div>

          <div v-if="moderationItems.length" class="admin-dashboard-list">
            <article v-for="item in moderationItems" :key="item.id" class="admin-dashboard-row">
              <div class="admin-dashboard-badge" :class="`is-${item.tone}`">
                <IconGlyph :name="item.icon" :size="16" />
              </div>
              <div class="admin-dashboard-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.copy }}</span>
                <small>{{ item.meta }}</small>
              </div>
              <StatusPill :label="item.label" :tone="item.statusTone" />
            </article>
          </div>
          <StatePanel
            v-else
            icon="moderation"
            title="No open moderation items"
            message="Moderation records and AI response reports will show here when they exist."
          />
        </article>

        <article class="panel admin-dashboard-panel">
          <div class="row-between admin-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Admin Notifications</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/settings')">View All</button>
          </div>

          <div v-if="notifications.length" class="admin-dashboard-list">
            <NotificationLinkButton
              v-for="item in notifications.slice(0, 4)"
              :key="item.id"
              :notification-id="item.id"
              :target-path="item.targetPath"
              fallback-to="/admin/settings"
              @read="markAdminNotificationRead"
            >
              <article class="admin-dashboard-row">
                <div class="admin-dashboard-dot" :class="{ 'is-unread': item.status !== 'READ' }" />
                <div class="admin-dashboard-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.message }}</span>
                  <small>{{ formatDateTime(item.createdAt) }}</small>
                </div>
              </article>
            </NotificationLinkButton>
          </div>
          <StatePanel
            v-else
            icon="bell"
            title="No admin notifications"
            message="Unread and recent admin notifications will appear here."
          />
        </article>

        <article class="panel admin-dashboard-panel admin-dashboard-panel--wide">
          <div class="row-between admin-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Platform Analytics Snapshot</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/analytics')">
              Open Analytics
            </button>
          </div>

          <div class="admin-dashboard-analytics-grid">
            <article class="admin-dashboard-mini-stat">
              <small>Total Students</small>
              <strong>{{ formatNumber(analytics.overview.totalStudents) }}</strong>
              <span>From admin analytics overview</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>Total Teachers</small>
              <strong>{{ formatNumber(analytics.overview.totalTeachers) }}</strong>
              <span>Approved and pending combined</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>Published Content</small>
              <strong>{{ formatNumber(analytics.overview.publishedContentItems) }}</strong>
              <span>Visible content items tracked by analytics</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>AI Requests</small>
              <strong>{{ formatNumber(analytics.overview.aiRequests) }}</strong>
              <span>Student-facing AI usage only</span>
            </article>
          </div>

          <div v-if="registrationTrendRows.length" class="admin-dashboard-breakdown-list">
            <article v-for="item in registrationTrendRows" :key="item.label" class="admin-dashboard-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ formatNumber(item.value) }}</span>
              </div>
              <div class="admin-dashboard-track">
                <span :style="{ width: `${item.percentage}%` }" />
              </div>
            </article>
          </div>
          <p v-else class="admin-dashboard-empty-copy">
            Registration trend data will appear here once the analytics endpoint returns trend points.
          </p>
        </article>

        <article class="panel admin-dashboard-panel">
          <div class="row-between admin-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Finance Overview</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/finance')">
              Open Finance
            </button>
          </div>

          <div class="admin-dashboard-finance-grid">
            <article class="admin-dashboard-mini-stat">
              <small>Total Revenue</small>
              <strong>{{
                formatCurrency(
                  dashboard.paymentRevenueSummary.totalRevenue,
                  dashboard.paymentRevenueSummary.currencyCode,
                )
              }}</strong>
              <span>{{ formatNumber(dashboard.paymentRevenueSummary.paidTransactions) }} paid transaction(s)</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>Pending Refunds</small>
              <strong>{{ formatNumber(dashboard.pendingRefunds) }}</strong>
              <span>Requires admin decision or status update</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>Pending Payouts</small>
              <strong>{{ formatNumber(dashboard.pendingPayouts) }}</strong>
              <span>Teacher payout requests still open</span>
            </article>
          </div>
        </article>

        <article class="panel admin-dashboard-panel">
          <div class="row-between admin-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Recent Audit Logs</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/settings')">View All</button>
          </div>

          <div v-if="auditItems.length" class="admin-dashboard-list">
            <article v-for="item in auditItems" :key="item.id" class="admin-dashboard-row">
              <div class="admin-dashboard-badge" :class="`is-${item.tone}`">
                <IconGlyph :name="item.icon" :size="16" />
              </div>
              <div class="admin-dashboard-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.actionType }}</span>
                <small>{{ item.time }}</small>
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="history"
            title="No audit logs"
            message="Recent admin actions will appear here once they are recorded."
          />
        </article>

        <article class="panel admin-dashboard-panel">
          <div class="student-section-heading">
            <strong>Platform Health</strong>
          </div>

          <div class="admin-dashboard-health-banner" :class="{ 'is-up': healthStatus === 'UP' }">
            <IconGlyph name="status" :size="18" />
            <div class="stack u-gap-4">
              <strong>{{ healthStatus === 'UP' ? 'All Systems Operational' : 'Status Unavailable' }}</strong>
              <span>Checked {{ healthTimestampLabel }}</span>
            </div>
          </div>

          <div class="admin-dashboard-health-grid">
            <article class="admin-dashboard-mini-stat">
              <small>Backend Service</small>
              <strong>{{ health.service || 'backend' }}</strong>
              <span>Live health endpoint</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>Audit Entries</small>
              <strong>{{ formatNumber(analytics.overview.auditLogEntries) }}</strong>
              <span>Tracked in analytics overview</span>
            </article>
            <article class="admin-dashboard-mini-stat">
              <small>Unread Notifications</small>
              <strong>{{ formatNumber(analytics.overview.adminUnreadNotifications) }}</strong>
              <span>Admin analytics unread count</span>
            </article>
          </div>
        </article>

        <article class="panel admin-dashboard-panel">
          <div class="student-section-heading">
            <strong>Quick Actions</strong>
          </div>

          <div class="admin-dashboard-actions">
            <button
              v-for="item in quickActions"
              :key="item.label"
              class="admin-dashboard-action"
              type="button"
              @click="router.push(item.to)"
            >
              <IconGlyph :name="item.icon" :size="18" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </article>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Admin executive dashboard — the landing surface for administrators.
 *
 * <p>Aggregates platform-wide KPIs (users, courses, content review
 * queue depth, finance health, moderation backlog) into a single
 * read-only overview. Heavy drill-downs live in their own sibling
 * pages so this dashboard stays a single-fetch summary.</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { adminApi, systemApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import {
  adminAiReports,
  adminAnalytics,
  adminAuditLogs,
  adminDashboard,
  adminModerationRecords,
  adminNotifications,
  teacherApplicationsPending,
} from '@/demo/data'
import { formatCurrency, formatDate, formatDateTime, formatNumber, ratioPercent, toFiniteNumber } from '@/utils/format'

const router = useRouter()

const dashboardResource = useResource(() => adminApi.dashboard(), adminDashboard)
const analyticsResource = useResource(() => adminApi.analytics(), adminAnalytics)
const teachersResource = useResource(() => adminApi.pendingTeachers(), teacherApplicationsPending)
const moderationResource = useResource(() => adminApi.moderationRecords(), adminModerationRecords)
const notificationsResource = useResource(() => adminApi.notifications(), adminNotifications)
const auditResource = useResource(() => adminApi.auditLogs(), adminAuditLogs)
const aiReportsResource = useResource(() => adminApi.aiReports(), adminAiReports)
const healthResource = useResource(() => systemApi.health(), {
  status: 'UP',
  service: 'backend',
  timestamp: new Date().toISOString(),
})

const emptyDashboard = {
  fullName: '',
  totalStudents: 0,
  totalTeachers: 0,
  pendingTeacherApprovals: 0,
  totalModules: 0,
  totalLessons: 0,
  totalQuizzes: 0,
  totalGames: 0,
  recentUserActivity: [],
  aiUsageSummary: {
    totalRequests: 0,
    successfulRequests: 0,
  },
  paymentRevenueSummary: {
    totalTransactions: 0,
    paidTransactions: 0,
    totalRevenue: 0,
    currencyCode: 'MYR',
  },
  pendingRefunds: 0,
  pendingPayouts: 0,
  recentAuditLogs: [],
}
const emptyAnalytics = {
  overview: {
    totalStudents: 0,
    totalTeachers: 0,
    pendingTeacherApprovals: 0,
    approvedTeachers: 0,
    rejectedTeachers: 0,
    publishedContentItems: 0,
    totalRevenue: 0,
    currencyCode: 'MYR',
    pendingRefunds: 0,
    pendingPayouts: 0,
    moderationActions: 0,
    auditLogEntries: 0,
    adminUnreadNotifications: 0,
    aiRequests: 0,
  },
  userRegistrationTrend: [],
  teacherApprovalBreakdown: [],
  contentStatusBreakdown: [],
  paymentStatusBreakdown: [],
  aiStatusBreakdown: [],
}
const emptyHealth = {
  status: 'UNKNOWN',
  service: 'backend',
  timestamp: '',
}

const dashboard = computed(
  () => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? adminDashboard : emptyDashboard),
)
const analytics = computed(
  () => analyticsResource.data.value ?? (analyticsResource.usingFallback.value ? adminAnalytics : emptyAnalytics),
)
const pendingTeachers = computed(
  () => teachersResource.data.value ?? (teachersResource.usingFallback.value ? teacherApplicationsPending : []),
)
const moderationRecords = computed(
  () => moderationResource.data.value ?? (moderationResource.usingFallback.value ? adminModerationRecords : []),
)
const notifications = computed(
  () => notificationsResource.data.value ?? (notificationsResource.usingFallback.value ? adminNotifications : []),
)
const auditLogs = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? adminAuditLogs : []))
const aiReports = computed(
  () => aiReportsResource.data.value ?? (aiReportsResource.usingFallback.value ? adminAiReports : []),
)
const health = computed(
  () =>
    healthResource.data.value ??
    (healthResource.usingFallback.value
      ? {
          status: 'UP',
          service: 'backend',
          timestamp: new Date().toISOString(),
        }
      : emptyHealth),
)

const pageLoading = computed(
  () =>
    dashboardResource.loading.value ||
    analyticsResource.loading.value ||
    teachersResource.loading.value ||
    moderationResource.loading.value ||
    notificationsResource.loading.value ||
    auditResource.loading.value ||
    aiReportsResource.loading.value ||
    healthResource.loading.value,
)
const pageError = computed(
  () =>
    dashboardResource.error.value ||
    analyticsResource.error.value ||
    teachersResource.error.value ||
    moderationResource.error.value ||
    notificationsResource.error.value ||
    auditResource.error.value ||
    aiReportsResource.error.value ||
    healthResource.error.value,
)
const usingFallback = computed(
  () =>
    dashboardResource.usingFallback.value ||
    analyticsResource.usingFallback.value ||
    teachersResource.usingFallback.value ||
    moderationResource.usingFallback.value ||
    notificationsResource.usingFallback.value ||
    auditResource.usingFallback.value ||
    aiReportsResource.usingFallback.value ||
    healthResource.usingFallback.value,
)

function markAdminNotificationRead(notificationId: number) {
  notificationsResource.data.value = notifications.value.map((item) =>
    item.id === notificationId ? { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() } : item,
  )
}

const todayLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)

const statCards = computed(() => [
  {
    label: 'Total Users',
    value: formatNumber(dashboard.value.totalStudents + dashboard.value.totalTeachers),
    copy: `${formatNumber(dashboard.value.totalStudents)} students and ${formatNumber(dashboard.value.totalTeachers)} teachers`,
    icon: 'users',
    tone: 'purple',
  },
  {
    label: 'Pending Teacher Reviews',
    value: formatNumber(dashboard.value.pendingTeacherApprovals),
    copy: 'Matches the current dashboard aggregate',
    icon: 'verification',
    tone: 'gold',
  },
  {
    label: 'Moderation Actions',
    value: formatNumber(analytics.value.overview.moderationActions),
    copy: 'Tracked moderation activity from analytics',
    icon: 'moderation',
    tone: 'red',
  },
  {
    label: 'AI Requests',
    value: formatNumber(dashboard.value.aiUsageSummary.totalRequests),
    copy: `${formatNumber(dashboard.value.aiUsageSummary.successfulRequests)} successful request(s)`,
    icon: 'ai',
    tone: 'blue',
  },
  {
    label: 'Platform Revenue',
    value: formatCurrency(
      dashboard.value.paymentRevenueSummary.totalRevenue,
      dashboard.value.paymentRevenueSummary.currencyCode,
    ),
    copy: `${formatNumber(dashboard.value.paymentRevenueSummary.totalTransactions)} recorded transaction(s)`,
    icon: 'money',
    tone: 'mint',
  },
])

const teacherQueueItems = computed(() =>
  pendingTeachers.value.slice(0, 4).map((item, index) => ({
    teacherId: item.teacherId,
    fullName: item.fullName,
    email: item.email,
    appliedLabel: formatDate(item.latestSubmissionTime || item.createdAt),
    avatarKind: (['female', 'male', 'hijab', 'student'] as const)[index % 4],
  })),
)

const moderationItems = computed(() => {
  const contentItems = moderationRecords.value.slice(0, 2).map((item) => ({
    id: item.id,
    title: item.actionTaken,
    copy: `${item.targetType}: ${item.targetTitle}`,
    meta: formatDateTime(item.createdAt),
    label: 'Moderation',
    tone: 'orange',
    icon: 'moderation',
    statusTone: 'warning' as const,
  }))
  const aiItems = aiReports.value.slice(0, 2).map((item) => ({
    id: `ai-${item.id}`,
    title: formatReportReason(item.reasonCategory),
    copy: item.description || formatFeatureKey(item.featureKey),
    meta: formatDateTime(item.createdAt),
    label: formatStatus(item.status),
    tone: 'purple',
    icon: 'ai',
    statusTone: reportStatusTone(item.status),
  }))

  return [...contentItems, ...aiItems].slice(0, 4)
})

const registrationTrendRows = computed(() => {
  const points = analytics.value.userRegistrationTrend
  const max = Math.max(...points.map((item) => Math.max(0, toFiniteNumber(item.value))), 0)
  return points.slice(0, 6).map((item) => {
    const value = Math.max(0, toFiniteNumber(item.value))

    return {
      label: item.label,
      value,
      percentage: max > 0 ? Math.max(8, ratioPercent(value, max)) : 0,
    }
  })
})

const auditItems = computed(() =>
  auditLogs.value.slice(0, 4).map((item) => ({
    id: item.id,
    title: item.description,
    actionType: formatStatus(item.actionType),
    time: formatDateTime(item.createdAt),
    icon: auditIcon(item.actionType),
    tone: auditTone(item.actionType),
  })),
)

const healthStatus = computed(() => health.value.status || 'UNKNOWN')
const healthTimestampLabel = computed(() => (health.value.timestamp ? formatDateTime(health.value.timestamp) : '--'))

const quickActions = [
  { label: 'Teacher Verification', icon: 'verification', to: '/admin/teacher-verification' },
  { label: 'Moderation Queue', icon: 'moderation', to: '/admin/moderation' },
  { label: 'Analytics', icon: 'analytics', to: '/admin/analytics' },
  { label: 'Finance', icon: 'finance', to: '/admin/finance' },
]

function auditIcon(actionType: string) {
  if (actionType.includes('APPROVE')) return 'status'
  if (actionType.includes('REJECT')) return 'close'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'content'
  return 'history'
}

function auditTone(actionType: string) {
  if (actionType.includes('APPROVE')) return 'green'
  if (actionType.includes('REJECT')) return 'red'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'purple'
  return 'blue'
}

function formatStatus(status: string) {
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function formatFeatureKey(featureKey: string) {
  return formatStatus(featureKey)
}

function formatReportReason(reason: string) {
  return formatStatus(reason)
}

function reportStatusTone(status: string) {
  switch (status) {
    case 'PENDING_REVIEW':
      return 'warning' as const
    case 'IN_REVIEW':
      return 'info' as const
    case 'DISMISSED':
      return 'primary' as const
    case 'RESOLVED':
      return 'success' as const
    default:
      return 'primary' as const
  }
}

function reloadAll() {
  void dashboardResource.reload()
  void analyticsResource.reload()
  void teachersResource.reload()
  void moderationResource.reload()
  void notificationsResource.reload()
  void auditResource.reload()
  void aiReportsResource.reload()
  void healthResource.reload()
}
</script>

<style scoped>
.admin-dashboard-page {
  position: relative;
}

.admin-dashboard-date-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 16px;
  border-radius: 16px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #39486a;
  font-weight: 700;
}

.admin-dashboard-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.admin-dashboard-kpi,
.admin-dashboard-panel {
  border-radius: 24px;
}

.admin-dashboard-kpi {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 18px;
  border: 1px solid rgba(232, 236, 245, 0.94);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 24px rgba(49, 64, 94, 0.06);
}

.admin-dashboard-kpi-icon,
.admin-dashboard-badge {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
}

.admin-dashboard-kpi-icon.is-purple,
.admin-dashboard-badge.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-dashboard-kpi-icon.is-gold {
  background: rgba(255, 181, 68, 0.14);
  color: #ee9618;
}

.admin-dashboard-kpi-icon.is-red,
.admin-dashboard-badge.is-red {
  background: rgba(255, 121, 121, 0.14);
  color: #e05c52;
}

.admin-dashboard-kpi-icon.is-blue,
.admin-dashboard-badge.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-dashboard-kpi-icon.is-mint,
.admin-dashboard-badge.is-orange {
  background: rgba(111, 210, 104, 0.12);
  color: #49a347;
}

.admin-dashboard-kpi-copy,
.admin-dashboard-copy,
.admin-dashboard-mini-stat {
  display: grid;
  gap: 4px;
}

.admin-dashboard-kpi-copy small,
.admin-dashboard-kpi-copy span,
.admin-dashboard-copy span,
.admin-dashboard-copy small,
.admin-dashboard-mini-stat small,
.admin-dashboard-mini-stat span,
.admin-dashboard-empty-copy,
.admin-dashboard-breakdown-row span,
.admin-dashboard-health-banner span {
  color: #72809d;
}

.admin-dashboard-kpi-copy strong,
.admin-dashboard-mini-stat strong {
  font-family: 'Fredoka', sans-serif;
  line-height: 1;
}

.admin-dashboard-kpi-copy strong {
  font-size: 1.8rem;
}

.admin-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
}

.admin-dashboard-panel {
  grid-column: span 4;
  display: grid;
  gap: 14px;
}

.admin-dashboard-panel--wide {
  grid-column: span 8;
}

.admin-dashboard-panel-head {
  align-items: center;
}

.admin-dashboard-list,
.admin-dashboard-breakdown-list,
.admin-dashboard-actions {
  display: grid;
  gap: 12px;
}

.admin-dashboard-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-dashboard-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.admin-dashboard-copy strong,
.admin-dashboard-breakdown-row strong {
  color: #344260;
}

.admin-dashboard-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(183, 190, 209, 0.96);
}

.admin-dashboard-dot.is-unread {
  background: #7a56ff;
}

.admin-dashboard-analytics-grid,
.admin-dashboard-finance-grid,
.admin-dashboard-health-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.admin-dashboard-mini-stat {
  padding: 14px;
  border-radius: 18px;
  background: rgba(246, 248, 252, 0.96);
}

.admin-dashboard-mini-stat strong {
  font-size: 1.6rem;
}

.admin-dashboard-breakdown-row {
  display: grid;
  gap: 8px;
}

.admin-dashboard-track {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(229, 233, 244, 0.96);
}

.admin-dashboard-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #7a56ff, #9f8cff);
}

.admin-dashboard-health-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(244, 249, 240, 0.96);
  color: #339734;
}

.admin-dashboard-health-banner:not(.is-up) {
  background: rgba(248, 246, 240, 0.96);
  color: #9c7a26;
}

.admin-dashboard-actions {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.admin-dashboard-action {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 14px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #334260;
  font-weight: 700;
}

@media (max-width: 1500px) {
  .admin-dashboard-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .admin-dashboard-panel,
  .admin-dashboard-panel--wide {
    grid-column: span 6;
  }

  .admin-dashboard-analytics-grid,
  .admin-dashboard-finance-grid,
  .admin-dashboard-health-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .admin-dashboard-kpis,
  .admin-dashboard-grid,
  .admin-dashboard-actions {
    grid-template-columns: 1fr;
  }

  .admin-dashboard-panel,
  .admin-dashboard-panel--wide {
    grid-column: auto;
  }

  .admin-dashboard-row {
    grid-template-columns: 1fr;
  }
}
</style>
