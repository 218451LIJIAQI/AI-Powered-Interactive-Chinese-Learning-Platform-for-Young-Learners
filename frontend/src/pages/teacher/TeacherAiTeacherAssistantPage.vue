<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiTeacherAssistantResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()
const translateEnum = useEnumLabel()

const loading = ref(false)
const errorMessage = ref('')
const result = ref<AiTeacherAssistantResponse | null>(null)
const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')

const form = reactive({
  topic: '',
  level: 'Beginner',
  category: 'Vocabulary',
  learningObjective: '',
  activityTypes: ['flashcards', 'quiz', 'matching'],
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
    result.value = await aiService.teacherAssistant({
      topic: form.topic.trim(),
      level: form.level,
      category: form.category,
      learningObjective: form.learningObjective.trim() || undefined,
      activityTypes: form.activityTypes,
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
      :eyebrow="t('ai.teacherEyebrow')"
      :title="t('ai.teacherTitle')"
      :description="t('ai.teacherDescription')"
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
        <el-input v-model="form.learningObjective" :placeholder="t('ai.learningObjectivePlaceholder')" maxlength="500" />
      </div>

      <div class="student-practice-card__actions">
        <el-button type="primary" round :loading="loading" :disabled="teacherLocked" @click="generateDraft">
          {{ t('ai.generateTeacherDraft') }}
        </el-button>
      </div>
    </el-card>

    <el-card v-if="result" class="dashboard-panel ai-result-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('ai.draftOnlyLabel') }}</p>
          <h2>{{ t('nav.aiTeacherAssistant') }}</h2>
          <p>{{ t('ai.teacherDraftNotice') }}</p>
        </div>
        <el-tag round>{{ translateEnum('aiStatus', result.status) }}</el-tag>
      </div>

      <div class="stacked-summary-list">
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.lessonOutline') }}</strong>
            <p>{{ result.lessonOutline }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.storyLessonIdea') }}</strong>
            <p>{{ result.storyBasedLessonIdea || '-' }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.teachingNotes') }}</strong>
            <p>{{ result.teachingNotes }}</p>
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
          <h3>{{ t('ai.exampleSentences') }}</h3>
          <p v-for="(sentence, index) in result.exampleSentences" :key="`${sentence}-${index}`">{{ sentence }}</p>
        </article>
        <article class="mini-card">
          <h3>{{ t('ai.flashcardIdeas') }}</h3>
          <p v-for="(item, index) in result.flashcardContent" :key="`${item}-${index}`">{{ item }}</p>
        </article>
        <article class="mini-card">
          <h3>{{ t('ai.matchingIdeas') }}</h3>
          <p v-for="(item, index) in result.matchingGameContent" :key="`${item}-${index}`">{{ item }}</p>
        </article>
      </div>
    </el-card>
  </div>
</template>
