<template>
  <div class="page-stack admin-analytics-page">
    <PageHeaderBlock :title="t('adminAnalytics.title')" :description="t('adminAnalytics.description')" icon="analytics">
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
              <strong>{{ t('adminAnalytics.registrationTrend') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openUsers">
              {{ t('adminAnalytics.openUsers') }}
            </button>
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
            :title="t('adminAnalytics.noRegistration')"
            :message="t('adminAnalytics.noRegistrationHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.teacherApproval') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openTeacherQueue">
              {{ t('adminAnalytics.openQueue') }}
            </button>
          </div>

          <div v-if="teacherApprovalRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in teacherApprovalRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="item.toneClass" :style="item.barStyle" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="verification"
            :title="t('adminAnalytics.noTeacherApproval')"
            :message="t('adminAnalytics.noTeacherApprovalHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.contentStatus') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openContent">
              {{ t('adminAnalytics.openContent') }}
            </button>
          </div>

          <div v-if="contentRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in contentRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="item.toneClass" :style="item.barStyle" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="content"
            :title="t('adminAnalytics.noContentStatus')"
            :message="t('adminAnalytics.noContentStatusHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.paymentStatus') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openFinance">
              {{ t('adminAnalytics.openFinance') }}
            </button>
          </div>

          <div v-if="paymentRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in paymentRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="item.toneClass" :style="item.barStyle" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="finance"
            :title="t('adminAnalytics.noPaymentStatus')"
            :message="t('adminAnalytics.noPaymentStatusHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.aiStatus') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openModeration">
              {{ t('adminAnalytics.openModeration') }}
            </button>
          </div>

          <div v-if="aiRows.length" class="admin-analytics-breakdown-list">
            <article v-for="item in aiRows" :key="item.label" class="admin-analytics-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="admin-analytics-track">
                <span :class="item.toneClass" :style="item.barStyle" />
              </div>
            </article>
          </div>
          <StatePanel
            v-else
            icon="ai"
            :title="t('adminAnalytics.noAiBreakdown')"
            :message="t('adminAnalytics.noAiBreakdownHint')"
          />
        </article>

        <article class="panel admin-analytics-panel admin-analytics-panel--wide">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.recentAiUsage') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openModeration">
              {{ t('adminAnalytics.openModeration') }}
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
            :title="t('adminAnalytics.noAiUsage')"
            :message="t('adminAnalytics.noAiUsageHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.liveClassSnapshot') }}</strong>
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
            :title="t('adminAnalytics.noLiveClasses')"
            :message="t('adminAnalytics.noLiveClassesHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="student-section-heading">
            <strong>{{ t('adminAnalytics.systemHealth') }}</strong>
          </div>

          <div class="admin-analytics-health-banner" :class="{ 'is-up': isHealthUp }">
            <IconGlyph name="status" :size="18" />
            <div class="stack u-gap-4">
              <strong>{{ healthStatusLabel }}</strong>
              <span>{{ t('adminAnalytics.checked', { time: healthCheckedLabel }) }}</span>
            </div>
          </div>

          <div class="admin-analytics-health-grid">
            <article class="admin-analytics-mini-stat">
              <small>{{ t('adminAnalytics.service') }}</small>
              <strong>{{ health.service || 'backend' }}</strong>
              <span>{{ t('adminAnalytics.liveHealth') }}</span>
            </article>
            <article class="admin-analytics-mini-stat">
              <small>{{ t('adminAnalytics.auditEntries') }}</small>
              <strong>{{ formatNumber(analytics.overview.auditLogEntries) }}</strong>
              <span>{{ t('adminAnalytics.analyticsCount') }}</span>
            </article>
            <article class="admin-analytics-mini-stat">
              <small>{{ t('adminAnalytics.unreadAdminNotifications') }}</small>
              <strong>{{ formatNumber(analytics.overview.adminUnreadNotifications) }}</strong>
              <span>{{ t('adminAnalytics.analyticsCount') }}</span>
            </article>
          </div>
        </article>

        <article class="panel admin-analytics-panel admin-analytics-panel--wide">
          <div class="row-between admin-analytics-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('adminAnalytics.recentAudit') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openSettings">
              {{ t('adminAnalytics.viewAll') }}
            </button>
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
            :title="t('adminAnalytics.noAudit')"
            :message="t('adminAnalytics.noAuditHint')"
          />
        </article>

        <article class="panel admin-analytics-panel">
          <div class="student-section-heading">
            <strong>{{ t('adminAnalytics.coverageNotes') }}</strong>
          </div>

          <div class="admin-analytics-notes">
            <p>
              {{ t('adminAnalytics.coverageRealFields') }}
            </p>
            <p>
              {{ t('adminAnalytics.coverageRemoved') }}
            </p>
          </div>
        </article>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

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
import { adminAnalyticsMessages } from '@/plugins/adminAnalyticsMessages'
import type { AdminAiUsageLogResponse, AnalyticsBreakdownItem, LiveClassResponse } from '@/types/api'
import { formatCurrency, formatDateTime, formatNumber, ratioPercent, toFiniteNumber } from '@/utils/format'

const HEALTH_FALLBACK = {
  status: 'UP',
  service: 'backend',
  timestamp: new Date().toISOString(),
}

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { adminAnalytics: adminAnalyticsMessages.en },
    ms: { adminAnalytics: adminAnalyticsMessages.ms },
    zh: { adminAnalytics: adminAnalyticsMessages.zh },
  },
})

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
const healthResource = useResource(() => systemApi.health(), HEALTH_FALLBACK)

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

