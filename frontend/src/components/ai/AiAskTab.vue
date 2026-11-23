<template>
  <div class="ai-ask">
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

      <div ref="thread" class="ai-thread" tabindex="0" aria-live="polite">
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
              <AiResultLabel v-if="exchange.answered" />
            </p>
            <p v-if="exchange.pending && !exchange.answer" class="ai-answer is-thinking">
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
                {{ t('aiSafety.report') }}
              </button>
              <span v-else class="ai-reported"><IconGlyph name="status" :size="14" />{{ t('aiSafety.reported') }}</span>
            </div>
          </div>
        </template>
      </div>

      <div class="ai-ask-composer">
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>
        <div class="ai-topics">
          <span class="ai-topics-label">{{ t('aiHelper.quickStart') }}</span>
          <div class="btn-group is-sm">
            <button
              v-for="starter in STARTERS"
              :key="starter.labelKey"
              class="btn btn-secondary btn-sm"
              type="button"
              :disabled="Boolean(blocked) || asking"
              @click="startWith(t(starter.startKey))"
            >
              <IconGlyph :name="starter.icon" :size="16" />
              {{ t(starter.labelKey) }}
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
            :placeholder="placeholder"
            @keydown.enter.exact="sendOnEnter"
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
      <AiNotice :text="aiNotice" />
    </div>

    <AiReportDialog
      v-if="reportTarget"
      :payload="reportTarget.payload"
      :title="t(reportTarget.titleKey)"
      :subtitle="reportTarget.leadKey ? t(reportTarget.leadKey) : undefined"
      :attached="t(reportTarget.attachedKey)"
      @close="reportTarget = null"
      @sent="onReported"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiAskMessages } from '@/plugins/aiAskMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { appLocale } from '@/plugins/i18n'
import { resolveApiError } from '@/utils/apiError'
import { serverMessageOr } from '@/utils/serverMessages'
import { truncateText } from '@/utils/truncateText'

import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import type { AiBlockedReason, AiReportTarget } from './aiHelper'
import '@/styles/ai-ask.css'

/**
 * Ask SparkBot: short explanations and examples for beginners, streamed as they are written. A visit keeps one chat
 * session so follow-up questions have context; the Quick start buttons (explain a word, check a sentence, give examples,
 * explain grammar) start a question for the learner to finish, and `?q=` (from the review of a class quiz) fills the
 * box. The page puts My reports in the `rail` slot.
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
/** Quick start: the start of a question for the student to finish. */
const STARTERS = [
  { icon: 'language', labelKey: 'aiHelper.explainWord', startKey: 'aiHelper.explainWordStart' },
  { icon: 'checklist', labelKey: 'aiHelper.checkSentence', startKey: 'aiHelper.checkSentenceStart' },
  { icon: 'layers', labelKey: 'aiHelper.giveExamples', startKey: 'aiHelper.giveExamplesStart' },
  { icon: 'learn', labelKey: 'aiHelper.explainGrammar', startKey: 'aiHelper.explainGrammarStart' },
] as const
const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiAskMessages] })
const baseId = useId()

const question = ref(truncateText(props.initialQuestion ?? '', MAX_QUESTION))
const input = ref<HTMLTextAreaElement | null>(null)
const thread = ref<HTMLDivElement | null>(null)
const exchanges = ref<Exchange[]>([])
const sessionId = ref<number | null>(null)
const asking = computed(() => exchanges.value.at(-1)?.pending ?? false)
const error = ref<string | null>(null)
const reportTarget = ref<AiReportTarget | null>(null)
let stream: AbortController | null = null
let nextId = 1

const placeholder = computed(() => {
  if (props.blocked === 'limit') return t('aiHelper.limitPlaceholder')
  return t(props.blocked ? 'aiHelper.blockedPlaceholder' : 'aiHelper.askPlaceholder')
})

onMounted(() => {
  if (question.value) input.value?.focus()
})

watch(
  () => props.initialQuestion,
  (next) => {
    if (!next) return
    question.value = truncateText(next, MAX_QUESTION)
    void nextTick(() => input.value?.focus())
  },
)

async function startWith(prefix: string): Promise<void> {
  question.value = prefix
  await nextTick()
  input.value?.focus()
  input.value?.setSelectionRange(prefix.length, prefix.length)
}

/** Enter sends, unless it belongs to an input method turning pinyin into characters. */
function sendOnEnter(event: KeyboardEvent): void {
  if (event.isComposing) return
  event.preventDefault()
  void ask(question.value)
}

let scrollQueued = false

/** Keeps the newest message in view above the composer while the answer streams in. */
function scrollToEnd(): void {
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight
}

/** Coalesces rapid SSE deltas into at most one DOM scroll measurement per render flush. */
function scheduleScrollToEnd(): void {
  if (scrollQueued) return
  scrollQueued = true
  void nextTick(() => {
    scrollQueued = false
    scrollToEnd()
  })
}

async function ask(text: string): Promise<void> {
  const message = text.trim()
  if (!message || asking.value || props.blocked) return
  const sessionVersion = getAuthSessionVersion()
  error.value = null
  question.value = ''
  const current = reactive<Exchange>({
    id: nextId++,
    question: message,
    answer: '',
    pending: true,
    answered: false,
    messageId: null,
    reported: false,
  })
  exchanges.value.push(current)
  const controller = new AbortController()
  stream = controller
  const isCurrentRequest = () => !controller.signal.aborted && sessionVersion === getAuthSessionVersion()
  await nextTick()
  if (!isCurrentRequest()) return
  scrollToEnd()
  try {
    const reply = await aiHelperApi.chatStream(
      { message, languagePreference: appLocale.value, sessionId: sessionId.value ?? undefined },
      {
        onDelta: (chunk) => {
          if (!isCurrentRequest()) return
          current.answer += chunk
          scheduleScrollToEnd()
        },
        signal: controller.signal,
      },
    )
    if (!isCurrentRequest()) return
    current.answered = reply.status === 'SUCCESS'
    // Without an answer the reply is a fixed server message (unavailable, blocked): shown in the student's language.
    current.answer = current.answered ? reply.aiResponse : serverMessageOr(reply.aiResponse, t('aiHelper.askError'))
    current.messageId = reply.chatMessageId ?? null
    if (reply.sessionId) sessionId.value = reply.sessionId
  } catch (reason) {
    if (!isCurrentRequest()) return
    exchanges.value = exchanges.value.filter((item) => item.id !== current.id)
    question.value = message
    error.value = resolveApiError(reason, t('aiHelper.askError'))
  } finally {
    current.pending = false
    if (stream === controller) stream = null
    if (isCurrentRequest()) {
      emit('used')
      void nextTick(scrollToEnd)
    }
  }
}

function newChat(): void {
  exchanges.value = []
  sessionId.value = null
  error.value = null
}

function report(exchange: Exchange): void {
  if (!exchange.messageId) return
  reportTarget.value = {
    payload: { featureKey: 'CHAT_TUTOR', chatMessageId: exchange.messageId },
    titleKey: 'aiHelper.reportAnswerTitle',
    attachedKey: 'aiHelper.reportAnswerAttached',
    leadKey: 'aiHelper.reportLeadHere',
  }
}

function onReported(): void {
  const id = reportTarget.value?.payload.chatMessageId
  const reported = exchanges.value.find((item) => item.messageId === id)
  if (reported) reported.reported = true
  reportTarget.value = null
  emit('reported')
}

onBeforeUnmount(() => stream?.abort())
</script>
