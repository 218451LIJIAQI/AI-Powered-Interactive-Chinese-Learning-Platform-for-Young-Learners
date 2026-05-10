<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import AnalyticsChartCard from '../../components/common/AnalyticsChartCard.vue'
import { dashboardService } from '../../services/dashboardService'
import { useAuthStore } from '../../stores/authStore'
import type { TeacherAnalyticsResponse } from '../../types/analytics'
import type { TeacherDashboardSummary } from '../../types/dashboard'
import {
  buildBarChartOption,
  buildDonutChartOption,
  buildLineChartOption,
  currency,
  hasBreakdownData,
  hasTrendData,
  normalizeBreakdown,
  translateAnalyticsKey,
} from '../../utils/analytics'

const authStore = useAuthStore()
const router = useRouter()
const { t } = useI18n()

const user = computed(() => authStore.currentUser)
const teacherStatus = computed(() => authStore.teacherStatus)
const dashboard = ref<TeacherDashboardSummary | null>(null)
const analytics = ref<TeacherAnalyticsResponse | null>(null)
const loading = ref(true)
const analyticsError = ref('')
const contentLocked = computed(() => teacherStatus.value !== 'APPROVED')

const statusType = computed(() => {
  if (teacherStatus.value === 'APPROVED') {
    return 'success'
  }

  if (teacherStatus.value === 'REJECTED') {
    return 'danger'
  }

  return 'warning'
})

const statusMessage = computed(() => {
  if (teacherStatus.value === 'APPROVED') {
    return t('dashboard.teacherApproved')
  }

  if (teacherStatus.value === 'REJECTED') {
    return t('dashboard.teacherRejected')
  }

  return t('dashboard.teacherPending')
})

const analyticsCards = computed(() => [
  { label: t('analytics.cards.totalLessons'), value: analytics.value?.overview.totalLessons ?? dashboard.value?.totalLessons ?? 0 },
  { label: t('analytics.cards.totalActiveStudents'), value: analytics.value?.overview.totalActiveStudents ?? dashboard.value?.totalStudents ?? 0 },
  { label: t('analytics.cards.averageQuizScore'), value: `${analytics.value?.overview.averageQuizScore ?? dashboard.value?.averageQuizScore ?? 0}%` },
  { label: t('analytics.cards.averageRating'), value: analytics.value?.overview.averageRating ?? dashboard.value?.ratingAverage ?? 0 },
  {
    label: t('analytics.cards.netEarnings'),
    value: currency(analytics.value?.overview.netEarnings ?? dashboard.value?.earningsSummary.netAmount ?? 0),
  },
])

const secondaryAnalyticsCards = computed(() => [
  {
    label: t('analytics.cards.totalModules'),
    value: analytics.value?.overview.totalModules ?? 0,
  },
  {
    label: t('analytics.cards.totalPackages'),
    value: analytics.value?.overview.totalPackages ?? 0,
  },
  {
    label: t('analytics.cards.availableBalance'),
    value: currency(analytics.value?.overview.availableBalance ?? dashboard.value?.earningsSummary.availableBalance ?? 0),
  },
  {
    label: t('analytics.cards.upcomingLiveClasses'),
    value: analytics.value?.overview.upcomingLiveClasses ?? 0,
  },
  {
    label: t('analytics.cards.aiDraftRequests'),
    value: analytics.value?.overview.aiDraftRequests ?? 0,
  },
])

const actionCards = computed(() => [
  {
    title: t('content.moduleManagementTitle'),
    description: t('content.moduleManagementDescription'),
    routeName: 'teacher-modules',
  },
  {
    title: t('content.lessonManagementTitle'),
    description: t('content.lessonManagementDescription'),
    routeName: 'teacher-lessons',
  },
  {
    title: t('content.packageManagementTitle'),
    description: t('content.packageManagementDescription'),
    routeName: 'teacher-packages',
  },
  { title: t('dashboard.manageQuizzes'), description: t('dashboard.manageQuizzesDesc'), routeName: 'teacher-quizzes' },
  { title: t('dashboard.manageFlashcards'), description: t('dashboard.manageFlashcardsDesc'), routeName: 'teacher-flashcards' },
  {
    title: t('dashboard.manageMatchingGames'),
    description: t('dashboard.manageMatchingGamesDesc'),
    routeName: 'teacher-matching-games',
  },
  {
    title: t('dashboard.viewStudentProgress'),
    description: t('dashboard.viewStudentProgressDesc'),
    routeName: 'teacher-quiz-results',
  },
  { title: t('dashboard.giveFeedback'), description: t('dashboard.giveFeedbackDesc'), routeName: 'teacher-feedback' },
])

const contentStatusBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.contentStatusBreakdown ?? [], ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHED', 'ARCHIVED']),
)

const reviewRatingBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.reviewRatingBreakdown ?? [], ['1', '2', '3', '4', '5']),
)

const quizTrendOption = computed(() =>
  buildLineChartOption(analytics.value?.quizScoreTrend ?? [], t, {
    color: '#567eff',
    seriesName: t('analytics.charts.quizScoreTrend'),
    valueSuffix: '%',
    decimals: 0,
    area: true,
  }),
)

const earningsTrendOption = computed(() =>
  buildBarChartOption(analytics.value?.earningsTrend ?? [], t, {
    color: '#22c55e',
    seriesName: t('analytics.charts.earningsTrend'),
    decimals: 2,
  }),
)

const contentStatusOption = computed(() =>
  buildDonutChartOption(contentStatusBreakdown.value, t, {
    centerLabel: t('analytics.charts.contentStatusBreakdown'),
  }),
)

const reviewRatingOption = computed(() =>
  buildDonutChartOption(reviewRatingBreakdown.value, t, {
    centerLabel: t('analytics.charts.reviewRatingBreakdown'),
  }),
)

const quizTrendEmpty = computed(() => !hasTrendData(analytics.value?.quizScoreTrend ?? []))
const earningsTrendEmpty = computed(() => !hasTrendData(analytics.value?.earningsTrend ?? []))
const contentStatusEmpty = computed(() => !hasBreakdownData(contentStatusBreakdown.value))
const reviewRatingEmpty = computed(() => !hasBreakdownData(reviewRatingBreakdown.value))

const reviewRows = computed(() =>
  dashboard.value?.recentReviews.map((review) => ({
    ...review,
    ratingLabel: translateAnalyticsKey(String(review.rating), t),
  })) ?? [],
)

