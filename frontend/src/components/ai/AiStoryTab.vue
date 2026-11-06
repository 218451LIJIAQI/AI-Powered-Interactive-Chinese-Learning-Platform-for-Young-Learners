<template>
  <section class="panel ai-maker" :aria-labelledby="`${baseId}-title`">
    <h2 :id="`${baseId}-title`" class="card-title">{{ t('aiHelper.storyTitle') }}</h2>

    <form class="ai-maker-form" @submit.prevent="make">
      <div class="field">
        <label class="field-label" :for="`${baseId}-topic`">{{ t('aiHelper.storyTopic') }}</label>
        <input
          :id="`${baseId}-topic`"
          v-model="topic"
          class="input"
          autocomplete="off"
          maxlength="200"
          :disabled="Boolean(blocked) || making"
          :placeholder="t('aiHelper.storyTopicPlaceholder')"
        />
      </div>
      <button
        class="btn btn-primary"
        type="submit"
        :disabled="Boolean(blocked) || making || !topic.trim()"
        :aria-busy="making"
      >
        {{ making ? t('aiHelper.making') : t('aiHelper.makeStory') }}
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
      <p class="ai-result-title">{{ t('aiHelper.storyMakingHint', { topic: requestedTopic }) }}</p>
    </div>

    <article v-else-if="story" ref="resultEl" class="ai-panel" data-testid="ai-story-result">
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
      <h3 class="ai-story-title">
        <span lang="zh-CN">{{ story.title }}</span>
        <span class="ai-story-title-gloss"> · {{ story.titleTranslation || requestedTopic }}</span>
      </h3>

      <div class="ai-story-columns">
        <section class="ai-story-text" :aria-labelledby="`${baseId}-story`">
          <div class="ai-story-bar">
            <h4 :id="`${baseId}-story`" class="page-eyebrow">{{ t('aiHelper.storyLabel') }}</h4>
            <div class="ai-story-show" role="group" :aria-label="t('aiHelper.show')">
              <span aria-hidden="true">{{ t('aiHelper.show') }}</span>
              <label class="choice"><input v-model="showPinyin" type="checkbox" />{{ t('aiHelper.pinyin') }}</label>
              <label class="choice">
                <input v-model="showTranslation" type="checkbox" />{{ t('aiHelper.translation') }}
              </label>
            </div>
          </div>
          <ol class="ai-story-lines">
            <li v-for="(line, index) in story.lines" :key="index" :class="{ 'is-reading': readingIndex === index }">
              <p class="ai-story-line" lang="zh-CN">{{ line.chinese }}</p>
              <p v-if="showPinyin && line.pinyin" class="ai-line-pinyin">{{ line.pinyin }}</p>
              <p v-if="showTranslation && line.translation" class="ai-line-pinyin">{{ line.translation }}</p>
            </li>
          </ol>
          <button
            v-if="speech.canSpeak"
            class="btn btn-ghost btn-sm ai-story-read"
            type="button"
            @click="toggleReading"
          >
            <IconGlyph :name="readingIndex === null ? 'audio' : 'stop'" :size="16" />
            {{ readingIndex === null ? t('aiHelper.readAloud') : t('aiHelper.stopReading') }}
          </button>
        </section>

        <section v-if="words.length" class="ai-story-words" :aria-labelledby="`${baseId}-words`">
          <h4 :id="`${baseId}-words`" class="page-eyebrow">{{ t('aiHelper.keyWords') }}</h4>
          <ul class="row-list ai-key-words">
            <li v-for="word in words" :key="word.chineseWord">
              <button
                class="ai-key-word"
                type="button"
                :disabled="!speech.canSpeak"
                :aria-label="`${word.chineseWord} ${word.pinyin} ${word.meaning}`"
                @click="speech.speak(word.chineseWord)"
              >
                <strong lang="zh-CN">{{ word.chineseWord }}</strong>
                <span class="ai-key-word-pinyin">{{ word.pinyin }}</span>
                <span>{{ word.meaning }}</span>
                <IconGlyph v-if="speech.canSpeak" name="audio" :size="16" />
              </button>
            </li>
          </ul>
        </section>
      </div>

      <section v-if="story.comprehensionQuestions.length" class="ai-story-check" :aria-labelledby="`${baseId}-check`">
        <h4 :id="`${baseId}-check`" class="page-eyebrow">{{ t('aiHelper.checkUnderstanding') }}</h4>
        <AiChoiceQuestions :questions="questions" :columns="2" />
      </section>
    </article>

    <div v-else-if="!blocked" class="ai-panel">
      <StatePanel
        tone="empty"
        icon="story"
        :title="t('aiHelper.storyEmptyTitle')"
        :message="t('aiHelper.storyEmptyHint')"
      />
    </div>

    <AiNotice :text="aiNotice" guidance />
  </section>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { useUiStore } from '@/stores/ui'
