<template>
  <div class="teacher-card-head">
    <h2 :id="headingId" class="card-title">{{ t('teacherAiDraft.title') }}</h2>
    <p class="type-caption">{{ t('teacherAiDraft.hint') }}</p>
  </div>

  <p v-if="checkingAvailability" class="field-hint">{{ t('teacherAiDraft.checking') }}</p>
  <p v-else-if="!available" class="callout" role="status">
    <IconGlyph name="info" :size="18" /><span>{{ unavailableMessage }}</span>
  </p>

  <form class="form-grid" @submit.prevent="generate">
    <div class="field">
      <label class="field-label" :for="`${baseId}-topic`">{{ t('teacherAiDraft.topic') }}</label>
      <input
        :id="`${baseId}-topic`"
        v-model="topic"
        class="input"
        maxlength="200"
        :placeholder="t('teacherAiDraft.topicPlaceholder')"
        :disabled="controlsDisabled"
        data-testid="teacher-ai-draft-topic"
      />
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-count`">{{ t('teacherAiDraft.questionCount') }}</label>
      <input
        :id="`${baseId}-count`"
        v-model.number="questionCount"
        class="input"
        type="number"
        min="1"
        :max="MAX_QUESTIONS"
        :disabled="controlsDisabled"
        :aria-describedby="`${baseId}-count-hint`"
      />
      <p :id="`${baseId}-count-hint`" class="field-hint">{{ t('teacherAiDraft.questionCountHint') }}</p>
    </div>
    <p v-if="errorMessage" class="field-error" role="alert">{{ errorMessage }}</p>
    <button
      class="btn btn-secondary btn-block"
      type="submit"
      :disabled="controlsDisabled"
      :aria-busy="generating"
      data-testid="teacher-ai-draft-generate"
    >
      <IconGlyph name="ai" :size="16" />
      {{ generating ? t('teacherAiDraft.generating') : t('teacherAiDraft.generate') }}
    </button>
  </form>

  <AiDisclaimer />
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { teacherApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherAiDraftMessages } from '@/plugins/teacherAiDraftMessages'
import { useUiStore } from '@/stores/ui'
import type { AiQuizGeneratorResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

/**
 * AI-assisted quiz draft generator for approved teachers (FRAI05). The parent receives the draft through `quiz-draft`
 * and adds its questions for the teacher to check.
 */
const props = defineProps<{ disabled?: boolean; headingId?: string }>()
const emit = defineEmits<{ 'quiz-draft': [draft: AiQuizGeneratorResponse] }>()

const { t } = useLocalMessages({ teacherAiDraft: teacherAiDraftMessages })
const ui = useUiStore()
const baseId = useId()

const MAX_QUESTIONS = 5
/** The classes are written for beginners; the server still asks for a level. */
const DRAFT_LEVEL = 'HSK 1'

const topic = ref('')
const questionCount = ref(3)
const generating = ref(false)
const checkingAvailability = ref(false)
const available = ref(true)
const availabilityMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)

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

  const cleanTopic = topic.value.trim()
  if (!cleanTopic) {
    errorMessage.value = t('teacherAiDraft.topicRequired')
    return
  }

  generating.value = true
  try {
    const draft = await teacherApi.aiQuizDraft({
      topic: cleanTopic,
      level: DRAFT_LEVEL,
      numberOfQuestions: Math.min(Math.max(Math.round(questionCount.value || 3), 1), MAX_QUESTIONS),
      languagePreference: ui.locale,
    })
    emit('quiz-draft', draft)
  } catch (reason) {
    errorMessage.value = resolveApiError(reason, t('teacherAiDraft.error'))
  } finally {
    generating.value = false
  }
}
</script>
