<template>
  <section class="teacher-ai-draft-panel" :data-testid="`teacher-ai-draft-${mode}`">
    <div class="teacher-ai-draft-head">
      <div class="teacher-ai-draft-title">
        <span class="teacher-ai-draft-icon" aria-hidden="true"><IconGlyph name="ai" :size="16" /></span>
        <strong>{{ t('teacherAiDraft.title') }}</strong>
        <AiResultLabel />
      </div>
      <p class="teacher-ai-draft-hint">
        {{ mode === 'quiz' ? t('teacherAiDraft.quizHint') : t('teacherAiDraft.storyHint') }}
      </p>
    </div>

    <AiDisclaimer />

    <p v-if="previewMode" class="teacher-ai-draft-note">{{ t('teacherAiDraft.previewNotice') }}</p>
    <p v-else-if="checkingAvailability" class="teacher-ai-draft-note">{{ t('teacherAiDraft.checking') }}</p>
    <p v-else-if="!available" class="teacher-ai-draft-note is-warning" role="status">
      {{ unavailableMessage }}
    </p>

    <form class="teacher-ai-draft-form" @submit.prevent="generate">
      <label class="field teacher-ai-draft-topic">
        <span class="field-label">{{ t('teacherAiDraft.topic') }}</span>
        <input
          v-model="topic"
          class="input"
          maxlength="200"
          :placeholder="t('teacherAiDraft.topicPlaceholder')"
          :disabled="controlsDisabled"
          data-testid="teacher-ai-draft-topic"
        />
      </label>

      <label class="field">
        <span class="field-label">{{ t('teacherAiDraft.level') }}</span>
        <select v-model="level" class="select" :disabled="controlsDisabled">
          <option value="HSK 1">{{ t('teacherAiDraft.levelHsk1') }}</option>
          <option value="HSK 2">{{ t('teacherAiDraft.levelHsk2') }}</option>
        </select>
      </label>

      <label v-if="mode === 'quiz'" class="field">
        <span class="field-label">{{ t('teacherAiDraft.questionCount') }}</span>
        <input
          v-model.number="questionCount"
          class="input"
          type="number"
          min="1"
          max="10"
          :disabled="controlsDisabled"
        />
      </label>

      <label v-else class="field">
        <span class="field-label">{{ t('teacherAiDraft.length') }}</span>
        <select v-model="lengthPreference" class="select" :disabled="controlsDisabled">
          <option value="short">{{ t('teacherAiDraft.lengthShort') }}</option>
          <option value="medium">{{ t('teacherAiDraft.lengthMedium') }}</option>
        </select>
      </label>

      <button
        class="btn btn-secondary teacher-ai-draft-submit"
        type="submit"
        :disabled="controlsDisabled"
        :aria-busy="generating"
        data-testid="teacher-ai-draft-generate"
      >
        {{ generating ? t('teacherAiDraft.generating') : t('teacherAiDraft.generate') }}
      </button>
    </form>

    <p v-if="errorMessage" class="teacher-ai-draft-note is-error" role="alert">{{ errorMessage }}</p>
    <div v-if="resultMessage" class="teacher-ai-draft-result" role="status" data-testid="teacher-ai-draft-result">
      <AiResultLabel />
      <span>{{ resultMessage }}</span>
      <small>{{ t('teacherAiDraft.notSaved') }}</small>
    </div>

    <div
      v-if="mode === 'story' && storyVocabulary.length"
      class="teacher-ai-draft-result"
      data-testid="teacher-ai-draft-vocabulary"
    >
      <div class="teacher-ai-draft-title">
        <strong>{{ t('teacherAiDraft.draftVocabulary') }}</strong>
        <AiResultLabel />
      </div>
      <small>{{ t('teacherAiDraft.draftVocabularyHint') }}</small>
      <div class="teacher-ai-draft-table-wrap">
        <table class="teacher-ai-draft-table">
          <thead>
            <tr>
              <th scope="col">{{ t('teacherAiDraft.vocabWord') }}</th>
              <th scope="col">{{ t('teacherAiDraft.vocabPinyin') }}</th>
              <th scope="col">{{ t('teacherAiDraft.vocabMeaning') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in storyVocabulary" :key="index" data-testid="teacher-ai-draft-vocabulary-item">
              <td lang="zh-CN">{{ item.chineseWord }}</td>
              <td>{{ item.pinyin }}</td>
              <td>{{ item.meaning }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="teacher-ai-draft-copy-row">
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          data-testid="teacher-ai-draft-copy-vocabulary"
          @click="copyVocabulary"
        >
          {{ t('teacherAiDraft.copyVocabulary') }}
        </button>
        <small role="status" aria-live="polite">{{ copyMessage }}</small>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { teacherApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { teacherAiQuizDraft, teacherAiStoryDraft } from '@/demo/data'
import { teacherAiDraftMessages } from '@/plugins/teacherAiDraftMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { AiQuizGeneratorResponse, AiStoryGeneratorResponse } from '@/types/api'
import { storyVocabularyBlock } from '@/utils/aiDraft'
import { storyVocabularyRows } from '@/utils/aiPracticeQuiz'
import { isForbiddenApiError, resolveApiError } from '@/utils/apiError'

/**
 * AI-assisted quiz / story draft generator for approved teachers (D6). The parent receives the draft through
 * `quiz-draft` / `story-draft` and fills its own form; this panel never saves anything.
 */
const props = withDefaults(defineProps<{ mode: 'quiz' | 'story'; disabled?: boolean }>(), { disabled: false })
const emit = defineEmits<{
  'quiz-draft': [draft: AiQuizGeneratorResponse]
  'story-draft': [draft: AiStoryGeneratorResponse]
  forbidden: []
}>()

const { t } = useI18n({
  messages: {
    en: { teacherAiDraft: teacherAiDraftMessages.en },
    ms: { teacherAiDraft: teacherAiDraftMessages.ms },
    'zh-CN': { teacherAiDraft: teacherAiDraftMessages['zh-CN'] },
  },
})
const auth = useAuthStore()
const ui = useUiStore()

const topic = ref('')
const level = ref('HSK 1')
const questionCount = ref(3)
const lengthPreference = ref('short')
const generating = ref(false)
const checkingAvailability = ref(false)
const available = ref(true)
const availabilityMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const resultMessage = ref<string | null>(null)
/** Last story draft, kept only for the read-only vocabulary preview; it is never saved from here. */
const storyDraft = ref<AiStoryGeneratorResponse | null>(null)
const copyMessage = ref('')

const storyVocabulary = computed(() => storyVocabularyRows(storyDraft.value, ui.locale))

const previewMode = computed(() => auth.isPreviewSession && !auth.isAuthenticated)
const controlsDisabled = computed(
  () => props.disabled || generating.value || checkingAvailability.value || (!previewMode.value && !available.value),
)
const unavailableMessage = computed(() => availabilityMessage.value?.trim() || t('teacherAiDraft.unavailable'))

// Checked once, the first time the panel is enabled (it can start disabled and be enabled without remounting).
let availabilityChecked = false
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled || previewMode.value || availabilityChecked) return
    availabilityChecked = true
    void checkAvailability()
  },
  { immediate: true },
)

async function checkAvailability(): Promise<void> {
  checkingAvailability.value = true
  try {
    const status = await teacherApi.aiAvailability()
    available.value = status.available && status.textGenerationAvailable
    availabilityMessage.value = available.value ? null : (status.message ?? null)
  } catch (reason) {
    available.value = false
    availabilityMessage.value = resolveApiError(reason, t('teacherAiDraft.unavailable'))
    if (isForbiddenApiError(reason)) emit('forbidden')
  } finally {
    checkingAvailability.value = false
  }
}

async function generate(): Promise<void> {
  if (controlsDisabled.value) return
  errorMessage.value = null
  resultMessage.value = null
  copyMessage.value = ''

  const cleanTopic = topic.value.trim()
  if (!cleanTopic) {
    errorMessage.value = t('teacherAiDraft.topicRequired')
    return
  }

  generating.value = true
  try {
    if (props.mode === 'quiz') {
      const draft = previewMode.value
        ? teacherAiQuizDraft
        : await teacherApi.aiQuizDraft({
            topic: cleanTopic,
            level: level.value,
            numberOfQuestions: Math.min(Math.max(Math.round(questionCount.value || 3), 1), 10),
            languagePreference: ui.locale,
          })
      emit('quiz-draft', draft)
      resultMessage.value = t('teacherAiDraft.filledQuiz', { title: draft.title, count: draft.questions.length })
    } else {
      const draft = previewMode.value
        ? teacherAiStoryDraft
        : await teacherApi.aiStoryDraft({
            topic: cleanTopic,
            level: level.value,
            lengthPreference: lengthPreference.value,
            languagePreference: ui.locale,
          })
      storyDraft.value = draft
      emit('story-draft', draft)
      resultMessage.value = t('teacherAiDraft.filledStory', { title: draft.title })
    }
  } catch (reason) {
    errorMessage.value = resolveApiError(reason, t('teacherAiDraft.error'))
    if (isForbiddenApiError(reason)) emit('forbidden')
  } finally {
    generating.value = false
  }
}

async function copyVocabulary(): Promise<void> {
  if (!storyDraft.value) return
  const text = storyVocabularyBlock(storyDraft.value, ui.locale, t('teacherAiDraft.vocabularyHeading'))
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
    await navigator.clipboard.writeText(text)
    copyMessage.value = t('teacherAiDraft.vocabularyCopied')
  } catch {
    copyMessage.value = t('teacherAiDraft.copyFailed')
  }
}
</script>

