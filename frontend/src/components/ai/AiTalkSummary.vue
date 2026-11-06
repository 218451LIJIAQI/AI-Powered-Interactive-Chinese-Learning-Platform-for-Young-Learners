<template>
  <section class="ai-talk-summary" :aria-labelledby="`${baseId}-title`" data-testid="ai-talk-summary">
    <header class="ai-summary-head">
      <div class="ai-summary-heading">
        <p class="page-eyebrow">{{ t('aiHelper.summaryEyebrow', { scenario: scenarioLabel, minutes }) }}</p>
        <h2 :id="`${baseId}-title`" class="ai-summary-title">{{ t('aiHelper.summaryTitle') }}</h2>
      </div>
      <button class="btn btn-primary" type="button" @click="emit('talkAgain')">{{ t('aiHelper.talkAgain') }}</button>
    </header>

    <div class="ai-summary-columns">
      <div class="ai-summary-main">
        <div class="ai-summary-farewell">
          <span class="ai-summary-face"><XiaomeiAvatar :state="loading ? 'thinking' : 'idle'" /></span>
          <div v-if="summary?.goodbye" class="ai-summary-goodbye">
            <p class="ai-summary-goodbye-line" lang="zh-CN">{{ summary.goodbye.chinese }}</p>
            <p v-if="summary.goodbye.pinyin" class="ai-line-pinyin">{{ summary.goodbye.pinyin }}</p>
            <p v-if="summary.goodbye.translation" class="ai-line-pinyin">{{ summary.goodbye.translation }}</p>
          </div>
          <p v-else-if="loading" class="ai-thinking">
            <span class="spinner is-sm" aria-hidden="true" />{{ t('aiHelper.summaryLoading') }}
          </p>
          <AiResultLabel v-if="summary" short />
        </div>

        <div v-if="error" class="alert alert-danger ai-summary-error" role="alert">
          <IconGlyph name="alert" :size="16" />
          <span>{{ error }}</span>
          <button class="btn btn-secondary btn-sm" type="button" :disabled="loading" @click="emit('retry')">
            {{ t('aiHelper.retry') }}
          </button>
        </div>
        <div v-else-if="loading" class="ai-summary-loading" aria-hidden="true">
          <span class="skeleton skeleton-line" />
          <span class="skeleton skeleton-line is-short" />
        </div>

        <p v-if="summary?.feedback" class="ai-summary-feedback">{{ summary.feedback }}</p>

        <section v-if="summary?.newExpressions.length" class="ai-summary-section" :aria-labelledby="`${baseId}-new`">
          <h3 :id="`${baseId}-new`" class="card-title">{{ t('aiHelper.newExpressions') }}</h3>
          <ul class="ai-expressions">
            <li v-for="line in summary.newExpressions" :key="line.chinese">
              <p class="ai-expression-zh" lang="zh-CN">{{ line.chinese }}</p>
              <p v-if="line.pinyin" class="ai-line-pinyin">{{ line.pinyin }}</p>
              <p class="ai-line-pinyin">{{ line.translation }}</p>
              <button
                v-if="speech.canSpeak"
                class="btn btn-ghost btn-sm ai-expression-listen"
                type="button"
                :aria-label="`${t('aiHelper.listen')}: ${line.chinese}`"
                @click="speech.speak(line.chinese)"
              >
                <IconGlyph name="audio" :size="16" />
                {{ t('aiHelper.listen') }}
              </button>
            </li>
          </ul>
        </section>

        <section v-if="tips.length" class="ai-summary-section" :aria-labelledby="`${baseId}-natural`">
          <h3 :id="`${baseId}-natural`" class="card-title">{{ t('aiHelper.sayItNaturally') }}</h3>
          <ul class="ai-naturals">
            <li v-for="tip in tips.slice(-2)" :key="tip.said">
              <p class="ai-natural is-said">
                <span class="page-eyebrow">{{ t('aiHelper.youSaid') }}</span>
                <span class="ai-natural-zh" lang="zh-CN">{{ tip.said }}</span>
              </p>
              <IconGlyph name="chevronRight" :size="20" />
              <p class="ai-natural is-better">
                <span class="page-eyebrow">{{ t('aiHelper.moreNaturalLabel') }}</span>
                <span class="ai-natural-zh" lang="zh-CN">{{ tip.natural }}</span>
              </p>
              <p v-if="tip.reason" class="ai-natural-reason">{{ tip.reason }}</p>
            </li>
          </ul>
        </section>
      </div>

      <aside class="ai-summary-rail">
        <section v-if="summary?.wordsUsed.length" class="ai-summary-section" :aria-labelledby="`${baseId}-words`">
          <h3 :id="`${baseId}-words`" class="card-title">{{ t('aiHelper.wordsYouUsed') }}</h3>
          <ul class="ai-words">
            <li v-for="word in summary.wordsUsed" :key="word.chinese">
              <span class="ai-word-zh" lang="zh-CN">{{ word.chinese }}</span>
              <small>{{ word.translation }}</small>
            </li>
          </ul>
        </section>
        <section class="ai-summary-section" :aria-labelledby="`${baseId}-conversation`">
          <h3 :id="`${baseId}-conversation`" class="card-title">{{ t('aiHelper.conversation') }}</h3>
          <ol class="ai-transcript">
            <li v-for="entry in entries" :key="entry.id" :class="`is-${entry.speaker.toLowerCase()}`">
              <span v-if="entry.speaker === 'XIAOMEI'" class="ai-transcript-who" lang="zh-CN">小美</span>
              <span v-else class="ai-transcript-who is-you">{{ t('aiHelper.you') }}</span>
              <span class="ai-transcript-line" lang="zh-CN">{{ entry.chinese }}</span>
            </li>
          </ol>
        </section>
      </aside>
    </div>

    <AiNotice :text="aiNotice" guidance />
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import type { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import type { XiaomeiSummary, XiaomeiTurn } from '@/types/ai'

import AiNotice from './AiNotice.vue'
import AiResultLabel from './AiResultLabel.vue'
import XiaomeiAvatar from './XiaomeiAvatar.vue'

export interface TalkTip {
  said: string
  natural: string
  reason: string
}

/**
 * The end of a Talk with Xiaomei: she says goodbye, SparkBot's feedback, new expressions to listen to again and "Say it
 * more naturally" from the chat's tips, with the words used and the whole conversation in the rail. Minutes, tips and
 * the conversation come from the chat itself, so they show even when the summary cannot be written.
 */
defineProps<{
  scenarioLabel: string
  minutes: number
  tips: readonly TalkTip[]
  entries: readonly { id: number; speaker: XiaomeiTurn['speaker']; chinese: string }[]
  summary: XiaomeiSummary | null
  loading: boolean
  error: string | null
  aiNotice: string
  speech: ReturnType<typeof useMandarinSpeech>
}>()
const emit = defineEmits<{ talkAgain: []; retry: [] }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const baseId = useId()
</script>
