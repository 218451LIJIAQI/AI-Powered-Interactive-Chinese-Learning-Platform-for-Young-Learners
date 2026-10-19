<template>
  <div class="page-stack teacher-dashboard-page">
    <PageHeaderBlock
      :title="t('teacherDashboard.title')"
      :description="t('teacherDashboard.description')"
      icon="dashboard"
    >
      <template #meta>
        <StatusPill :label="teacherStatusLabel" :tone="teacherStatusTone" />
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" :detail="teacherNoticeDetail" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="teacher-dashboard-summary" :aria-label="t('teacherDashboard.title')">
        <article v-for="card in statCards" :key="card.label" class="teacher-dashboard-stat-card">
          <div class="teacher-dashboard-stat-icon" :class="`is-${card.tone}`" aria-hidden="true">
            <IconGlyph :name="card.icon" :size="20" />
          </div>
          <div class="teacher-dashboard-stat-copy">
            <span class="teacher-dashboard-stat-label">{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <span>{{ card.copy }}</span>
          </div>
        </article>
      </section>

      <section class="teacher-dashboard-grid">
        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--content">
          <div class="card-header">
            <h2 class="card-title">{{ t('teacherDashboard.contentSnapshot') }}</h2>
            <button class="btn btn-link" type="button" @click="openContent">
              {{ t('teacherDashboard.manageContent') }}
            </button>
          </div>

          <div class="teacher-dashboard-mini-grid">
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.modules') }}</span>
              <strong>{{ analytics.overview.totalModules }}</strong>
              <span>{{ t('teacherDashboard.modulesHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.lessons') }}</span>
              <strong>{{ analytics.overview.totalLessons }}</strong>
              <span>{{ t('teacherDashboard.lessonsHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.packages') }}</span>
              <strong>{{ analytics.overview.totalPackages }}</strong>
              <span>{{ t('teacherDashboard.packagesHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.submittedItems') }}</span>
              <strong>{{ analytics.overview.submittedContentItems }}</strong>
              <span>{{ t('teacherDashboard.submittedHint') }}</span>
            </article>
          </div>

          <div v-if="contentBreakdownRows.length" class="teacher-dashboard-breakdown-list">
            <div v-for="item in contentBreakdownRows" :key="item.label" class="teacher-dashboard-breakdown-row">
              <div class="teacher-dashboard-breakdown-head">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <ProgressBar :progress="item.percentage" :tone="item.tone" :label="item.label" />
            </div>
          </div>
          <p v-else class="teacher-dashboard-empty-copy">
            {{ t('teacherDashboard.noContentBreakdown') }}
          </p>
        </article>

        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--classes">
          <div class="card-header">
            <h2 class="card-title">{{ t('teacherDashboard.upcomingClasses') }}</h2>
            <button class="btn btn-link" type="button" :disabled="teacherApprovalLocked" @click="openClasses">
              {{ t('teacherDashboard.openCalendar') }}
            </button>
          </div>

          <StatePanel
            v-if="teacherApprovalLocked"
            icon="classes"
            :title="teacherApprovalTitle"
            :message="teacherApprovalMessage"
            compact
          />
          <ul v-else-if="upcomingClasses.length" class="teacher-dashboard-list teacher-dashboard-list--classes">
            <li v-for="item in upcomingClasses" :key="item.id" class="teacher-dashboard-class-row">
              <div class="teacher-dashboard-class-time">
                <strong>{{ formatDate(item.startTime, { month: 'short', day: 'numeric' }) }}</strong>
                <span>{{ timeRange(item.startTime, item.endTime) }}</span>
              </div>
              <div class="teacher-dashboard-class-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ classContextLabel(item) }}</span>
              </div>
              <StatusPill
                :label="liveClassStatusLabel(effectiveLiveClassStatus(item))"
                :tone="classJoinTone(item.canJoin)"
              />
            </li>
          </ul>
          <StatePanel
            v-else
            icon="classes"
            :title="t('teacherDashboard.noUpcomingClasses')"
            :message="t('teacherDashboard.noUpcomingClassesHint')"
            compact
          />
        </article>

        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--half">
          <div class="card-header">
            <h2 class="card-title">{{ t('teacherDashboard.recentQuizResults') }}</h2>
            <button class="btn btn-link" type="button" @click="openStudents">
              {{ t('teacherDashboard.openStudents') }}
            </button>
          </div>

          <ul v-if="dashboard.recentQuizResults.length" class="teacher-dashboard-list">
            <li
              v-for="item in dashboard.recentQuizResults.slice(0, 4)"
              :key="item.attemptId"
              class="teacher-dashboard-activity-row"
            >
              <div class="teacher-dashboard-activity-copy">
                <strong>{{ item.quizTitle }}</strong>
                <span>{{ item.studentName }}</span>
                <small>{{ formatDateTime(item.completedAt) }}</small>
              </div>
              <strong class="teacher-dashboard-score">{{ scoreLabel(item.score, item.maxScore) }}</strong>
            </li>
          </ul>
          <StatePanel
            v-else
            icon="quiz"
            :title="t('teacherDashboard.noQuizAttempts')"
            :message="t('teacherDashboard.noQuizAttemptsHint')"
            compact
          />
        </article>

        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--half">
          <div class="card-header">
            <h2 class="card-title">{{ t('teacherDashboard.recentNotifications') }}</h2>
            <button class="btn btn-link" type="button" @click="openProfile">
              {{ t('teacherDashboard.openProfile') }}
            </button>
          </div>

          <div v-if="dashboard.recentNotifications.length" class="teacher-dashboard-list">
            <NotificationLinkButton
              v-for="item in dashboard.recentNotifications.slice(0, 4)"
              :key="item.notificationId"
              class="teacher-dashboard-notification"
              :notification-id="item.notificationId"
              :target-path="item.targetPath"
              fallback-to="/teacher/profile"
              @read="markTeacherNotificationRead"
            >
              <span class="teacher-dashboard-notification-row">
                <span
                  class="teacher-dashboard-notification-dot"
                  :class="{ 'is-unread': item.status !== 'READ' }"
                  aria-hidden="true"
                />
                <span class="teacher-dashboard-activity-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.message }}</span>
                  <small>{{ formatDateTime(item.createdAt) }}</small>
                </span>
              </span>
            </NotificationLinkButton>
          </div>
          <StatePanel
            v-else
            icon="bell"
            :title="t('teacherDashboard.noNotifications')"
            :message="t('teacherDashboard.noNotificationsHint')"
            compact
          />
        </article>

        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--full">
          <div class="card-header">
            <h2 class="card-title">{{ t('teacherDashboard.teachingOperations') }}</h2>
            <button class="btn btn-link" type="button" @click="openStudents">
              {{ t('teacherDashboard.openLearners') }}
            </button>
          </div>

          <div class="teacher-dashboard-mini-grid">
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.teacher') }}</span>
              <strong class="teacher-dashboard-mini-name">{{ teacherName }}</strong>
              <span>{{ teacherStatusLabel }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.activeStudents') }}</span>
              <strong>{{ analytics.overview.totalActiveStudents }}</strong>
              <span>{{ t('teacherDashboard.activeStudentsHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.liveEnrolments') }}</span>
              <strong>{{ analytics.overview.totalLiveEnrolments }}</strong>
              <span>{{ t('teacherDashboard.liveEnrolmentsHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <span class="teacher-dashboard-stat-label">{{ t('teacherDashboard.averageQuizScore') }}</span>
              <strong>{{ formatPercent(analytics.overview.averageQuizScore) }}</strong>
              <span>{{ t('teacherDashboard.averageQuizHint') }}</span>
            </article>
          </div>

          <div class="card-footer teacher-dashboard-actions">
            <button
              v-for="item in quickActions"
              :key="item.label"
              class="btn btn-secondary"
              type="button"
              @click="item.action"
            >
              <IconGlyph :name="item.icon" :size="16" />
              <span>{{ item.label }}</span>
            </button>
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

import { teacherApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { teacherAnalytics, teacherDashboard, teacherLiveClasses } from '@/demo/data'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import { teacherDashboardMessages } from '@/plugins/teacherDashboardMessages'
import type { AnalyticsBreakdownItem, LiveClassResponse, TeacherDashboardResponse } from '@/types/api'
import {
  formatDate,
  formatDateTime,
  formatNumber,
  formatPercent,
  getDisplayLocale,
  ratioPercent,
  toFiniteNumber,
} from '@/utils/format'
import { effectiveLiveClassStatus, isTerminalLiveClass, sortLiveClassesByStart } from '@/utils/liveClassActions'

const router = useRouter()
const { t, locale } = useI18n({
  messages: {
    en: { teacherDashboard: teacherDashboardMessages.en },
    ms: { teacherDashboard: teacherDashboardMessages.ms },
    'zh-CN': { teacherDashboard: teacherDashboardMessages['zh-CN'] },
  },
})
const { teacherApprovalLocked, teacherApprovalTitle, teacherApprovalMessage } = useTeacherAccess()
const { statusLabel } = useStatusLabels()

const dashboardResource = useResource(() => teacherApi.dashboard(), teacherDashboard)
const analyticsResource = useResource(() => teacherApi.analytics(), teacherAnalytics)
const liveClassesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.liveClasses()),
  teacherLiveClasses,
)

const emptyDashboard: TeacherDashboardResponse = {
  fullName: '',
  teacherStatus: 'PENDING',
  totalLessons: 0,
  totalStudents: 0,
  averageQuizScore: 0,
  recentQuizResults: [],
  recentNotifications: [],
}
const emptyAnalytics = {
  overview: {
    totalModules: 0,
    totalLessons: 0,
    totalPackages: 0,
    publishedContentItems: 0,
    submittedContentItems: 0,
    totalActiveStudents: 0,
    averageQuizScore: 0,
    upcomingLiveClasses: 0,
    totalLiveEnrolments: 0,
  },
  quizScoreTrend: [],
  contentStatusBreakdown: [],
}

const dashboard = computed(
  () => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? teacherDashboard : emptyDashboard),
)
const analytics = computed(
  () => analyticsResource.data.value ?? (analyticsResource.usingFallback.value ? teacherAnalytics : emptyAnalytics),
)
const liveClasses = computed(
  () => liveClassesResource.data.value ?? (liveClassesResource.usingFallback.value ? teacherLiveClasses : []),
)

const pageLoading = computed(
  () => dashboardResource.loading.value || analyticsResource.loading.value || liveClassesResource.loading.value,
)
const pageError = computed(
  () => dashboardResource.error.value || analyticsResource.error.value || liveClassesResource.error.value,
)
const usingFallback = computed(
  () =>
    dashboardResource.usingFallback.value ||
    analyticsResource.usingFallback.value ||
    liveClassesResource.usingFallback.value,
)
const teacherNoticeDetail = computed(() => (teacherApprovalLocked.value ? teacherApprovalMessage.value : null))

function markTeacherNotificationRead(notificationId: number) {
  dashboardResource.data.value = {
    ...dashboard.value,
    recentNotifications: dashboard.value.recentNotifications.map((item) =>
      item.notificationId === notificationId ? { ...item, status: 'READ' } : item,
    ),
  }
}

const teacherName = computed(() => dashboard.value.fullName || t('teacherDashboard.teacher'))
const teacherStatusLabel = computed(() =>
  statusLabel('teacherStatus', dashboard.value.teacherStatus?.trim().toUpperCase(), t('teacherDashboard.unknown')),
)
const teacherStatusTone = computed(() => (dashboard.value.teacherStatus === 'APPROVED' ? 'success' : 'warning'))

const statCards = computed(() => [
  {
    label: t('teacherDashboard.linkedStudents'),
    value: formatNumber(dashboard.value.totalStudents),
    copy: t('teacherDashboard.linkedStudentsHint'),
    icon: 'students',
    tone: 'purple',
  },
  {
    label: t('teacherDashboard.publishedContent'),
    value: formatNumber(analytics.value.overview.publishedContentItems),
    copy: t('teacherDashboard.submittedCount', {
      count: formatNumber(analytics.value.overview.submittedContentItems),
    }),
    icon: 'content',
    tone: 'blue',
  },
  {
    label: t('teacherDashboard.upcomingClasses'),
    value: formatNumber(analytics.value.overview.upcomingLiveClasses),
    copy: t('teacherDashboard.upcomingCount', {
      count: formatNumber(analytics.value.overview.totalLiveEnrolments),
    }),
    icon: 'classes',
    tone: 'orange',
  },
  {
    label: t('teacherDashboard.averageQuizScore'),
    value: formatPercent(dashboard.value.averageQuizScore),
    copy: t('teacherDashboard.dashboardQuizHint'),
    icon: 'quiz',
    tone: 'green',
  },
])

// Cancelled and completed classes are not upcoming.
const upcomingClasses = computed(() =>
  sortLiveClassesByStart(liveClasses.value.filter((item) => !isTerminalLiveClass(item))).slice(0, 4),
)

const contentBreakdownRows = computed(() => withPercentages(analytics.value.contentStatusBreakdown))

const quickActions = computed(() => [
  { label: t('teacherDashboard.createContent'), icon: 'content', action: openContent },
  { label: t('teacherDashboard.manageClasses'), icon: 'classes', action: openClasses },
  { label: t('teacherDashboard.reviewStudents'), icon: 'students', action: openStudents },
  { label: t('teacherDashboard.openFeedback'), icon: 'help', action: openFeedback },
])

function withPercentages(items: AnalyticsBreakdownItem[]) {
  const total = items.reduce((sum, item) => sum + Math.max(0, toFiniteNumber(item.value)), 0)
  const tones = ['primary', 'success', 'warning', 'info'] as const

  return items.map((item, index) => {
    const value = Math.max(0, toFiniteNumber(item.value))
    const percentage = ratioPercent(value, total)
    return {
      label: contentStatusLabel(item.label),
      displayValue: `${formatNumber(value)} (${percentage}%)`,
      percentage,
      tone: tones[index % tones.length],
    }
  })
}

/** Content review states of the analytics breakdown (labels may arrive as "PUBLISHED" or "Published"). */
function contentStatusLabel(status: string | null | undefined): string {
  if (!status?.trim()) return t('teacherDashboard.unknown')
  const statusKeys: Record<string, string> = {
    PUBLISHED: 'statusPublished',
    SUBMITTED: 'statusSubmitted',
    DRAFT: 'statusDraft',
    REJECTED: 'statusRejected',
    ARCHIVED: 'statusArchived',
  }
  const translatedKey = statusKeys[status.trim().toUpperCase().replace(/\s+/g, '_')]
  if (translatedKey) return t(`teacherDashboard.${translatedKey}`)
  return humanizeEnumValue(status)
}

// W4: teacher approval and live-class states use the shared status labels.
function liveClassStatusLabel(status: string | null | undefined): string {
  const normalized = (status ?? '').trim().toUpperCase()
  if (!normalized) return t('teacherDashboard.unknown')
  return statusLabel('liveClass', normalized === 'LIVE' || normalized === 'IN_PROGRESS' ? 'ONGOING' : normalized)
}

function scoreLabel(score: number, maxScore: number): string {
  return `${score}/${maxScore}`
}

function classContextLabel(item: LiveClassResponse): string {
  if (item.lesson?.lessonTitle) {
    return `${item.lesson.lessonTitle} • ${t('teacherDashboard.enrolments', { count: item.enrolledCount })}`
  }

  if (item.coursePackage?.packageTitle) {
    return `${item.coursePackage.packageTitle} • ${t('teacherDashboard.enrolments', { count: item.enrolledCount })}`
  }

  return t('teacherDashboard.enrolments', { count: item.enrolledCount })
}

function classJoinTone(canJoin: boolean): 'success' | 'warning' {
  return canJoin ? 'success' : 'warning'
}

function timeRange(startTime: string, endTime: string): string {
  const formatter = new Intl.DateTimeFormat(getDisplayLocale(locale.value), {
    hour: 'numeric',
    minute: '2-digit',
  })

  return `${formatter.format(new Date(startTime))} - ${formatter.format(new Date(endTime))}`
}

function openContent(): void {
  void router.push('/teacher/content')
}

function openClasses(): void {
  void router.push('/teacher/classes')
}

function openStudents(): void {
  void router.push('/teacher/students')
}

function openFeedback(): void {
  void router.push('/teacher/feedback')
}

function openProfile(): void {
  void router.push('/teacher/profile')
}

function reloadAll(): void {
  void dashboardResource.reload()
  void analyticsResource.reload()
  void liveClassesResource.reload()
}
</script>

<style scoped>
.teacher-dashboard-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--grid-gap);
}

.teacher-dashboard-stat-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
}

