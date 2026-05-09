<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { StudentLessonActivitySummary, StudentLessonPlayer } from '../../types/learning'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(true)
const errorMessage = ref('')
const lesson = ref<StudentLessonPlayer | null>(null)

const lessonId = computed(() => Number(route.params.lessonId))

const activityCards = computed(() => lesson.value?.activities ?? [])

function activityRoute(activity: StudentLessonActivitySummary) {
  if (!activity.sourceId || activity.locked || !activity.available) {
    return null
  }

  switch (activity.activityType) {
    case 'FLASHCARD':
      return { name: 'student-flashcards', params: { lessonId: lessonId.value } }
    case 'QUIZ':
      return { name: 'student-quiz', params: { quizId: activity.sourceId } }
    case 'MATCHING':
      return { name: 'student-matching-game', params: { gameId: activity.sourceId } }
    case 'LISTENING':
      return { name: 'student-listening', params: { exerciseId: activity.sourceId } }
    case 'TRACING':
      return { name: 'student-tracing', params: { exerciseId: activity.sourceId } }
    default:
      return null
  }
}

async function loadLesson() {
  loading.value = true
  errorMessage.value = ''

  try {
    lesson.value = await studentLearningService.getLessonPlayer(lessonId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

function openActivity(activity: StudentLessonActivitySummary) {
  const target = activityRoute(activity)
  if (!target) {
    return
  }

  router.push(target)
}

onMounted(() => {
  loadLesson()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('content.lesson')"
      :title="lesson?.title || t('content.lesson')"
      :description="lesson?.description || t('dashboard.studentOverview')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--student" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('dashboard.learningJourney') }}</p>
              <h1>{{ lesson?.title }}</h1>
              <p>{{ lesson?.basicContent || lesson?.description }}</p>
            </div>
            <div class="dashboard-hero__aside dashboard-hero__aside--student">
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('dashboard.learningProgress') }}</span>
                <strong>{{ lesson?.completionPercent ?? 0 }}%</strong>
              </div>
              <el-progress
                :percentage="lesson?.completionPercent ?? 0"
                :stroke-width="16"
                color="#3bb3c3"
                status="success"
              />
            </div>
          </div>
        </el-card>

        <el-alert
          v-if="lesson?.locked"
          :title="t('content.locked')"
          type="warning"
          show-icon
          :closable="false"
          :description="t('content.freeToBrowse')"
        />

        <section class="student-activity-grid">
          <el-card
            v-for="activity in activityCards"
            :key="`${activity.activityType}-${activity.sourceId ?? activity.placeholderId}`"
            class="student-activity-card"
            shadow="hover"
          >
            <div class="student-activity-card__top">
              <div>
                <p class="section-label">{{ activity.activityType }}</p>
                <h2>{{ activity.title }}</h2>
              </div>
              <el-tag :type="activity.completed ? 'success' : activity.available ? 'warning' : 'info'" round>
                {{
                  activity.completed
                    ? t('dashboard.completed')
                    : activity.locked
                      ? t('content.locked')
                      : activity.available
                        ? t('dashboard.continueLearning')
                        : t('common.comingSoon')
                }}
              </el-tag>
            </div>

            <p>{{ activity.description || t('dashboard.studentOverview') }}</p>

            <div class="student-activity-card__actions">
              <el-button
                type="primary"
                round
                :disabled="!activityRoute(activity)"
                @click="openActivity(activity)"
              >
                {{ activity.completed ? t('dashboard.continueLearning') : t('common.startLearning') }}
              </el-button>
            </div>
          </el-card>
        </section>

        <el-empty v-if="!activityCards.length" :description="t('content.noActivities')" />
      </template>
    </el-skeleton>
  </div>
</template>
