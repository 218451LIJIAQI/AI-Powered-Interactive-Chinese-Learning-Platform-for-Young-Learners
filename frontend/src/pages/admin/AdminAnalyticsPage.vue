<template>
  <div class="page-stack admin-analytics-page">
    <PageHeaderBlock
      title="Analytics Overview"
      description="Review the real analytics payload exposed by the admin backend without synthetic trend fabrication."
      icon="analytics"
    >
      <template #actions>
        <div class="admin-analytics-range">
          <IconGlyph name="calendar" :size="18" />
          <span>{{ rangeLabel }}</span>
        </div>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" :error="pageError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-analytics-kpis">
        <article v-for="card in kpiCards" :key="card.label" class="admin-analytics-kpi">
          <div class="admin-analytics-kpi-icon" :class="`is-${card.tone}`">
            <IconGlyph :name="card.icon" :size="22" />
          </div>
          <div class="admin-analytics-kpi-copy">
            <small>{{ card.label }}</small>
            <strong>{{ card.value }}</strong>
            <span>{{ card.copy }}</span>
          </div>
        </article>
      </section>

      <section class="admin-analytics-grid">
        <article class="panel admin-analytics-panel admin-analytics-panel--wide">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>User Registration Trend</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/users')">Open Users</button>
          </div>

          <template v-if="analytics.userRegistrationTrend.length">
            <div class="admin-analytics-chart-shell">
              <TrendChart :values="analytics.userRegistrationTrend" />
            </div>

            <div class="admin-analytics-trend-list">
              <article
                v-for="item in analytics.userRegistrationTrend"
                :key="item.label"
                class="admin-analytics-trend-row"
              >
                <strong>{{ item.label }}</strong>
                <span>{{ formatNumber(item.value) }}</span>
              </article>
            </div>
          </template>
          <StatePanel
            v-else
            icon="analytics"
            title="No registration trend data"
            message="The analytics endpoint has not returned user registration trend points yet."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>Teacher Approval Breakdown</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/teacher-verification')">
              Open Queue
            </button>
          </div>

          <div v-if="teacherApprovalRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in teacherApprovalRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="`is-${item.tone}`" :style="{ width: `${item.percentage}%` }" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="verification"
            title="No teacher approval data"
            message="Teacher approval breakdown data will appear here once analytics provides it."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>Content Status Breakdown</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/content')">
              Open Content
            </button>
          </div>

          <div v-if="contentRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in contentRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="`is-${item.tone}`" :style="{ width: `${item.percentage}%` }" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="content"
            title="No content status data"
            message="Content status breakdown data will appear here once analytics provides it."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>Payment Status Breakdown</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/finance')">
              Open Finance
            </button>
          </div>

          <div v-if="paymentRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in paymentRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="`is-${item.tone}`" :style="{ width: `${item.percentage}%` }" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="finance"
            title="No payment status data"
            message="Payment status breakdown data will appear here once analytics provides it."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>AI Request Status Breakdown</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/moderation')">
              Open Moderation
            </button>
          </div>

          <div v-if="aiRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in aiRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="`is-${item.tone}`" :style="{ width: `${item.percentage}%` }" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="ai"
            title="No AI breakdown data"
            message="AI request breakdown data will appear here once analytics provides it."
          />
        </article>

        <article class="panel admin-analytics-panel admin-analytics-panel--wide">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>Recent AI Usage</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/moderation')">
              Open Moderation
            </button>
          </div>

          <div v-if="aiUsageRows.length" class="admin-analytics-audit-list">
            <article v-for="item in aiUsageRows" :key="item.id" class="admin-analytics-audit-row">
              <div class="admin-analytics-audit-badge" :class="`is-${item.tone}`">
                <IconGlyph name="ai" :size="16" />
              </div>
              <div class="admin-analytics-audit-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.meta }}</span>
                <small>{{ item.detail }}</small>
                <small>{{ item.time }}</small>
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="ai"
            title="No AI usage logs"
            message="The admin AI usage endpoint has not returned any student-facing usage records yet."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>Live Class Snapshot</strong>
            </div>
            <span class="muted">{{ liveClassSummaryLabel }}</span>
          </div>

          <div v-if="liveClassRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in liveClassRows" :key="item.id" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.title }}</strong>
                <StatusPill :label="item.status" :tone="item.tone" />
              </div>
              <div class="admin-analytics-notes">
                <p>{{ item.teacherLabel }}</p>
                <p>{{ item.scheduleLabel }}</p>
                <p>{{ item.enrolmentLabel }}</p>
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="classes"
            title="No live classes"
            message="No live class rows are currently returned by the admin live class endpoint."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="student-section-heading">
            <strong>System Health</strong>
          </div>

          <div class="admin-analytics-health-banner" :class="{ 'is-up': health.status === 'UP' }">
            <IconGlyph name="status" :size="18" />
            <div class="stack u-gap-4">
              <strong>{{ health.status === 'UP' ? 'All Systems Operational' : 'Status Unavailable' }}</strong>
              <span>Checked {{ healthCheckedLabel }}</span>
            </div>
          </div>

          <div class="admin-analytics-health-grid">
            <article class="admin-analytics-mini-stat">
              <small>Service</small>
              <strong>{{ health.service || 'backend' }}</strong>
              <span>From live health endpoint</span>
            </article>
            <article class="admin-analytics-mini-stat">
              <small>Audit Entries</small>
              <strong>{{ formatNumber(analytics.overview.auditLogEntries) }}</strong>
              <span>Analytics overview count</span>
            </article>
            <article class="admin-analytics-mini-stat">
              <small>Unread Admin Notifications</small>
              <strong>{{ formatNumber(analytics.overview.adminUnreadNotifications) }}</strong>
              <span>Analytics overview count</span>
            </article>
          </div>
        </article>

        <article class="panel admin-analytics-panel admin-analytics-panel--wide">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>Recent Audit Log</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/admin/settings')">View All</button>
          </div>

          <div v-if="auditItems.length" class="admin-analytics-audit-list">
            <article v-for="item in auditItems" :key="item.id" class="admin-analytics-audit-row">
              <div class="admin-analytics-audit-badge" :class="`is-${item.tone}`">
                <IconGlyph :name="item.icon" :size="16" />
              </div>
              <div class="admin-analytics-audit-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.actionType }}</span>
                <small>{{ item.time }}</small>
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="history"
            title="No audit data"
            message="Recent audit entries will appear here once the backend records admin actions."
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="student-section-heading">
            <strong>Coverage Notes</strong>
          </div>

          <div class="admin-analytics-notes">
            <p>
              This page intentionally renders only fields that the current admin analytics and audit endpoints expose.
            </p>
            <p>
              Unsupported metrics such as uptime percentages, fabricated approval trends, and language-share estimates
              were removed during audit cleanup.
            </p>
          </div>
        </article>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Admin analytics surface — platform-wide metrics, trend charts,
 * conversion / retention funnels.
 *
 * <p>Aggregates {@code adminApi.analytics()} into stacked trend
 * cards using {@link TrendChart} — pure SVG sparklines, no external
 * chart library, so the page stays light and accessible.</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { adminApi, systemApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import TrendChart from '@/components/common/TrendChart.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminAnalytics, adminAuditLogs } from '@/demo/data'
import type { AdminAiUsageLogResponse, AnalyticsBreakdownItem, LiveClassResponse } from '@/types/api'
import { formatCurrency, formatDateTime, formatNumber } from '@/utils/format'

const router = useRouter()

const analyticsResource = useResource(() => adminApi.analytics(), adminAnalytics)
const auditResource = useResource(() => adminApi.auditLogs(), adminAuditLogs)
const aiUsageResource = useResource(() => adminApi.aiUsageLogs({ size: 8 }), {
  items: [],
  page: 0,
  size: 8,
  totalItems: 0,
  totalPages: 0,
})
const liveClassesResource = useResource(() => adminApi.liveClasses(), [])
const healthResource = useResource(() => systemApi.health(), {
  status: 'UP',
  service: 'backend',
  timestamp: new Date().toISOString(),
})

const emptyAnalytics = {
  overview: {
    totalStudents: 0,
    totalTeachers: 0,
    pendingTeacherApprovals: 0,
    approvedTeachers: 0,
    rejectedTeachers: 0,
    publishedContentItems: 0,
    totalRevenue: 0,
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

const analytics = computed(
  () => analyticsResource.data.value ?? (analyticsResource.usingFallback.value ? adminAnalytics : emptyAnalytics),
)
const auditLogs = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? adminAuditLogs : []))
const aiUsageLogs = computed(() => aiUsageResource.data.value?.items ?? [])
const liveClasses = computed(() => liveClassesResource.data.value ?? [])
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
    analyticsResource.loading.value ||
    auditResource.loading.value ||
    aiUsageResource.loading.value ||
    liveClassesResource.loading.value ||
    healthResource.loading.value,
)
const pageError = computed(
  () =>
    analyticsResource.error.value ||
    auditResource.error.value ||
    aiUsageResource.error.value ||
    liveClassesResource.error.value ||
    healthResource.error.value,
)
const usingFallback = computed(
  () =>
    analyticsResource.usingFallback.value ||
    auditResource.usingFallback.value ||
    aiUsageResource.usingFallback.value ||
    liveClassesResource.usingFallback.value ||
    healthResource.usingFallback.value,
)

