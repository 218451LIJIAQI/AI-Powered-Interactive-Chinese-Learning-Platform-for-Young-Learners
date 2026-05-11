<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { studentLearningService } from '../../services/studentLearningService'
import type { QuizAttemptResult } from '../../types/learning'

const { t } = useI18n()
const attemptId = useNumericRouteParam('attemptId')
const loading = ref(true)
const result = ref<QuizAttemptResult | null>(null)
const errorMessage = ref('')

async function loadResult() {
  loading.value = true
  try {
    result.value = await studentLearningService.getQuizAttempt(attemptId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadResult()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.quizResultEyebrow')"
      :title="result?.quizTitle || t('learning.quizResultTitle')"
      :description="t('learning.quizResultDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--student" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('learning.scoreLabel') }}</p>
              <h1>{{ result?.score }} / {{ result?.maxScore }}</h1>
              <p>{{ t('learning.pointsEarnedRound', { points: result?.earnedPoints ?? 0 }) }}</p>
            </div>
            <div class="dashboard-hero__aside dashboard-hero__aside--student">
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('learning.correctAnswers') }}</span>
                <strong>{{ result?.correctCount }}</strong>
              </div>
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('learning.wrongAnswers') }}</span>
                <strong>{{ result?.wrongCount }}</strong>
              </div>
            </div>
          </div>
        </el-card>

        <section class="dashboard-stack" v-if="result">
          <el-card v-for="answer in result.answers" :key="answer.questionId" class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ answer.questionType }}</p>
                <h2>{{ answer.questionText }}</h2>
              </div>
              <el-tag :type="answer.correct ? 'success' : 'danger'" round>
                {{ answer.correct ? t('learning.correct') : t('learning.tryAgain') }}
              </el-tag>
            </div>
            <p><strong>{{ t('learning.yourAnswer') }}:</strong> {{ answer.studentAnswer }}</p>
            <p><strong>{{ t('learning.correctAnswer') }}:</strong> {{ answer.correctAnswer }}</p>
            <p v-if="answer.explanation"><strong>{{ t('learning.explanation') }}:</strong> {{ answer.explanation }}</p>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
