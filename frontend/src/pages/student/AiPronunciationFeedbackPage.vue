<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiPronunciationFeedbackResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()

const loading = ref(false)
const selectedFile = ref<File | null>(null)
const errorMessage = ref('')
const result = ref<AiPronunciationFeedbackResponse | null>(null)

const form = reactive({
  targetText: '',
  pinyin: '',
  tone: '',
  languagePreference: authStore.currentUser?.preferredLanguage ?? 'en',
})

function handleFileChange(file: { raw?: File }) {
  selectedFile.value = file.raw ?? null
}

async function submitFeedback() {
  if (!selectedFile.value) {
    ElMessage.error(t('ai.validation.audioFileRequired'))
    return
  }
  if (!form.targetText.trim()) {
    ElMessage.error(t('ai.validation.targetTextRequired'))
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('targetText', form.targetText.trim())
    if (form.pinyin.trim()) {
      formData.append('pinyin', form.pinyin.trim())
    }
    if (form.tone.trim()) {
      formData.append('tone', form.tone.trim())
    }
    formData.append('languagePreference', form.languagePreference)

    result.value = await aiService.pronunciationFeedback(formData)
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
      :eyebrow="t('ai.pronunciationEyebrow')"
      :title="t('ai.pronunciationTitle')"
      :description="t('ai.pronunciationDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-form-grid">
        <el-input v-model="form.targetText" :placeholder="t('ai.targetTextPlaceholder')" maxlength="200" />
        <el-input v-model="form.pinyin" :placeholder="t('ai.pinyinPlaceholder')" maxlength="120" />
        <el-input v-model="form.tone" :placeholder="t('ai.tonePlaceholder')" maxlength="50" />
      </div>

      <el-upload
        :auto-upload="false"
        :limit="1"
        accept=".webm,.wav,.mp3,.mpeg,audio/*"
        :show-file-list="true"
        :on-change="handleFileChange"
      >
        <template #trigger>
          <el-button plain type="primary">{{ t('ai.selectAudioFile') }}</el-button>
        </template>
      </el-upload>

      <div class="student-practice-card__actions">
        <el-button type="primary" round :loading="loading" @click="submitFeedback">
          {{ t('ai.submitForFeedback') }}
        </el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card v-if="result" class="dashboard-panel ai-result-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('ai.feedbackLabel') }}</p>
          <h2>{{ result.feedback }}</h2>
        </div>
        <el-tag round>{{ result.status }}</el-tag>
      </div>

      <div class="stacked-summary-list">
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.pronunciationComment') }}</strong>
            <p>{{ result.pronunciationComment }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.toneComment') }}</strong>
            <p>{{ result.toneComment }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.improvementTips') }}</strong>
            <p>{{ result.improvementTips }}</p>
          </div>
        </article>
      </div>

      <el-alert :title="result.encouragement" type="success" show-icon :closable="false" />
    </el-card>
  </div>
</template>
