<template>
  <div class="ai-stage ai-talk-summary" data-testid="ai-talk-summary">
    <aside class="ai-presence ai-goodbye">
      <XiaomeiAvatar class="ai-presence-portrait" :state="loading ? 'thinking' : 'idle'" />
      <div class="ai-goodbye-copy">
        <p class="ai-overline">{{ t('aiHelper.goodbyeLabel') }}</p>
        <template v-if="summary?.goodbye">
          <p class="ai-goodbye-line cjk-text" lang="zh-CN">{{ summary.goodbye.chinese }}</p>
          <p v-if="summary.goodbye.pinyin" class="ai-line-py">{{ summary.goodbye.pinyin }}</p>
          <p v-if="summary.goodbye.translation" class="ai-line-meaning">{{ summary.goodbye.translation }}</p>
        </template>
        <p v-else-if="loading" class="ai-thinking">
          <span class="spinner is-sm" aria-hidden="true" />{{ t('aiHelper.summaryLoading') }}
        </p>
      </div>
      <button class="btn btn-primary" type="button" @click="emit('talkAgain')">
        <IconGlyph name="retry" :size="16" />
        {{ t('aiHelper.talkAgain') }}
      </button>
    </aside>

    <section class="ai-sheet ai-recap" :aria-labelledby="`${baseId}-title`">
      <header class="ai-recap-head">
        <p class="ai-overline">{{ t('aiHelper.summaryEyebrow', { scenario: scenarioLabel, minutes }) }}</p>
        <h2 :id="`${baseId}-title`" class="ai-sheet-title">{{ t('aiHelper.summaryTitle') }}</h2>
      </header>

      <dl class="ai-recap-stats">
        <div>
          <dt>{{ t('aiHelper.statMinutes', minutes) }}</dt>
          <dd class="stat-figure">{{ minutes }}</dd>
        </div>
        <div>
          <dt>{{ t('aiHelper.statReplies', { voice: voiceReplies }, replies) }}</dt>
          <dd class="stat-figure">{{ replies }}</dd>
        </div>
        <div v-if="summary">
          <dt>{{ t('aiHelper.statWords', summary.wordsUsed.length) }}</dt>
          <dd class="stat-figure">{{ summary.wordsUsed.length }}</dd>
        </div>
        <div>
          <dt>{{ t('aiHelper.statTips', tips.length) }}</dt>
          <dd class="stat-figure">{{ tips.length }}</dd>
        </div>
      </dl>

      <div v-if="error" class="callout is-compact is-danger" role="alert">
        <span>{{ error }}</span>
        <button class="btn btn-secondary btn-sm" type="button" :disabled="loading" @click="emit('retry')">
          {{ t('aiHelper.retry') }}
        </button>
      </div>
      <div v-else-if="loading" class="ai-recap-loading" aria-hidden="true">
        <span class="skeleton skeleton-line" />
        <span class="skeleton skeleton-line is-short" />
      </div>

      <div v-if="summary" class="ai-recap-feedback">
        <p>{{ summary.feedback }}</p>
        <AiResultLabel />
      </div>

      <section v-if="summary?.newExpressions.length" class="ai-recap-section" :aria-labelledby="`${baseId}-new`">
        <h3 :id="`${baseId}-new`" class="ai-recap-label">{{ t('aiHelper.newExpressions') }}</h3>
        <ul class="ai-expressions">
          <li v-for="line in summary.newExpressions" :key="line.chinese">
            <p class="ai-expression-zh cjk-text" lang="zh-CN">{{ line.chinese }}</p>
            <p class="ai-expression-gloss">
              <span v-if="line.pinyin" class="ai-line-py">{{ line.pinyin }}</span>
              <span>{{ line.translation }}</span>
            </p>
            <button
              v-if="speech.canSpeak"
              class="btn btn-ghost btn-sm btn-icon"
              type="button"
              :aria-label="`${t('aiHelper.listen')}: ${line.chinese}`"
              @click="speech.speak(line.chinese)"
            >
              <IconGlyph name="audio" :size="16" />
            </button>
          </li>
        </ul>
      </section>

      <section v-if="tips.length" class="ai-recap-section" :aria-labelledby="`${baseId}-natural`">
        <h3 :id="`${baseId}-natural`" class="ai-recap-label">{{ t('aiHelper.sayItNaturally') }}</h3>
        <ul class="ai-naturals">
          <li v-for="tip in tips.slice(-2)" :key="tip.said">
            <p class="ai-natural-said">
              <span class="ai-overline">{{ t('aiHelper.youSaid') }}</span>
              <s class="cjk-text" lang="zh-CN">{{ tip.said }}</s>
            </p>
            <IconGlyph class="ai-natural-arrow" name="chevronRight" :size="20" />
            <p class="ai-natural-better">
              <span class="ai-overline">{{ t('aiHelper.moreNaturalLabel') }}</span>
              <strong class="cjk-text" lang="zh-CN">{{ tip.natural }}</strong>
            </p>
            <p v-if="tip.reason" class="ai-natural-reason">{{ tip.reason }}</p>
          </li>
        </ul>
      </section>

      <section v-if="summary?.wordsUsed.length" class="ai-recap-section" :aria-labelledby="`${baseId}-words`">
        <h3 :id="`${baseId}-words`" class="ai-recap-label">{{ t('aiHelper.wordsYouUsed') }}</h3>
        <ul class="ai-words">
          <li v-for="word in summary.wordsUsed" :key="word.chinese">
            <span class="hanzi" lang="zh-CN">{{ word.chinese }}</span>
            <small>{{ word.translation }}</small>
          </li>
        </ul>
      </section>

      <details class="ai-recap-transcript">
        <summary>
          <IconGlyph class="ai-disclosure" name="chevronDown" :size="16" />
          {{ t('aiHelper.conversation') }}
          <span>{{ t('aiHelper.messages', { count: entries.length }, entries.length) }}</span>
        </summary>
        <ol class="ai-recap-lines">
          <li v-for="entry in entries" :key="entry.id" :class="`is-${entry.speaker.toLowerCase()}`">
            <span class="ai-transcript-who">{{ entry.speaker === 'XIAOMEI' ? '小美' : t('aiHelper.you') }}</span>
            <span class="cjk-text" lang="zh-CN">{{ entry.chinese }}</span>
          </li>
        </ol>
      </details>

      <p class="ai-footnote">{{ t('aiHelper.summaryFootnote') }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import type { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import type { XiaomeiSummary, XiaomeiTurn } from '@/types/ai'

import AiResultLabel from './AiResultLabel.vue'
import XiaomeiAvatar from './XiaomeiAvatar.vue'

export interface TalkTip {
  said: string
  natural: string
  reason: string
}

/**
 * The end of a Talk with Xiaomei: she says goodbye beside a recap sheet with how long the chat was, replies (and how
 * many by voice), words used and tips as figures, SparkBot's feedback, new expressions to listen to again, "Say it more
 * naturally" from the chat's tips, the words used and the whole conversation on request. Minutes, replies and tips
 * come from the conversation itself, so they show even when the summary cannot be written.
 */
defineProps<{
  scenarioLabel: string
  minutes: number
  replies: number
  voiceReplies: number
  tips: readonly TalkTip[]
  entries: readonly { id: number; speaker: XiaomeiTurn['speaker']; chinese: string }[]
  summary: XiaomeiSummary | null
  loading: boolean
  error: string | null
  speech: ReturnType<typeof useMandarinSpeech>
}>()
const emit = defineEmits<{ talkAgain: []; retry: [] }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const baseId = useId()
</script>
