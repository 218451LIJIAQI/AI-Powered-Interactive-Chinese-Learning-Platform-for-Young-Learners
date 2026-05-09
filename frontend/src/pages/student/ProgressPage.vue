<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { StudentProgressSummary } from '../../types/learning'

const { t } = useI18n()
const loading = ref(true)
const progress = ref<StudentProgressSummary | null>(null)
const errorMessage = ref('')

async function loadProgress() {
  loading.value = true
  try {
    progress.value = await studentLearningService.getProgress()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProgress()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.progressEyebrow')"
      :title="t('learning.progressTitle')"
      :description="t('learning.progressDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <section class="student-stat-grid" v-if="progress">
          <el-card class="metric-card metric-card--student metric-card--sun" shadow="hover">
            <p>{{ t('learning.lessonsStarted') }}</p>
            <strong>{{ progress.totalLessonsStarted }}</strong>
          </el-card>
          <el-card class="metric-card metric-card--student metric-card--sea" shadow="hover">
            <p>{{ t('learning.completedLessons') }}</p>
            <strong>{{ progress.completedLessons }}</strong>
          </el-card>
          <el-card class="metric-card metric-card--student metric-card--berry" shadow="hover">
            <p>{{ t('learning.quizAverage') }}</p>
            <strong>{{ progress.quizAverage }}%</strong>
          </el-card>
          <el-card class="metric-card metric-card--student metric-card--lime" shadow="hover">
            <p>{{ t('learning.overallProgress') }}</p>
            <strong>{{ progress.overallCompletionPercent }}%</strong>
          </el-card>
        </section>

        <section class="dashboard-grid" v-if="progress">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('learning.recentLessons') }}</p>
                <h2>{{ t('learning.keepGoing') }}</h2>
              </div>
            </div>
            <div class="stacked-summary-list">
              <article v-for="lesson in progress.recentLessons" :key="lesson.lessonId" class="summary-row">
                <div>
                  <strong>{{ lesson.lessonTitle }}</strong>
                  <p>{{ lesson.completionPercent }}% {{ t('dashboard.complete') }}</p>
                </div>
                <el-progress :percentage="lesson.completionPercent" :show-text="false" />
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.revisionFocus') }}</p>
                <h2>{{ t('dashboard.weakAreas') }}</h2>
              </div>
            </div>
            <div class="tag-list" v-if="progress.weakAreas.length">
              <el-tag v-for="item in progress.weakAreas" :key="item" type="warning" round>{{ item }}</el-tag>
            </div>
            <el-empty v-else :description="t('dashboard.noWeakAreas')" />
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