const rangeLabel = computed(() =>
  analytics.value.userRegistrationTrend.length
    ? `${analytics.value.userRegistrationTrend.length} tracked registration point(s)`
    : 'Current backend snapshot',
)

const kpiCards = computed(() => [
  {
    label: 'Students',
    value: formatNumber(analytics.value.overview.totalStudents),
    copy: 'Current analytics overview total',
    icon: 'students',
    tone: 'purple',
  },
  {
    label: 'Teachers',
    value: formatNumber(analytics.value.overview.totalTeachers),
    copy: `${formatNumber(analytics.value.overview.pendingTeacherApprovals)} pending approval(s)`,
    icon: 'users',
    tone: 'blue',
  },
  {
    label: 'Published Content',
    value: formatNumber(analytics.value.overview.publishedContentItems),
    copy: 'Visible items tracked by analytics',
    icon: 'content',
    tone: 'green',
  },
  {
    label: 'AI Requests',
    value: formatNumber(analytics.value.overview.aiRequests),
    copy: 'Student-facing AI request volume',
    icon: 'ai',
    tone: 'gold',
  },
  {
    label: 'Revenue',
    value: formatCurrency(analytics.value.overview.totalRevenue),
    copy: `${formatNumber(analytics.value.overview.pendingRefunds)} refund(s) pending`,
    icon: 'finance',
    tone: 'sky',
  },
  {
    label: 'Audit Entries',
    value: formatNumber(analytics.value.overview.auditLogEntries),
    copy: `${formatNumber(analytics.value.overview.adminUnreadNotifications)} admin notification(s) unread`,
    icon: 'history',
    tone: 'lavender',
  },
])