.teacher-dashboard-stat-icon {
  display: grid;
  place-items: center;
  width: var(--control-height-md);
  height: var(--control-height-md);
  border-radius: var(--radius-md);
}

.teacher-dashboard-stat-icon.is-purple {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.teacher-dashboard-stat-icon.is-blue {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.teacher-dashboard-stat-icon.is-orange {
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.teacher-dashboard-stat-icon.is-green {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.teacher-dashboard-stat-copy,
.teacher-dashboard-mini-stat,
.teacher-dashboard-activity-copy,
.teacher-dashboard-class-time,
.teacher-dashboard-class-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-dashboard-stat-label {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.teacher-dashboard-stat-copy strong,
.teacher-dashboard-mini-stat strong,
.teacher-dashboard-score {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-dashboard-stat-copy strong {
  font-size: var(--font-size-3xl);
}

.teacher-dashboard-stat-copy > span:last-child,
.teacher-dashboard-mini-stat > span:last-child,
.teacher-dashboard-empty-copy,
.teacher-dashboard-class-time span,
.teacher-dashboard-class-copy span,
.teacher-dashboard-activity-copy span,
.teacher-dashboard-activity-copy small,
.teacher-dashboard-breakdown-head span {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.teacher-dashboard-activity-copy small {
  font-size: var(--font-size-xs);
}

/* 12-column grid: content (7) + classes (5), two halves, then the operations strip. */
.teacher-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-dashboard-panel {
  display: grid;
  gap: var(--section-gap);
  grid-column: span 6;
  min-width: 0;
}

.teacher-dashboard-panel--content {
  grid-column: span 7;
}

.teacher-dashboard-panel--classes {
  grid-column: span 5;
}

.teacher-dashboard-panel--full {
  grid-column: 1 / -1;
}

.teacher-dashboard-panel > .card-header {
  align-items: center;
}

.teacher-dashboard-mini-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 120px), 1fr));
  gap: var(--space-3);
}

.teacher-dashboard-mini-stat {
  align-content: start;
  padding: var(--space-3) var(--space-3-5);
  border-radius: var(--radius-lg);
  background: var(--surface-sunken);
}

.teacher-dashboard-mini-stat strong {
  font-size: var(--font-size-2xl);
}

.teacher-dashboard-mini-stat .teacher-dashboard-mini-name {
  font-size: var(--font-size-xl);
  overflow-wrap: anywhere;
}

.teacher-dashboard-breakdown-list {
  display: grid;
  gap: var(--space-3);
}

.teacher-dashboard-breakdown-row {
  display: grid;
  gap: var(--space-1-5);
}

.teacher-dashboard-breakdown-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  font-size: var(--font-size-md);
}

.teacher-dashboard-empty-copy {
  margin: 0;
}

.teacher-dashboard-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-dashboard-class-row,
.teacher-dashboard-activity-row,
.teacher-dashboard-notification {
  padding-block: var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
}

.teacher-dashboard-list > :first-child {
  padding-top: 0;
}

.teacher-dashboard-list > :last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

/* Rows share one column grid (subgrid) so times, titles and pills line up across rows. */
.teacher-dashboard-list--classes {
  grid-template-columns: max-content minmax(0, 1fr) auto;
  column-gap: var(--space-4);
}

.teacher-dashboard-class-row {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: subgrid;
  align-items: center;
}

.teacher-dashboard-class-time strong,
.teacher-dashboard-score {
  font-size: var(--font-size-lg);
}

.teacher-dashboard-class-time strong {
  font-family: var(--font-display);
  font-weight: var(--font-weight-semibold);
}

.teacher-dashboard-class-copy strong,
.teacher-dashboard-activity-copy strong {
  font-size: var(--font-size-md);
  overflow-wrap: anywhere;
}

.teacher-dashboard-activity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.teacher-dashboard-notification {
  border-radius: var(--radius-sm);
}

.teacher-dashboard-notification:hover .teacher-dashboard-activity-copy strong {
  color: var(--text-link);
}

.teacher-dashboard-notification-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-3);
}

.teacher-dashboard-notification-dot {
  width: var(--space-2-5);
  height: var(--space-2-5);
  margin-top: var(--space-1-5);
  border-radius: var(--radius-round);
  background: var(--color-ink-300);
}

.teacher-dashboard-notification-dot.is-unread {
  background: var(--color-primary-500);
}

.teacher-dashboard-actions {
  justify-content: flex-start;
}

@media (max-width: 1200px) {
  .teacher-dashboard-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .teacher-dashboard-panel--content,
  .teacher-dashboard-panel--classes {
    grid-column: 1 / -1;
  }
}

@media (max-width: 900px) {
  .teacher-dashboard-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .teacher-dashboard-stat-card {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-3-5);
  }

  .teacher-dashboard-stat-copy strong {
    font-size: var(--font-size-2xl);
  }

  .teacher-dashboard-list--classes {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .teacher-dashboard-class-copy {
    grid-column: 1 / -1;
    grid-row: 2;
  }
}
</style>
