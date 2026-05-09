<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiQuizGeneratorResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const loading = ref(false)
const errorMessage = ref('')
const result = ref<AiQuizGeneratorResponse | null>(null)

const form = reactive({
  topic: '',
  level: 'Beginner',
  category: 'Vocabulary',
  numberOfQuestions: 5,
  languagePreference: authStore.currentUser?.preferredLanguage ?? 'en',
})

async function generateDraft() {
  if (teacherLocked.value) {
    ElMessage.warning(t('ai.teacherApprovalRequired'))
    return
  }
  if (!form.topic.trim()) {
    ElMessage.error(t('ai.validation.topicRequired'))
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    result.value = await aiService.quizGenerator({
      topic: form.topic.trim(),
      level: form.level,
      category: form.category,
      numberOfQuestions: form.numberOfQuestions,
      targetRoleContext: 'TEACHER',
      languagePreference: form.languagePreference,
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('ai.messages.unavailable')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <PageHero
      :eyebrow="t('ai.quizEyebrow')"
      :title="t('ai.teacherQuizTitle')"
      :description="t('ai.teacherQuizDescription')"
    />

    <el-alert
      v-if="teacherLocked"
      :title="t('ai.teacherApprovalRequired')"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-form-grid">
        <el-input v-model="form.topic" :placeholder="t('ai.topicPlaceholder')" maxlength="200" />
        <el-select v-model="form.level">
          <el-option :label="t('ai.levels.beginner')" value="Beginner" />
          <el-option :label="t('ai.levels.intermediate')" value="Intermediate" />
        </el-select>
        <el-select
          v-model="form.category"
          filterable
          allow-create
          default-first-option
          :placeholder="t('ai.categoryLabel')"
        >
          <el-option :label="t('ai.categories.vocabulary')" value="Vocabulary" />
          <el-option :label="t('ai.categories.grammar')" value="Grammar" />
          <el-option :label="t('ai.categories.conversation')" value="Conversation" />
          <el-option :label="t('ai.categories.pronunciation')" value="Pronunciation" />
          <el-option :label="t('ai.categories.writing')" value="Writing" />
          <el-option :label="t('ai.categories.listening')" value="Listening" />
        </el-select>
        <el-input-number v-model="form.numberOfQuestions" :min="1" :max="10" />
      </div>

      <div class="student-practice-card__actions">
        <el-button type="primary" round :loading="loading" :disabled="teacherLocked" @click="generateDraft">
          {{ t('ai.generateTeacherQuizDraft') }}
        </el-button>
      </div>
    </el-card>

    <el-card v-if="result" class="dashboard-panel ai-result-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('ai.draftOnlyLabel') }}</p>
          <h2>{{ result.title }}</h2>
          <p>{{ t('ai.teacherDraftNotice') }}</p>
        </div>
        <el-tag round>{{ result.status }}</el-tag>
      </div>

      <div class="stacked-summary-list">
        <article v-for="(question, index) in result.questions" :key="`${question.questionText}-${index}`" class="summary-row">
          <div>
            <strong>{{ index + 1 }}. {{ question.questionText }}</strong>
            <p>{{ question.questionType }}</p>
            <p>{{ question.options.join(' | ') }}</p>
            <p><strong>{{ t('learning.correctAnswer') }}:</strong> {{ question.correctAnswer }}</p>
            <p v-if="question.explanation">{{ question.explanation }}</p>
          </div>
        </article>
      </div>
    </el-card>
  </div>
</template>