const analytics = computed(
  () => analyticsResource.data.value ?? (analyticsResource.usingFallback.value ? adminAnalytics : emptyAnalytics),
)
const auditLogs = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? adminAuditLogs : []))
const aiUsageLogs = computed(() => aiUsageResource.data.value?.items ?? [])
const liveClasses = computed(() => liveClassesResource.data.value ?? [])
const health = computed(
  () => healthResource.data.value ?? (healthResource.usingFallback.value ? HEALTH_FALLBACK : emptyHealth),
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
    ? t('adminAnalytics.trackedPoints', { count: analytics.value.userRegistrationTrend.length })
    : t('adminAnalytics.currentSnapshot'),
)

const kpiCards = computed(() => [
  {
    label: t('adminAnalytics.students'),
    value: formatNumber(analytics.value.overview.totalStudents),
    copy: t('adminAnalytics.overviewTotal'),
    icon: 'students',
    tone: 'purple',
  },
  {
    label: t('adminAnalytics.teachers'),
    value: formatNumber(analytics.value.overview.totalTeachers),
    copy: t('adminAnalytics.pendingApprovals', {
      count: formatNumber(analytics.value.overview.pendingTeacherApprovals),
    }),
    icon: 'users',
    tone: 'blue',
  },
  {
    label: t('adminAnalytics.publishedContent'),
    value: formatNumber(analytics.value.overview.publishedContentItems),
    copy: t('adminAnalytics.visibleItems'),
    icon: 'content',
    tone: 'green',
  },
  {
    label: t('adminAnalytics.aiRequests'),
    value: formatNumber(analytics.value.overview.aiRequests),
    copy: t('adminAnalytics.aiVolume'),
    icon: 'ai',
    tone: 'gold',
  },
  {
    label: t('adminAnalytics.revenue'),
    value: formatCurrency(analytics.value.overview.totalRevenue, analytics.value.overview.currencyCode),
    copy: t('adminAnalytics.pendingRefunds', { count: formatNumber(analytics.value.overview.pendingRefunds) }),
    icon: 'finance',
    tone: 'sky',
  },
  {
    label: t('adminAnalytics.auditEntries'),
    value: formatNumber(analytics.value.overview.auditLogEntries),
    copy: t('adminAnalytics.unreadAdmin', {
      count: formatNumber(analytics.value.overview.adminUnreadNotifications),
    }),
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
    meta: [
      item.userName || item.userEmail || t('adminAnalytics.unknownUser'),
      item.userRole || t('adminAnalytics.unknownRole'),
    ]
      .filter(Boolean)
      .join(' / '),
    detail:
      [
        item.providerName || t('adminAnalytics.providerUnavailable'),
        item.modelName || t('adminAnalytics.modelUnavailable'),
        item.tokenCount != null ? t('adminAnalytics.tokens', { count: formatNumber(item.tokenCount) }) : null,
        item.errorSummary || item.outputSummary || item.inputSummary || null,
      ]
        .filter(Boolean)
        .join(' · ') || t('adminAnalytics.noRequestSummary'),
    time: formatDateTime(item.createdAt),
    tone: aiUsageTone(item),
  })),
)

