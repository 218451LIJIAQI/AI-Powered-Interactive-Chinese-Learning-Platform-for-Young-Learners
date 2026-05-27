<template>
  <div class="page-stack teacher-dashboard-page">
    <PageHeaderBlock
      title="Teaching Dashboard"
      description="Track your learners, live classes, content pipeline, and teacher activity from one place."
      icon="dashboard"
    >
      <template #actions>
        <StatusPill
          :label="teacherStatusLabel"
          :tone="dashboard.teacherStatus === 'APPROVED' ? 'success' : 'warning'"
        />
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
              <strong>Content Snapshot</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/teacher/content')">
              Manage Content
            </button>
          </div>

          <div class="teacher-dashboard-content-top">
            <article class="teacher-dashboard-mini-stat">
              <small>Modules</small>
              <strong>{{ analytics.overview.totalModules }}</strong>
              <span>Created under your account</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Lessons</small>
              <strong>{{ analytics.overview.totalLessons }}</strong>
              <span>Across all teaching flows</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Packages</small>
              <strong>{{ analytics.overview.totalPackages }}</strong>
              <span>Published and draft bundles</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Submitted Items</small>
              <strong>{{ analytics.overview.submittedContentItems }}</strong>
              <span>Waiting for review or publish steps</span>
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
            Content status breakdown will appear here once your teacher analytics endpoint returns tracked items.
          </p>
        </article>

        <article class="panel teacher-dashboard-panel">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Upcoming Live Classes</strong>
            </div>
            <button
              class="student-link-button"
              type="button"
              :disabled="teacherApprovalLocked"
              @click="router.push('/teacher/classes')"
            >
              Open Calendar
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
              <StatusPill :label="formatStatus(item.liveClassStatus)" :tone="item.canJoin ? 'success' : 'warning'" />
            </article>
          </div>
          <StatePanel
            v-else
            icon="classes"
            title="No upcoming classes"
            message="Create or reschedule a live class to see your next session here."
          />
        </article>

        <article class="panel teacher-dashboard-panel">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Recent Quiz Results</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/teacher/students')">
              Open Students
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
            title="No quiz attempts yet"
            message="Student quiz submissions will appear here after linked learners complete teacher quizzes."
          />
        </article>

        <article class="panel teacher-dashboard-panel">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Recent Notifications</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/teacher/profile')">
              Open Profile
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
            title="No notifications"
            message="Teacher notifications will appear here when the backend sends them."
          />
        </article>

        <article class="panel teacher-dashboard-panel teacher-dashboard-panel--wide">
          <div class="row-between teacher-dashboard-panel-head">
            <div class="student-section-heading">
              <strong>Teaching Operations</strong>
            </div>
            <button class="student-link-button" type="button" @click="router.push('/teacher/students')">
              Open Learners
            </button>
          </div>

          <div class="teacher-dashboard-operations-grid">
            <article class="teacher-dashboard-mini-stat">
              <small>Teacher</small>
              <strong>{{ teacherName }}</strong>
              <span>{{ teacherStatusLabel }}</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Active Students</small>
              <strong>{{ analytics.overview.totalActiveStudents }}</strong>
              <span>Linked through classes, lessons, or attempts</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Live Enrolments</small>
              <strong>{{ analytics.overview.totalLiveEnrolments }}</strong>
              <span>Across upcoming and past classes</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Average Quiz Score</small>
              <strong>{{ formatPercent(analytics.overview.averageQuizScore) }}</strong>
              <span>Latest teacher analytics snapshot</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Available Balance</small>
              <strong>{{
                formatCurrency(analytics.overview.availableBalance, analytics.overview.currencyCode)
              }}</strong>
              <span>Ready for payout request</span>
            </article>
            <article class="teacher-dashboard-mini-stat">
              <small>Net Earnings</small>
              <strong>{{ formatCurrency(analytics.overview.netEarnings, analytics.overview.currencyCode) }}</strong>
              <span>Across paid teacher content</span>
            </article>
          </div>

          <div class="teacher-dashboard-actions">
            <button
              v-for="item in quickActions"
              :key="item.label"
              class="teacher-dashboard-action"
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
 * Teacher dashboard — verification + classroom + earnings hero.
 *
 * <p>Combines verification status, today's class lineup, and the
 * earnings snapshot into a single read-only landing. Single-fetch
 * design via {@code teacherApi.dashboard()} keeps the page fast
 * even when the underlying teacher has dozens of classes.</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

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

