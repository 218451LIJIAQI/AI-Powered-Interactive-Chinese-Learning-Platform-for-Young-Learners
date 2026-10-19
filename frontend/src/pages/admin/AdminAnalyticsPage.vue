<template>
  <div class="page-stack admin-analytics-page">
    <PageHeaderBlock :title="t('adminAnalytics.title')" :description="t('adminAnalytics.description')" icon="analytics">
      <template #meta>
        <span class="tag">
          <IconGlyph name="calendar" :size="14" aria-hidden="true" />
          {{ rangeLabel }}
        </span>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-kpi-grid admin-kpi-grid--3">
        <AdminKpiTile
          v-for="card in kpiCards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :hint="card.copy"
          :icon="card.icon"
          :tone="card.tone"
        />
      </section>

      <article class="panel admin-panel">
        <div class="card-header">
          <h2 class="card-title">{{ t('adminAnalytics.registrationTrend') }}</h2>
          <button class="btn btn-link" type="button" @click="openUsers">
            {{ t('adminAnalytics.openUsers') }}
          </button>
        </div>

        <template v-if="analytics.userRegistrationTrend.length">
          <div class="admin-analytics-chart">
            <TrendChart :values="analytics.userRegistrationTrend" :label="t('adminAnalytics.registrationTrend')" />
          </div>
          <dl class="admin-analytics-points">
            <div v-for="item in analytics.userRegistrationTrend" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ formatNumber(item.value) }}</dd>
            </div>
          </dl>
        </template>
        <StatePanel
          v-else
          compact
          icon="analytics"
          :title="t('adminAnalytics.noRegistration')"
          :message="t('adminAnalytics.noRegistrationHint')"
        />
      </article>

      <section class="admin-grid-2 admin-analytics-row">
        <article v-for="panel in breakdownPanels" :key="panel.title" class="panel admin-panel">
          <div class="card-header">
            <h2 class="card-title">{{ panel.title }}</h2>
            <button class="btn btn-link" type="button" @click="panel.open">
              {{ panel.linkLabel }}
            </button>
          </div>

          <ul v-if="panel.rows.length" class="admin-breakdown">
            <li v-for="item in panel.rows" :key="item.label" class="admin-breakdown-item">
              <div class="admin-breakdown-head">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <ProgressBar class="is-sm" :progress="item.barPercent" :tone="item.barTone" aria-hidden="true" />
            </li>
          </ul>
          <StatePanel v-else compact :icon="panel.emptyIcon" :title="panel.emptyTitle" :message="panel.emptyHint" />
        </article>

        <article class="panel admin-panel">
          <h2 class="card-title">{{ t('adminAnalytics.coverageNotes') }}</h2>
          <ul class="admin-analytics-notes">
            <li>{{ t('adminAnalytics.coverageRealFields') }}</li>
            <li>{{ t('adminAnalytics.coverageRemoved') }}</li>
          </ul>
        </article>
      </section>

      <section class="admin-grid-2 admin-analytics-row">
        <article class="panel admin-panel">
          <div class="card-header">
            <h2 class="card-title">{{ t('adminAnalytics.recentAiUsage') }}</h2>
            <button class="btn btn-link" type="button" @click="openModeration">
              {{ t('adminAnalytics.openModeration') }}
            </button>
          </div>

          <ul v-if="aiUsageRows.length" class="list-divided admin-feed">
            <li v-for="item in aiUsageRows" :key="item.id" class="admin-feed-item">
              <span class="admin-icon-badge is-sm" :class="`admin-tone-${item.tone}`" aria-hidden="true">
                <IconGlyph name="ai" :size="16" />
              </span>
              <div class="admin-feed-copy">
                <span class="admin-feed-title">{{ item.title }}</span>
                <span class="admin-feed-text">{{ item.meta }}</span>
                <span class="admin-feed-meta">{{ item.detail }} · {{ item.time }}</span>
              </div>
            </li>
          </ul>
          <StatePanel
            v-else
            compact
            icon="ai"
            :title="t('adminAnalytics.noAiUsage')"
            :message="t('adminAnalytics.noAiUsageHint')"
          />
        </article>

        <article class="panel admin-panel">
          <div class="card-header">
            <h2 class="card-title">{{ t('adminAnalytics.liveClassSnapshot') }}</h2>
            <span class="tag">{{ liveClassSummaryLabel }}</span>
          </div>

          <ul v-if="liveClassRows.length" class="list-divided admin-feed">
            <li v-for="item in liveClassRows" :key="item.id" class="admin-feed-item">
              <div class="admin-feed-copy">
                <span class="admin-feed-title">{{ item.title }}</span>
                <span class="admin-feed-text">{{ item.teacherLabel }}</span>
                <span class="admin-feed-meta">{{ item.scheduleLabel }} · {{ item.enrolmentLabel }}</span>
              </div>
              <div class="admin-feed-aside">
                <StatusPill :label="item.status" :tone="item.tone" />
              </div>
            </li>
          </ul>
          <StatePanel
            v-else
            compact
            icon="classes"
            :title="t('adminAnalytics.noLiveClasses')"
            :message="t('adminAnalytics.noLiveClassesHint')"
          />
        </article>

        <article class="panel admin-panel">
          <h2 class="card-title">{{ t('adminAnalytics.systemHealth') }}</h2>

          <div class="admin-health">
            <StatusPill :label="healthStatusLabel" :tone="isHealthUp ? 'success' : 'danger'" icon="status" />
            <span class="type-caption">{{ t('adminAnalytics.checked', { time: healthCheckedLabel }) }}</span>
          </div>

          <dl class="admin-stat-tiles">
            <div class="admin-stat-tile">
              <dt>{{ t('adminAnalytics.service') }}</dt>
              <dd>{{ health.service || 'backend' }}</dd>
              <dd>{{ t('adminAnalytics.liveHealth') }}</dd>
            </div>
            <div class="admin-stat-tile">
              <dt>{{ t('adminAnalytics.auditEntries') }}</dt>
              <dd>{{ formatNumber(analytics.overview.auditLogEntries) }}</dd>
              <dd>{{ t('adminAnalytics.analyticsCount') }}</dd>
            </div>
            <div class="admin-stat-tile">
              <dt>{{ t('adminAnalytics.unreadAdminNotifications') }}</dt>
              <dd>{{ formatNumber(analytics.overview.adminUnreadNotifications) }}</dd>
              <dd>{{ t('adminAnalytics.analyticsCount') }}</dd>
            </div>
          </dl>
        </article>

        <article class="panel admin-panel">
          <div class="card-header">
            <h2 class="card-title">{{ t('adminAnalytics.recentAudit') }}</h2>
            <button class="btn btn-link" type="button" @click="openSettings">
              {{ t('adminAnalytics.viewAll') }}
            </button>
          </div>

          <ul v-if="auditItems.length" class="list-divided admin-feed">
            <li v-for="item in auditItems" :key="item.id" class="admin-feed-item">
              <span class="admin-icon-badge is-sm" :class="`admin-tone-${item.tone}`" aria-hidden="true">
                <IconGlyph :name="item.icon" :size="16" />
              </span>
              <div class="admin-feed-copy">
                <span class="admin-feed-title">{{ item.title }}</span>
                <span class="admin-feed-text">{{ item.actionType }}</span>
                <span class="admin-feed-meta">{{ item.time }}</span>
              </div>
            </li>
          </ul>
          <StatePanel
            v-else
            compact
            icon="history"
            :title="t('adminAnalytics.noAudit')"
            :message="t('adminAnalytics.noAuditHint')"
          />
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
import AdminKpiTile from '@/components/admin/AdminKpiTile.vue'
import type { AdminTone } from '@/components/admin/adminTone'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import TrendChart from '@/components/common/TrendChart.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { useResource } from '@/composables/useResource'
import { adminAnalytics, adminAuditLogs } from '@/demo/data'
import { adminAnalyticsMessages } from '@/plugins/adminAnalyticsMessages'
import type { AdminAiUsageLogResponse, AiFeatureKey, AnalyticsBreakdownItem, LiveClassResponse } from '@/types/api'
import { formatDateTime, formatNumber, ratioPercent, toFiniteNumber } from '@/utils/format'

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
    'zh-CN': { adminAnalytics: adminAnalyticsMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

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
    moderationActions: 0,
    auditLogEntries: 0,
    adminUnreadNotifications: 0,
    aiRequests: 0,
  },
  userRegistrationTrend: [],
  teacherApprovalBreakdown: [],
  contentStatusBreakdown: [],
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

