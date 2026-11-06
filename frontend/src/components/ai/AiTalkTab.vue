<template>
  <AiTalkSummary
    v-if="ended"
    ref="summaryView"
    :scenario-label="t(currentScenario.labelKey)"
    :minutes="minutes"
    :tips="tips"
    :entries="entries"
    :summary="summary"
    :loading="summaryLoading"
    :error="summaryError"
    :ai-notice="aiNotice"
    :speech="speech"
    @talk-again="restart(scenario)"
    @retry="loadSummary"
  />

  <section v-else class="ai-talk is-fill" :aria-labelledby="`${baseId}-stage`">
    <h2 :id="`${baseId}-stage`" class="sr-only">{{ t('aiHelper.talkTitle') }}</h2>

    <!-- One height in every state: the floor gives way to the text, so the reply dock never moves. -->
    <div class="ai-talk-stage">
      <div class="ai-talk-floor">
        <span class="ai-talk-state" role="status" aria-live="polite">
          <StatusPill :label="stageStateLabel" :tone="avatarState === 'away' ? 'outline' : 'strong'" />
        </span>
        <div
          v-if="speech.canSpeak"
          class="filter-chips ai-talk-voice"
          role="group"
          :aria-label="t('aiHelper.voiceOptions')"
        >
          <button class="filter-chip" type="button" @click="toggleVoice">
            {{ voiceOn ? t('aiHelper.voiceOn') : t('aiHelper.voiceOff') }}
          </button>
          <button class="filter-chip" type="button" :aria-pressed="slow" @click="slow = !slow">
            {{ t('aiHelper.slow') }}
          </button>
        </div>
        <XiaomeiAvatar class="ai-talk-face" :state="avatarState" />
        <p class="ai-talk-name">
          <span class="ai-talk-seal" aria-hidden="true"><IconGlyph name="about" :size="12" /></span>
          <span><span lang="zh-CN">小美</span> Xiaomei</span>
          <AiResultLabel v-if="currentEntry.ai" short />
        </p>
      </div>

      <div class="ai-talk-middle">
        <div v-if="listening" class="ai-talk-script" data-testid="ai-talk-listening">
          <p class="ai-talk-line hanzi is-faded" lang="zh-CN">{{ currentEntry.chinese }}</p>
          <p class="page-eyebrow">{{ t('aiHelper.youTimer', { time: formatClock(listenSeconds) }) }}</p>
          <p class="ai-talk-heard hanzi" lang="zh-CN">{{ speech.heard.value || t('aiHelper.speakNow') }}</p>
          <span class="ai-talk-wave" aria-hidden="true"><span v-for="bar in 32" :key="bar" /></span>
        </div>
        <div v-else class="ai-talk-script">
          <p v-if="lastReply" class="ai-talk-said">
            <span class="page-eyebrow">{{
              t(lastReply.via === 'voice' ? 'aiHelper.youVoice' : 'aiHelper.youTyped')
            }}</span>
            <span lang="zh-CN">{{ lastReply.chinese }}</span>
          </p>
          <p v-if="lastReply?.natural" class="ai-talk-tip" data-testid="ai-talk-tip">
            <span class="page-eyebrow">{{ t('aiHelper.tip') }}</span>
            <span>
              {{ t('aiHelper.moreNatural') }} <strong lang="zh-CN">{{ lastReply.natural }}</strong>
              <template v-if="lastReply.tip"> {{ lastReply.tip }}</template>
            </span>
          </p>
          <p v-if="thinking" class="ai-talk-typing" aria-hidden="true"><span /><span /><span /></p>
          <template v-else>
            <p class="ai-talk-line hanzi" lang="zh-CN" data-testid="ai-talk-line">{{ currentEntry.chinese }}</p>
            <p v-if="showPinyin && currentEntry.pinyin" class="ai-talk-gloss">{{ currentEntry.pinyin }}</p>
            <p v-if="showEnglish && currentEntry.translation" class="ai-talk-gloss">{{ currentEntry.translation }}</p>
          </template>
          <div class="ai-talk-tools" role="group" :aria-label="t('aiHelper.lineOptions')">
            <button
              v-if="speech.canSpeak"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              @click="speech.speak(currentEntry.chinese, slow)"
            >
              {{ t('aiHelper.listen') }}
            </button>
            <button class="btn btn-ghost btn-sm" type="button" :disabled="thinking" @click="showPinyin = !showPinyin">
              {{ showPinyin ? t('aiHelper.hidePinyin') : t('aiHelper.showPinyin') }}
            </button>
            <button class="btn btn-ghost btn-sm" type="button" :disabled="thinking" @click="showEnglish = !showEnglish">
              {{ showEnglish ? t('aiHelper.hideEnglish') : t('aiHelper.showEnglish') }}
            </button>
            <button
              v-if="currentEntry.ai && !currentEntry.reported"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              @click="reportEntry(currentEntry)"
            >
              {{ t('aiHelper.report') }}
            </button>
            <span v-else-if="currentEntry.reported" class="ai-reported">
              <IconGlyph name="status" :size="14" />
              {{ t('aiHelper.reported') }}
            </span>
          </div>
        </div>

        <section v-if="help.open" class="ai-suggest" :aria-labelledby="`${baseId}-help`">
          <header class="ai-suggest-head">
            <h3 :id="`${baseId}-help`" class="field-label">{{ t('aiHelper.trySaying') }}</h3>
            <button class="btn btn-ghost btn-sm" type="button" @click="help.open = false">
              {{ t('aiHelper.hideHelp') }}
            </button>
          </header>
          <p v-if="help.loading" class="ai-thinking">
            <span class="spinner is-sm" aria-hidden="true" />{{ t('aiHelper.findingIdeas') }}
          </p>
          <p v-else-if="help.error" class="callout is-compact is-danger" role="alert">{{ help.error }}</p>
          <ul v-else class="ai-suggest-list">
            <li v-for="line in help.lines" :key="line.chinese">
              <button class="ai-suggest-option" type="button" :disabled="!canSend" @click="send(line.chinese, 'help')">
                <span lang="zh-CN">{{ line.chinese }}</span>
                <small>{{ line.translation }}</small>
              </button>
            </li>
          </ul>
        </section>

        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>
      </div>

      <div class="ai-talk-reply">
        <form v-if="typing" class="ai-talk-dock ai-talk-type" @submit.prevent="send(draft, 'typed')">
          <button
            class="btn btn-secondary"
            type="button"
            :aria-expanded="help.open"
            :disabled="!help.open && !canSend"
            @click="toggleHelp"
          >
            {{ t('aiHelper.helpMeReply') }}
          </button>
          <label class="sr-only" :for="`${baseId}-draft`">{{ t('aiHelper.typeReplyLabel') }}</label>
          <input
            :id="`${baseId}-draft`"
            ref="draftInput"
            v-model="draft"
            class="input cjk-text"
            lang="zh-CN"
            autocomplete="off"
            :maxlength="200"
            :disabled="!canSend"
            :placeholder="blocked ? t('aiHelper.talkBlockedPlaceholder') : t('aiHelper.typeReplyPlaceholder')"
          />
          <button class="btn btn-primary" type="submit" :disabled="!canSend || !draft.trim()" :aria-busy="thinking">
            {{ t('aiHelper.send') }}
          </button>
        </form>
        <div v-else class="ai-talk-dock ai-talk-voice-dock">
          <button v-if="listening" class="btn btn-secondary is-start" type="button" @click="cancelListening">
            {{ t('aiHelper.cancel') }}
          </button>
          <button
            v-else
            class="btn btn-secondary is-start"
            type="button"
            :aria-expanded="help.open"
            :disabled="!help.open && !canSend"
            @click="toggleHelp"
          >
            {{ t('aiHelper.helpMeReply') }}
          </button>
          <button
            ref="micButton"
            class="ai-talk-mic"
            :class="{ 'is-listening': listening }"
            type="button"
            :disabled="!listening && !canSend"
            :aria-label="listening ? t('aiHelper.stopListening') : t('aiHelper.speakReply')"
            data-testid="ai-talk-mic"
            @click="listening ? finishListening() : startListening()"
          >
            <IconGlyph :name="listening ? 'stop' : 'mic'" :size="24" />
          </button>
          <button
            v-if="listening"
            class="btn btn-primary"
            type="button"
            :disabled="!speech.heard.value"
            @click="sendHeard"
          >
            {{ t('aiHelper.send') }}
          </button>
          <button v-else class="btn btn-secondary" type="button" :disabled="!canSend" @click="chooseTyping">
            {{ t('aiHelper.typeInstead') }}
          </button>
        </div>
        <p class="ai-talk-hint">
          <button v-if="typing && micReady" class="ai-talk-hint-action" type="button" @click="chooseMic">
            {{ t('aiHelper.useMicInstead') }}
          </button>
          <template v-else-if="typing && !blocked">{{ t('aiHelper.hintTyping') }}</template>
          <template v-else-if="!listening && !blocked">{{ t('aiHelper.hintMic') }}</template>
        </p>
      </div>
    </div>

    <aside class="ai-talk-rail">
      <div class="ai-talk-scenarios">
        <p class="page-eyebrow" aria-hidden="true">{{ t('aiHelper.scenario') }}</p>
        <FilterChips
          :model-value="scenario"
          :options="scenarioOptions"
          :label="t('aiHelper.scenario')"
          size="sm"
          :disabled="thinking"
          @update:model-value="chooseScenario"
        />
      </div>

      <h3 class="card-title">{{ t('aiHelper.conversation') }}</h3>
      <ol ref="transcript" class="ai-transcript" tabindex="0" :aria-label="t('aiHelper.conversation')">
        <li v-for="entry in entries" :key="entry.id" :class="`is-${entry.speaker.toLowerCase()}`">
          <span v-if="entry.speaker === 'XIAOMEI'" class="ai-transcript-who" lang="zh-CN">小美</span>
          <span v-else class="ai-transcript-who is-you">{{ t('aiHelper.you') }}</span>
          <span class="ai-transcript-line" lang="zh-CN">{{ entry.chinese }}</span>
          <span v-if="entry.natural" class="ai-transcript-tip" lang="zh-CN"
            >{{ t('aiHelper.tip') }}: {{ entry.natural }}</span
          >
        </li>
      </ol>

      <div class="ai-talk-end">
        <button
          class="btn btn-secondary btn-block"
          type="button"
          :disabled="!studentEntries.length || thinking || listening"
          :aria-describedby="studentEntries.length ? undefined : `${baseId}-end`"
          data-testid="ai-talk-end"
          @click="endConversation"
        >
          {{ t('aiHelper.endConversation') }}
        </button>
        <p v-if="!studentEntries.length" :id="`${baseId}-end`" class="field-hint">{{ t('aiHelper.replyOnceHint') }}</p>
        <AiNotice :text="aiNotice" guidance />
      </div>
    </aside>
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
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { useUiStore } from '@/stores/ui'
import type { XiaomeiScenario, XiaomeiSummary, XiaomeiTurn } from '@/types/ai'
import type { AiStoryLine } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatClock } from '@/utils/format'
import { localizedServerMessage } from '@/utils/serverMessages'

