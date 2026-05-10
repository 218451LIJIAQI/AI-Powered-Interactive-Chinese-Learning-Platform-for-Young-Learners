<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiVocabularyHelperResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()
const translateEnum = useEnumLabel()

const loading = ref(false)
const errorMessage = ref('')
const result = ref<AiVocabularyHelperResponse | null>(null)

const form = reactive({
  word: '',
  inputLanguage: 'en',
  outputLanguage: 'zh',
  learnerLevel: 'Beginner',
  languagePreference: authStore.currentUser?.preferredLanguage ?? 'en',
})

async function generateVocabulary() {
  if (!form.word.trim()) {
    ElMessage.error(t('ai.validation.wordRequired'))
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    result.value = await aiService.vocabularyHelper({
      word: form.word.trim(),
      inputLanguage: form.inputLanguage,
      outputLanguage: form.outputLanguage,
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
      :eyebrow="t('ai.vocabularyEyebrow')"
      :title="t('ai.vocabularyTitle')"
      :description="t('ai.vocabularyDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.aiVocabulary') }}</p>
          <h2>{{ t('ai.vocabularyFormTitle') }}</h2>
        </div>
      </div>

      <div class="content-form-grid">
        <el-input v-model="form.word" :placeholder="t('ai.wordPlaceholder')" maxlength="200" show-word-limit />
        <el-select v-model="form.inputLanguage">
          <el-option :label="t('ai.languages.en')" value="en" />
          <el-option :label="t('ai.languages.ms')" value="ms" />
          <el-option :label="t('ai.languages.zh')" value="zh" />
        </el-select>
        <el-select v-model="form.outputLanguage">
          <el-option :label="t('ai.languages.zh')" value="zh" />
          <el-option :label="t('ai.languages.en')" value="en" />
          <el-option :label="t('ai.languages.ms')" value="ms" />
        </el-select>
        <el-select v-model="form.learnerLevel">
          <el-option :label="t('ai.levels.beginner')" value="Beginner" />
          <el-option :label="t('ai.levels.intermediate')" value="Intermediate" />
        </el-select>
      </div>

      <div class="student-practice-card__actions">
        <el-button type="primary" round :loading="loading" @click="generateVocabulary">
          {{ t('ai.generateVocabulary') }}
        </el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card v-if="result" class="dashboard-panel ai-result-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('ai.resultLabel') }}</p>
          <h2>{{ result.chineseWord || form.word }}</h2>
        </div>
        <el-tag round>{{ translateEnum('aiStatus', result.status) }}</el-tag>
      </div>

      <div class="ai-result-grid">
        <article class="mini-card">
          <h3>{{ t('learning.fields.pinyin') }}</h3>
          <p>{{ result.pinyin || '-' }}</p>
        </article>
        <article class="mini-card">
          <h3>{{ t('learning.fields.englishMeaning') }}</h3>
          <p>{{ result.englishMeaning || '-' }}</p>
        </article>
        <article class="mini-card">
          <h3>{{ t('learning.fields.malayMeaning') }}</h3>
          <p>{{ result.bahasaMelayuMeaning || '-' }}</p>
        </article>
      </div>

      <div class="stacked-summary-list">
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.simpleExplanation') }}</strong>
            <p>{{ result.simpleExplanation }}</p>
          </div>
        </article>
        <article class="summary-row" v-if="result.exampleSentence">
          <div>
            <strong>{{ t('ai.exampleSentence') }}</strong>
            <p>{{ result.exampleSentence }}</p>
            <p>{{ result.exampleSentencePinyin }}</p>
            <p>{{ result.exampleSentenceTranslation }}</p>
          </div>
        </article>
        <article class="summary-row" v-if="result.imagePromptOrDescription">
          <div>
            <strong>{{ t('ai.imageIdea') }}</strong>
            <p>{{ result.imagePromptOrDescription }}</p>
          </div>
        </article>
        <article class="summary-row" v-if="result.miniPracticeQuestion">
          <div>
            <strong>{{ t('ai.practiceQuestion') }}</strong>
            <p>{{ result.miniPracticeQuestion }}</p>
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