const teacherName = computed(() => dashboard.value.fullName || 'Teacher')
const teacherStatusLabel = computed(() => formatStatus(dashboard.value.teacherStatus))

const statCards = computed(() => [
  {
    label: 'Linked Students',
    value: formatNumber(dashboard.value.totalStudents),
    copy: 'Students connected through classes, lessons, or quizzes',
    icon: 'students',
    tone: 'purple',
  },
  {
    label: 'Published Content',
    value: formatNumber(analytics.value.overview.publishedContentItems),
    copy: `${formatNumber(analytics.value.overview.submittedContentItems)} item(s) still in submission flow`,
    icon: 'content',
    tone: 'blue',
  },
  {
    label: 'Upcoming Classes',
    value: formatNumber(analytics.value.overview.upcomingLiveClasses),
    copy: `${formatNumber(analytics.value.overview.totalLiveEnrolments)} total live enrolments tracked`,
    icon: 'classes',
    tone: 'orange',
  },
  {
    label: 'Average Quiz Score',
    value: formatPercent(dashboard.value.averageQuizScore),
    copy: 'Teacher dashboard aggregate from submitted quiz attempts',
    icon: 'quiz',
    tone: 'green',
  },
  {
    label: 'Available Balance',
    value: formatCurrency(
      dashboard.value.earningsSummary.availableBalance,
      dashboard.value.earningsSummary.currencyCode,
    ),
    copy: `${formatCurrency(dashboard.value.earningsSummary.pendingBalance, dashboard.value.earningsSummary.currencyCode)} still pending`,
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

const quickActions = [
  { label: 'Create Content', icon: 'content', to: '/teacher/content' },
  { label: 'Manage Classes', icon: 'classes', to: '/teacher/classes' },
  { label: 'Review Students', icon: 'students', to: '/teacher/students' },
  { label: 'Open Feedback', icon: 'help', to: '/teacher/feedback' },
]

function withPercentages(items: AnalyticsBreakdownItem[]) {
  const total = items.reduce((sum, item) => sum + Math.max(0, toFiniteNumber(item.value)), 0)
  const tones = ['purple', 'green', 'orange', 'blue'] as const

  return items.map((item, index) => {
    const value = Math.max(0, toFiniteNumber(item.value))
    const percentage = ratioPercent(value, total)
    return {
      label: item.label,
      displayValue: `${formatNumber(value)} (${percentage}%)`,
      percentage,
      tone: tones[index % tones.length],
    }
  })
}

function formatStatus(status: string | null | undefined) {
  if (!status) return 'Unknown'
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function scoreLabel(score: number, maxScore: number) {
  return `${score}/${maxScore}`
}

function classContextLabel(item: LiveClassResponse) {
  if (item.lesson?.lessonTitle) {
    return `${item.lesson.lessonTitle} • ${item.enrolledCount} enrolment(s)`
  }

  if (item.coursePackage?.packageTitle) {
    return `${item.coursePackage.packageTitle} • ${item.enrolledCount} enrolment(s)`
  }

  return `${item.enrolledCount} enrolment(s)`
}

function timeRange(startTime: string, endTime: string) {
  const formatter = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  })

  return `${formatter.format(new Date(startTime))} - ${formatter.format(new Date(endTime))}`
}

function reloadAll() {
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
