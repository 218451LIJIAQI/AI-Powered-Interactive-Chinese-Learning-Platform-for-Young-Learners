<template>
  <h3 class="ai-story-title">
    <span v-if="story.title" lang="zh-CN">{{ story.title }}</span>
    <span v-if="titleGloss" class="ai-story-title-gloss">{{ story.title ? ` · ${titleGloss}` : titleGloss }}</span>
  </h3>

  <div class="ai-story-columns">
    <section class="ai-story-text" :aria-labelledby="`${baseId}-story`">
      <div class="ai-story-bar">
        <h4 :id="`${baseId}-story`" class="page-eyebrow">{{ t('aiHelper.storyText') }}</h4>
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
          <p v-if="showPinyin && line.pinyin" class="ai-line-pinyin" lang="zh-Latn">{{ line.pinyin }}</p>
          <p v-if="showTranslation && line.translation" class="ai-line-pinyin">{{ line.translation }}</p>
        </li>
      </ol>
      <button
        v-if="speech.canSpeak.value"
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
        <li v-for="(word, index) in words" :key="`${index}-${word.chineseWord}`">
          <button
            v-if="speech.canSpeak.value"
            class="ai-key-word"
            type="button"
            @click="readText(word.chineseWord, readingIndex)"
          >
            <strong lang="zh-CN">{{ word.chineseWord }}</strong>
            <span class="ai-key-word-pinyin" lang="zh-Latn">{{ word.pinyin }}</span>
            <span>{{ word.meaning }}</span>
            <IconGlyph name="audio" :size="16" />
            <span class="sr-only">{{ t('aiHelper.listen') }}</span>
          </button>
          <div v-else class="ai-key-word">
            <strong lang="zh-CN">{{ word.chineseWord }}</strong>
            <span class="ai-key-word-pinyin" lang="zh-Latn">{{ word.pinyin }}</span>
            <span>{{ word.meaning }}</span>
          </div>
        </li>
      </ul>
    </section>
  </div>

  <section v-if="questions.length" class="ai-story-check" :aria-labelledby="`${baseId}-check`">
    <h4 :id="`${baseId}-check`" class="page-eyebrow">{{ t('aiHelper.checkUnderstanding') }}</h4>
    <AiChoiceQuestions :questions="questions" :columns="2" />
  </section>
</template>

<script setup lang="ts">
import { computed, onDeactivated, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { aiGeneratorMessages } from '@/plugins/aiGeneratorMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { appLocale } from '@/plugins/i18n'
import type { AiStoryGeneratorResponse } from '@/types/ai'
import { storyVocabularyRows } from '@/utils/aiPracticeQuiz'

import AiChoiceQuestions, { type ChoiceQuestion } from './AiChoiceQuestions.vue'

const props = defineProps<{
  story: AiStoryGeneratorResponse
  topic: string
  questions: readonly ChoiceQuestion[]
}>()

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiGeneratorMessages] })
const baseId = useId()
const speech = useMandarinReadAloud()

const showPinyin = ref(true)
const showTranslation = ref(false)
const readingIndex = ref<number | null>(null)
const words = computed(() => storyVocabularyRows(props.story, appLocale.value))
const titleGloss = computed(() => {
  const gloss = (props.story.titleTranslation || props.topic).trim()
  return gloss && gloss !== props.story.title?.trim() ? gloss : ''
})

/** A key word can interrupt a story line and resume at the next line once the word finishes. */
function readText(text: string, storyIndex: number | null): void {
  speech.stopSpeaking()
  readingIndex.value = storyIndex
  const accepted = speech.speak(text, false, (_playing, reason) => {
    if (reason === 'ended' && storyIndex !== null) readFrom(storyIndex + 1)
    else if (reason !== 'started') readingIndex.value = null
  })
  if (!accepted) readingIndex.value = null
}

function readFrom(index: number): void {
  const lines = props.story.lines
  for (let at = index; at < lines.length; at++) {
    const text = lines[at]!.chinese
    if (!text.trim()) continue
    readText(text, at)
    return
  }
  readingIndex.value = null
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
  () => props.story,
  () => stopReading(),
)
onDeactivated(stopReading)

defineExpose({ stopReading })
</script>
