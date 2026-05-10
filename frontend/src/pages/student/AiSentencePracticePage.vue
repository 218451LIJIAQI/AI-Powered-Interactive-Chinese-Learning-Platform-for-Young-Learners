<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiSentencePracticeResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()
const translateEnum = useEnumLabel()

const loading = ref(false)
const errorMessage = ref('')
const result = ref<AiSentencePracticeResponse | null>(null)

const form = reactive({
  sentence: '',
  learnerLevel: 'Beginner',
  languagePreference: authStore.currentUser?.preferredLanguage ?? 'en',
})

async function checkSentence() {
  if (!form.sentence.trim()) {
    ElMessage.error(t('ai.validation.sentenceRequired'))
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    result.value = await aiService.sentencePractice({
      sentence: form.sentence.trim(),
      learnerLevel: form.learnerLevel,
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
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('ai.sentenceEyebrow')"
      :title="t('ai.sentenceTitle')"
      :description="t('ai.sentenceDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.aiSentencePractice') }}</p>
          <h2>{{ t('ai.sentenceFormTitle') }}</h2>
        </div>
      </div>

      <el-input
        v-model="form.sentence"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
        :placeholder="t('ai.sentencePlaceholder')"
      />

      <div class="student-practice-card__actions">
        <el-select v-model="form.learnerLevel" class="content-filter">
          <el-option :label="t('ai.levels.beginner')" value="Beginner" />
          <el-option :label="t('ai.levels.intermediate')" value="Intermediate" />
        </el-select>
        <el-button type="primary" round :loading="loading" @click="checkSentence">
          {{ t('ai.checkSentence') }}
        </el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card v-if="result" class="dashboard-panel ai-result-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('ai.resultLabel') }}</p>
          <h2>{{ result.correctedSentence }}</h2>
        </div>
        <el-tag round>{{ translateEnum('aiStatus', result.status) }}</el-tag>
      </div>

      <div class="stacked-summary-list">
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.originalSentence') }}</strong>
            <p>{{ result.originalSentence }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.correctedSentence') }}</strong>
            <p>{{ result.correctedSentence }}</p>
          </div>
        </article>
        <article class="summary-row" v-if="result.pinyin">
          <div>
            <strong>{{ t('learning.fields.pinyin') }}</strong>
            <p>{{ result.pinyin }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.improvementExplanation') }}</strong>
            <p>{{ result.improvementExplanation }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.naturalVersion') }}</strong>
            <p>{{ result.naturalSentenceVersion }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.practiceTask') }}</strong>
            <p>{{ result.practiceTask }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.encouragementLabel') }}</strong>
            <p>{{ result.encouragement }}</p>
          </div>
        </article>
      </div>
    </el-card>
  </div>
</template>
