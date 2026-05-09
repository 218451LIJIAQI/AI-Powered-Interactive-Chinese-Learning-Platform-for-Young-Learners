<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { ListeningExercisePlay } from '../../types/learning'

const route = useRoute()
const { t } = useI18n()
const loading = ref(true)
const submitting = ref(false)
const exercise = ref<ListeningExercisePlay | null>(null)
const selectedAnswer = ref('')
const feedback = ref('')
const errorMessage = ref('')

const exerciseId = computed(() => Number(route.params.exerciseId))

async function loadExercise() {
  loading.value = true
  try {
    exercise.value = await studentLearningService.getListeningExercise(exerciseId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

function speakText() {
  if (!exercise.value?.chineseText || !('speechSynthesis' in window)) {
    return
  }

  const utterance = new SpeechSynthesisUtterance(exercise.value.chineseText)
  utterance.lang = 'zh-CN'
  window.speechSynthesis.speak(utterance)
}

async function submitAnswer() {
  if (!selectedAnswer.value.trim()) {
    ElMessage.error(t('learning.validation.chooseAnswer'))
    return
  }

  submitting.value = true
  try {
    const result = await studentLearningService.submitListeningExercise(exerciseId.value, {
      answerText: selectedAnswer.value,
    })
    feedback.value = result.correct
      ? t('learning.listeningCorrect', { points: result.earnedPoints })
      : t('learning.listeningIncorrect', { answer: result.correctAnswer })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('learning.messages.listeningSubmitFailed'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadExercise()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.listeningEyebrow')"
      :title="exercise?.title || t('learning.listeningPlayTitle')"
      :description="t('learning.listeningPlayDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="6">
      <template #default>
        <el-card v-if="exercise" class="dashboard-panel" shadow="hover">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('learning.audioPrompt') }}</p>
              <h2>{{ exercise.chineseText }}</h2>
            </div>
            <el-button round @click="speakText">{{ t('learning.playAudio') }}</el-button>
          </div>

          <el-radio-group v-model="selectedAnswer" class="student-option-group">
            <el-radio v-for="option in exercise.options" :key="option.optionText" :value="option.optionText" border size="large">
              {{ option.optionText }}
            </el-radio>
          </el-radio-group>

          <div class="student-practice-card__actions">
            <el-button type="primary" round :loading="submitting" @click="submitAnswer">{{ t('learning.submitAnswer') }}</el-button>
          </div>

          <el-alert v-if="feedback" :title="feedback" type="success" show-icon :closable="false" />
        </el-card>
      </template>
    </el-skeleton>
  </div>
</template>