import type { AiStoryGeneratorResponse } from '@/types/api'
import { storyVocabularyRows } from '@/utils/aiPracticeQuiz'
import { resolveApiError } from '@/utils/apiError'

import AiChoiceQuestions, { type ChoiceQuestion } from './AiChoiceQuestions.vue'
import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import { revealResult, STORY_TOPIC_KEYS, type AiBlockedReason, type AiReportTarget } from './aiHelper'

/**
 * Story: a topic on top, the reading panel below. A short beginner story on any topic, line by line with pinyin and the
 * translation to show when wanted, read aloud with the current line marked, its key words to hear and two questions
 * checked in the browser. Self-practice only: nothing is saved.
 */
const props = defineProps<{
  blocked: AiBlockedReason
  aiNotice: string
}>()
const emit = defineEmits<{ used: []; reported: [] }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const ui = useUiStore()
const speech = useMandarinSpeech()
const baseId = useId()

const topic = ref('')
const requestedTopic = ref('')
const making = ref(false)
const error = ref<string | null>(null)
const story = ref<AiStoryGeneratorResponse | null>(null)
const showPinyin = ref(true)
const showTranslation = ref(false)
const reported = ref(false)
const reportTarget = ref<AiReportTarget | null>(null)
const readingIndex = ref<number | null>(null)
const resultEl = ref<HTMLElement | null>(null)

const topicOptions = computed(() => STORY_TOPIC_KEYS.map((key) => ({ value: t(key), label: t(key) })))
const words = computed(() => storyVocabularyRows(story.value, ui.locale))
const questions = computed<ChoiceQuestion[]>(() =>
  (story.value?.comprehensionQuestions ?? []).map((question) => ({
    text: question.question ?? '',
    options: question.options,
    answer: question.answer ?? '',
  })),
)

async function make(): Promise<void> {
  const cleanTopic = topic.value.trim()
  if (!cleanTopic || making.value || props.blocked) return
  stopReading()
  error.value = null
  requestedTopic.value = cleanTopic
  making.value = true
  try {
    const result = await aiHelperApi.story({ topic: cleanTopic, languagePreference: ui.locale })
    if (result.status !== 'SUCCESS' || !result.lines.length) {
      error.value = result.status === 'UNAVAILABLE' ? t('aiHelper.sparkbotUnavailableShort') : t('aiHelper.storyError')
      return
    }
    story.value = result
    reported.value = false
    making.value = false
    await nextTick()
    revealResult(resultEl.value)
  } catch (reason) {
    error.value = resolveApiError(reason, t('aiHelper.storyError'))
  } finally {
    making.value = false
    emit('used')
  }
}

/** Reads the story line by line, marking the line being read. */
function readFrom(index: number): void {
  const line = story.value?.lines[index]
  if (!line) {
    readingIndex.value = null
    return
  }
  readingIndex.value = index
  speech.speak(line.chinese)
}

function toggleReading(): void {
  if (readingIndex.value === null) readFrom(0)
  else stopReading()
}

function stopReading(): void {
  readingIndex.value = null
  speech.stopSpeaking()
}

watch(
  () => speech.speaking.value,
  (speaking) => {
    if (!speaking && readingIndex.value !== null) readFrom(readingIndex.value + 1)
  },
)

function report(): void {
  if (!story.value) return
  const value = story.value
  const questions = value.comprehensionQuestions.map(
    (question, index) => `${index + 1}. ${question.question} [${question.options.join(' / ')}] → ${question.answer}`,
  )
  reportTarget.value = {
    payload: {
      featureKey: 'STORY_GENERATOR',
      promptSnapshot: requestedTopic.value,
      aiResponseSnapshot: [value.title, ...value.lines.map((line) => line.chinese), ...questions].join('\n'),
    },
    titleKey: 'aiHelper.reportStoryTitle',
    attachedKey: 'aiHelper.reportStoryAttached',
  }
}

function onReported(): void {
  reportTarget.value = null
  reported.value = true
  emit('reported')
}
</script>