import AiNotice from './AiNotice.vue'
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
 * Talk with Xiaomei, face to face: her live state (your turn, listening, thinking, speaking, away) on the floor, her
 * line as a subtitle with pinyin and English on request, read aloud when Voice is on. The student answers from the
 * reply dock by voice (the browser writes it down in Chinese) or by typing, can ask for reply ideas, gets a gentle
 * "more natural" tip and ends with a summary. The rail holds the scenarios and the transcript. The conversation lives
 * only in this page.
 */
const props = defineProps<{
  blocked: AiBlockedReason
  aiNotice: string
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
/** The student chose Type instead; without a microphone the dock always types. */
const typedMode = ref(false)
const draftInput = ref<HTMLInputElement | null>(null)
const micButton = ref<HTMLButtonElement | null>(null)
const transcript = ref<HTMLOListElement | null>(null)
const summaryView = ref<InstanceType<typeof AiTalkSummary> | null>(null)
const showPinyin = ref(false)
const showEnglish = ref(false)
const voiceOn = ref(true)
const slow = ref(false)
const error = ref<string | null>(null)
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
const typing = computed(() => typedMode.value || !micReady.value)
const canSend = computed(() => !props.blocked && !thinking.value && !listening.value)
const studentEntries = computed(() => entries.value.filter((entry) => entry.speaker === 'STUDENT'))
const currentEntry = computed(() => entries.value.filter((entry) => entry.speaker === 'XIAOMEI').at(-1)!)
const lastReply = computed(() => studentEntries.value.at(-1))
const scenarioOptions = computed(() =>
  XIAOMEI_SCENARIOS.map((option) => ({ value: option.key, label: t(option.labelKey) })),
)
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

/** Keeps the newest line in view in the rail's transcript. */
async function showLatest(): Promise<void> {
  await nextTick()
  if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
}

async function chooseTyping(): Promise<void> {
  typedMode.value = true
  await nextTick()
  draftInput.value?.focus()
}

async function chooseMic(): Promise<void> {
  typedMode.value = false
  await nextTick()
  micButton.value?.focus()
}

function toggleHelp(): void {
  if (help.open) help.open = false
  else void openHelp()
}

async function send(text: string, via: TalkEntry['via']): Promise<void> {
  const message = text.trim()
  if (!message || !canSend.value) return
  error.value = null
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
  help.open = false
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

/** The stop button: what was heard goes to Xiaomei; with nothing heard the student is back where they were. */
function finishListening(): void {
  if (speech.heard.value) sendHeard()
  else cancelListening()
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
