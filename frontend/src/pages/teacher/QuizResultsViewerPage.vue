<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherActivityService } from '../../services/teacherActivityService'
import { teacherContentService } from '../../services/teacherContentService'
import type { LessonItem } from '../../types/contentManagement'
import type { TeacherQuiz, TeacherQuizAttemptSummary } from '../../types/learning'

const { t } = useI18n()

const loadingLessons = ref(true)
const loadingQuizzes = ref(false)
const loadingAttempts = ref(false)
const selectedLessonId = ref<number | null>(null)
const selectedQuizId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const quizzes = ref<TeacherQuiz[]>([])
const attempts = ref<TeacherQuizAttemptSummary[]>([])

const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const selectedQuiz = computed(() => quizzes.value.find((quiz) => quiz.id === selectedQuizId.value) ?? null)
const averageScore = computed(() => {
  if (!attempts.value.length) {
    return null
  }

  const total = attempts.value.reduce((sum, attempt) => sum + (attempt.score ?? 0), 0)
  return Math.round((total / attempts.value.length) * 10) / 10
})

async function loadLessons() {
  loadingLessons.value = true

  try {
    lessons.value = (await teacherContentService.getLessons()).filter((lesson) => lesson.contentStatus !== 'ARCHIVED')
    if (!selectedLessonId.value && lessons.value.length) {
      selectedLessonId.value = lessons.value[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingLessons.value = false
  }
}

async function loadQuizzes() {
  if (!selectedLessonId.value) {
    quizzes.value = []
    selectedQuizId.value = null
    return
  }

  loadingQuizzes.value = true

  try {
    quizzes.value = await teacherActivityService.getQuizzes(selectedLessonId.value)
    selectedQuizId.value = quizzes.value[0]?.id ?? null
  } catch (error) {
    quizzes.value = []
    selectedQuizId.value = null
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingQuizzes.value = false
  }
}

async function loadAttempts() {
  if (!selectedQuizId.value) {
    attempts.value = []
    return
  }

  loadingAttempts.value = true

  try {
    attempts.value = await teacherActivityService.getQuizAttempts(selectedQuizId.value)
  } catch (error) {
    attempts.value = []
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingAttempts.value = false
  }
}

watch(selectedLessonId, async () => {
  await loadQuizzes()
  await loadAttempts()
})

watch(selectedQuizId, async () => {
  await loadAttempts()
})

// `loadLessons()` sets `selectedLessonId` on success which triggers the watch
// above, which in turn cascades to `loadQuizzes` → `loadAttempts`. Calling
// them again here would fire 3 extra duplicate API requests on every mount.
onMounted(() => {
  loadLessons()
})
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('learning.quizResultsEyebrow') }}</p>
          <h1>{{ t('learning.quizResultsTitle') }}</h1>
          <p>{{ t('learning.quizResultsDescription') }}</p>
        </div>
        <div class="teacher-profile-actions">
          <el-select
            v-model="selectedLessonId"
            class="content-filter"
            :placeholder="t('learning.selectLesson')"
            :loading="loadingLessons"
          >
            <el-option v-for="lesson in lessons" :key="lesson.id" :label="lesson.title" :value="lesson.id" />
          </el-select>
          <el-select
            v-model="selectedQuizId"
            class="content-filter"
            :placeholder="t('learning.selectQuiz')"
            :loading="loadingQuizzes"
            :disabled="!quizzes.length"
          >
            <el-option v-for="quiz in quizzes" :key="quiz.id" :label="quiz.title" :value="quiz.id" />
          </el-select>
        </div>
      </div>
    </el-card>

    <section class="student-stat-grid">
      <el-card class="metric-card metric-card--teacher" shadow="hover">
        <p>{{ t('learning.totalAttempts') }}</p>
        <strong>{{ attempts.length }}</strong>
      </el-card>
      <el-card class="metric-card metric-card--teacher" shadow="hover">
        <p>{{ t('learning.quizQuestions') }}</p>
        <strong>{{ selectedQuiz?.questions.length ?? 0 }}</strong>
      </el-card>
      <el-card class="metric-card metric-card--teacher" shadow="hover">
        <p>{{ t('learning.averageScore') }}</p>
        <strong>{{ averageScore ?? '-' }}</strong>
      </el-card>
      <el-card class="metric-card metric-card--teacher" shadow="hover">
        <p>{{ t('learning.lesson') }}</p>
        <strong>{{ selectedLesson?.title ?? '-' }}</strong>
      </el-card>
      <el-card class="metric-card metric-card--teacher" shadow="hover">
        <p>{{ t('learning.quizTitleLabel') }}</p>
        <strong>{{ selectedQuiz?.title ?? '-' }}</strong>
      </el-card>
    </section>

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('learning.quizAttempts') }}</p>
          <h2>{{ selectedQuiz?.title || t('learning.selectQuiz') }}</h2>
        </div>
      </div>

      <el-table v-loading="loadingAttempts" :data="attempts" class="phase-table">
        <el-table-column prop="studentName" :label="t('learning.fields.studentName')" min-width="220" />
        <el-table-column :label="t('learning.fields.score')" min-width="160">
          <template #default="{ row }">{{ row.score ?? 0 }} / {{ row.maxScore ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="submittedAt" :label="t('learning.fields.submittedAt')" min-width="220" />
      </el-table>

      <el-empty v-if="!loadingAttempts && !attempts.length" :description="t('learning.noQuizAttempts')" />
    </el-card>
  </div>
</template>