const kpiCards = computed<Array<{ label: string; value: string; copy: string; icon: string; tone: AdminTone }>>(() => [
  {
    label: t('adminAnalytics.students'),
    value: formatNumber(analytics.value.overview.totalStudents),
    copy: t('adminAnalytics.overviewTotal'),
    icon: 'students',
    tone: 'primary',
  },
  {
    label: t('adminAnalytics.teachers'),
    value: formatNumber(analytics.value.overview.totalTeachers),
    copy: t('adminAnalytics.pendingApprovals', {
      count: formatNumber(analytics.value.overview.pendingTeacherApprovals),
    }),
    icon: 'users',
    tone: 'info',
  },
  {
    label: t('adminAnalytics.publishedContent'),
    value: formatNumber(analytics.value.overview.publishedContentItems),
    copy: t('adminAnalytics.visibleItems'),
    icon: 'content',
    tone: 'success',
  },
  {
    label: t('adminAnalytics.aiRequests'),
    value: formatNumber(analytics.value.overview.aiRequests),
    copy: t('adminAnalytics.aiVolume'),
    icon: 'ai',
    tone: 'warning',
  },
  {
    label: t('adminAnalytics.moderationActions'),
    value: formatNumber(analytics.value.overview.moderationActions),
    copy: t('adminAnalytics.moderationActionsHint'),
    icon: 'moderation',
    tone: 'danger',
  },
  {
    label: t('adminAnalytics.auditEntries'),
    value: formatNumber(analytics.value.overview.auditLogEntries),
    copy: t('adminAnalytics.unreadAdmin', {
      count: formatNumber(analytics.value.overview.adminUnreadNotifications),
    }),
    icon: 'history',
    tone: 'neutral',
  },
])

