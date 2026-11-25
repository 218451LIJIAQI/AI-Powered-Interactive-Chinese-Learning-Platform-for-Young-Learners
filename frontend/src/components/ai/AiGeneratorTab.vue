<template>
  <section class="panel ai-maker" :aria-labelledby="`${baseId}-title`">
    <h2 :id="`${baseId}-title`" class="card-title">{{ t(isQuiz ? 'aiHelper.quickQuiz' : 'aiHelper.story') }}</h2>

    <form class="ai-maker-form" :class="{ 'has-count': isQuiz }" @submit.prevent="make">
      <div class="field">
        <label class="field-label" :for="`${baseId}-topic`">{{
          t(isQuiz ? 'aiHelper.topic' : 'aiHelper.storyTopic')
        }}</label>
        <input
          :id="`${baseId}-topic`"
          v-model="topic"
          class="input"
          autocomplete="off"
          maxlength="200"
          :disabled="locked"
          :placeholder="t(isQuiz ? 'aiHelper.quizTopicPlaceholder' : 'aiHelper.storyTopicPlaceholder')"
        />
      </div>
      <div v-if="isQuiz" class="field">
        <label class="field-label" :for="`${baseId}-count`">{{ t('aiHelper.questions') }}</label>
        <select :id="`${baseId}-count`" v-model.number="count" class="select" :disabled="locked">
          <option v-for="value in QUESTION_COUNTS" :key="value" :value="value">{{ value }}</option>
        </select>
      </div>
      <button class="btn btn-primary" type="submit" :disabled="locked || !topic.trim()" :aria-busy="making">
        {{ making ? t('aiHelper.making') : t(isQuiz ? 'aiHelper.makeQuiz' : 'aiHelper.makeStory') }}
        <IconGlyph v-if="!making" name="chevronRight" :size="16" />
      </button>
    </form>

    <div class="ai-topics">
      <span class="ai-topics-label" aria-hidden="true">{{ t('aiHelper.tryATopic') }}</span>
      <FilterChips
        v-model="topic"
        :options="topicOptions"
        :label="t('aiHelper.tryATopic')"
        size="sm"
        :disabled="locked"
      />
    </div>

    <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>

    <div
      v-if="making || generation || !blocked"
      ref="resultEl"
      class="ai-panel"
      :data-testid="generation && !making && isQuiz ? 'ai-quiz-result' : undefined"
    >
      <header v-if="making || generation" class="ai-panel-head">
        <div class="ai-panel-source">
          <span class="page-eyebrow">{{ t('aiHelper.madeBySparkbot') }}</span>
          <AiResultLabel />
        </div>
        <div class="ai-panel-aside">
          <AiNotice :text="aiNotice" />
          <template v-if="generation && !making">
            <button v-if="!reported" class="btn btn-ghost btn-sm ai-result-report" type="button" @click="report">
              <IconGlyph name="flag" :size="16" />
              {{ t('aiSafety.report') }}
            </button>
            <span v-else class="ai-reported ai-result-report"
              ><IconGlyph name="status" :size="14" />{{ t('aiSafety.reported') }}</span
            >
          </template>
        </div>
      </header>
      <p v-if="making" class="ai-result-title" role="status" aria-live="polite">
        {{
          isQuiz
            ? t('aiHelper.quizMakingHint', { count, topic: topic.trim() }, count)
            : t('aiHelper.storyMakingHint', { topic: topic.trim() })
        }}
      </p>
      <div v-if="generation" v-show="!making">
        <AiChoiceQuestions v-if="isQuiz" :questions="questions" :paused="making" hints />
        <AiStoryResult
          v-else-if="story"
          ref="storyResultRef"
          :story="story"
          :topic="generation.topic"
          :questions="questions"
        />
      </div>
      <template v-if="!making && !generation">
        <StatePanel
          tone="empty"
          :icon="isQuiz ? 'quiz' : 'story'"
          :title="t(isQuiz ? 'aiHelper.quizEmptyTitle' : 'aiHelper.storyEmptyTitle')"
          :message="t(isQuiz ? 'aiHelper.quizEmptyHint' : 'aiHelper.storyEmptyHint')"
        />
        <AiNotice :text="aiNotice" />
      </template>
    </div>
    <AiNotice v-else :text="aiNotice" />

    <AiReportDialog
      v-if="reportTarget"
      :payload="reportTarget.payload"
      :title="t(reportTarget.titleKey)"
      :attached="t(reportTarget.attachedKey)"
      @close="reportTarget = null"
      @sent="onReported"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiGeneratorMessages } from '@/plugins/aiGeneratorMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { appLocale } from '@/plugins/i18n'
import type { AiStoryGeneratorResponse } from '@/types/ai'
import type { AiQuizGeneratorResponse } from '@/types/api'
import { storyVocabularyRows } from '@/utils/aiPracticeQuiz'
import { resolveApiError } from '@/utils/apiError'
import { serverMessageOr } from '@/utils/serverMessages'

import AiChoiceQuestions, { type ChoiceQuestion } from './AiChoiceQuestions.vue'
import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import AiStoryResult from './AiStoryResult.vue'
import { revealResult, type AiBlockedReason, type AiReportTarget } from './aiHelper'
import '@/styles/ai-generator.css'

/** A topic generates a quiz or a story for self-practice. Answers are checked in the browser. */
const props = defineProps<{
  kind: 'quiz' | 'story'
  blocked: AiBlockedReason
  aiNotice: string
}>()
const emit = defineEmits<{ used: []; reported: [] }>()

