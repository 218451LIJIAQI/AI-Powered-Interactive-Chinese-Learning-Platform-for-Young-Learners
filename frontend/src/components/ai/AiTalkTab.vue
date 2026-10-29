<template>
  <AiTalkSummary
    v-if="ended"
    ref="summaryView"
    :scenario-label="t(currentScenario.labelKey)"
    :minutes="minutes"
    :replies="studentEntries.length"
    :voice-replies="voiceReplies"
    :tips="tips"
    :entries="entries"
    :summary="summary"
    :loading="summaryLoading"
    :error="summaryError"
    :speech="speech"
    @talk-again="restart(scenario)"
    @retry="loadSummary"
  />

  <section v-else class="ai-stage" :aria-labelledby="`${baseId}-stage`">
    <h2 :id="`${baseId}-stage`" class="sr-only">{{ t('aiHelper.talkTitle') }}</h2>

    <aside class="ai-presence">
      <XiaomeiAvatar class="ai-presence-portrait" :state="avatarState" />
      <div class="ai-presence-who">
        <p class="ai-presence-name">
          <span class="hanzi" lang="zh-CN">小美</span>
          <span class="ai-presence-latin">Xiaomei</span>
        </p>
        <p class="ai-presence-role">{{ t('aiHelper.xiaomeiRole') }}</p>
        <p class="ai-presence-state" :class="`is-${avatarState}`" role="status" aria-live="polite">
          <span class="ai-presence-dot" aria-hidden="true" />
          {{ stageStateLabel }}
        </p>
      </div>

      <div class="ai-talk-scenarios" role="group" :aria-labelledby="`${baseId}-scenario`">
        <p :id="`${baseId}-scenario`" class="ai-overline">{{ t('aiHelper.scenario') }}</p>
        <div class="ai-talk-scenario-list">
          <button
            v-for="option in XIAOMEI_SCENARIOS"
            :key="option.key"
            class="ai-talk-scenario"
            type="button"
            :aria-pressed="option.key === scenario"
            :disabled="thinking"
            @click="chooseScenario(option.key)"
          >
            {{ t(option.labelKey) }}
          </button>
        </div>
      </div>
    </aside>

    <div class="ai-chat">
      <header class="ai-chat-head">
        <p class="ai-chat-title">
          {{ t(currentScenario.labelKey) }}
          <span>{{ t('aiHelper.messages', { count: entries.length }, entries.length) }}</span>
        </p>
        <div class="ai-chat-tools">
          <div v-if="speech.canSpeak" class="ai-toggle-group" role="group" :aria-label="t('aiHelper.voiceOptions')">
            <button class="ai-toggle" type="button" :aria-pressed="voiceOn" @click="toggleVoice">
              <IconGlyph :name="voiceOn ? 'audio' : 'micOff'" :size="14" />
              {{ t('aiHelper.voice') }}
            </button>
            <button class="ai-toggle" type="button" :aria-pressed="slow" @click="slow = !slow">
              <IconGlyph name="slow" :size="14" />
              {{ t('aiHelper.slow') }}
            </button>
          </div>
          <div class="ai-toggle-group" role="group" :aria-label="t('aiHelper.lineOptions')">
            <button class="ai-toggle" type="button" :aria-pressed="showPinyin" @click="showPinyin = !showPinyin">
              {{ t('aiHelper.showPinyin') }}
            </button>
            <button
              class="ai-toggle"
              type="button"
              :aria-pressed="showTranslation"
              @click="showTranslation = !showTranslation"
            >
              {{ t('aiHelper.showTranslation') }}
            </button>
          </div>
        </div>
      </header>

      <div class="ai-chat-body">
        <ol ref="transcript" class="ai-transcript" :aria-label="t('aiHelper.conversation')">
          <li
            v-for="entry in entries"
            :key="entry.id"
            :class="[`is-${entry.speaker.toLowerCase()}`, { 'is-current': entry === currentEntry }]"
          >
            <template v-if="entry.speaker === 'XIAOMEI'">
              <p class="sr-only">小美</p>
              <p
                class="ai-line-zh cjk-text"
                lang="zh-CN"
                :data-testid="entry === currentEntry ? 'ai-talk-line' : undefined"
              >
                {{ entry.chinese }}
              </p>
              <p v-if="showPinyin && entry.pinyin" class="ai-line-py">{{ entry.pinyin }}</p>
              <p v-if="showTranslation && entry.translation" class="ai-line-meaning">{{ entry.translation }}</p>
              <div class="ai-line-actions">
                <button
                  v-if="speech.canSpeak"
                  class="ai-line-action"
                  type="button"
                  @click="speech.speak(entry.chinese, slow)"
                >
                  <IconGlyph name="audio" :size="14" />
                  {{ t('aiHelper.listen') }}
                </button>
                <button
                  v-if="entry.ai && !entry.reported"
                  class="ai-line-action"
                  type="button"
                  @click="reportEntry(entry)"
                >
                  <IconGlyph name="flag" :size="14" />
                  {{ t('aiHelper.report') }}
                </button>
                <span v-else-if="entry.reported" class="ai-reported">
                  <IconGlyph name="status" :size="14" />
                  {{ t('aiHelper.reported') }}
                </span>
                <AiResultLabel v-if="entry.ai && entry === currentEntry" />
              </div>
            </template>
            <template v-else>
              <p class="ai-bubble cjk-text" lang="zh-CN">
                <span class="sr-only">{{ t('aiHelper.you') }}: </span>{{ entry.chinese }}
              </p>
              <p class="ai-bubble-meta">
                <IconGlyph :name="entry.via === 'voice' ? 'mic' : 'keyboard'" :size="12" />
                {{ t(entry.via === 'voice' ? 'aiHelper.youVoice' : 'aiHelper.youTyped') }}
              </p>
              <p v-if="entry.natural" class="ai-tip" data-testid="ai-talk-tip">
                <span class="ai-tip-label">{{ t('aiHelper.tip') }}</span>
                <span>
                  {{ t('aiHelper.moreNatural') }} <strong class="cjk-text" lang="zh-CN">{{ entry.natural }}</strong>
                  <template v-if="entry.tip"> {{ entry.tip }}</template>
                </span>
              </p>
            </template>
          </li>
        </ol>
        <p v-if="thinking" class="ai-typing" aria-hidden="true"><span /><span /><span /></p>
        <p v-else-if="!studentEntries.length && !blocked" class="ai-chat-hint">{{ t('aiHelper.firstReplyHint') }}</p>
      </div>

      <div class="ai-composer" :class="{ 'is-idle': blocked }">
        <p v-if="notice" class="callout is-compact" role="status">{{ notice }}</p>
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>

        <div class="ai-composer-bar">
          <button
            class="ai-help-chip"
            type="button"
            :aria-expanded="help.open"
            :disabled="!help.open && !canSend"
            @click="help.open ? (help.open = false) : openHelp()"
          >
            <IconGlyph name="hint" :size="14" />
            {{ t('aiHelper.helpMeReply') }}
          </button>
          <button
            class="ai-help-chip ai-talk-end"
            type="button"
            :disabled="!studentEntries.length || thinking || listening"
            :aria-describedby="`${baseId}-end`"
            data-testid="ai-talk-end"
            @click="endConversation"
          >
            <IconGlyph name="checklist" :size="14" />
            {{ t('aiHelper.endConversation') }}
          </button>
          <span :id="`${baseId}-end`" class="sr-only">
            {{ studentEntries.length ? t('aiHelper.endHint') : t('aiHelper.replyOnceHint') }}
          </span>
        </div>

        <section v-if="help.open" class="ai-suggest" :aria-labelledby="`${baseId}-help`">
          <h3 :id="`${baseId}-help`" class="ai-overline">{{ t('aiHelper.trySaying') }}</h3>
          <p v-if="help.loading" class="ai-thinking">
            <span class="spinner is-sm" aria-hidden="true" />{{ t('aiHelper.findingIdeas') }}
          </p>
          <p v-else-if="help.error" class="callout is-compact is-danger" role="alert">{{ help.error }}</p>
          <ul v-else class="ai-suggest-list">
            <li v-for="line in help.lines" :key="line.chinese">
              <button class="ai-suggest-option" type="button" :disabled="!canSend" @click="send(line.chinese, 'help')">
                <span class="cjk-text" lang="zh-CN">{{ line.chinese }}</span>
                <small>{{ line.translation }}</small>
              </button>
            </li>
          </ul>
        </section>

        <div v-if="listening" class="ai-listening" data-testid="ai-talk-listening">
          <span class="ai-listening-dot" aria-hidden="true" />
          <p class="ai-listening-heard">
            <span class="ai-listening-time">{{ t('aiHelper.youTimer', { time: formatClock(listenSeconds) }) }}</span>
            <span class="cjk-text" lang="zh-CN">{{ speech.heard.value || t('aiHelper.speakNow') }}</span>
          </p>
          <div class="ai-talk-listening-actions">
            <button class="btn btn-ghost" type="button" @click="cancelListening">{{ t('aiHelper.cancel') }}</button>
            <button class="btn btn-primary" type="button" :disabled="!speech.heard.value" @click="sendHeard">
              <IconGlyph name="send" :size="16" />
              {{ t('aiHelper.send') }}
            </button>
          </div>
        </div>

        <form v-else class="ai-talk-type" @submit.prevent="send(draft, 'typed')">
          <label class="sr-only" :for="`${baseId}-draft`">{{ t('aiHelper.typeReplyLabel') }}</label>
          <input
            :id="`${baseId}-draft`"
            v-model="draft"
            class="input cjk-text"
            lang="zh-CN"
            autocomplete="off"
            :maxlength="200"
            :disabled="!canSend"
            :placeholder="blocked ? t('aiHelper.talkBlockedPlaceholder') : t('aiHelper.typeReplyPlaceholder')"
          />
          <button
            class="btn btn-icon"
            :class="draft.trim() && canSend ? 'btn-primary' : 'btn-secondary'"
            type="submit"
            :disabled="!canSend || !draft.trim()"
            :aria-busy="thinking"
            :aria-label="t('aiHelper.send')"
          >
            <IconGlyph v-if="!thinking" name="send" :size="18" />
          </button>
          <button
            v-if="micReady"
            class="ai-mic"
            type="button"
            :disabled="!canSend"
            :aria-label="t('aiHelper.speakReply')"
            data-testid="ai-talk-mic"
            @click="startListening"
          >
            <IconGlyph name="mic" :size="22" />
          </button>
        </form>
      </div>
    </div>
  </section>

  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
  <ConfirmDialog
    v-if="pendingScenario"
    :title="t('aiHelper.switchScenarioTitle')"
    :message="t('aiHelper.switchScenarioMessage')"
    :confirm-label="t('aiHelper.switchScenarioConfirm')"
    :cancel-label="t('aiHelper.keepTalking')"
    @confirm="restart(pendingScenario)"
    @cancel="pendingScenario = null"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { useUiStore } from '@/stores/ui'
