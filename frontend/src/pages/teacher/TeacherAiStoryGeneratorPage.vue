<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiStoryGeneratorResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const loading = ref(false)
const errorMessage = ref('')
const result = ref<AiStoryGeneratorResponse | null>(null)

const form = reactive({
  topic: '',
  level: 'Beginner',
  lengthPreference: 'Short',
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
    result.value = await aiService.storyGenerator({
      topic: form.topic.trim(),
      level: form.level,
      lengthPreference: form.lengthPreference,
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
      :eyebrow="t('ai.storyEyebrow')"
      :title="t('ai.teacherStoryTitle')"
      :description="t('ai.teacherStoryDescription')"
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
        <el-select v-model="form.lengthPreference">
          <el-option :label="t('ai.lengthOptions.short')" value="Short" />
          <el-option :label="t('ai.lengthOptions.medium')" value="Medium" />
        </el-select>
      </div>

      <div class="student-practice-card__actions">
        <el-button type="primary" round :loading="loading" :disabled="teacherLocked" @click="generateDraft">
          {{ t('ai.generateTeacherStoryDraft') }}
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
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.chineseStory') }}</strong>
            <p>{{ result.chineseStory }}</p>
          </div>
        </article>
        <article class="summary-row" v-if="result.pinyin">
          <div>
            <strong>{{ t('learning.fields.pinyin') }}</strong>
            <p>{{ result.pinyin }}</p>
          </div>
        </article>
        <article class="summary-row" v-if="result.englishTranslation || result.bahasaMelayuTranslation">
          <div>
            <strong>{{ t('ai.translationLabel') }}</strong>
            <p>{{ result.englishTranslation }}</p>
            <p>{{ result.bahasaMelayuTranslation }}</p>
          </div>
        </article>
      </div>

      <div class="ai-result-grid">
        <article class="mini-card">
          <h3>{{ t('ai.storyVocabularyList') }}</h3>
          <p v-for="(item, index) in result.vocabularyList" :key="`${item.chineseWord}-${index}`">
            {{ item.chineseWord }} - {{ item.pinyin }} - {{ item.englishMeaning || item.bahasaMelayuMeaning }}
          </p>
        </article>
        <article class="mini-card">
          <h3>{{ t('ai.comprehensionQuestions') }}</h3>
          <p v-for="(question, index) in result.comprehensionQuestions" :key="`${question.question}-${index}`">
            {{ question.question }} {{ question.answer ? `- ${question.answer}` : '' }}
          </p>
        </article>
      </div>

      <el-alert :title="result.encouragement" type="success" show-icon :closable="false" />
    </el-card>
  </div>
</template>