const QUESTION_COUNTS = [1, 2, 3, 4, 5] as const
const QUIZ_TOPICS = [
  'aiHelper.topicNumbers',
  'aiHelper.topicFood',
  'aiHelper.topicDirections',
  'aiHelper.topicTime',
  'aiHelper.topicFamily',
  'aiHelper.topicShopping',
  'aiHelper.topicWeather',
  'aiHelper.topicHobbies',
  'aiHelper.topicTransport',
  'aiHelper.topicCampus',
] as const
const STORY_TOPICS = [
  'aiHelper.topicLrt',
  'aiHelper.topicMyFamily',
  'aiHelper.topicRainyDay',
  'aiHelper.topicPasarMalam',
  'aiHelper.topicWeekend',
  'aiHelper.topicFirstDay',
  'aiHelper.topicLostPhone',
  'aiHelper.topicBirthday',
  'aiHelper.topicClinic',
  'aiHelper.topicHariRaya',
] as const

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiGeneratorMessages] })
const baseId = useId()
const isQuiz = computed(() => props.kind === 'quiz')
const errorKey = computed(() => (isQuiz.value ? 'aiHelper.quizError' : 'aiHelper.storyError'))

const topic = ref('')
const count = ref(3)
const making = ref(false)
const locked = computed(() => Boolean(props.blocked) || making.value)
const error = ref<string | null>(null)
const generation = shallowRef<{ topic: string; result: AiQuizGeneratorResponse | AiStoryGeneratorResponse } | null>(
  null,
)
const reported = ref(false)
const reportTarget = ref<AiReportTarget | null>(null)
const resultEl = ref<HTMLElement | null>(null)
const storyResultRef = ref<InstanceType<typeof AiStoryResult> | null>(null)
const isUnmounted = useIsUnmounted()

const topicOptions = computed(() =>
  (isQuiz.value ? QUIZ_TOPICS : STORY_TOPICS).map((key) => {
    const label = t(key)
    return { value: label, label }
  }),
)
const story = computed(() => {
  const result = generation.value?.result
  return result && 'lines' in result ? result : null
})
const questions = computed<ChoiceQuestion[]>(() => {
  const result = generation.value?.result
  if (!result) return []
  if ('questions' in result) {
    return result.questions.map((question) => ({
      text: question.questionText,
      options: question.options,
      answer: question.correctAnswer,
      note: question.explanation,
    }))
  }
  return result.comprehensionQuestions.map((question) => ({
    text: question.question,
    options: question.options,
    answer: question.answer,
  }))
})

async function make(): Promise<void> {
  const cleanTopic = topic.value.trim()
  if (!cleanTopic || locked.value) return
  const sessionVersion = getAuthSessionVersion()
  const stillActive = () => !isUnmounted() && sessionVersion === getAuthSessionVersion()
  storyResultRef.value?.stopReading()
  error.value = null
  making.value = true
  try {
    const request = { topic: cleanTopic, languagePreference: appLocale.value }
    const result = isQuiz.value
      ? await aiHelperApi.quiz({ ...request, numberOfQuestions: count.value })
      : await aiHelperApi.story(request)
    if (!stillActive()) return
    if (result.status !== 'SUCCESS' || !('questions' in result ? result.questions.length : result.lines.length)) {
      const fallback = t(result.status === 'UNAVAILABLE' ? 'aiHelper.sparkbotUnavailableShort' : errorKey.value)
      error.value = serverMessageOr(result.message, fallback)
      return
    }
    generation.value = { topic: cleanTopic, result }
    reported.value = false
  } catch (reason) {
    if (stillActive()) error.value = resolveApiError(reason, t(errorKey.value))
    return
  } finally {
    if (!isUnmounted()) making.value = false
    if (stillActive()) emit('used')
  }
  await nextTick()
  if (stillActive()) revealResult(resultEl.value)
}

function report(): void {
  if (!generation.value) return
  const { result, topic: resultTopic } = generation.value
  const storyResult = story.value
  const titleLine = storyResult?.titleTranslation
    ? [result.title, storyResult.titleTranslation].filter(Boolean).join(' · ')
    : (result.title ?? '')
  const storyLines =
    storyResult?.lines.map((line) => [line.chinese, line.pinyin, line.translation].filter(Boolean).join(' | ')) ?? []
  const vocabularyLines = storyResult
    ? storyVocabularyRows(storyResult, appLocale.value).map((word) =>
        [word.chineseWord, word.pinyin, word.meaning].filter(Boolean).join(' — '),
      )
    : []
  const questionLines = questions.value.map((question, index) => {
    const base = `${index + 1}. ${question.text} [${question.options.join(' / ')}] → ${question.answer}`
    return question.note ? `${base} (${question.note})` : base
  })
  reportTarget.value = {
    payload: {
      featureKey: isQuiz.value ? 'QUIZ_GENERATOR' : 'STORY_GENERATOR',
      promptSnapshot: resultTopic,
      aiResponseSnapshot: [titleLine, ...storyLines, ...vocabularyLines, ...questionLines].filter(Boolean).join('\n'),
    },
    titleKey: isQuiz.value ? 'aiHelper.reportQuizTitle' : 'aiHelper.reportStoryTitle',
    attachedKey: isQuiz.value ? 'aiHelper.reportQuizAttached' : 'aiHelper.reportStoryAttached',
  }
}

function onReported(): void {
  reportTarget.value = null
  reported.value = true
  emit('reported')
}
</script>