const teacherApprovalRows = computed(() => normalizeBreakdown(analytics.value.teacherApprovalBreakdown))
const contentRows = computed(() => normalizeBreakdown(analytics.value.contentStatusBreakdown))
const paymentRows = computed(() => normalizeBreakdown(analytics.value.paymentStatusBreakdown))
const aiRows = computed(() => normalizeBreakdown(analytics.value.aiStatusBreakdown))

const auditItems = computed(() =>
  auditLogs.value.slice(0, 5).map((item) => ({
    id: item.id,
    title: item.description,
    actionType: formatStatus(item.actionType),
    time: formatDateTime(item.createdAt),
    icon: auditIcon(item.actionType),
    tone: auditTone(item.actionType),
  })),
)

const aiUsageRows = computed(() =>
  aiUsageLogs.value.map((item) => ({
    id: item.id,
    title: `${formatFeatureKey(item.featureKey)} · ${formatStatus(item.status)}`,
    meta: [item.userName || item.userEmail || 'Unknown user', item.userRole || 'Unknown role']
      .filter(Boolean)
      .join(' / '),
    detail:
      [
        item.providerName || 'provider unavailable',
        item.modelName || 'model unavailable',
        item.tokenCount != null ? `${formatNumber(item.tokenCount)} tokens` : null,
        item.errorSummary || item.outputSummary || item.inputSummary || null,
      ]
        .filter(Boolean)
        .join(' · ') || 'No request summary available.',
    time: formatDateTime(item.createdAt),
    tone: aiUsageTone(item),
  })),
)