const liveClassRows = computed(() =>
  liveClasses.value.slice(0, 5).map((item) => ({
    id: item.id,
    title: item.title,
    teacherLabel: item.teacher?.displayName
      ? t('adminAnalytics.teacherNamed', { name: item.teacher.displayName })
      : t('adminAnalytics.teacherUnavailable'),
    scheduleLabel: t('adminAnalytics.schedule', {
      start: formatDateTime(item.startTime),
      end: formatDateTime(item.endTime),
    }),
    enrolmentLabel: t('adminAnalytics.enrolmentCapacity', {
      enrolled: formatNumber(item.enrolledCount),
      capacity: item.maxParticipants ?? t('adminAnalytics.unlimited'),
    }),
    status: formatStatus(item.liveClassStatus),
    tone: liveClassTone(item),
  })),
)

const healthCheckedLabel = computed(() => (health.value.timestamp ? formatDateTime(health.value.timestamp) : '--'))
const isHealthUp = computed(() => health.value.status === 'UP')
const healthStatusLabel = computed(() =>
  t(isHealthUp.value ? 'adminAnalytics.allOperational' : 'adminAnalytics.statusUnavailable'),
)
const liveClassSummaryLabel = computed(() =>
  t('adminAnalytics.rowsLoaded', { count: formatNumber(liveClasses.value.length) }),
)

function normalizeBreakdown(items: AnalyticsBreakdownItem[]): Array<{
  label: string
  displayValue: string
  toneClass: string
  barStyle: { width: string }
}> {
  const total = items.reduce((sum, item) => sum + Math.max(0, toFiniteNumber(item.value)), 0)
  const tones = ['purple', 'green', 'orange', 'blue'] as const

  return items.map((item, index) => {
    const value = Math.max(0, toFiniteNumber(item.value))
    const percentage = ratioPercent(value, total)
    return {
      label: formatStatus(item.label),
      displayValue: `${formatNumber(value)} (${percentage}%)`,
      toneClass: `is-${tones[index % tones.length]}`,
      barStyle: { width: `${percentage}%` },
    }
  })
}

function formatStatus(status: string): string {
  const statusKeys: Record<string, string> = {
    APPROVED: 'statusApproved',
    PENDING: 'statusPending',
    REJECTED: 'statusRejected',
    PUBLISHED: 'statusPublished',
    SUBMITTED: 'statusSubmitted',
    DRAFT: 'statusDraft',
    PAID: 'statusPaid',
    FAILED: 'statusFailed',
    COMPLETED: 'statusCompleted',
    UNAVAILABLE: 'statusUnavailableValue',
    SCHEDULED: 'statusScheduled',
    ONGOING: 'statusOngoing',
    RESCHEDULED: 'statusRescheduled',
    CANCELLED: 'statusCancelled',
  }
  const normalized = status.trim().toUpperCase()
  const translatedKey = statusKeys[normalized]
  if (translatedKey) return t(`adminAnalytics.${translatedKey}`)
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function auditIcon(actionType: string): string {
  if (actionType.includes('APPROVE')) return 'status'
  if (actionType.includes('REJECT')) return 'close'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'content'
  return 'history'
}

function auditTone(actionType: string): string {
  if (actionType.includes('APPROVE')) return 'green'
  if (actionType.includes('REJECT')) return 'red'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'purple'
  return 'blue'
}

function formatFeatureKey(featureKey: string): string {
  return featureKey
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function aiUsageTone(item: AdminAiUsageLogResponse): string {
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

function openUsers(): void {
  void router.push('/admin/users')
}

function openTeacherQueue(): void {
  void router.push('/admin/teacher-verification')
}

function openContent(): void {
  void router.push('/admin/content')
}

function openFinance(): void {
  void router.push('/admin/finance')
}

function openModeration(): void {
  void router.push('/admin/moderation')
}

function openSettings(): void {
  void router.push('/admin/settings')
}

function reloadAll(): void {
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
