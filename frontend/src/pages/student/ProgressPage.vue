<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { aiService } from '../../services/aiService'
import { studentLearningService } from '../../services/studentLearningService'
import type { AiLearningRecommendationResponse, AiProgressInsightResponse } from '../../types/ai'
import type { StudentProgressSummary } from '../../types/learning'

const { t } = useI18n()
const loading = ref(true)
const progress = ref<StudentProgressSummary | null>(null)
const aiLoading = ref(false)
const errorMessage = ref('')
const recommendation = ref<AiLearningRecommendationResponse | null>(null)
const insight = ref<AiProgressInsightResponse | null>(null)

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

async function loadAiInsight() {
  if (!progress.value) {
    return
  }

  aiLoading.value = true

  try {
    const [recommendationResult, insightResult] = await Promise.all([
      aiService.learningRecommendation({
        currentLevel: progress.value.overallCompletionPercent >= 50 ? 'Intermediate' : 'Beginner',
        weakAreas: progress.value.weakAreas,
        languagePreference: 'en',
      }),
      aiService.progressInsight({
        progressSummary: progress.value.lastActivitySummary || `Overall completion ${progress.value.overallCompletionPercent}%`,
        weakAreas: progress.value.weakAreas,
        audience: 'STUDENT',
        languagePreference: 'en',
      }),
    ])

    recommendation.value = recommendationResult
    insight.value = insightResult
  } finally {
    aiLoading.value = false
  }
}

onMounted(() => {
  loadProgress().then(() => loadAiInsight())
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

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('ai.featureType') }}</p>
                <h2>{{ t('ai.aiRecommendationTitle') }}</h2>
              </div>
            </div>

            <el-skeleton v-if="aiLoading" animated :rows="4" />
            <div v-else-if="recommendation" class="stacked-summary-list">
              <article class="summary-row">
                <div>
                  <strong>{{ recommendation.recommendedNextStep }}</strong>
                  <p>{{ recommendation.recommendedLessonOrActivity }}</p>
                </div>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.weakAreas') }}</strong>
                  <p>{{ recommendation.weakAreaSummary }}</p>
                </div>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.revisionFocus') }}</strong>
                  <p>{{ recommendation.revisionSuggestion }}</p>
                </div>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('nav.aiTutor') }}</p>
                <h2>{{ t('ai.aiInsightTitle') }}</h2>
              </div>
            </div>

            <el-skeleton v-if="aiLoading" animated :rows="4" />
            <div v-else-if="insight" class="stacked-summary-list">
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.learningProgress') }}</strong>
                  <p>{{ insight.performanceSummary }}</p>
                </div>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('ai.nextStepsLabel') }}</strong>
                  <p>{{ insight.nextStepSuggestions.join(' | ') }}</p>
                </div>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('ai.encouragementLabel') }}</strong>
                  <p>{{ insight.studentEncouragement }}</p>
                </div>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
