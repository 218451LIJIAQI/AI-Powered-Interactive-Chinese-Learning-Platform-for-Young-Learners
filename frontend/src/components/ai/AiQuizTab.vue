<template>
  <section class="ai-stage" :aria-labelledby="`${baseId}-title`">
    <aside class="ai-side">
      <header class="ai-side-head">
        <h2 :id="`${baseId}-title`" class="ai-side-title">{{ t('aiHelper.quizTitle') }}</h2>
        <p class="ai-side-lede">{{ t('aiHelper.quizSubtitle') }}</p>
      </header>

      <form class="ai-maker-form" @submit.prevent="make">
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
          {{ t('aiHelper.makeQuiz') }}
          <IconGlyph v-if="!making" name="chevronRight" :size="16" />
        </button>
      </form>

      <div class="ai-topics" role="group" :aria-labelledby="`${baseId}-topics`">
        <p :id="`${baseId}-topics`" class="ai-overline">{{ t('aiHelper.tryATopic') }}</p>
        <div class="ai-topic-list">
          <button
            v-for="key in QUIZ_TOPIC_KEYS"
            :key="key"
            class="ai-help-chip"
            type="button"
            :aria-pressed="topic === t(key)"
            :disabled="making"
            @click="topic = t(key)"
          >
            {{ t(key) }}
          </button>
        </div>
      </div>
    </aside>

    <div class="ai-output">
      <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>
      <p v-if="notice" class="callout is-compact" role="status">{{ notice }}</p>

      <StatePanel
        v-if="making"
        tone="loading"
        :title="t('aiHelper.making')"
        :message="t('aiHelper.quizMakingHint', { count: requested.count, topic: requested.topic }, requested.count)"
      />

      <article v-else-if="quiz" ref="resultEl" class="ai-sheet ai-result" data-testid="ai-quiz-result">
        <header class="ai-result-head">
          <div class="ai-result-heading">
            <p class="ai-overline">{{ t('aiHelper.madeBySparkbot') }}</p>
            <h3 class="ai-result-title">{{ quiz.title }}</h3>
            <p class="ai-result-meta">{{ t('aiHelper.questionCount', quiz.questions.length) }}</p>
          </div>
          <div class="ai-result-tools">
            <AiResultLabel />
            <button v-if="!reported" class="ai-line-action ai-result-report" type="button" @click="report">
              <IconGlyph name="flag" :size="14" />
              {{ t('aiHelper.report') }}
            </button>
            <span v-else class="ai-reported ai-result-report"
              ><IconGlyph name="status" :size="14" />{{ t('aiHelper.reported') }}</span
            >
          </div>
        </header>

        <AiChoiceQuestions :questions="questions" />
        <div class="ai-result-notes">
          <p class="ai-footnote">{{ t('aiHelper.selfPracticeOnly') }}</p>
          <AiNotice :text="aiNotice" />
        </div>
      </article>

      <StatePanel
        v-else
        tone="empty"
        icon="quiz"
        :title="t('aiHelper.quizEmptyTitle')"
        :message="t('aiHelper.quizEmptyHint')"
      />
    </div>
  </section>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
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
 * Quick quiz: the maker on the left (a topic, how many questions, topic ideas), the quiz sheet on the right. SparkBot
 * writes 1-5 multiple-choice questions on any beginner topic; answers are checked in the browser and a wrong one can
 * be tried again. Self-practice only: nothing is saved or counted.
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
const notice = ref<string | null>(null)
const quiz = ref<AiQuizGeneratorResponse | null>(null)
const reported = ref(false)
const reportTarget = ref<AiReportTarget | null>(null)
const resultEl = ref<HTMLElement | null>(null)

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
  notice.value = null
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
  notice.value = t('aiHelper.reportSentNotice')
  emit('reported')
}
</script>
