<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import AnalyticsChartCard from '../../components/common/AnalyticsChartCard.vue'
import KidStatCard from '../../components/common/KidStatCard.vue'
import MascotPanda from '../../components/common/MascotPanda.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { dashboardService } from '../../services/dashboardService'
import { useAuthStore } from '../../stores/authStore'
import type { StudentAnalyticsResponse } from '../../types/analytics'
import type { StudentDashboardSummary } from '../../types/dashboard'
import {
  buildDonutChartOption,
  buildLineChartOption,
  hasBreakdownData,
  hasTrendData,
  normalizeBreakdown,
  translateAnalyticsKey,
} from '../../utils/analytics'

const authStore = useAuthStore()
const { t } = useI18n()
const translateEnum = useEnumLabel()
const user = computed(() => authStore.currentUser)

const loading = ref(true)
const dashboard = ref<StudentDashboardSummary | null>(null)
const analytics = ref<StudentAnalyticsResponse | null>(null)
const analyticsError = ref('')

const quickActions = computed(() => [
  {
    title: t('dashboard.quickActionLessons'),
    description: t('dashboard.quickActionLessonsDesc'),
    icon: 'Reading',
    emoji: '\ud83d\udcda',
    routeName: 'student-lessons',
  },
  {
    title: t('nav.aiTutor'),
    description: t('ai.quickTutorDescription'),
    icon: 'ChatDotRound',
    emoji: '\ud83e\udd16',
    routeName: 'student-ai-tutor',
  },
  {
    title: t('dashboard.quickActionFlashcards'),
    description: t('dashboard.quickActionFlashcardsDesc'),
    icon: 'Files',
    emoji: '\ud83c\udfb4',
    routeName: 'student-lessons',
  },
  {
    title: t('dashboard.quickActionWrongAnswers'),
    description: t('dashboard.quickActionWrongAnswersDesc'),
    icon: 'DocumentDelete',
    emoji: '\ud83d\udd0d',
    routeName: 'student-wrong-answers',
  },
  {
    title: t('dashboard.quickActionRewards'),
    description: t('dashboard.quickActionRewardsDesc'),
    icon: 'TrophyBase',
    emoji: '\ud83c\udfc6',
    routeName: 'student-rewards',
  },
])

const statCards = computed(() => [
  {
    label: t('kid.statLevel'),
    value: dashboard.value?.currentLevel ?? 'Beginner',
    accent: 'sun' as const,
    emoji: '\ud83c\udf1f',
  },
  {
    label: t('kid.statProgress'),
    value: `${dashboard.value?.learningProgressPercent ?? 0}%`,
    accent: 'sea' as const,
    emoji: '\ud83d\ude80',
  },
  {
    label: t('kid.statStreak'),
    value: `${dashboard.value?.dailyStreak ?? 0} ${t('dashboard.days')}`,
    accent: 'berry' as const,
    emoji: '\ud83d\udd25',
  },
  {
    label: t('kid.statPoints'),
    value: dashboard.value?.points ?? 0,
    accent: 'lime' as const,
    emoji: '\u2b50',
  },
  {
    label: t('kid.statBadges'),
    value: dashboard.value?.badgeCount ?? 0,
    accent: 'plum' as const,
    emoji: '\ud83c\udfc5',
  },
])

const studentOverviewCards = computed(() => [
  {
    label: t('kid.statLessonsDone'),
    value: analytics.value?.overview.completedLessons ?? 0,
    accent: 'sun' as const,
    emoji: '\ud83d\udcd6',
  },
  {
    label: t('kid.statQuizScore'),
    value: `${analytics.value?.overview.averageQuizScore ?? 0}%`,
    accent: 'sea' as const,
    emoji: '\ud83c\udfaf',
  },
  {
    label: t('kid.statVideos'),
    value: analytics.value?.overview.completedVideos ?? 0,
    accent: 'berry' as const,
    emoji: '\ud83c\udfa5',
  },
  {
    label: t('kid.statLiveClasses'),
    value: analytics.value?.overview.upcomingLiveClasses ?? 0,
    accent: 'lime' as const,
    emoji: '\ud83c\udf93',
  },
  {
    label: t('kid.statMessages'),
    value: analytics.value?.overview.unreadNotifications ?? 0,
    accent: 'plum' as const,
    emoji: '\ud83d\udce8',
  },
])

const lessonBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.lessonStatusBreakdown ?? [], ['COMPLETED', 'IN_PROGRESS']),
)

const learningModeBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.learningModeBreakdown ?? [], ['LIVE', 'RECORDED', 'HYBRID']),
)

const quizTrendOption = computed(() =>
  buildLineChartOption(analytics.value?.quizScoreTrend ?? [], t, {
    color: '#3bb3c3',
    seriesName: t('analytics.charts.quizScoreTrend'),
    valueSuffix: '%',
    decimals: 0,
    area: true,
  }),
)

