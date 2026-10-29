<template>
  <section class="teacher-ai-draft-panel">
    <div class="teacher-ai-draft-head">
      <div class="teacher-ai-draft-title">
        <span class="teacher-ai-draft-icon" aria-hidden="true"><IconGlyph name="ai" :size="16" /></span>
        <strong>{{ t('teacherAiDraft.title') }}</strong>
        <AiResultLabel />
      </div>
      <p class="teacher-ai-draft-hint">{{ t('teacherAiDraft.quizHint') }}</p>
    </div>

    <AiDisclaimer />

    <p v-if="checkingAvailability" class="teacher-ai-draft-note">{{ t('teacherAiDraft.checking') }}</p>
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

      <label class="field">
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

      <button
        class="btn btn-secondary"
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
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { teacherApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherAiDraftMessages } from '@/plugins/teacherAiDraftMessages'
import { useUiStore } from '@/stores/ui'
import type { AiQuizGeneratorResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

/**
 * AI-assisted quiz draft generator for approved teachers (FRAI05). The parent receives the draft through
 * `quiz-draft` and fills its own question builder; this panel never saves anything.
 */
const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ 'quiz-draft': [draft: AiQuizGeneratorResponse] }>()

const { t } = useLocalMessages({ teacherAiDraft: teacherAiDraftMessages })
const ui = useUiStore()

const topic = ref('')
const level = ref('HSK 1')
const questionCount = ref(3)
const generating = ref(false)
const checkingAvailability = ref(false)
const available = ref(true)
const availabilityMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const resultMessage = ref<string | null>(null)

const controlsDisabled = computed(
  () => props.disabled || generating.value || checkingAvailability.value || !available.value,
)
const unavailableMessage = computed(() => availabilityMessage.value?.trim() || t('teacherAiDraft.unavailable'))

// Checked once, the first time the panel is enabled (it can start disabled and be enabled without remounting).
let availabilityChecked = false
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled || availabilityChecked) return
    availabilityChecked = true
    void checkAvailability()
  },
  { immediate: true },
)

async function checkAvailability(): Promise<void> {
  checkingAvailability.value = true
  try {
    const status = await teacherApi.aiAvailability()
    // The server's reason is a configuration detail; the translated unavailable text is shown instead (FRAI09).
    available.value = status.available
  } catch (reason) {
    available.value = false
    availabilityMessage.value = resolveApiError(reason, t('teacherAiDraft.unavailable'))
  } finally {
    checkingAvailability.value = false
  }
}

async function generate(): Promise<void> {
  if (controlsDisabled.value) return
  errorMessage.value = null
  resultMessage.value = null

  const cleanTopic = topic.value.trim()
  if (!cleanTopic) {
    errorMessage.value = t('teacherAiDraft.topicRequired')
    return
  }

  generating.value = true
  try {
    const draft = await teacherApi.aiQuizDraft({
      topic: cleanTopic,
      level: level.value,
      numberOfQuestions: Math.min(Math.max(Math.round(questionCount.value || 3), 1), 10),
      languagePreference: ui.locale,
    })
    emit('quiz-draft', draft)
    resultMessage.value = t('teacherAiDraft.filledQuiz', { title: draft.title, count: draft.questions.length })
  } catch (reason) {
    errorMessage.value = resolveApiError(reason, t('teacherAiDraft.error'))
  } finally {
    generating.value = false
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
</style>