<style scoped>
.teacher-ai-draft-panel {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4);
  border: 1px dashed var(--color-primary-a32);
  border-radius: var(--radius-lg);
  background: var(--color-primary-a04);
}

.teacher-ai-draft-head {
  display: grid;
  gap: var(--space-1-5);
}

.teacher-ai-draft-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.teacher-ai-draft-title strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.teacher-ai-draft-icon {
  display: grid;
  place-items: center;
  width: var(--control-height-sm);
  height: var(--control-height-sm);
  border-radius: var(--radius-sm);
  background: var(--color-primary-100);
  color: var(--color-primary-700);
}

.teacher-ai-draft-hint,
.teacher-ai-draft-note {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

/* Topic on its own row; the remaining controls and the button share the next row when there is room. */
.teacher-ai-draft-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 128px), 1fr));
  gap: var(--space-3);
  align-items: end;
}

.teacher-ai-draft-topic {
  grid-column: 1 / -1;
}

.teacher-ai-draft-note.is-warning {
  color: var(--color-warning-text);
  font-weight: var(--font-weight-semibold);
}

.teacher-ai-draft-note.is-error {
  color: var(--color-danger-text);
  font-weight: var(--font-weight-semibold);
}

.teacher-ai-draft-result {
  display: grid;
  gap: var(--space-1-5);
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  font-size: var(--font-size-md);
}

.teacher-ai-draft-result small {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-ai-draft-table-wrap {
  overflow-x: auto;
}

.teacher-ai-draft-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-md);
}

.teacher-ai-draft-table th,
.teacher-ai-draft-table td {
  padding: var(--space-1-5) var(--space-2);
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
}

.teacher-ai-draft-table th {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.teacher-ai-draft-copy-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
}
</style>
