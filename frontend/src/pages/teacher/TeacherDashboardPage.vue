<template>
  <div class="page-stack teacher-dashboard-page">
    <PageHeaderBlock
      :title="t('teacherDashboard.title')"
      :description="t('teacherDashboard.description')"
      icon="dashboard"
    >
      <template #actions>
        <StatusPill :label="teacherStatusLabel" :tone="teacherStatusTone" />
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" :error="pageError" :detail="teacherNoticeDetail" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="teacher-dashboard-summary">
        <article v-for="card in statCards" :key="card.label" class="teacher-dashboard-stat-card">
          <div class="teacher-dashboard-stat-icon" :class="`is-${card.tone}`">
            <IconGlyph :name="card.icon" :size="22" />
          </div>
          <div class="teacher-dashboard-stat-copy">
            <small>{{ card.label }}</small>
            <strong>{{ card.value }}</strong>
            <span>{{ card.copy }}</span>
          </div>
        </article>
      </section>

      <section class="teacher-dashboard-grid">
        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--wide">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherDashboard.contentSnapshot') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openContent">
              {{ t('teacherDashboard.manageContent') }}
            </button>
          </div>

          <div class="teacher-dashboard-content-top">
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.modules') }}</small>
              <strong>{{ analytics.overview.totalModules }}</strong>
              <span>{{ t('teacherDashboard.modulesHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.lessons') }}</small>
              <strong>{{ analytics.overview.totalLessons }}</strong>
              <span>{{ t('teacherDashboard.lessonsHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.packages') }}</small>
              <strong>{{ analytics.overview.totalPackages }}</strong>
              <span>{{ t('teacherDashboard.packagesHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.submittedItems') }}</small>
              <strong>{{ analytics.overview.submittedContentItems }}</strong>
              <span>{{ t('teacherDashboard.submittedHint') }}</span>
            </article>
          </div>

          <div v-if="contentBreakdownRows.length" class="teacher-dashboard-breakdown-list">
            <article v-for="item in contentBreakdownRows" :key="item.label" class="teacher-dashboard-breakdown-row">
              <div class="row-between">
                <strong>{{ item.label }}</strong>
                <span>{{ item.displayValue }}</span>
              </div>
              <div class="teacher-dashboard-breakdown-track">
                <span :class="`is-${item.tone}`" :style="{ width: `${item.percentage}%` }" />
              </div>
            </article>
          </div>
          <p v-else class="teacher-dashboard-empty-copy">
            {{ t('teacherDashboard.noContentBreakdown') }}
          </p>
        </article>

        <article class="panel teacher-dashboard-panel">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherDashboard.upcomingClasses') }}</strong>
            </div>
            <button class="student-link-button" type="button" :disabled="teacherApprovalLocked" @click="openClasses">
              {{ t('teacherDashboard.openCalendar') }}
            </button>
          </div>

          <StatePanel
            v-if="teacherApprovalLocked"
            icon="classes"
            :title="teacherApprovalTitle"
            :message="teacherApprovalMessage"
          />
          <div v-else-if="upcomingClasses.length" class="teacher-dashboard-list">
            <article v-for="item in upcomingClasses" :key="item.id" class="teacher-dashboard-class-row">
              <div class="teacher-dashboard-class-time">
                <strong>{{ formatDate(item.startTime, { month: 'short', day: 'numeric' }) }}</strong>
                <span>{{ timeRange(item.startTime, item.endTime) }}</span>
              </div>
              <div class="teacher-dashboard-class-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ classContextLabel(item) }}</span>
              </div>
              <StatusPill :label="formatStatus(item.liveClassStatus)" :tone="classJoinTone(item.canJoin)" />
            </article>
          </div>
          <StatePanel
            v-else
            icon="classes"
            :title="t('teacherDashboard.noUpcomingClasses')"
            :message="t('teacherDashboard.noUpcomingClassesHint')"
          />
        </article>

        <article class="panel teacher-dashboard-panel">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherDashboard.recentQuizResults') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openStudents">
              {{ t('teacherDashboard.openStudents') }}
            </button>
          </div>

          <div v-if="dashboard.recentQuizResults.length" class="teacher-dashboard-list">
            <article
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
            </article>
          </div>
          <StatePanel
            v-else
            icon="quiz"
            :title="t('teacherDashboard.noQuizAttempts')"
            :message="t('teacherDashboard.noQuizAttemptsHint')"
          />
        </article>

        <article class="panel teacher-dashboard-panel">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherDashboard.recentNotifications') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openProfile">
              {{ t('teacherDashboard.openProfile') }}
            </button>
          </div>

          <div v-if="dashboard.recentNotifications.length" class="teacher-dashboard-list">
            <NotificationLinkButton
              v-for="item in dashboard.recentNotifications.slice(0, 4)"
              :key="item.notificationId"
              :notification-id="item.notificationId"
              :target-path="item.targetPath"
              fallback-to="/teacher/profile"
              @read="markTeacherNotificationRead"
            >
              <article class="teacher-dashboard-notification-row">
                <div class="teacher-dashboard-notification-dot" :class="{ 'is-unread': item.status !== 'READ' }" />
                <div class="teacher-dashboard-activity-copy">
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
            :title="t('teacherDashboard.noNotifications')"
            :message="t('teacherDashboard.noNotificationsHint')"
          />
        </article>

        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--wide">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherDashboard.teachingOperations') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openStudents">
              {{ t('teacherDashboard.openLearners') }}
            </button>
          </div>

          <div class="teacher-dashboard-operations-grid">
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.teacher') }}</small>
              <strong>{{ teacherName }}</strong>
              <span>{{ teacherStatusLabel }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.activeStudents') }}</small>
              <strong>{{ analytics.overview.totalActiveStudents }}</strong>
              <span>{{ t('teacherDashboard.activeStudentsHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.liveEnrolments') }}</small>
              <strong>{{ analytics.overview.totalLiveEnrolments }}</strong>
              <span>{{ t('teacherDashboard.liveEnrolmentsHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.averageQuizScore') }}</small>
              <strong>{{ formatPercent(analytics.overview.averageQuizScore) }}</strong>
              <span>{{ t('teacherDashboard.averageQuizHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.availableBalance') }}</small>
              <strong>{{
                formatCurrency(analytics.overview.availableBalance, analytics.overview.currencyCode)
              }}</strong>
              <span>{{ t('teacherDashboard.availableBalanceHint') }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>{{ t('teacherDashboard.netEarnings') }}</small>
              <strong>{{ formatCurrency(analytics.overview.netEarnings, analytics.overview.currencyCode) }}</strong>
              <span>{{ t('teacherDashboard.netEarningsHint') }}</span>
            </article>
          </div>

          <div class="teacher-dashboard-actions">
            <button
              v-for="item in quickActions"
              :key="item.label"
              class="teacher-dashboard-action"
              type="button"
              @click="item.action"
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { teacherApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { teacherAnalytics, teacherDashboard, teacherLiveClasses } from '@/demo/data'
import { teacherDashboardMessages } from '@/plugins/teacherDashboardMessages'
import type { AnalyticsBreakdownItem, LiveClassResponse, TeacherDashboardResponse } from '@/types/api'
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatNumber,
  formatPercent,
  ratioPercent,
  toFiniteNumber,
} from '@/utils/format'

const router = useRouter()
const { t, locale } = useI18n({
  messages: {
    en: { teacherDashboard: teacherDashboardMessages.en },
    ms: { teacherDashboard: teacherDashboardMessages.ms },
    zh: { teacherDashboard: teacherDashboardMessages.zh },
  },
})
const { teacherApprovalLocked, teacherApprovalTitle, teacherApprovalMessage } = useTeacherAccess()

const dashboardResource = useResource(() => teacherApi.dashboard(), teacherDashboard)
const analyticsResource = useResource(() => teacherApi.analytics(), teacherAnalytics)
const liveClassesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.liveClasses()),
  teacherLiveClasses,
)

