<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { WrongAnswerItem } from '../../types/learning'

const { t } = useI18n()
const loading = ref(true)
const wrongAnswers = ref<WrongAnswerItem[]>([])
const errorMessage = ref('')

async function loadWrongAnswers() {
  loading.value = true
  try {
    wrongAnswers.value = await studentLearningService.getWrongAnswers()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadWrongAnswers()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.wrongAnswersEyebrow')"
      :title="t('learning.wrongAnswersTitle')"
      :description="t('learning.wrongAnswersDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <section class="dashboard-stack">
          <el-card v-for="item in wrongAnswers" :key="`${item.attemptId}-${item.questionId}`" class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ item.quizTitle }}</p>
                <h2>{{ item.questionText }}</h2>
              </div>
              <el-tag type="warning" round>{{ item.questionType }}</el-tag>
            </div>
            <p><strong>{{ t('learning.yourAnswer') }}:</strong> {{ item.studentAnswer }}</p>
            <p><strong>{{ t('learning.correctAnswer') }}:</strong> {{ item.correctAnswer }}</p>
            <p v-if="item.explanation"><strong>{{ t('learning.explanation') }}:</strong> {{ item.explanation }}</p>
            <div class="student-practice-card__actions">
              <router-link
                :to="{
                  name: 'student-ai-tutor',
                  query: {
                    topic: item.questionText,
                    context: item.correctAnswer,
                  },
                }"
              >
                <el-button round>{{ t('ai.askAiAboutMistake') }}</el-button>
              </router-link>
            </div>
          </el-card>
        </section>

        <el-empty v-if="!wrongAnswers.length" :description="t('learning.noWrongAnswers')" />
      </template>
    </el-skeleton>
  </div>
</template>
