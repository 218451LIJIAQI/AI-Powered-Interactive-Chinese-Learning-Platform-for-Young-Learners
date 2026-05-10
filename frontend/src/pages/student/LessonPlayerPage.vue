<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { liveClassService } from '../../services/liveClassService'
import { studentLearningService } from '../../services/studentLearningService'
import type { StudentLessonActivitySummary, StudentLessonPlayer } from '../../types/learning'
import type { LiveClass, RecordedLessonVideo } from '../../types/liveLearning'
import { formatDateTime } from '../../utils/datetime'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const translateEnum = useEnumLabel()

const loading = ref(true)
const errorMessage = ref('')
const lesson = ref<StudentLessonPlayer | null>(null)

const lessonId = computed(() => Number(route.params.lessonId))

const activityCards = computed(() => lesson.value?.activities ?? [])
const liveClasses = computed(() => lesson.value?.liveClasses ?? [])
const recordedVideos = computed(() => lesson.value?.recordedVideos ?? [])
const showLiveSection = computed(() => liveClasses.value.length > 0 || lesson.value?.learningType === 'LIVE' || lesson.value?.learningType === 'HYBRID')
const showRecordedSection = computed(() => recordedVideos.value.length > 0 || lesson.value?.learningType === 'RECORDED' || lesson.value?.learningType === 'HYBRID')


function statusType(status: string | null) {
  if (status === 'ONGOING') return 'success'
  if (status === 'CANCELLED') return 'danger'
  if (status === 'RESCHEDULED') return 'warning'
  return 'info'
}

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

async function enrolLiveClass(item: LiveClass) {
  try {
    await liveClassService.enrolLiveClass(item.id)
    ElMessage.success(t('phase10.messages.enrolSuccess'))
    await loadLesson()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function cancelEnrolment(item: LiveClass) {
  try {
    await liveClassService.cancelLiveClassEnrolment(item.id)
    ElMessage.success(t('phase10.messages.cancelEnrolmentSuccess'))
    await loadLesson()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function joinLiveClass(item: LiveClass) {
  try {
    const result = await liveClassService.joinLiveClass(item.id)
    window.open(result.meetingLink, '_blank', 'noopener')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('phase10.joinUnavailable'))
  }
}

function openRecordedVideo(video: RecordedLessonVideo) {
  router.push({
    name: 'student-recorded-lesson',
    params: { id: video.id },
    query: { lessonId: lessonId.value.toString() },
  })
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
              <router-link :to="{ name: 'student-ai-tutor', query: { lessonId: lessonId, topic: lesson?.title } }">
                <el-button round>{{ t('ai.askAiHelp') }}</el-button>
              </router-link>
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
          :description="t('phase10.lockedHybridMessage')"
        />

        <section v-if="showLiveSection" class="dashboard-panel">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('phase10.liveClassesEyebrow') }}</p>
              <h2>{{ t('phase10.liveClasses') }}</h2>
            </div>
          </div>

          <el-table v-if="liveClasses.length" :data="liveClasses" class="phase-table">
            <el-table-column prop="title" :label="t('phase10.liveClasses')" min-width="220" />
            <el-table-column :label="t('phase10.startTime')" min-width="180">
              <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
            </el-table-column>
            <el-table-column :label="t('phase10.meetingPlatform')" min-width="150">
              <template #default="{ row }">{{ row.meetingPlatform ? translateEnum('meetingPlatform', row.meetingPlatform) : '-' }}</template>
            </el-table-column>
            <el-table-column :label="t('commerce.status')" min-width="140">
              <template #default="{ row }">
                <el-tag round :type="statusType(row.liveClassStatus)">{{ translateEnum('liveClassStatus', row.liveClassStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column width="340">
              <template #default="{ row }">
                <div class="table-actions">
                  <router-link :to="{ name: 'student-live-class-detail', params: { id: row.id } }">
                    <el-button plain type="primary">{{ t('content.reviewDetail') }}</el-button>
                  </router-link>
                  <el-button v-if="row.canEnrol" type="primary" @click="enrolLiveClass(row)">{{ t('phase10.enrol') }}</el-button>
                  <el-button v-else-if="row.enrolled" plain type="warning" @click="cancelEnrolment(row)">
                    {{ t('phase10.cancelEnrolment') }}
                  </el-button>
                  <el-button v-if="row.canJoin" type="success" @click="joinLiveClass(row)">{{ t('phase10.joinClass') }}</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-else :description="t('phase10.emptyLiveClasses')" />
        </section>

        <section v-if="showRecordedSection" class="dashboard-panel">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('phase10.recordedLessonsEyebrow') }}</p>
              <h2>{{ t('phase10.recordedLessons') }}</h2>
            </div>
          </div>

          <el-table v-if="recordedVideos.length" :data="recordedVideos" class="phase-table">
            <el-table-column prop="title" :label="t('phase10.recordedVideo')" min-width="220" />
            <el-table-column prop="sourceType" :label="t('phase10.videoSourceType')" min-width="150" />
            <el-table-column :label="t('phase10.duration')" min-width="120">
              <template #default="{ row }">{{ row.durationSeconds ?? 0 }}</template>
            </el-table-column>
            <el-table-column width="180">
              <template #default="{ row }">
                <el-button type="primary" @click="openRecordedVideo(row)">{{ t('phase10.continueWatching') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-else :description="t('phase10.emptyRecordedVideos')" />
        </section>

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
