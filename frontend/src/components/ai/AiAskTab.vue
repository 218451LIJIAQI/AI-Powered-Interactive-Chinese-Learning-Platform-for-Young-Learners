<template>
  <section class="ai-stage" :aria-labelledby="`${baseId}-title`">
    <h2 :id="`${baseId}-title`" class="sr-only">{{ t('aiHelper.askTitle') }}</h2>

    <aside class="ai-presence is-bot">
      <span class="ai-bot-mark" aria-hidden="true"><IconGlyph name="ai" :size="28" /></span>
      <div class="ai-presence-who">
        <p class="ai-presence-name"><span class="ai-presence-latin">SparkBot</span></p>
        <p class="ai-presence-role">{{ t('aiHelper.askSubtitle') }}</p>
        <p class="ai-presence-state" :class="`is-${botState}`">
          <span class="ai-presence-dot" aria-hidden="true" />
          {{ botStateLabel }}
        </p>
      </div>
      <button
        v-if="exchanges.length"
        class="btn btn-secondary btn-sm"
        type="button"
        :disabled="asking"
        @click="newChat"
      >
        <IconGlyph name="plus" :size="16" />
        {{ t('aiHelper.newChat') }}
      </button>
    </aside>

    <div class="ai-chat">
      <div ref="thread" class="ai-thread" aria-live="polite" data-testid="ai-ask-thread">
        <div v-if="!exchanges.length" class="ai-thread-empty">
          <p class="ai-thread-empty-lead">{{ t('aiHelper.askEmpty') }}</p>
          <ul class="ai-samples">
            <li v-for="key in SAMPLE_KEYS" :key="key">
              <button class="ai-sample" type="button" :disabled="Boolean(blocked)" @click="ask(t(key))">
                <IconGlyph name="chat" :size="16" />
                <span>{{ t(key) }}</span>
              </button>
            </li>
          </ul>
        </div>
        <template v-for="exchange in exchanges" :key="exchange.id">
          <div class="ai-message is-you">
            <p class="ai-bubble">
              <span class="sr-only">{{ t('aiHelper.you') }}: </span>{{ exchange.question }}
            </p>
          </div>
          <div class="ai-message is-bot" :aria-busy="exchange.pending">
            <p class="sr-only">SparkBot</p>
            <div
              v-if="exchange.pending && !exchange.answer"
              class="ai-answer is-thinking"
              data-testid="ai-ask-thinking"
            >
              <span class="skeleton skeleton-line" aria-hidden="true" />
              <span class="skeleton skeleton-line is-short" aria-hidden="true" />
              <span class="ai-thinking"
                ><span class="spinner is-sm" aria-hidden="true" />{{ t('aiHelper.sparkbotThinking') }}</span
              >
            </div>
            <p v-else class="ai-answer cjk-text" :class="{ 'is-problem': !exchange.answered && !exchange.pending }">
              {{ exchange.answer }}
            </p>
            <div v-if="exchange.answered" class="ai-line-actions ai-message-actions">
              <AiResultLabel />
              <template v-if="exchange.messageId">
                <button v-if="!exchange.reported" class="ai-line-action" type="button" @click="report(exchange)">
                  <IconGlyph name="flag" :size="14" />
                  {{ t('aiHelper.report') }}
                </button>
                <span v-else class="ai-reported"
                  ><IconGlyph name="status" :size="14" />{{ t('aiHelper.reported') }}</span
                >
              </template>
            </div>
          </div>
        </template>
      </div>

      <div class="ai-composer" :class="{ 'is-idle': blocked }">
        <p v-if="notice" class="callout is-compact" role="status">{{ notice }}</p>
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>
        <div class="ai-quick-start">
          <button
            class="ai-help-chip"
            type="button"
            :disabled="Boolean(blocked)"
            @click="startWith(t('aiHelper.explainWordStart'))"
          >
            <IconGlyph name="language" :size="14" />
            {{ t('aiHelper.explainWord') }}
          </button>
          <button
            class="ai-help-chip"
            type="button"
            :disabled="Boolean(blocked)"
            @click="startWith(t('aiHelper.checkSentenceStart'))"
          >
            <IconGlyph name="checklist" :size="14" />
            {{ t('aiHelper.checkSentence') }}
          </button>
        </div>
        <form class="ai-ask-bar" @submit.prevent="ask(question)">
          <label class="sr-only" :for="`${baseId}-question`">{{ t('aiHelper.askLabel') }}</label>
          <textarea
            :id="`${baseId}-question`"
            ref="input"
            v-model="question"
            class="textarea"
            rows="2"
            :maxlength="MAX_QUESTION"
            :disabled="Boolean(blocked) || asking"
            :placeholder="blockedPlaceholder || t('aiHelper.askPlaceholder')"
            @keydown.enter.exact.prevent="ask(question)"
          />
          <button
            class="btn btn-primary"
            type="submit"
            :disabled="Boolean(blocked) || asking || !question.trim()"
            :aria-busy="asking"
          >
            <IconGlyph v-if="!asking" name="send" :size="16" />
            {{ t('aiHelper.send') }}
          </button>
        </form>
        <AiNotice :text="aiNotice" />
      </div>
    </div>
  </section>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { useUiStore } from '@/stores/ui'
