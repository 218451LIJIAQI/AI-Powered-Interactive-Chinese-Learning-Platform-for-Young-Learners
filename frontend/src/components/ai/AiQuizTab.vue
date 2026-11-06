<template>
  <section class="panel ai-maker" :aria-labelledby="`${baseId}-title`">
    <h2 :id="`${baseId}-title`" class="card-title">{{ t('aiHelper.quizTitle') }}</h2>

    <form class="ai-maker-form has-count" @submit.prevent="make">
      <div class="field">
        <label class="field-label" :for="`${baseId}-topic`">{{ t('aiHelper.topic') }}</label>
        <input
          :id="`${baseId}-topic`"
          v-model="topic"
          class="input"
          autocomplete="off"
          maxlength="200"
          :disabled="Boolean(blocked) || making"
          :placeholder="t('aiHelper.quizTopicPlaceholder')"
        />
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-count`">{{ t('aiHelper.questions') }}</label>
        <select :id="`${baseId}-count`" v-model.number="count" class="select" :disabled="Boolean(blocked) || making">
          <option v-for="value in QUESTION_COUNTS" :key="value" :value="value">{{ value }}</option>
        </select>
      </div>
      <button
        class="btn btn-primary"
        type="submit"
        :disabled="Boolean(blocked) || making || !topic.trim()"
        :aria-busy="making"
      >
        {{ making ? t('aiHelper.making') : t('aiHelper.makeQuiz') }}
        <IconGlyph v-if="!making" name="chevronRight" :size="16" />
      </button>
    </form>

    <div class="ai-topics">
      <span class="ai-topics-label" aria-hidden="true">{{ t('aiHelper.tryATopic') }}</span>
      <FilterChips
        :model-value="topic"
        :options="topicOptions"
        :label="t('aiHelper.tryATopic')"
        size="sm"
        :disabled="Boolean(blocked) || making"
        @update:model-value="topic = $event"
      />
    </div>

    <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>

    <div v-if="making" class="ai-panel" role="status" aria-live="polite">
      <div class="ai-panel-source">
        <span class="page-eyebrow">{{ t('aiHelper.madeBySparkbot') }}</span>
        <AiResultLabel short />
      </div>
      <p class="ai-result-title">
        {{ t('aiHelper.quizMakingHint', { count: requested.count, topic: requested.topic }, requested.count) }}
      </p>
    </div>

    <article v-else-if="quiz" ref="resultEl" class="ai-panel" data-testid="ai-quiz-result">
      <header class="ai-panel-head">
        <div class="ai-panel-source">
          <span class="page-eyebrow">{{ t('aiHelper.madeBySparkbot') }}</span>
          <AiResultLabel short />
        </div>
        <button v-if="!reported" class="btn btn-ghost btn-sm ai-result-report" type="button" @click="report">
          <IconGlyph name="flag" :size="16" />
          {{ t('aiHelper.report') }}
        </button>
        <span v-else class="ai-reported ai-result-report"
          ><IconGlyph name="status" :size="14" />{{ t('aiHelper.reported') }}</span
        >
      </header>
      <h3 class="ai-result-title">{{ quiz.title }} · {{ t('aiHelper.questionCount', quiz.questions.length) }}</h3>
      <AiChoiceQuestions :questions="questions" />
    </article>

    <div v-else-if="!blocked" class="ai-panel">
      <StatePanel
        tone="empty"
        icon="quiz"
        :title="t('aiHelper.quizEmptyTitle')"
        :message="t('aiHelper.quizEmptyHint')"
      />
    </div>

    <AiNotice :text="aiNotice" guidance />
  </section>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { useUiStore } from '@/stores/ui'
import type { AiQuizGeneratorResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

import AiChoiceQuestions, { type ChoiceQuestion } from './AiChoiceQuestions.vue'
import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import { QUIZ_TOPIC_KEYS, revealResult, type AiBlockedReason, type AiReportTarget } from './aiHelper'

/**
 * Quick quiz: a topic, how many questions and topic ideas on top, the quiz below. SparkBot writes 1-5 multiple-choice
 * questions on any beginner topic; answers are checked in the browser and a wrong one can be tried again. Self-practice
 * only: nothing is saved or counted.
 */
const props = defineProps<{
  blocked: AiBlockedReason
  aiNotice: string
}>()
const emit = defineEmits<{ used: []; reported: [] }>()

const QUESTION_COUNTS = [1, 2, 3, 4, 5] as const
const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const ui = useUiStore()
const baseId = useId()

const topic = ref('')
const count = ref(3)
const requested = reactive({ topic: '', count: 3 })
const making = ref(false)
const error = ref<string | null>(null)
const quiz = ref<AiQuizGeneratorResponse | null>(null)
const reported = ref(false)
const reportTarget = ref<AiReportTarget | null>(null)
const resultEl = ref<HTMLElement | null>(null)

const topicOptions = computed(() => QUIZ_TOPIC_KEYS.map((key) => ({ value: t(key), label: t(key) })))
const questions = computed<ChoiceQuestion[]>(() =>
  (quiz.value?.questions ?? []).map((question) => ({
    text: question.questionText ?? '',
    options: question.options,
    answer: question.correctAnswer ?? '',
    note: question.explanation,
  })),
)

async function make(): Promise<void> {
  const cleanTopic = topic.value.trim()
  if (!cleanTopic || making.value || props.blocked) return
  error.value = null
  requested.topic = cleanTopic
  requested.count = count.value
  making.value = true
  try {
    const result = await aiHelperApi.quiz({
      topic: cleanTopic,
      numberOfQuestions: count.value,
      languagePreference: ui.locale,
    })
    if (result.status !== 'SUCCESS' || !result.questions.length) {
      error.value = result.status === 'UNAVAILABLE' ? t('aiHelper.sparkbotUnavailableShort') : t('aiHelper.quizError')
      return
    }
    quiz.value = result
    reported.value = false
    making.value = false
    await nextTick()
    revealResult(resultEl.value)
  } catch (reason) {
    error.value = resolveApiError(reason, t('aiHelper.quizError'))
  } finally {
    making.value = false
    emit('used')
  }
}

function report(): void {
  if (!quiz.value) return
  const lines = quiz.value.questions.map(
    (question, index) =>
      `${index + 1}. ${question.questionText} [${question.options.join(' / ')}] → ${question.correctAnswer}`,
  )
  reportTarget.value = {
    payload: {
      featureKey: 'QUIZ_GENERATOR',
      promptSnapshot: requested.topic,
      aiResponseSnapshot: [quiz.value.title, ...lines].join('\n'),
    },
    titleKey: 'aiHelper.reportQuizTitle',
    attachedKey: 'aiHelper.reportQuizAttached',
  }
}

function onReported(): void {
  reportTarget.value = null
  reported.value = true
  emit('reported')
}
</script>