import type { XiaomeiScenario, XiaomeiSummary, XiaomeiTurn } from '@/types/ai'
import type { AiStoryLine } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatClock } from '@/utils/format'
import { localizedServerMessage } from '@/utils/serverMessages'

import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import AiTalkSummary, { type TalkTip } from './AiTalkSummary.vue'
import XiaomeiAvatar, { type XiaomeiState } from './XiaomeiAvatar.vue'
import {
  lineText,
  minutesBetween,
  transcriptText,
  XIAOMEI_SCENARIOS,
  revealResult,
  type AiBlockedReason,
  type AiReportTarget,
} from './aiHelper'

/**
 * Talk with Xiaomei: a conversation with a person, not a form. Xiaomei sits beside the chat with her live state
 * (your turn, listening, thinking, speaking, away); her lines show Chinese with pinyin and the meaning on request and
 * are read aloud when Voice is on; the student answers by voice (the browser writes it down in Chinese) or by typing
 * in the same composer, can ask for reply ideas, gets a gentle "more natural" tip, and ends with a summary. The
 * conversation lives only in this page.
 */
const props = defineProps<{
  blocked: AiBlockedReason
}>()
const emit = defineEmits<{ used: []; reported: [] }>()

interface TalkEntry extends AiStoryLine {
  id: number
  speaker: XiaomeiTurn['speaker']
  /** False for the scenario's scripted opener. */
  ai: boolean
  via?: 'voice' | 'typed' | 'help'
  natural?: string | null
  tip?: string | null
  reported?: boolean
}

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const ui = useUiStore()
const speech = useMandarinSpeech()
const baseId = useId()