const teacherApprovalRows = computed(() => normalizeBreakdown(analytics.value.teacherApprovalBreakdown))
const contentRows = computed(() => normalizeBreakdown(analytics.value.contentStatusBreakdown))
const aiRows = computed(() => normalizeBreakdown(analytics.value.aiStatusBreakdown))
const breakdownPanels = computed(() => [
  {
    title: t('adminAnalytics.teacherApproval'),
    linkLabel: t('adminAnalytics.openQueue'),
    open: openTeacherQueue,
    rows: teacherApprovalRows.value,
    emptyIcon: 'verification',
    emptyTitle: t('adminAnalytics.noTeacherApproval'),
    emptyHint: t('adminAnalytics.noTeacherApprovalHint'),
  },
  {
    title: t('adminAnalytics.contentStatus'),
    linkLabel: t('adminAnalytics.openContent'),
    open: openContent,
    rows: contentRows.value,
    emptyIcon: 'content',
    emptyTitle: t('adminAnalytics.noContentStatus'),
    emptyHint: t('adminAnalytics.noContentStatusHint'),
  },
  {
    title: t('adminAnalytics.aiStatus'),
    linkLabel: t('adminAnalytics.openModeration'),
    open: openModeration,
    rows: aiRows.value,
    emptyIcon: 'ai',
    emptyTitle: t('adminAnalytics.noAiBreakdown'),
    emptyHint: t('adminAnalytics.noAiBreakdownHint'),
  },
])

const auditItems = computed(() =>
  auditLogs.value.slice(0, 5).map((item) => ({
    id: item.id,
    title: item.description,
    actionType: statusLabel('auditAction', item.actionType),
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
  barTone: 'primary' | 'success' | 'warning' | 'info'
  barPercent: number
}> {
  const total = items.reduce((sum, item) => sum + Math.max(0, toFiniteNumber(item.value)), 0)
  const tones = ['primary', 'success', 'warning', 'info'] as const

  return items.map((item, index) => {
    const value = Math.max(0, toFiniteNumber(item.value))
    const percentage = ratioPercent(value, total)
    return {
      label: formatStatus(item.label),
      displayValue: `${formatNumber(value)} (${percentage}%)`,
      barTone: tones[index % tones.length],
      barPercent: percentage,
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
    ARCHIVED: 'statusArchived',
    UNKNOWN: 'statusUnknown',
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

function auditTone(actionType: string): AdminTone {
  if (actionType.includes('APPROVE')) return 'success'
  if (actionType.includes('REJECT')) return 'danger'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'primary'
  return 'info'
}

function formatFeatureKey(featureKey: string): string {
  const keys: Record<AiFeatureKey, string> = {
    CHAT_TUTOR: 'chatTutor',
    VOCABULARY_HELPER: 'vocabularyHelper',
    SENTENCE_PRACTICE: 'sentencePractice',
    QUIZ_GENERATOR: 'quizGenerator',
    STORY_GENERATOR: 'storyGenerator',
    PRONUNCIATION_FEEDBACK: 'pronunciationFeedback',
    WRITING_FEEDBACK: 'writingFeedback',
  }
  const key = (keys as Record<string, string | undefined>)[featureKey.toUpperCase()]
  if (key) return t(`adminAnalytics.${key}`)
  return featureKey
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function aiUsageTone(item: AdminAiUsageLogResponse): AdminTone {
  if (item.status.includes('FAILED') || item.status.includes('ERROR')) return 'danger'
  if (item.status.includes('UNAVAILABLE')) return 'warning'
  return 'primary'
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
.admin-analytics-row {
  align-items: stretch;
}

.admin-analytics-chart {
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.admin-analytics-points {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(96px, 1fr));
  gap: var(--space-2);
  margin: 0;
}

.admin-analytics-points > div {
  display: grid;
  gap: var(--space-0-5);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.admin-analytics-points dt {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.admin-analytics-points dd {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.admin-analytics-notes {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding-left: var(--space-5);
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}
</style>
