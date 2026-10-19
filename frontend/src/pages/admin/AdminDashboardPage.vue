<template>
  <div class="page-stack admin-dashboard-page">
    <PageHeaderBlock :title="t('adminDashboard.title')" :description="t('adminDashboard.description')" icon="dashboard">
      <template #meta>
        <span class="tag">
          <IconGlyph name="calendar" :size="14" aria-hidden="true" />
          {{ todayLabel }}
        </span>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-kpi-grid">
        <AdminKpiTile
          v-for="card in statCards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :hint="card.copy"
          :icon="card.icon"
          :tone="card.tone"
        />
      </section>

      <section class="admin-grid-2">
        <div class="admin-stack">
          <article class="panel admin-panel">
            <div class="card-header">
              <h2 class="card-title">{{ t('adminDashboard.teacherQueue') }}</h2>
              <button class="btn btn-link" type="button" @click="openTeacherVerification">
                {{ t('adminDashboard.viewAll') }}
              </button>
            </div>

            <ul v-if="teacherQueueItems.length" class="list-divided admin-feed">
              <li v-for="item in teacherQueueItems" :key="item.teacherId" class="admin-feed-item">
                <PortraitAvatar :size="40" :name="item.fullName" :tone="item.avatarTone" />
                <div class="admin-feed-copy">
                  <span class="admin-feed-title">{{ item.fullName }}</span>
                  <span class="admin-feed-text">{{ item.email }}</span>
                  <span class="admin-feed-meta">{{ item.appliedLabel }}</span>
                </div>
                <div class="admin-feed-aside">
                  <StatusPill :label="t('adminDashboard.pending')" tone="warning" />
                </div>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="verification"
              :title="t('adminDashboard.noPendingTeachers')"
              :message="t('adminDashboard.noPendingTeachersHint')"
            />
          </article>

          <article class="panel admin-panel">
            <div class="card-header">
              <h2 class="card-title">{{ t('adminDashboard.moderationReports') }}</h2>
              <button class="btn btn-link" type="button" @click="openModeration">
                {{ t('adminDashboard.openQueue') }}
              </button>
            </div>

            <ul v-if="moderationItems.length" class="list-divided admin-feed">
              <li v-for="item in moderationItems" :key="item.id" class="admin-feed-item">
                <span class="admin-icon-badge is-sm" :class="`admin-tone-${item.tone}`" aria-hidden="true">
                  <IconGlyph :name="item.icon" :size="16" />
                </span>
                <div class="admin-feed-copy">
                  <span class="admin-feed-title">{{ item.title }}</span>
                  <span class="admin-feed-text">{{ item.copy }}</span>
                  <span class="admin-feed-meta">{{ item.meta }}</span>
                </div>
                <div class="admin-feed-aside">
                  <StatusPill :label="item.label" :tone="item.statusTone" />
                </div>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="moderation"
              :title="t('adminDashboard.noModeration')"
              :message="t('adminDashboard.noModerationHint')"
            />
          </article>

          <article class="panel admin-panel">
            <div class="card-header">
              <h2 class="card-title">{{ t('adminDashboard.analyticsSnapshot') }}</h2>
              <button class="btn btn-link" type="button" @click="openAnalytics">
                {{ t('adminDashboard.openAnalytics') }}
              </button>
            </div>

            <dl class="admin-stat-tiles admin-stat-tiles--pairs">
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.totalStudents') }}</dt>
                <dd>{{ formatNumber(analytics.overview.totalStudents) }}</dd>
                <dd>{{ t('adminDashboard.totalStudentsHint') }}</dd>
              </div>
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.totalTeachers') }}</dt>
                <dd>{{ formatNumber(analytics.overview.totalTeachers) }}</dd>
                <dd>{{ t('adminDashboard.totalTeachersHint') }}</dd>
              </div>
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.publishedContent') }}</dt>
                <dd>{{ formatNumber(analytics.overview.publishedContentItems) }}</dd>
                <dd>{{ t('adminDashboard.publishedContentHint') }}</dd>
              </div>
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.aiRequests') }}</dt>
                <dd>{{ formatNumber(analytics.overview.aiRequests) }}</dd>
                <dd>{{ t('adminDashboard.aiRequestsHint') }}</dd>
              </div>
            </dl>

            <ul v-if="registrationTrendRows.length" class="admin-breakdown">
              <li v-for="item in registrationTrendRows" :key="item.label" class="admin-breakdown-item">
                <div class="admin-breakdown-head">
                  <strong>{{ item.label }}</strong>
                  <span>{{ formatNumber(item.value) }}</span>
                </div>
                <ProgressBar class="is-sm" :progress="item.barPercent" aria-hidden="true" />
              </li>
            </ul>
            <p v-else class="field-hint">
              {{ t('adminDashboard.noRegistrationTrend') }}
            </p>
          </article>
        </div>

        <div class="admin-stack">
          <article class="panel admin-panel">
            <h2 class="card-title">{{ t('adminDashboard.quickActions') }}</h2>
            <div class="admin-quick-actions">
              <button
                v-for="item in quickActions"
                :key="item.label"
                class="btn btn-secondary"
                type="button"
                @click="openQuickAction(item.to)"
              >
                <IconGlyph :name="item.icon" :size="16" />
                <span>{{ item.label }}</span>
              </button>
            </div>
          </article>

          <article class="panel admin-panel">
            <h2 class="card-title">{{ t('adminDashboard.platformHealth') }}</h2>

            <div class="admin-health">
              <StatusPill :label="healthStatusLabel" :tone="isHealthUp ? 'success' : 'danger'" icon="status" />
              <span class="type-caption">{{ t('adminDashboard.checked', { time: healthTimestampLabel }) }}</span>
            </div>

            <dl class="admin-stat-tiles">
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.backendService') }}</dt>
                <dd>{{ health.service || 'backend' }}</dd>
                <dd>{{ t('adminDashboard.backendServiceHint') }}</dd>
              </div>
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.auditEntries') }}</dt>
                <dd>{{ formatNumber(analytics.overview.auditLogEntries) }}</dd>
                <dd>{{ t('adminDashboard.auditEntriesHint') }}</dd>
              </div>
              <div class="admin-stat-tile">
                <dt>{{ t('adminDashboard.unreadNotifications') }}</dt>
                <dd>{{ formatNumber(analytics.overview.adminUnreadNotifications) }}</dd>
                <dd>{{ t('adminDashboard.unreadNotificationsHint') }}</dd>
              </div>
            </dl>
          </article>

          <article class="panel admin-panel">
            <div class="card-header">
              <h2 class="card-title">{{ t('adminDashboard.adminNotifications') }}</h2>
              <button class="btn btn-link" type="button" @click="openNotifications">
                {{ t('adminDashboard.viewAll') }}
              </button>
            </div>

            <ul v-if="notifications.length" class="list-divided admin-feed">
              <li v-for="item in notifications.slice(0, 4)" :key="item.id">
                <NotificationLinkButton
                  class="admin-feed-link"
                  :notification-id="item.id"
                  :target-path="item.targetPath"
                  fallback-to="/admin/settings"
                  @read="markAdminNotificationRead"
                >
                  <span class="admin-feed-item">
                    <span
                      class="admin-notification-dot"
                      :class="{ 'is-unread': item.status !== 'READ' }"
                      aria-hidden="true"
                    />
                    <span v-if="item.status !== 'READ'" class="sr-only">{{
                      statusLabel('notificationStatus', 'UNREAD')
                    }}</span>
                    <span class="admin-feed-copy">
                      <span class="admin-feed-title">{{ item.title }}</span>
                      <span class="admin-feed-text">{{ item.message }}</span>
                      <span class="admin-feed-meta">{{ formatDateTime(item.createdAt) }}</span>
                    </span>
                  </span>
                </NotificationLinkButton>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="bell"
              :title="t('adminDashboard.noNotifications')"
              :message="t('adminDashboard.noNotificationsHint')"
            />
          </article>

          <article class="panel admin-panel">
            <div class="card-header">
              <h2 class="card-title">{{ t('adminDashboard.auditLogs') }}</h2>
              <button class="btn btn-link" type="button" @click="openSettings">
                {{ t('adminDashboard.viewAll') }}
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
              :title="t('adminDashboard.noAuditLogs')"
              :message="t('adminDashboard.noAuditLogsHint')"
            />
          </article>
        </div>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { adminApi, notificationsApi, systemApi } from '@/api/services'