import { resolveApiError } from '@/utils/apiError'
import { localizeServerMessage } from '@/utils/serverMessages'

import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import type { AiBlockedReason, AiReportTarget } from './aiHelper'

/**
 * Ask SparkBot: short explanations and examples for beginners, streamed as they are written. SparkBot sits beside
 * the chat (ready, writing, away); a visit keeps one chat session so follow-up questions have context; "Explain a
 * word" and "Check a sentence" start a question for the student to finish, and `?q=` (from a class or practice page)
 * fills the box.
 */
const props = defineProps<{
  blocked: AiBlockedReason
  initialQuestion?: string | null
  aiNotice: string
}>()
const emit = defineEmits<{ used: []; reported: [] }>()

interface Exchange {
  id: number
  question: string
  answer: string
  pending: boolean
  answered: boolean
  messageId: number | null
  reported: boolean
}

const MAX_QUESTION = 2000
const SAMPLE_KEYS = ['aiHelper.sampleHello', 'aiHelper.sampleThanks', 'aiHelper.sampleNumbers'] as const
const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const ui = useUiStore()
const baseId = useId()

const question = ref((props.initialQuestion ?? '').slice(0, MAX_QUESTION))
const input = ref<HTMLTextAreaElement | null>(null)
const thread = ref<HTMLDivElement | null>(null)
const exchanges = ref<Exchange[]>([])
const sessionId = ref<number | null>(null)
const asking = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const reportTarget = ref<AiReportTarget | null>(null)
const reportingId = ref<number | null>(null)
let stream: AbortController | null = null
let nextId = 1

const blockedPlaceholder = computed(() => (props.blocked ? t('aiHelper.blockedPlaceholder') : ''))
const botState = computed(() => (asking.value ? 'thinking' : props.blocked ? 'away' : 'idle'))
const botStateLabel = computed(() => {
  if (asking.value) return t('aiHelper.sparkbotThinking')
  if (props.blocked === 'limit') return t('aiHelper.stateBackTomorrow')
  return props.blocked ? t('aiHelper.stateAway') : t('aiHelper.stateReady')
})

onMounted(() => {
  if (question.value) input.value?.focus()
})

async function startWith(prefix: string): Promise<void> {
  question.value = prefix
  await nextTick()
  input.value?.focus()
  input.value?.setSelectionRange(prefix.length, prefix.length)
}

/** Keeps the newest message in view above the pinned composer while the answer streams in. */
function scrollToEnd(): void {
  thread.value?.lastElementChild?.scrollIntoView?.({ block: 'nearest' })
}

async function ask(text: string): Promise<void> {
  const message = text.trim()
  if (!message || asking.value || props.blocked) return
  error.value = null
  notice.value = null
  question.value = ''
  asking.value = true
  const exchange: Exchange = {
    id: nextId++,
    question: message,
    answer: '',
    pending: true,
    answered: false,
    messageId: null,
    reported: false,
  }
  exchanges.value.push(exchange)
  const current = exchanges.value.at(-1)!
  await nextTick()
  scrollToEnd()
  stream = new AbortController()
  try {
    const reply = await aiHelperApi.chatStream(
      { message, languagePreference: ui.locale, sessionId: sessionId.value ?? undefined },
      {
        onDelta: (chunk) => {
          current.answer += chunk
          scrollToEnd()
        },
        signal: stream.signal,
      },
    )
    current.answered = reply.status === 'SUCCESS'
    // Without an answer the reply is a fixed server message (unavailable, blocked): shown in the student's language.
    current.answer = current.answered ? reply.aiResponse : localizeServerMessage(reply.aiResponse, ui.locale)
    current.messageId = reply.chatMessageId ?? null
    if (reply.sessionId) sessionId.value = reply.sessionId
  } catch (reason) {
    if (reason instanceof DOMException && reason.name === 'AbortError') return
    exchanges.value = exchanges.value.filter((item) => item.id !== exchange.id)
    question.value = message
    error.value = resolveApiError(reason, t('aiHelper.askError'))
  } finally {
    current.pending = false
    asking.value = false
    emit('used')
  }
}

function newChat(): void {
  stream?.abort()
  exchanges.value = []
  sessionId.value = null
  error.value = null
  notice.value = null
}

function report(exchange: Exchange): void {
  if (!exchange.messageId) return
  reportingId.value = exchange.id
  reportTarget.value = {
    payload: { featureKey: 'CHAT_TUTOR', chatMessageId: exchange.messageId },
    titleKey: 'aiHelper.reportAnswerTitle',
    attachedKey: 'aiHelper.reportAnswerAttached',
  }
}

function onReported(): void {
  const reported = exchanges.value.find((item) => item.id === reportingId.value)
  if (reported) reported.reported = true
  reportTarget.value = null
  notice.value = t('aiHelper.reportSentNotice')
  emit('reported')
}

onBeforeUnmount(() => stream?.abort())
</script>
