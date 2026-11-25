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
          <span class="ai-summary-face"><XiaomeiAvatar state="idle" /></span>
          <div v-if="summary?.goodbye" class="ai-summary-goodbye">
            <p class="ai-summary-goodbye-line" lang="zh-CN">{{ summary.goodbye.chinese }}</p>
            <p v-if="summary.goodbye.pinyin" class="ai-line-pinyin" lang="zh-Latn">{{ summary.goodbye.pinyin }}</p>
            <p v-if="summary.goodbye.translation" class="ai-line-pinyin">{{ summary.goodbye.translation }}</p>
          </div>
          <p v-else-if="loading" class="ai-thinking" role="status">
            <span class="spinner is-sm" aria-hidden="true" />{{ t('aiHelper.summaryLoading') }}
          </p>
          <AiResultLabel v-if="summary" />
        </div>

        <div v-if="error" class="callout is-danger" role="alert">
          <IconGlyph name="alert" :size="16" />
          <span>{{ error }}</span>
          <button class="btn btn-secondary btn-sm" type="button" :disabled="loading" @click="emit('retry')">
            {{ t('common.retry') }}
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
            <li v-for="(line, index) in summary.newExpressions" :key="`${index}-${line.chinese}`">
              <p class="ai-expression-zh" lang="zh-CN">{{ line.chinese }}</p>
              <p v-if="line.pinyin" class="ai-line-pinyin" lang="zh-Latn">{{ line.pinyin }}</p>
              <p v-if="line.translation" class="ai-line-pinyin">{{ line.translation }}</p>
              <button
                v-if="canListen"
                class="btn btn-ghost btn-sm ai-expression-listen"
                type="button"
                :aria-label="`${t('aiHelper.listen')}: ${line.chinese}`"
                @click="speak(line.chinese)"
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
            <li v-for="line in tips" :key="line.id">
              <p class="ai-natural is-said">
                <span class="page-eyebrow">{{ t('aiHelper.youSaid') }}</span>
                <span class="ai-natural-zh" lang="zh-CN">{{ line.chinese }}</span>
              </p>
              <IconGlyph name="chevronRight" :size="20" />
              <p class="ai-natural is-better">
                <span class="page-eyebrow">{{ t('aiHelper.moreNaturalLabel') }}</span>
                <span class="ai-natural-zh" lang="zh-CN">{{ line.natural }}</span>
              </p>
              <p v-if="line.tip" class="ai-natural-reason">{{ line.tip }}</p>
            </li>
          </ul>
        </section>
      </div>

      <aside class="ai-summary-rail">
        <section v-if="summary?.wordsUsed.length" class="ai-summary-section" :aria-labelledby="`${baseId}-words`">
          <h3 :id="`${baseId}-words`" class="card-title">{{ t('aiHelper.wordsYouUsed') }}</h3>
          <ul class="ai-words">
            <li v-for="(word, index) in summary.wordsUsed" :key="`${index}-${word.chinese}`">
              <span class="ai-word-zh" lang="zh-CN">{{ word.chinese }}</span>
              <small v-if="word.translation">{{ word.translation }}</small>
            </li>
          </ul>
        </section>
        <!-- What she keeps for the next chats: shown once known, and kept on screen after the last note is forgotten. -->
        <section
          v-if="memory && (memory.length || forgotOne)"
          class="ai-summary-section"
          :aria-labelledby="`${baseId}-memory`"
          data-testid="ai-summary-memory"
        >
          <h3 :id="`${baseId}-memory`" class="card-title">{{ t('aiHelper.memoryTitle') }}</h3>
          <ul v-if="memory.length" class="row-list">
            <li v-for="note in memory" :key="note.id" data-testid="ai-memory-note">
              <span class="row-list-copy">{{ noteText(note) }}</span>
              <span class="row-list-actions">
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :aria-label="t('aiHelper.memoryForgetLabel', { note: noteText(note) })"
                  @click="forget(note)"
                >
                  {{ t('aiHelper.memoryForget') }}
                </button>
              </span>
            </li>
          </ul>
          <p v-else class="field-hint">{{ t('aiHelper.memoryEmpty') }}</p>
          <p v-if="memoryError" class="field-error" role="alert">{{ memoryError }}</p>
          <p class="field-hint">{{ t('aiHelper.memoryFootnote') }}</p>
        </section>
        <section class="ai-summary-section" :aria-labelledby="`${baseId}-conversation`">
          <h3 :id="`${baseId}-conversation`" class="card-title">{{ t('aiHelper.conversation') }}</h3>
          <ol class="ai-transcript">
            <li v-for="entry in entries" :key="entry.id">
              <span v-if="entry.speaker === 'XIAOMEI'" class="ai-transcript-who" lang="zh-CN">小美</span>
              <span v-else class="ai-transcript-who is-you">{{ t('aiHelper.you') }}</span>
              <span class="ai-transcript-line" lang="zh-CN">{{ entry.chinese }}</span>
            </li>
          </ol>
        </section>
      </aside>
    </div>

    <!-- The summary is SparkBot's writing: it can be reported like any AI answer. -->
    <footer class="ai-summary-foot">
      <AiNotice :text="aiNotice" :live-video="liveVideo" />
      <template v-if="summary">
        <span v-if="reported" class="ai-reported"
          ><IconGlyph name="status" :size="14" />{{ t('aiSafety.reported') }}</span
        >
        <button
          v-else
          class="btn btn-ghost btn-sm"
          type="button"
          data-testid="ai-summary-report"
          @click="emit('report')"
        >
          <IconGlyph name="flag" :size="16" />
          {{ t('aiSafety.report') }}
        </button>
      </template>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { goalTitle } from '@/components/plan/planFormat'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiTalkMessages } from '@/plugins/aiTalkMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { XiaomeiMemoryNote, XiaomeiSummary } from '@/types/ai'