import AdminKpiTile from '@/components/admin/AdminKpiTile.vue'
import {
  aiReportStatusTone,
  auditIcon,
  auditTone,
  EMPTY_ADMIN_ANALYTICS,
  EMPTY_HEALTH,
  previewHealth,
} from '@/components/admin/adminDisplay'
import type { AdminTone } from '@/components/admin/adminTone'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import type { AvatarTone } from '@/components/common/avatarTone'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
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
import { adminDashboardMessages } from '@/plugins/adminDashboardMessages'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import {
  formatDate,
  formatDateTime,
  formatNumber,
  getDisplayLocale,
  ratioPercent,
  toFiniteNumber,
} from '@/utils/format'

const AVATAR_TONES: readonly AvatarTone[] = ['primary', 'info', 'success', 'warning']

const HEALTH_FALLBACK = previewHealth()

const router = useRouter()
const { t, locale } = useI18n({
  messages: {
    en: { adminDashboard: adminDashboardMessages.en },
    ms: { adminDashboard: adminDashboardMessages.ms },
    'zh-CN': { adminDashboard: adminDashboardMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

const dashboardResource = useResource(() => adminApi.dashboard(), adminDashboard)
const analyticsResource = useResource(() => adminApi.analytics(), adminAnalytics)
const teachersResource = useResource(() => adminApi.pendingTeachers(), teacherApplicationsPending)
const moderationResource = useResource(() => adminApi.moderationRecords(), adminModerationRecords)
const notificationsResource = useResource(() => notificationsApi.list(), adminNotifications)
const auditResource = useResource(() => adminApi.auditLogs(), adminAuditLogs)
const aiReportsResource = useResource(() => adminApi.aiReports(), adminAiReports)
const healthResource = useResource(() => systemApi.health(), HEALTH_FALLBACK)

const emptyDashboard = {
  fullName: '',
  totalStudents: 0,
  totalTeachers: 0,
  pendingTeacherApprovals: 0,
  aiUsageSummary: {
    totalRequests: 0,
    successfulRequests: 0,
  },
}

const dashboard = computed(
  () => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? adminDashboard : emptyDashboard),
)
const analytics = computed(
  () =>
    analyticsResource.data.value ?? (analyticsResource.usingFallback.value ? adminAnalytics : EMPTY_ADMIN_ANALYTICS),
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
  () => healthResource.data.value ?? (healthResource.usingFallback.value ? HEALTH_FALLBACK : EMPTY_HEALTH),
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

function markAdminNotificationRead(notificationId: number): void {
  notificationsResource.data.value = notifications.value.map((item) =>
    item.id === notificationId ? { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() } : item,
  )
}

const todayLabel = computed(() =>
  new Intl.DateTimeFormat(getDisplayLocale(locale.value), {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)

const statCards = computed<Array<{ label: string; value: string; copy: string; icon: string; tone: AdminTone }>>(() => [
  {
    label: t('adminDashboard.totalUsers'),
    value: formatNumber(dashboard.value.totalStudents + dashboard.value.totalTeachers),
    copy: t('adminDashboard.userCount', {
      students: formatNumber(dashboard.value.totalStudents),
      teachers: formatNumber(dashboard.value.totalTeachers),
    }),
    icon: 'users',
    tone: 'primary',
  },
  {
    label: t('adminDashboard.pendingTeacherReviews'),
    value: formatNumber(dashboard.value.pendingTeacherApprovals),
    copy: t('adminDashboard.pendingTeacherReviewsHint'),
    icon: 'verification',
    tone: 'warning',
  },
  {
    label: t('adminDashboard.moderationActions'),
    value: formatNumber(analytics.value.overview.moderationActions),
    copy: t('adminDashboard.moderationActionsHint'),
    icon: 'moderation',
    tone: 'danger',
  },
  {
    label: t('adminDashboard.aiRequests'),
    value: formatNumber(dashboard.value.aiUsageSummary.totalRequests),
    copy: t('adminDashboard.successfulRequests', {
      count: formatNumber(dashboard.value.aiUsageSummary.successfulRequests),
    }),
    icon: 'ai',
    tone: 'info',
  },
])

const teacherQueueItems = computed(() =>
  pendingTeachers.value.slice(0, 4).map((item, index) => ({
    teacherId: item.teacherId,
    fullName: item.fullName,
    email: item.email,
    appliedLabel: formatDate(item.latestSubmissionTime || item.createdAt),
    avatarTone: AVATAR_TONES[index % AVATAR_TONES.length],
  })),
)

const moderationItems = computed(() => {
  const contentItems = moderationRecords.value.slice(0, 2).map((item) => ({
    id: item.id,
    title: formatStatus(item.actionTaken),
    copy: `${formatStatus(item.targetType)}: ${item.targetTitle ?? `#${item.targetId}`}`,
    meta: formatDateTime(item.createdAt),
    label: t('adminDashboard.moderation'),
    tone: 'warning' as AdminTone,
    icon: 'moderation',
    statusTone: 'warning' as const,
  }))
  const aiItems = aiReports.value.slice(0, 2).map((item) => ({
    id: `ai-${item.id}`,
    title: formatStatus(item.reasonCategory),
    copy: item.description || formatStatus(item.featureKey),
    meta: formatDateTime(item.createdAt),
    label: formatStatus(item.status),
    tone: 'primary' as AdminTone,
    icon: 'ai',
    statusTone: aiReportStatusTone(item.status),
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
      barPercent: max > 0 ? Math.max(8, ratioPercent(value, max)) : 0,
    }
  })
})

const auditItems = computed(() =>
  auditLogs.value.slice(0, 4).map((item) => ({
    id: item.id,
    title: item.description,
    actionType: statusLabel('auditAction', item.actionType),
    time: formatDateTime(item.createdAt),
    icon: auditIcon(item.actionType),
    tone: auditTone(item.actionType),
  })),
)

const healthStatus = computed(() => health.value.status || 'UNKNOWN')
const isHealthUp = computed(() => healthStatus.value === 'UP')
const healthStatusLabel = computed(() =>
  t(isHealthUp.value ? 'adminDashboard.allOperational' : 'adminDashboard.statusUnavailable'),
)
const healthTimestampLabel = computed(() => (health.value.timestamp ? formatDateTime(health.value.timestamp) : '--'))

const quickActions = computed(() => [
  { label: t('adminDashboard.teacherVerification'), icon: 'verification', to: '/admin/teacher-verification' },
  { label: t('adminDashboard.moderationQueue'), icon: 'moderation', to: '/admin/moderation' },
  { label: t('adminDashboard.analytics'), icon: 'analytics', to: '/admin/analytics' },
  { label: t('adminDashboard.settings'), icon: 'settings', to: '/admin/settings' },
])

function formatStatus(status: string): string {
  const statusKeys: Record<string, string> = {
    PENDING_REVIEW: 'statusPendingReview',
    IN_REVIEW: 'statusInReview',
    DISMISSED: 'statusDismissed',
    RESOLVED: 'statusResolved',
    INCORRECT_CONTENT: 'reasonIncorrectContent',
    HALLUCINATION: 'reasonHallucination',
    UNSAFE_CONTENT: 'reasonUnsafeContent',
    INAPPROPRIATE_CONTENT: 'reasonInappropriateContent',
    OTHER: 'reasonOther',
    PACKAGE: 'typeCourse',
    MODULE: 'typeModule',
    LESSON: 'typeLesson',
  }
  const normalized = status.trim().toUpperCase()
  const translatedKey = statusKeys[normalized]
  if (translatedKey) return t(`adminDashboard.${translatedKey}`)
  return humanizeEnumValue(status)
}

function openTeacherVerification(): void {
  void router.push('/admin/teacher-verification')
}

function openModeration(): void {
  void router.push('/admin/moderation')
}

function openSettings(): void {
  void router.push('/admin/settings')
}

function openAnalytics(): void {
  void router.push('/admin/analytics')
}

function openNotifications(): void {
  void router.push({ name: 'admin-notifications' })
}

function openQuickAction(to: string): void {
  void router.push(to)
}

function reloadAll(): void {
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