async function loadDashboard() {
  loading.value = true
  analyticsError.value = ''

  try {
    const [dashboardResponse, analyticsResponse] = await Promise.all([
      dashboardService.getTeacherDashboard(),
      dashboardService.getTeacherAnalytics().catch((error) => {
        analyticsError.value = error instanceof Error ? error.message : t('analytics.loadFailed')
        return null
      }),
    ])
    dashboard.value = dashboardResponse
    analytics.value = analyticsResponse
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="10">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('nav.teacherDashboard') }}</p>
              <h1>{{ t('dashboard.teacherWelcome', { name: user?.fullName ?? dashboard?.fullName ?? 'Teacher' }) }}</h1>
              <p>{{ t('dashboard.teacherOverview') }}</p>
            </div>

            <div class="dashboard-hero__aside">
              <el-tag :type="statusType" size="large" round>{{ teacherStatus ?? 'PENDING' }}</el-tag>
              <el-button
                type="primary"
                size="large"
                :disabled="contentLocked"
                @click="router.push({ name: 'teacher-lesson-new' })"
              >
                {{ t('dashboard.createNewLesson') }}
              </el-button>
            </div>
          </div>
        </el-card>

        <el-alert :title="statusMessage" :type="statusType" show-icon :closable="false" />

        <section class="teacher-analytics-grid">
          <el-card v-for="card in analyticsCards" :key="card.label" class="metric-card metric-card--teacher" shadow="hover">
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
          </el-card>
          <el-card v-for="card in secondaryAnalyticsCards" :key="card.label" class="metric-card metric-card--teacher" shadow="hover">
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
          </el-card>
        </section>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.workspaceActions') }}</p>
                <h2>{{ t('dashboard.teachingTools') }}</h2>
              </div>
            </div>

            <div class="action-card-grid">
              <router-link
                v-for="card in actionCards"
                :key="card.title"
                :to="{ name: card.routeName }"
                class="management-card"
                :class="{ 'management-card--locked': contentLocked && card.routeName !== 'teacher-quiz-results' && card.routeName !== 'teacher-feedback' }"
              >
                <div>
                  <h3>{{ card.title }}</h3>
                  <p>{{ card.description }}</p>
                </div>
                <el-tag
                  v-if="contentLocked && card.routeName !== 'teacher-quiz-results' && card.routeName !== 'teacher-feedback'"
                  type="warning"
                  round
                >
                  {{ t('dashboard.lockedUntilApproval') }}
                </el-tag>
              </router-link>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.teacherProfile') }}</p>
                <h2>{{ t('dashboard.profileAndStatus') }}</h2>
              </div>
            </div>

            <div class="profile-summary">
              <p><strong>{{ t('auth.fields.email') }}:</strong> {{ user?.email }}</p>
              <p><strong>{{ t('dashboard.teacherStatus') }}:</strong> {{ teacherStatus ?? 'PENDING' }}</p>
              <p><strong>{{ t('auth.fields.bio') }}:</strong> {{ user?.teacherProfile?.bio || t('dashboard.noBio') }}</p>
              <p v-if="teacherStatus === 'REJECTED' && user?.teacherProfile?.rejectionReason">
                <strong>{{ t('dashboard.rejectionReason') }}:</strong> {{ user.teacherProfile.rejectionReason }}
              </p>
            </div>
          </el-card>
        </section>

        <section class="dashboard-grid">
          <AnalyticsChartCard
            :title="t('analytics.charts.quizScoreTrend')"
            :subtitle="t('analytics.teacherQuizTrendDescription')"
            :option="quizTrendOption"
            :loading="loading"
            :error="analyticsError"
            :empty="quizTrendEmpty"
            :empty-description="t('analytics.emptyTrend')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.earningsTrend')"
            :subtitle="t('analytics.teacherEarningsDescription')"
            :option="earningsTrendOption"
            :loading="loading"
            :error="analyticsError"
            :empty="earningsTrendEmpty"
            :empty-description="t('analytics.emptyFinancialTrend')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.contentStatusBreakdown')"
            :subtitle="t('analytics.teacherContentStatusDescription')"
            :option="contentStatusOption"
            :loading="loading"
            :error="analyticsError"
            :empty="contentStatusEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.reviewRatingBreakdown')"
            :subtitle="t('analytics.teacherReviewRatingDescription')"
            :option="reviewRatingOption"
            :loading="loading"
            :error="analyticsError"
            :empty="reviewRatingEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />
        </section>

        <section class="dashboard-grid">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.activeStudents') }}</p>
                <h2>{{ t('dashboard.learnersOverview') }}</h2>
              </div>
            </div>
            <el-empty v-if="!dashboard?.activeStudents.length" :description="t('dashboard.noActiveStudents')" />
            <div v-else class="stacked-summary-list">
              <article v-for="student in dashboard.activeStudents" :key="student.studentId" class="summary-row">
                <div>
                  <strong>{{ student.fullName }}</strong>
                  <p>{{ student.currentLevel || t('dashboard.currentLevel') }}</p>
                </div>
                <el-tag round type="success">{{ t('analytics.activeLearnerLabel') }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recentQuizResults') }}</p>
                <h2>{{ t('dashboard.quizPerformance') }}</h2>
              </div>
            </div>

            <el-empty v-if="!dashboard?.recentQuizResults.length" :description="t('dashboard.noQuizResultsYet')" />
            <div v-else class="stacked-summary-list">
              <article v-for="quiz in dashboard.recentQuizResults" :key="quiz.attemptId" class="summary-row">
                <div>
                  <strong>{{ quiz.quizTitle }}</strong>
                  <p>{{ quiz.studentName }} - {{ quiz.score ?? 0 }}/{{ quiz.maxScore ?? 0 }}</p>
                </div>
                <el-tag round type="success">{{ t('dashboard.reviewed') }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recentReviews') }}</p>
                <h2>{{ t('dashboard.feedbackSnapshot') }}</h2>
              </div>
            </div>
            <el-empty v-if="!reviewRows.length" :description="t('dashboard.noReviewsYet')" />
            <div v-else class="stacked-summary-list">
              <article v-for="review in reviewRows" :key="review.reviewId" class="summary-row">
                <div>
                  <strong>{{ review.reviewerName }}</strong>
                  <p>{{ review.comment || t('analytics.noCommentProvided') }}</p>
                </div>
                <el-tag round type="warning">{{ review.ratingLabel }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.notifications') }}</p>
                <h2>{{ t('dashboard.recentUpdates') }}</h2>
              </div>
            </div>
            <el-empty
              v-if="!dashboard?.recentNotifications.length"
              :description="t('dashboard.noNotifications')"
            />
            <div v-else class="stacked-summary-list">
              <article v-for="notification in dashboard.recentNotifications" :key="notification.notificationId" class="summary-row">
                <div>
                  <strong>{{ notification.title }}</strong>
                  <p>{{ notification.message }}</p>
                </div>
                <el-tag round>{{ notification.status }}</el-tag>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
