<template>
  <div class="ai-ask is-fill">
    <section class="panel ai-ask-chat" :aria-labelledby="`${baseId}-title`">
      <header class="section-header">
        <h2 :id="`${baseId}-title`" class="card-title">{{ t('aiHelper.askTitle') }}</h2>
        <button
          v-if="exchanges.length"
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="asking"
          @click="newChat"
        >
          {{ t('aiHelper.newChat') }}
        </button>
      </header>

      <div ref="thread" class="ai-thread" tabindex="0" aria-live="polite" data-testid="ai-ask-thread">
        <StatePanel
          v-if="!exchanges.length"
          class="ai-thread-empty"
          tone="empty"
          icon="chat"
          :title="t('aiHelper.askEmptyTitle')"
          :message="t('aiHelper.askEmpty')"
        >
          <template #actions>
            <button
              v-for="key in SAMPLE_KEYS"
              :key="key"
              class="btn btn-secondary btn-sm"
              type="button"
              :disabled="Boolean(blocked)"
              @click="ask(t(key))"
            >
              {{ t(key) }}
            </button>
          </template>
        </StatePanel>
        <template v-for="exchange in exchanges" :key="exchange.id">
          <div class="ai-message is-you">
            <p class="ai-message-who">{{ t('aiHelper.you') }}</p>
            <p class="ai-bubble">{{ exchange.question }}</p>
          </div>
          <div class="ai-message is-bot" :aria-busy="exchange.pending">
            <p class="ai-message-who">
              SparkBot
              <AiResultLabel v-if="exchange.answered" short />
            </p>
            <p v-if="exchange.pending && !exchange.answer" class="ai-answer is-thinking" data-testid="ai-ask-thinking">
              <IconGlyph name="clock" :size="16" />
              {{ t('aiHelper.sparkbotThinking') }}
              <span class="ai-dots" aria-hidden="true"><span /><span /><span /></span>
            </p>
            <p v-else class="ai-answer" :class="{ 'is-problem': !exchange.answered && !exchange.pending }">
              {{ exchange.answer }}
            </p>
            <div v-if="exchange.answered && exchange.messageId" class="ai-message-actions">
              <button v-if="!exchange.reported" class="btn btn-ghost btn-sm" type="button" @click="report(exchange)">
                <IconGlyph name="flag" :size="16" />
                {{ t('aiHelper.report') }}
              </button>
              <span v-else class="ai-reported"><IconGlyph name="status" :size="14" />{{ t('aiHelper.reported') }}</span>
            </div>
          </div>
        </template>
      </div>

      <div class="ai-ask-composer">
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>
        <div class="ai-quick-start">
          <span class="ai-quick-start-label">{{ t('aiHelper.quickStart') }}</span>
          <div class="btn-group is-sm">
            <button
              class="btn btn-secondary btn-sm"
              type="button"
              :disabled="Boolean(blocked) || asking"
              @click="startWith(t('aiHelper.explainWordStart'))"
            >
              <IconGlyph name="language" :size="16" />
              {{ t('aiHelper.explainWord') }}
            </button>
            <button
              class="btn btn-secondary btn-sm"
              type="button"
              :disabled="Boolean(blocked) || asking"
              @click="startWith(t('aiHelper.checkSentenceStart'))"
            >
              <IconGlyph name="checklist" :size="16" />
              {{ t('aiHelper.checkSentence') }}
            </button>
          </div>
        </div>
        <form class="ai-ask-bar" @submit.prevent="ask(question)">
          <label class="sr-only" :for="`${baseId}-question`">{{ t('aiHelper.askLabel') }}</label>
          <textarea
            :id="`${baseId}-question`"
            ref="input"
            v-model="question"
            class="textarea"
            rows="1"
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
            {{ asking ? t('aiHelper.thinking') : t('aiHelper.send') }}
            <IconGlyph v-if="!asking" name="send" :size="16" />
          </button>
        </form>
      </div>
    </section>

    <div class="ai-ask-rail">
      <slot name="rail" />
      <AiNotice :text="aiNotice" guidance />
    </div>
  </div>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
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
 * Ask SparkBot: short explanations and examples for beginners, streamed as they are written. A visit keeps one chat
 * session so follow-up questions have context; "Explain a word" and "Check a sentence" start a question for the student
 * to finish, and `?q=` (from a class or practice page) fills the box. The page puts My reports in the `rail` slot.
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
const reportTarget = ref<AiReportTarget | null>(null)
const reportingId = ref<number | null>(null)
let stream: AbortController | null = null
let nextId = 1

const blockedPlaceholder = computed(() => {
  if (props.blocked === 'limit') return t('aiHelper.limitPlaceholder')
  return props.blocked ? t('aiHelper.blockedPlaceholder') : ''
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

/** Keeps the newest message in view above the composer while the answer streams in. */
function scrollToEnd(): void {
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight
}

async function ask(text: string): Promise<void> {
  const message = text.trim()
  if (!message || asking.value || props.blocked) return
  error.value = null
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
          void nextTick(scrollToEnd)
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
    void nextTick(scrollToEnd)
  }
}

function newChat(): void {
  stream?.abort()
  exchanges.value = []
  sessionId.value = null
  error.value = null
}

function report(exchange: Exchange): void {
  if (!exchange.messageId) return
  reportingId.value = exchange.id
  reportTarget.value = {
    payload: { featureKey: 'CHAT_TUTOR', chatMessageId: exchange.messageId },
    titleKey: 'aiHelper.reportAnswerTitle',
    attachedKey: 'aiHelper.reportAnswerAttached',
    leadKey: 'aiHelper.reportLeadHere',
  }
}

function onReported(): void {
  const reported = exchanges.value.find((item) => item.id === reportingId.value)
  if (reported) reported.reported = true
  reportTarget.value = null
  emit('reported')
}

onBeforeUnmount(() => stream?.abort())
</script>