const scenario = ref<XiaomeiScenario>('MEET_XIAOMEI')
const entries = ref<TalkEntry[]>([])
const startedAt = ref(Date.now())
const endedAt = ref<number | null>(null)
const thinking = ref(false)
/** Set when the browser refuses the microphone: the student types instead. */
const micBlocked = ref(false)
const draft = ref('')
const transcript = ref<HTMLOListElement | null>(null)
const summaryView = ref<InstanceType<typeof AiTalkSummary> | null>(null)
const showPinyin = ref(true)
const showTranslation = ref(false)
const voiceOn = ref(true)
const slow = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const help = reactive({ open: false, loading: false, lines: [] as AiStoryLine[], error: null as string | null })
const reportTarget = ref<AiReportTarget | null>(null)
/** The reply being reported, marked once the report is sent. */
const reportingId = ref<number | null>(null)
const pendingScenario = ref<XiaomeiScenario | null>(null)
const summary = ref<XiaomeiSummary | null>(null)
const summaryLoading = ref(false)
const summaryError = ref<string | null>(null)
const listenSeconds = ref(0)
let listenTimer: ReturnType<typeof setInterval> | null = null
let nextId = 1

const currentScenario = computed(() => XIAOMEI_SCENARIOS.find((option) => option.key === scenario.value)!)
const ended = computed(() => endedAt.value !== null)
const listening = computed(() => speech.listening.value)
const micReady = computed(() => speech.canListen && !micBlocked.value)
const canSend = computed(() => !props.blocked && !thinking.value && !listening.value)
const studentEntries = computed(() => entries.value.filter((entry) => entry.speaker === 'STUDENT'))
const currentEntry = computed(() => entries.value.filter((entry) => entry.speaker === 'XIAOMEI').at(-1)!)
const voiceReplies = computed(() => studentEntries.value.filter((entry) => entry.via === 'voice').length)
const minutes = computed(() => minutesBetween(startedAt.value, endedAt.value ?? Date.now()))
const tips = computed<TalkTip[]>(() =>
  studentEntries.value
    .filter((entry) => entry.natural)
    .map((entry) => ({ said: entry.chinese, natural: entry.natural!, reason: entry.tip ?? '' })),
)
const avatarState = computed<XiaomeiState>(() => {
  if (thinking.value) return 'thinking'
  if (listening.value) return 'listening'
  if (speech.speaking.value) return 'speaking'
  return props.blocked ? 'away' : 'idle'
})
const stageStateLabel = computed(() => {
  switch (avatarState.value) {
    case 'thinking':
      return t('aiHelper.stateThinking')
    case 'listening':
      return t('aiHelper.stateListening')
    case 'speaking':
      return t('aiHelper.stateSpeaking')
    case 'away':
      return props.blocked === 'limit' ? t('aiHelper.stateBackTomorrow') : t('aiHelper.stateAway')
    default:
      return t('aiHelper.stateYourTurn')
  }
})