import AiNotice from './AiNotice.vue'
import AiResultLabel from './AiResultLabel.vue'
import XiaomeiAvatar from './XiaomeiAvatar.vue'
import { summaryNaturalTips, type TalkEntry } from './xiaomeiTalk'

/**
 * The end of a Talk with Xiaomei: she says goodbye, SparkBot's feedback, new expressions to listen to again and "Say it
 * more naturally" from the chat's tips, with the words used, what Xiaomei will remember and the whole conversation in
 * the rail. Minutes, tips and the conversation come from the chat itself, so they show even when the summary cannot be
 * written; the notes too, since the plan's goal is no AI writing. "Forget" asks the page to forget a note (`forget`). A
 * written summary can be reported (`report`); `reported` marks it once sent.
 */
const props = defineProps<{
  scenarioLabel: string
  minutes: number
  entries: readonly TalkEntry[]
  summary: XiaomeiSummary | null
  loading: boolean
  error: string | null
  aiNotice: string
  liveVideo: boolean
  reported: boolean
  /** What Xiaomei will remember; null while it is not known. */
  memory: readonly XiaomeiMemoryNote[] | null
  /** Why the last "Forget" or its Undo did not work. */
  memoryError: string | null
}>()
const emit = defineEmits<{ talkAgain: []; retry: []; report: []; forget: [note: XiaomeiMemoryNote] }>()

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiTalkMessages], plan: planSummaryMessages })
const baseId = useId()
const { canSpeak: canListen, speak } = useMandarinReadAloud()
/** "Say it more naturally": the learner's last two lines that have a more natural version. */
const tips = computed(() => summaryNaturalTips(props.entries))
/** Once a note was forgotten here, the section stays, saying there is nothing left, rather than vanishing. */
const forgotOne = ref(false)

/** A note as the learner reads it: the plan's goal named as on the plan pages, "(your plan)" after it. */
function noteText(note: XiaomeiMemoryNote): string {
  if (!note.planReason) return note.text ?? ''
  return t('aiHelper.memoryPlanNote', { goal: goalTitle(t, note.text, note.planReason) })
}

function forget(note: XiaomeiMemoryNote): void {
  forgotOne.value = true
  emit('forget', note)
}
</script>
