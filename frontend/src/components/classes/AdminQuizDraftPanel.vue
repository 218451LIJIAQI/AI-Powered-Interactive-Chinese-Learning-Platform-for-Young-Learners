<template>
  <section class="panel authoring-card" :aria-labelledby="`${baseId}-title`">
    <div class="authoring-card-head">
      <h2 :id="`${baseId}-title`" class="card-title">{{ t('lessonEditor.aiDraft.title') }}</h2>
      <p class="type-caption">{{ t('lessonEditor.aiDraft.subtitle') }}</p>
    </div>

    <p v-if="availability.loading.value" class="field-hint" role="status">{{ t('lessonEditor.aiDraft.checking') }}</p>
    <DataNotice v-else-if="availability.error.value" :error="availability.error.value" @retry="retryAvailability" />
    <p v-else-if="!available" class="callout" role="status">
      <IconGlyph name="info" :size="18" /><span>{{ t('lessonEditor.aiDraft.unavailable') }}</span>
    </p>

    <form class="form-grid" novalidate @submit.prevent="generate">
      <div class="field">
        <label class="field-label" :for="`${baseId}-topic`">
          {{ t('lessonEditor.aiDraft.topic') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${baseId}-topic`"
          ref="topicField"
          v-model="topic"
          class="input"
          required
          maxlength="200"
          :placeholder="t('lessonEditor.aiDraft.topicPlaceholder')"
          :disabled="controlsDisabled"
          :aria-invalid="Boolean(topicError)"
          :aria-describedby="topicError ? `${baseId}-topic-error` : undefined"
          data-testid="quiz-draft-topic"
          @blur="validateTopic"
        />
        <p v-if="topicError" :id="`${baseId}-topic-error`" class="field-error" role="alert">{{ topicError }}</p>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-count`">{{ t('lessonEditor.aiDraft.questionCount') }}</label>
        <input
          :id="`${baseId}-count`"
          v-model.number="questionCount"
          class="input"
          type="number"
          min="1"
          :max="Math.max(1, remaining)"
          :disabled="controlsDisabled"
          :aria-describedby="countHintId"
          data-testid="quiz-draft-count"
        />
        <p :id="countHintId" class="field-hint" data-testid="quiz-draft-count-hint">
          {{
            remaining > 0
              ? t('lessonEditor.aiDraft.upTo', { count: remaining, total: QUIZ_SIZE })
              : t('lessonEditor.aiDraft.full', { total: QUIZ_SIZE })
          }}
        </p>
      </div>
      <!-- Once the AI is known to be off, the note above says so instead. -->
      <p v-if="errorMessage && available" class="field-error" role="alert">{{ errorMessage }}</p>
      <button
        class="btn btn-secondary btn-block"
        type="submit"
        :disabled="controlsDisabled || Boolean(topicError)"
        :aria-busy="generating"
        data-testid="quiz-draft-generate"
        @mousedown.prevent
      >
        <IconGlyph name="ai" :size="16" />
        {{ generating ? t('lessonEditor.aiDraft.generating') : t('lessonEditor.aiDraft.generate') }}
      </button>
    </form>

    <p class="lesson-footer-hint"><IconGlyph name="info" :size="16" />{{ t('lessonEditor.aiDraft.note') }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { adminQuizDraftApi } from '@/api/adminClasses'
import { useLessonPanelGuard } from '@/components/classes/useLessonPanelGuard'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import { appLocale } from '@/plugins/i18n'
import type { AiQuizGeneratorResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { QUIZ_SIZE } from '@/utils/lessonEditor'
import { serverMessageOr } from '@/utils/serverMessages'

/**
 * AI-assisted quiz draft of the lesson editor (A3.25–A3.28): SparkBot drafts up to the questions the quiz still needs
 * (`remaining`, a lesson quiz has exactly 5); the parent adds them for the admin to check. Nothing is saved here.
 */
const props = defineProps<{
  /** The lesson whose quiz the draft is for. */
  lessonId: number
  /** How many questions the quiz still needs; 0 when it is full. */
  remaining: number
  disabled?: boolean
  /** The id of the note under "Number of questions", which also says when the quiz is full. */
  countHintId: string
}>()
const emit = defineEmits<{ 'quiz-draft': [draft: AiQuizGeneratorResponse]; generating: [generating: boolean] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages })
const baseId = useId()

/** The classes are written for beginners, so the draft's prompt names this level. */
const DRAFT_LEVEL = 'HSK 1'

const topic = ref('')
const topicField = ref<HTMLInputElement | null>(null)
const questionCount = ref(Math.max(1, props.remaining))
const generating = ref(false)
const errorMessage = ref<string | null>(null)
watch([topic, questionCount], () => {
  errorMessage.value = null
})

// The count follows the room left: all of it by default, never more.
watch(
  () => props.remaining,
  (remaining, previous) => {
    if (remaining > 0 && (questionCount.value > remaining || previous === 0)) questionCount.value = remaining
  },
)

const {
  error: topicError,
  validate: validateTopic,
  reset: resetTopicError,
} = useFieldCheck(topic, (value) => (value.trim() ? null : t('lessonEditor.aiDraft.topicMissing')))

const guard = useLessonPanelGuard({
  lessonId: () => props.lessonId,
  saving: generating,
  onSaving: (busy) => emit('generating', busy),
  onReset: () => {
    topic.value = ''
    questionCount.value = Math.max(1, props.remaining)
    resetTopicError()
    errorMessage.value = null
    generating.value = false
  },
})

const availability = useResource(adminQuizDraftApi.availability)
const available = computed(() => availability.data.value?.available === true)
const controlsDisabled = computed(() => props.disabled || generating.value || !available.value || props.remaining <= 0)

function retryAvailability(): void {
  errorMessage.value = null
  void availability.reload()
}

async function generate(): Promise<void> {
  if (controlsDisabled.value) return
  if (!validateTopic()) {
    topicField.value?.focus()
    return
  }
  const parsedCount = Number(questionCount.value)
  const requestedCount = Number.isFinite(parsedCount) ? Math.round(parsedCount) : 1
  const clampedCount = Math.min(Math.max(requestedCount, 1), props.remaining)
  questionCount.value = clampedCount
  errorMessage.value = null
  generating.value = true
  await guard.run(
    async (isCurrent) => {
      const draft = await adminQuizDraftApi.draft({
        lessonId: props.lessonId,
        topic: topic.value.trim(),
        level: DRAFT_LEVEL,
        numberOfQuestions: clampedCount,
        languagePreference: appLocale.value,
      })
      if (!isCurrent()) return
      // A failed or unavailable draft comes back as a normal response with no questions. Unavailable means the AI went
      // off since the panel checked: it checks again, so the panel says so and stops offering Generate.
      if (draft.status === 'SUCCESS' && draft.questions.length > 0) emit('quiz-draft', draft)
      else if (draft.status === 'UNAVAILABLE') {
        const unavailableNotice = serverMessageOr(draft.message, t('lessonEditor.aiDraft.unavailable'))
        availability.data.value = null
        await availability.reload()
        if (!isCurrent()) return
        errorMessage.value = available.value ? unavailableNotice : null
      } else errorMessage.value = serverMessageOr(draft.message, t('lessonEditor.aiDraft.error'))
    },
    {
      onError: (reason) => {
        errorMessage.value = resolveApiError(reason, t('lessonEditor.aiDraft.error'))
      },
      onFinally: () => {
        generating.value = false
      },
    },
  )
}
</script>