const activityTrendOption = computed(() =>
  buildLineChartOption(analytics.value?.activityTrend ?? [], t, {
    color: '#ff8f42',
    seriesName: t('analytics.charts.activityTrend'),
    decimals: 0,
    area: true,
  }),
)

const lessonBreakdownOption = computed(() =>
  buildDonutChartOption(lessonBreakdown.value, t, {
    centerLabel: t('analytics.charts.lessonStatusBreakdown'),
  }),
)

const learningModeOption = computed(() =>
  buildDonutChartOption(learningModeBreakdown.value, t, {
    centerLabel: t('analytics.charts.learningModeBreakdown'),
  }),
)

const quizTrendEmpty = computed(() => !hasTrendData(analytics.value?.quizScoreTrend ?? []))
const activityTrendEmpty = computed(() => !hasTrendData(analytics.value?.activityTrend ?? []))
const lessonBreakdownEmpty = computed(() => !hasBreakdownData(lessonBreakdown.value))
const learningModeEmpty = computed(() => !hasBreakdownData(learningModeBreakdown.value))

const weakAreasList = computed(() =>
  (analytics.value?.weakAreaBreakdown ?? []).map((item) => ({
    label: translateAnalyticsKey(item.key, t),
    value: item.value,
  })),
)

async function loadDashboard() {
  loading.value = true
  analyticsError.value = ''

  try {
    const [dashboardResponse, analyticsResponse] = await Promise.all([
      dashboardService.getStudentDashboard(),
      dashboardService.getStudentAnalytics().catch((error) => {
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
      <template #template>
        <el-card class="dashboard-hero dashboard-hero--student" shadow="never">
          <el-skeleton-item variant="h3" style="width: 60%" />
          <el-skeleton-item variant="text" style="width: 90%; margin-top: 16px" />
        </el-card>
      </template>

      <template #default>
        <el-card class="dashboard-hero dashboard-hero--student" shadow="hover">
          <div class="dashboard-hero__split">
            <div class="dashboard-hero__copy">
              <p class="section-label">✨ {{ t('nav.studentDashboard') }} ✨</p>
              <h1>
                {{
                  user?.fullName || dashboard?.fullName
                    ? t('kid.dashboardWelcome', { name: user?.fullName ?? dashboard?.fullName })
                    : t('kid.dashboardWelcomeDefault')
                }}
              </h1>
              <p>{{ t('kid.dashboardSubtitle') }}</p>
            </div>

            <div class="dashboard-hero__aside dashboard-hero__aside--student">
              <MascotPanda size="lg" />
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">📍 {{ t('dashboard.recommendedNextStep') }}</span>
                <strong>{{ dashboard?.recommendedNextStep ?? t('dashboard.studentDefaultNextStep') }}</strong>
              </div>
              <el-button type="primary" size="large" round>
                🚀 {{ t('dashboard.continueLearning') }}
              </el-button>
            </div>
          </div>
        </el-card>

        <section class="student-stat-grid">
          <KidStatCard
            v-for="card in statCards"
            :key="card.label"
            :label="card.label"
            :value="card.value"
            :emoji="card.emoji"
            :accent="card.accent"
          />
        </section>

        <section class="student-stat-grid">
          <KidStatCard
            v-for="card in studentOverviewCards"
            :key="card.label"
            :label="card.label"
            :value="card.value"
            :emoji="card.emoji"
            :accent="card.accent"
          />
        </section>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel dashboard-panel--featured" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('nav.aiTutor') }}</p>
                <h2>{{ t('ai.dashboardCardTitle') }}</h2>
              </div>
              <el-tag type="success" round>{{ t('ai.childFriendlyLabel') }}</el-tag>
            </div>

            <p>{{ t('ai.dashboardCardDescription') }}</p>
            <div class="student-practice-card__actions">
              <router-link :to="{ name: 'student-ai-tutor' }">
                <el-button type="primary" round>{{ t('ai.openAiTutor') }}</el-button>
              </router-link>
              <router-link :to="{ name: 'student-ai-vocabulary' }">
                <el-button round>{{ t('ai.tryVocabularyHelper') }}</el-button>
              </router-link>
            </div>
          </el-card>

          <el-card class="dashboard-panel dashboard-panel--featured" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recommendedLessons') }}</p>
                <h2>{{ t('dashboard.learningJourney') }}</h2>
              </div>
              <el-progress
                :percentage="dashboard?.learningProgressPercent ?? 0"
                color="#3bb3c3"
                :stroke-width="14"
                status="success"
              />
            </div>

            <el-empty
              v-if="!dashboard?.recommendedLessons.length"
              :description="t('dashboard.noRecommendedLessons')"
            />
            <div v-else class="mini-card-grid">
              <article v-for="lesson in dashboard.recommendedLessons" :key="lesson.lessonId" class="mini-card">
                <h3>{{ lesson.title }}</h3>
                <p>{{ lesson.category || t('dashboard.generalPractice') }}</p>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">🎮 {{ t('kid.quickStartTitle') }}</p>
                <h2>{{ t('kid.quickStartSubtitle') }}</h2>
              </div>
            </div>

            <div class="quick-action-grid">
              <router-link
                v-for="action in quickActions"
                :key="action.title"
                :to="{ name: action.routeName }"
                class="quick-action-card"
              >
                <span class="quick-action-card__icon" aria-hidden="true">{{ action.emoji }}</span>
                <h3>{{ action.title }}</h3>
                <p>{{ action.description }}</p>
              </router-link>
            </div>
          </el-card>
        </section>

        <section class="dashboard-grid">
          <AnalyticsChartCard
            :title="t('analytics.charts.quizScoreTrend')"
            :subtitle="t('analytics.studentQuizTrendDescription')"
            :option="quizTrendOption"
            :loading="loading"
            :error="analyticsError"
            :empty="quizTrendEmpty"
            :empty-description="t('analytics.emptyTrend')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.activityTrend')"
            :subtitle="t('analytics.studentActivityDescription')"
            :option="activityTrendOption"
            :loading="loading"
            :error="analyticsError"
            :empty="activityTrendEmpty"
            :empty-description="t('analytics.emptyActivity')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.lessonStatusBreakdown')"
            :subtitle="t('analytics.studentLessonStatusDescription')"
            :option="lessonBreakdownOption"
            :loading="loading"
            :error="analyticsError"
            :empty="lessonBreakdownEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.learningModeBreakdown')"
            :subtitle="t('analytics.studentLearningModeDescription')"
            :option="learningModeOption"
            :loading="loading"
            :error="analyticsError"
            :empty="learningModeEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />
        </section>

        <section class="dashboard-grid">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recentQuizResults') }}</p>
                <h2>{{ t('dashboard.practiceSnapshot') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!dashboard?.recentQuizResults.length"
              :description="t('dashboard.noQuizResults')"
            />
            <div v-else class="stacked-summary-list">
              <article v-for="quiz in dashboard.recentQuizResults" :key="quiz.attemptId" class="summary-row">
                <div>
                  <strong>{{ quiz.quizTitle }}</strong>
                  <p>{{ quiz.score ?? 0 }} / {{ quiz.maxScore ?? 0 }}</p>
                </div>
                <el-tag round type="success">{{ t('dashboard.completed') }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.weakAreas') }}</p>
                <h2>{{ t('dashboard.revisionFocus') }}</h2>
              </div>
            </div>

            <div v-if="weakAreasList.length" class="stacked-summary-list">
              <article v-for="area in weakAreasList" :key="area.label" class="summary-row">
                <div>
                  <strong>{{ area.label }}</strong>
                  <p>{{ t('analytics.weakAreaItemDescription') }}</p>
                </div>
                <el-tag round type="warning">{{ area.value }}</el-tag>
              </article>
            </div>
            <div v-else-if="dashboard?.weakAreas.length" class="tag-list">
              <el-tag v-for="area in dashboard.weakAreas" :key="area" round type="warning">{{ area }}</el-tag>
            </div>
            <el-empty v-else :description="t('dashboard.noWeakAreas')" />
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.unfinishedLessons') }}</p>
                <h2>{{ t('dashboard.pickUpWhereYouLeftOff') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!dashboard?.unfinishedLessons.length"
              :description="t('dashboard.noUnfinishedLessons')"
            />
            <div v-else class="stacked-summary-list">
              <article v-for="lesson in dashboard.unfinishedLessons" :key="lesson.lessonId" class="summary-row">
                <div>
                  <strong>{{ lesson.lessonTitle }}</strong>
                  <p>{{ lesson.completionPercent ?? 0 }}% {{ t('dashboard.complete') }}</p>
                </div>
                <el-progress :percentage="lesson.completionPercent ?? 0" :show-text="false" />
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.savedItems') }}</p>
                <h2>{{ t('dashboard.savedForLater') }}</h2>
              </div>
            </div>

            <el-empty v-if="!dashboard?.savedItems.length" :description="t('dashboard.noSavedItems')" />
            <div v-else class="stacked-summary-list">
              <article v-for="item in dashboard.savedItems" :key="item.savedItemId" class="summary-row">
                <div>
                  <strong>{{ translateEnum('notification.savedItem', item.itemType) }}</strong>
                  <p>{{ item.note || t('dashboard.savedItemReady') }}</p>
                </div>
                <el-tag round>{{ t('dashboard.saved') }}</el-tag>
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
              <article
                v-for="notification in dashboard.recentNotifications"
                :key="notification.notificationId"
                class="summary-row"
              >
                <div>
                  <strong>{{ notification.title }}</strong>
                  <p>{{ notification.message }}</p>
                </div>
                <el-tag round :type="notification.status === 'UNREAD' ? 'danger' : 'info'">
                  {{ translateEnum('notification.status', notification.status) }}
                </el-tag>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