const emptyDashboard: TeacherDashboardResponse = {
  fullName: 'Teacher',
  teacherStatus: 'PENDING',
  totalLessons: 0,
  totalStudents: 0,
  averageQuizScore: 0,
  earningsSummary: {
    grossAmount: 0,
    netAmount: 0,
    availableBalance: 0,
    pendingBalance: 0,
    currencyCode: 'MYR',
  },
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
    netEarnings: 0,
    availableBalance: 0,
    currencyCode: 'MYR',
    upcomingLiveClasses: 0,
    totalLiveEnrolments: 0,
  },
  quizScoreTrend: [],
  earningsTrend: [],
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
const teacherStatusLabel = computed(() => formatStatus(dashboard.value.teacherStatus))
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
  {
    label: t('teacherDashboard.availableBalance'),
    value: formatCurrency(
      dashboard.value.earningsSummary.availableBalance,
      dashboard.value.earningsSummary.currencyCode,
    ),
    copy: t('teacherDashboard.pendingBalance', {
      amount: formatCurrency(
        dashboard.value.earningsSummary.pendingBalance,
        dashboard.value.earningsSummary.currencyCode,
      ),
    }),
    icon: 'earnings',
    tone: 'mint',
  },
])

const upcomingClasses = computed(() =>
  [...liveClasses.value]
    .filter((item) => item.liveClassStatus !== 'CANCELLED')
    .sort((left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime())
    .slice(0, 4),
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
  const tones = ['purple', 'green', 'orange', 'blue'] as const

  return items.map((item, index) => {
    const value = Math.max(0, toFiniteNumber(item.value))
    const percentage = ratioPercent(value, total)
    return {
      label: formatStatus(item.label),
      displayValue: `${formatNumber(value)} (${percentage}%)`,
      percentage,
      tone: tones[index % tones.length],
    }
  })
}

function formatStatus(status: string | null | undefined): string {
  if (!status) return t('teacherDashboard.unknown')
  const statusKeys: Record<string, string> = {
    APPROVED: 'statusApproved',
    PENDING: 'statusPending',
    REJECTED: 'statusRejected',
    PUBLISHED: 'statusPublished',
    SUBMITTED: 'statusSubmitted',
    DRAFT: 'statusDraft',
    ARCHIVED: 'statusArchived',
    SCHEDULED: 'statusScheduled',
    LIVE: 'statusLive',
    COMPLETED: 'statusCompleted',
    CANCELLED: 'statusCancelled',
  }
  const normalized = status.trim().toUpperCase()
  const translatedKey = statusKeys[normalized]
  if (translatedKey) return t(`teacherDashboard.${translatedKey}`)
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
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
  const localeCode = locale.value === 'zh' ? 'zh-CN' : locale.value === 'ms' ? 'ms-MY' : 'en-US'
  const formatter = new Intl.DateTimeFormat(localeCode, {
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
.teacher-dashboard-page {
  position: relative;
}

.teacher-dashboard-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.teacher-dashboard-stat-card,
.teacher-dashboard-panel {
  border-radius: 24px;
}

.teacher-dashboard-stat-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 18px;
  border: 1px solid rgba(232, 236, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 24px rgba(70, 84, 118, 0.06);
}

.teacher-dashboard-stat-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 16px;
}

.teacher-dashboard-stat-icon.is-purple {
  background: rgba(124, 88, 255, 0.12);
  color: #7556ff;
}

.teacher-dashboard-stat-icon.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.teacher-dashboard-stat-icon.is-orange {
  background: rgba(255, 181, 68, 0.14);
  color: #ee9618;
}

.teacher-dashboard-stat-icon.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #48aa43;
}

.teacher-dashboard-stat-icon.is-mint {
  background: rgba(88, 196, 132, 0.14);
  color: #3f9867;
}

.teacher-dashboard-stat-copy {
  display: grid;
  gap: 4px;
}

.teacher-dashboard-stat-copy small,
.teacher-dashboard-stat-copy span,
.teacher-dashboard-empty-copy,
.teacher-dashboard-class-time span,
.teacher-dashboard-class-copy span,
.teacher-dashboard-activity-copy span,
.teacher-dashboard-activity-copy small,
.teacher-dashboard-notification-row small,
.teacher-dashboard-mini-stat small,
.teacher-dashboard-mini-stat span,
.teacher-dashboard-breakdown-row span {
  color: #6f7b97;
}

.teacher-dashboard-stat-copy strong,
.teacher-dashboard-mini-stat strong,
.teacher-dashboard-score {
  font-family: 'Fredoka', sans-serif;
}

.teacher-dashboard-stat-copy strong {
  font-size: 1.9rem;
  line-height: 1;
}

.teacher-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.teacher-dashboard-panel {
  grid-column: span 4;
  display: grid;
  gap: 14px;
}

.teacher-dashboard-panel--wide {
  grid-column: span 8;
}

.teacher-dashboard-panel-head {
  align-items: center;
}

.teacher-dashboard-content-top,
.teacher-dashboard-operations-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.teacher-dashboard-mini-stat {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(246, 248, 252, 0.96);
}

.teacher-dashboard-mini-stat strong {
  font-size: 1.7rem;
  line-height: 1;
}

.teacher-dashboard-breakdown-list,
.teacher-dashboard-list {
  display: grid;
  gap: 12px;
}

.teacher-dashboard-breakdown-row {
  display: grid;
  gap: 8px;
}

.teacher-dashboard-breakdown-track {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(229, 233, 244, 0.96);
}

.teacher-dashboard-breakdown-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.teacher-dashboard-breakdown-track span.is-purple {
  background: linear-gradient(90deg, #7d58ff, #9c87ff);
}

.teacher-dashboard-breakdown-track span.is-green {
  background: linear-gradient(90deg, #63c454, #8edb72);
}

.teacher-dashboard-breakdown-track span.is-orange {
  background: linear-gradient(90deg, #ffb12c, #ffd061);
}

.teacher-dashboard-breakdown-track span.is-blue {
  background: linear-gradient(90deg, #3990ff, #67b4ff);
}

.teacher-dashboard-class-row,
.teacher-dashboard-activity-row,
.teacher-dashboard-notification-row {
  display: grid;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.teacher-dashboard-class-row {
  grid-template-columns: 108px minmax(0, 1fr) auto;
  align-items: center;
}

.teacher-dashboard-activity-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.teacher-dashboard-notification-row {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
}

.teacher-dashboard-class-row:last-child,
.teacher-dashboard-activity-row:last-child,
.teacher-dashboard-notification-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.teacher-dashboard-class-time,
.teacher-dashboard-class-copy,
.teacher-dashboard-activity-copy {
  display: grid;
  gap: 4px;
}

.teacher-dashboard-class-time strong,
.teacher-dashboard-score {
  font-size: 1.1rem;
}

.teacher-dashboard-notification-dot {
  width: 10px;
  height: 10px;
  margin-top: 7px;
  border-radius: 50%;
  background: rgba(180, 187, 206, 0.96);
}

.teacher-dashboard-notification-dot.is-unread {
  background: #7d58ff;
}

.teacher-dashboard-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.teacher-dashboard-action {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 14px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #344260;
  font-weight: 700;
}

@media (max-width: 1500px) {
  .teacher-dashboard-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .teacher-dashboard-panel,
  .teacher-dashboard-panel--wide {
    grid-column: span 6;
  }

  .teacher-dashboard-content-top,
  .teacher-dashboard-operations-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .teacher-dashboard-summary,
  .teacher-dashboard-grid,
  .teacher-dashboard-content-top,
  .teacher-dashboard-operations-grid,
  .teacher-dashboard-actions {
    grid-template-columns: 1fr;
  }

  .teacher-dashboard-panel,
  .teacher-dashboard-panel--wide {
    grid-column: auto;
  }

  .teacher-dashboard-class-row,
  .teacher-dashboard-activity-row {
    grid-template-columns: 1fr;
  }
}
</style>
