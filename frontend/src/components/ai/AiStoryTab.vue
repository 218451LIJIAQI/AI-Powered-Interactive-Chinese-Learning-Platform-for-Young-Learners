<template>
  <section class="ai-stage" :aria-labelledby="`${baseId}-title`">
    <aside class="ai-side">
      <header class="ai-side-head">
        <h2 :id="`${baseId}-title`" class="ai-side-title">{{ t('aiHelper.storyTitle') }}</h2>
        <p class="ai-side-lede">{{ t('aiHelper.storySubtitle') }}</p>
      </header>

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
          {{ t('aiHelper.makeStory') }}
          <IconGlyph v-if="!making" name="chevronRight" :size="16" />
        </button>
      </form>

      <div class="ai-topics" role="group" :aria-labelledby="`${baseId}-topics`">
        <p :id="`${baseId}-topics`" class="ai-overline">{{ t('aiHelper.tryATopic') }}</p>
        <div class="ai-topic-list">
          <button
            v-for="key in STORY_TOPIC_KEYS"
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
        :message="t('aiHelper.storyMakingHint', { topic: requestedTopic })"
      />

      <article v-else-if="story" ref="resultEl" class="ai-sheet ai-result" data-testid="ai-story-result">
        <header class="ai-result-head">
          <div class="ai-result-heading">
            <p class="ai-overline">{{ t('aiHelper.madeBySparkbot') }}</p>
            <h3 class="ai-story-title hanzi" lang="zh-CN">{{ story.title }}</h3>
            <p class="ai-result-meta">
              <template v-if="story.titleTranslation">{{ story.titleTranslation }} · </template
              >{{ t('aiHelper.storyMeta', { topic: requestedTopic }) }}
            </p>
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

        <section class="ai-story-text" :aria-labelledby="`${baseId}-story`">
          <div class="ai-story-bar">
            <h4 :id="`${baseId}-story`" class="sr-only">{{ t('aiHelper.storyLabel') }}</h4>
            <div class="ai-toggle-group" role="group" :aria-label="t('aiHelper.show')">
              <button class="ai-toggle" type="button" :aria-pressed="showPinyin" @click="showPinyin = !showPinyin">
                {{ t('aiHelper.pinyin') }}
              </button>
              <button
                class="ai-toggle"
                type="button"
                :aria-pressed="showTranslation"
                @click="showTranslation = !showTranslation"
              >
                {{ t('aiHelper.translation') }}
              </button>
            </div>
            <button v-if="speech.canSpeak" class="btn btn-secondary btn-sm" type="button" @click="toggleReading">
              <IconGlyph :name="readingIndex === null ? 'audio' : 'stop'" :size="16" />
              {{ readingIndex === null ? t('aiHelper.readAloud') : t('aiHelper.stopReading') }}
            </button>
          </div>
          <ol class="ai-story-lines">
            <li v-for="(line, index) in story.lines" :key="index" :class="{ 'is-reading': readingIndex === index }">
              <p class="ai-line-zh cjk-text" lang="zh-CN">{{ line.chinese }}</p>
              <p v-if="showPinyin && line.pinyin" class="ai-line-py">{{ line.pinyin }}</p>
              <p v-if="showTranslation && line.translation" class="ai-line-meaning">{{ line.translation }}</p>
            </li>
          </ol>
        </section>

        <div class="ai-story-extra">
          <section v-if="story.vocabularyList.length" class="ai-story-words" :aria-labelledby="`${baseId}-words`">
            <h4 :id="`${baseId}-words`" class="ai-recap-label">{{ t('aiHelper.keyWords') }}</h4>
            <ul class="ai-key-words">
              <li v-for="word in words" :key="word.chineseWord">
                <button
                  class="ai-key-word"
                  type="button"
                  :disabled="!speech.canSpeak"
                  @click="speech.speak(word.chineseWord)"
                >
                  <span class="hanzi" lang="zh-CN">{{ word.chineseWord }}</span>
                  <span class="ai-key-word-gloss">
                    <span class="ai-line-py">{{ word.pinyin }}</span>
                    <span>{{ word.meaning }}</span>
                  </span>
                  <IconGlyph v-if="speech.canSpeak" name="audio" :size="16" />
                </button>
              </li>
            </ul>
          </section>

          <section
            v-if="story.comprehensionQuestions.length"
            class="ai-story-check"
            :aria-labelledby="`${baseId}-check`"
          >
            <h4 :id="`${baseId}-check`" class="ai-recap-label">{{ t('aiHelper.checkUnderstanding') }}</h4>
            <AiChoiceQuestions :questions="questions" />
          </section>
        </div>

        <div class="ai-result-notes">
          <p class="ai-footnote">{{ t('aiHelper.selfPracticeOnly') }}</p>
          <AiNotice :text="aiNotice" />
        </div>
      </article>

      <StatePanel
        v-else
        tone="empty"
        icon="story"
        :title="t('aiHelper.storyEmptyTitle')"
        :message="t('aiHelper.storyEmptyHint')"
      />
    </div>
  </section>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
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
 * Story: the maker on the left, a reading sheet on the right. A short beginner story on any topic, line by line with
 * pinyin and the translation to show when wanted, read aloud with the current line marked, its key words to hear and
 * two questions checked in the browser. Self-practice only: nothing is saved.
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
const notice = ref<string | null>(null)
const story = ref<AiStoryGeneratorResponse | null>(null)
const showPinyin = ref(true)
const showTranslation = ref(false)
const reported = ref(false)
const reportTarget = ref<AiReportTarget | null>(null)
const readingIndex = ref<number | null>(null)
const resultEl = ref<HTMLElement | null>(null)

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
  notice.value = null
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
  notice.value = t('aiHelper.reportSentNotice')
  emit('reported')
}
</script>