const liveClassRows = computed(() =>
  liveClasses.value.slice(0, 5).map((item) => ({
    id: item.id,
    title: item.title,
    teacherLabel: item.teacher?.displayName ? `Teacher: ${item.teacher.displayName}` : 'Teacher unavailable',
    scheduleLabel: `${formatDateTime(item.startTime)} to ${formatDateTime(item.endTime)}`,
    enrolmentLabel: `${formatNumber(item.enrolledCount)} enrolled / ${item.maxParticipants ?? 'Unlimited'} capacity`,
    status: formatStatus(item.liveClassStatus),
    tone: liveClassTone(item),
  })),
)

const healthCheckedLabel = computed(() => (health.value.timestamp ? formatDateTime(health.value.timestamp) : '--'))
const liveClassSummaryLabel = computed(() => `${formatNumber(liveClasses.value.length)} row(s) loaded`)

function normalizeBreakdown(items: AnalyticsBreakdownItem[]) {
  const total = items.reduce((sum, item) => sum + (item.value ?? 0), 0)
  const tones = ['purple', 'green', 'orange', 'blue'] as const

  return items.map((item, index) => {
    const percentage = Math.round(total > 0 ? (item.value / total) * 100 : 0)
    return {
      label: item.label,
      displayValue: `${formatNumber(item.value)} (${percentage}%)`,
      percentage,
      tone: tones[index % tones.length],
    }
  })
}

function formatStatus(status: string) {
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

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

function formatFeatureKey(featureKey: string) {
  return featureKey
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function aiUsageTone(item: AdminAiUsageLogResponse) {
  if (item.status.includes('FAILED') || item.status.includes('ERROR')) return 'red'
  if (item.status.includes('UNAVAILABLE')) return 'orange'
  return 'purple'
}

function liveClassTone(item: LiveClassResponse): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (item.liveClassStatus) {
    case 'ONGOING':
      return 'success'
    case 'SCHEDULED':
      return 'info'
    case 'RESCHEDULED':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'primary'
  }
}

function reloadAll() {
  void analyticsResource.reload()
  void auditResource.reload()
  void aiUsageResource.reload()
  void liveClassesResource.reload()
  void healthResource.reload()
}
</script>

<style scoped>
.admin-analytics-page {
  position: relative;
}

.admin-analytics-range {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 16px;
  border-radius: 16px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #39486a;
  font-weight: 700;
}

.admin-analytics-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.admin-analytics-kpi,
.admin-analytics-panel {
  border-radius: 24px;
}

.admin-analytics-kpi {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 18px;
  border: 1px solid rgba(232, 236, 245, 0.94);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 24px rgba(49, 64, 94, 0.06);
}

.admin-analytics-kpi-icon,
.admin-analytics-audit-badge {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
}

.admin-analytics-kpi-icon.is-purple,
.admin-analytics-audit-badge.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-analytics-kpi-icon.is-blue,
.admin-analytics-audit-badge.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-analytics-kpi-icon.is-green,
.admin-analytics-audit-badge.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-analytics-kpi-icon.is-gold {
  background: rgba(255, 181, 68, 0.14);
  color: #ee9618;
}

.admin-analytics-kpi-icon.is-sky {
  background: rgba(68, 182, 255, 0.12);
  color: #2f86f6;
}

.admin-analytics-kpi-icon.is-lavender {
  background: rgba(148, 124, 255, 0.12);
  color: #7a56ff;
}

.admin-analytics-kpi-copy,
.admin-analytics-mini-stat,
.admin-analytics-audit-copy {
  display: grid;
  gap: 4px;
}

.admin-analytics-kpi-copy small,
.admin-analytics-kpi-copy span,
.admin-analytics-breakdown-row span,
.admin-analytics-mini-stat small,
.admin-analytics-mini-stat span,
.admin-analytics-empty-copy,
.admin-analytics-notes p,
.admin-analytics-audit-copy span,
.admin-analytics-audit-copy small,
.admin-analytics-health-banner span,
.admin-analytics-trend-row span {
  color: #72809d;
}

.admin-analytics-kpi-copy strong,
.admin-analytics-mini-stat strong,
.admin-analytics-trend-row strong {
  font-family: 'Fredoka', sans-serif;
  line-height: 1;
}

.admin-analytics-kpi-copy strong {
  font-size: 1.75rem;
}

.admin-analytics-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
}

