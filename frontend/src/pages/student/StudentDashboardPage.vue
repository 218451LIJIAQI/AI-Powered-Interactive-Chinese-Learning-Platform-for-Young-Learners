<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { dashboardService } from '../../services/dashboardService'
import { useAuthStore } from '../../stores/authStore'
import type { StudentDashboardSummary } from '../../types/dashboard'

const authStore = useAuthStore()
const { t } = useI18n()
const user = computed(() => authStore.currentUser)

const loading = ref(true)
const dashboard = ref<StudentDashboardSummary | null>(null)

const quickActions = [
  { title: 'Flashcards', description: 'Review words with visual cards.', icon: 'Files', routeName: 'student-modules' },
  { title: 'Games', description: 'Play matching and listening mini activities.', icon: 'Connection', routeName: 'student-progress' },
  { title: 'Quizzes', description: 'Practise with short revision challenges.', icon: 'DocumentChecked', routeName: 'student-progress' },
  { title: 'Tracing Practice', description: 'Warm up writing and tracing skills.', icon: 'EditPen', routeName: 'student-writing-feedback' },
]

const statCards = computed(() => [
  { label: t('dashboard.currentLevel'), value: dashboard.value?.currentLevel ?? 'Beginner', accent: 'sun' },
  { label: t('dashboard.learningProgress'), value: `${dashboard.value?.learningProgressPercent ?? 0}%`, accent: 'sea' },
  { label: t('dashboard.dailyStreak'), value: `${dashboard.value?.dailyStreak ?? 0} ${t('dashboard.days')}`, accent: 'berry' },
  { label: t('dashboard.points'), value: dashboard.value?.points ?? 0, accent: 'lime' },
  { label: t('dashboard.badges'), value: dashboard.value?.badgeCount ?? 0, accent: 'plum' },
])

async function loadDashboard() {
  loading.value = true

  try {
    dashboard.value = await dashboardService.getStudentDashboard()
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
            <div>
              <p class="section-label">{{ t('nav.studentDashboard') }}</p>
              <h1>{{ t('dashboard.studentWelcome', { name: user?.fullName ?? dashboard?.fullName ?? 'Learner' }) }}</h1>
              <p>{{ t('dashboard.studentOverview') }}</p>
            </div>

            <div class="dashboard-hero__aside dashboard-hero__aside--student">
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('dashboard.aiRecommendedNextStep') }}</span>
                <strong>{{ dashboard?.aiRecommendedNextStep ?? t('dashboard.studentDefaultAiStep') }}</strong>
              </div>
              <el-button type="primary" size="large" round>
                {{ t('dashboard.continueLearning') }}
              </el-button>
            </div>
          </div>
        </el-card>

        <section class="student-stat-grid">
          <el-card
            v-for="card in statCards"
            :key="card.label"
            class="metric-card metric-card--student"
            :class="`metric-card--${card.accent}`"
            shadow="hover"
          >
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
          </el-card>
        </section>

        <section class="dashboard-grid dashboard-grid--two-thirds">
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
                <p class="section-label">{{ t('dashboard.quickAccess') }}</p>
                <h2>{{ t('dashboard.quickStart') }}</h2>
              </div>
            </div>

            <div class="quick-action-grid">
              <router-link
                v-for="action in quickActions"
                :key="action.title"
                :to="{ name: action.routeName }"
                class="quick-action-card"
              >
                <el-icon class="quick-action-card__icon"><component :is="action.icon" /></el-icon>
                <h3>{{ action.title }}</h3>
                <p>{{ action.description }}</p>
              </router-link>
            </div>
          </el-card>
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

            <div v-if="dashboard?.weakAreas.length" class="tag-list">
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
                  <strong>{{ item.itemType }}</strong>
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
                  {{ notification.status }}
                </el-tag>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