function opener(key: XiaomeiScenario): TalkEntry {
  const option = XIAOMEI_SCENARIOS.find((item) => item.key === key)!
  return {
    id: nextId++,
    speaker: 'XIAOMEI',
    ai: false,
    chinese: option.opener.chinese,
    pinyin: option.opener.pinyin,
    translation: t(option.opener.translationKey),
  }
}

function restart(key: XiaomeiScenario): void {
  speech.cancelListening()
  speech.stopSpeaking()
  scenario.value = key
  entries.value = [opener(key)]
  startedAt.value = Date.now()
  endedAt.value = null
  summary.value = null
  summaryError.value = null
  pendingScenario.value = null
  error.value = null
  notice.value = null
  draft.value = ''
  help.open = false
}

restart('MEET_XIAOMEI')

// The scripted opener follows the interface language.
watch(
  () => ui.locale,
  () => {
    const first = entries.value[0]
    if (first && !first.ai) first.translation = t(currentScenario.value.opener.translationKey)
  },
)

function chooseScenario(key: XiaomeiScenario): void {
  if (key === scenario.value) return
  if (studentEntries.value.length) pendingScenario.value = key
  else restart(key)
}

function turns(list: readonly TalkEntry[]): XiaomeiTurn[] {
  return list.map((entry) => ({ speaker: entry.speaker, text: entry.chinese }))
}

/** Brings the newest line into view above the pinned composer. */
async function showLatest(): Promise<void> {
  await nextTick()
  transcript.value?.lastElementChild?.scrollIntoView?.({ block: 'nearest' })
}

async function send(text: string, via: TalkEntry['via']): Promise<void> {
  const message = text.trim()
  if (!message || !canSend.value) return
  error.value = null
  notice.value = null
  help.open = false
  const earlier = turns(entries.value)
  const studentEntry: TalkEntry = { id: nextId++, speaker: 'STUDENT', ai: false, chinese: message, via }
  entries.value.push(studentEntry)
  draft.value = ''
  thinking.value = true
  void showLatest()
  try {
    const result = await aiHelperApi.xiaomeiReply({
      scenario: scenario.value,
      turns: earlier,
      message,
      languagePreference: ui.locale,
    })
    if (result.status !== 'SUCCESS' || !result.reply) {
      entries.value = entries.value.filter((entry) => entry.id !== studentEntry.id)
      if (via === 'typed') draft.value = message
      error.value = localizedServerMessage(result.message, t('aiHelper.xiaomeiError'))
      return
    }
    const added = entries.value.find((entry) => entry.id === studentEntry.id)
    if (added) {
      added.natural = result.naturalVersion ?? null
      added.tip = result.tip ?? null
    }
    entries.value.push({ id: nextId++, speaker: 'XIAOMEI', ai: true, ...result.reply })
    if (voiceOn.value) speech.speak(result.reply.chinese, slow.value)
  } catch (reason) {
    entries.value = entries.value.filter((entry) => entry.id !== studentEntry.id)
    if (via === 'typed') draft.value = message
    error.value = resolveApiError(reason, t('aiHelper.xiaomeiError'))
  } finally {
    thinking.value = false
    emit('used')
    await showLatest()
  }
}