.admin-analytics-panel {
  grid-column: span 4;
  display: grid;
  gap: 14px;
}

.admin-analytics-panel--wide {
  grid-column: span 8;
}

.admin-analytics-panel-head {
  align-items: center;
}

.admin-analytics-chart-shell {
  padding: 12px;
  border-radius: 18px;
  background: rgba(246, 248, 252, 0.96);
}

.admin-analytics-trend-list,
.admin-analytics-breakdown-list,
.admin-analytics-audit-list {
  display: grid;
  gap: 12px;
}

.admin-analytics-trend-row,
.admin-analytics-audit-row {
  display: grid;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-analytics-trend-row {
  grid-template-columns: minmax(0, 1fr) auto;
}

.admin-analytics-audit-row {
  grid-template-columns: auto minmax(0, 1fr);
}

.admin-analytics-trend-row:last-child,
.admin-analytics-audit-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.admin-analytics-breakdown-row {
  display: grid;
  gap: 8px;
}

.admin-analytics-track {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(229, 233, 244, 0.96);
}

.admin-analytics-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.admin-analytics-track span.is-purple {
  background: linear-gradient(90deg, #7a56ff, #9e8dff);
}

.admin-analytics-track span.is-green {
  background: linear-gradient(90deg, #65c352, #8edb73);
}

.admin-analytics-track span.is-orange {
  background: linear-gradient(90deg, #ffb12b, #ffd15f);
}

.admin-analytics-track span.is-blue {
  background: linear-gradient(90deg, #3990ff, #67b5ff);
}

.admin-analytics-health-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(244, 249, 240, 0.96);
  color: #339734;
}

.admin-analytics-health-banner:not(.is-up) {
  background: rgba(248, 246, 240, 0.96);
  color: #9c7a26;
}

.admin-analytics-health-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.admin-analytics-mini-stat {
  padding: 14px;
  border-radius: 18px;
  background: rgba(246, 248, 252, 0.96);
}

.admin-analytics-mini-stat strong {
  font-size: 1.55rem;
}

.admin-analytics-notes {
  display: grid;
  gap: 10px;
}

.admin-analytics-notes p {
  margin: 0;
  line-height: 1.6;
}

@media (max-width: 1500px) {
  .admin-analytics-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .admin-analytics-panel,
  .admin-analytics-panel--wide {
    grid-column: span 6;
  }

  .admin-analytics-health-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .admin-analytics-kpis,
  .admin-analytics-grid {
    grid-template-columns: 1fr;
  }

  .admin-analytics-panel,
  .admin-analytics-panel--wide {
    grid-column: auto;
  }
}
</style>