async function openHelp(): Promise<void> {
  if (!canSend.value) return
  help.open = true
  help.loading = true
  help.error = null
  help.lines = []
  try {
    const result = await aiHelperApi.xiaomeiSuggestions({
      scenario: scenario.value,
      turns: turns(entries.value),
      languagePreference: ui.locale,
    })
    if (result.status === 'SUCCESS') help.lines = result.suggestions
    else help.error = localizedServerMessage(result.message, t('aiHelper.xiaomeiError'))
  } catch (reason) {
    help.error = resolveApiError(reason, t('aiHelper.xiaomeiError'))
  } finally {
    help.loading = false
    emit('used')
    void showLatest()
  }
}

function startListening(): void {
  error.value = null
  if (!speech.listen()) {
    micBlocked.value = true
    error.value = t('aiHelper.micUnavailable')
    return
  }
  listenSeconds.value = 0
  listenTimer = setInterval(() => (listenSeconds.value += 1), 1000)
}

function stopTimer(): void {
  if (listenTimer !== null) clearInterval(listenTimer)
  listenTimer = null
}

function cancelListening(): void {
  speech.cancelListening()
  stopTimer()
}

function sendHeard(): void {
  const heard = speech.heard.value
  speech.cancelListening()
  stopTimer()
  void send(heard, 'voice')
}

watch(
  () => speech.recognitionError.value,
  (problem) => {
    if (!problem) return
    stopTimer()
    if (problem === 'denied') {
      micBlocked.value = true
      error.value = t('aiHelper.micDenied')
    } else {
      error.value = problem === 'noSpeech' ? t('aiHelper.micNoSpeech') : t('aiHelper.micUnavailable')
    }
  },
)

// When the browser stops listening by itself (the student paused), what it heard is sent, as in a real conversation.
// Cancel and Send clear `heard` first, so they never send twice.
watch(listening, (active) => {
  if (active) return
  stopTimer()
  const heard = speech.heard.value.trim()
  if (heard && !speech.recognitionError.value) void send(heard, 'voice')
})

function toggleVoice(): void {
  voiceOn.value = !voiceOn.value
  if (!voiceOn.value) speech.stopSpeaking()
}

function reportEntry(entry: TalkEntry): void {
  const upTo = entries.value.slice(0, entries.value.indexOf(entry))
  reportingId.value = entry.id
  reportTarget.value = {
    payload: {
      featureKey: 'XIAOMEI_CONVERSATION',
      promptSnapshot: transcriptText(turns(upTo), t('aiHelper.you')),
      aiResponseSnapshot: lineText(entry),
    },
    titleKey: 'aiHelper.reportReplyTitle',
    attachedKey: 'aiHelper.reportReplyAttached',
  }
}

function onReported(): void {
  const reported = entries.value.find((entry) => entry.id === reportingId.value)
  if (reported) reported.reported = true
  reportTarget.value = null
  notice.value = t('aiHelper.reportReplySent')
  emit('reported')
}

async function loadSummary(): Promise<void> {
  summaryLoading.value = true
  summaryError.value = null
  try {
    const result = await aiHelperApi.xiaomeiSummary({
      scenario: scenario.value,
      turns: turns(entries.value),
      languagePreference: ui.locale,
    })
    if (result.status === 'SUCCESS') summary.value = result
    else summaryError.value = localizedServerMessage(result.message, t('aiHelper.xiaomeiError'))
  } catch (reason) {
    summaryError.value = resolveApiError(reason, t('aiHelper.summaryError'))
  } finally {
    summaryLoading.value = false
    emit('used')
  }
}

async function endConversation(): Promise<void> {
  speech.cancelListening()
  speech.stopSpeaking()
  endedAt.value = Date.now()
  void loadSummary()
  await nextTick()
  revealResult(summaryView.value?.$el as Element | undefined)
}

onBeforeUnmount(stopTimer)
</script>
