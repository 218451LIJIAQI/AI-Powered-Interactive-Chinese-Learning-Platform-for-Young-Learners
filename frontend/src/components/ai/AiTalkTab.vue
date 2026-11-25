<template>
  <AiTalkSummary
    v-if="ended"
    ref="summaryView"
    :scenario-label="t(currentScenario.labelKey)"
    :minutes="minutes"
    :entries="entries"
    :summary="summary"
    :loading="summaryLoading"
    :error="summaryError"
    :ai-notice="aiNotice"
    :live-video="hasLiveVideo"
    :reported="summaryReported"
    :memory="memory"
    :memory-error="memoryError"
    @talk-again="restart(scenario)"
    @retry="loadSummary"
    @report="reportSummary"
    @forget="forgetNote"
  />

  <section v-else class="ai-talk" :class="{ 'is-live': liveCall }" :aria-labelledby="`${baseId}-stage`">
    <h2 :id="`${baseId}-stage`" class="sr-only">{{ t('aiHelper.talk') }}</h2>

    <!-- The avatar and video share the floor above the dialogue. -->
    <div class="ai-talk-stage">
      <div v-if="liveCall" class="ai-talk-live">
        <div class="ai-talk-live-video">
          <XiaomeiLiveVideo
            :deployment-id="liveCall.deploymentId"
            :context="liveCall.context"
            :greeting="liveCall.greeting"
            @connected="liveCall.connected = true"
            @ended="closeLiveVideo"
            @failed="liveFailed"
            @utterance="addLiveTurn"
          />
        </div>
        <div class="ai-talk-live-controls">
          <span role="status">
            <StatusPill
              :label="t(liveCall.connected ? 'aiHelper.liveVideoConnected' : 'aiHelper.liveVideoLoading')"
              :tone="liveCall.connected ? 'success' : 'outline'"
            />
          </span>
          <button ref="hangupButton" class="btn btn-secondary btn-sm" type="button" @click="closeLiveVideo">
            {{ t('aiHelper.liveVideoEnd') }}
          </button>
        </div>
      </div>
      <div v-else class="ai-talk-floor">
        <span class="ai-talk-state" role="status" aria-live="polite">
          <StatusPill :label="stageStateLabel" :tone="avatarState === 'away' ? 'outline' : 'strong'" />
        </span>
        <div
          v-if="speech.canSpeak.value"
          class="filter-chips ai-talk-voice"
          role="group"
          :aria-label="t('aiHelper.voiceOptions')"
        >
          <button class="filter-chip" type="button" :aria-pressed="voiceOn" @click="toggleVoice">
            {{ voiceOn ? t('aiHelper.voiceOn') : t('aiHelper.voiceOff') }}
          </button>
          <button class="filter-chip" type="button" :aria-pressed="slow" @click="slow = !slow">
            {{ t('aiHelper.slow') }}
          </button>
        </div>
        <XiaomeiAvatar class="ai-talk-face" :state="avatarState === 'thinking' ? 'idle' : avatarState" />
        <p class="ai-talk-name">
          <span class="ai-talk-seal" aria-hidden="true"><IconGlyph name="sparkles" :size="12" /></span>
          <span><span lang="zh-CN">小美</span> Xiaomei</span>
          <AiResultLabel v-if="currentEntry.ai" class="is-sm" />
        </p>
      </div>

      <div class="ai-talk-middle">
        <div v-if="listening" class="ai-talk-script" data-testid="ai-talk-listening">
          <p class="ai-talk-line hanzi is-faded" lang="zh-CN">{{ currentEntry.chinese }}</p>
          <p class="page-eyebrow">{{ t('aiHelper.youTimer', { time: formatClock(listenSeconds) }) }}</p>
          <p class="ai-talk-heard hanzi" :lang="speech.heard.value ? 'zh-CN' : undefined">
            {{ speech.heard.value || t('aiHelper.speakNow') }}
          </p>
          <span class="ai-talk-wave" aria-hidden="true"><span v-for="bar in 32" :key="bar" /></span>
        </div>
        <div v-else class="ai-talk-script">
          <div class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{ liveReplyAnnouncement }}</div>
          <p v-if="lastReply && !liveCall" class="ai-talk-said">
            <span class="page-eyebrow">{{
              t(lastReply.via === 'voice' ? 'aiHelper.youVoice' : 'aiHelper.youTyped')
            }}</span>
            <span lang="zh-CN">{{ lastReply.chinese }}</span>
          </p>
          <p v-if="lastReply?.natural && !liveCall" class="ai-talk-tip" data-testid="ai-talk-tip">
            <span class="page-eyebrow">{{ t('aiHelper.tip') }}</span>
            <span>
              {{ t('aiHelper.moreNatural') }} <strong lang="zh-CN">{{ lastReply.natural }}</strong>
              <template v-if="lastReply.tip"> {{ lastReply.tip }}</template>
            </span>
          </p>
          <p v-if="thinking" class="ai-dots ai-talk-typing" aria-hidden="true"><span /><span /><span /></p>
          <template v-else>
            <p class="ai-talk-line hanzi" lang="zh-CN" data-testid="ai-talk-line">{{ currentEntry.chinese }}</p>
            <div
              v-if="(showPinyin && currentEntry.pinyin) || (showTranslation && currentTranslation)"
              class="ai-talk-gloss"
            >
              <p v-if="showPinyin && currentEntry.pinyin" :id="`${baseId}-pinyin`" lang="zh-Latn">
                {{ currentEntry.pinyin }}
              </p>
              <p v-if="showTranslation && currentTranslation" :id="`${baseId}-translation`">
                {{ currentTranslation }}
              </p>
            </div>
          </template>
          <div v-if="!liveCall" class="ai-talk-tools" role="group" :aria-label="t('aiHelper.lineOptions')">
            <button
              v-if="speech.canSpeak.value"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              @click="speech.speak(currentEntry.chinese, slow)"
            >
              {{ t('aiHelper.listen') }}
            </button>
            <button
              v-if="currentEntry.pinyin"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              :aria-expanded="showPinyin"
              :aria-controls="showPinyin && !thinking ? `${baseId}-pinyin` : undefined"
              @click="showPinyin = !showPinyin"
            >
              {{ t(showPinyin ? 'aiHelper.hidePinyin' : 'aiHelper.pinyin') }}
            </button>
            <button
              v-if="currentTranslation"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              :aria-expanded="showTranslation"
              :aria-controls="showTranslation && !thinking ? `${baseId}-translation` : undefined"
              @click="showTranslation = !showTranslation"
            >
              {{ showTranslation ? t('aiHelper.hideTranslation') : t('aiHelper.showTranslation') }}
            </button>
            <button
              v-if="currentEntry.ai && !currentEntry.reported"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              @click="reportLine"
            >
              <IconGlyph name="flag" :size="16" />
              {{ t('aiSafety.report') }}
            </button>
            <span v-else-if="currentEntry.reported" class="ai-reported">
              <IconGlyph name="status" :size="14" />
              {{ t('aiSafety.reported') }}
            </span>
          </div>
        </div>

        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>
      </div>

      <div v-if="!liveCall" ref="replyDock" class="ai-talk-reply">
        <form v-if="typing" class="ai-talk-dock ai-talk-type" @submit.prevent="send(draft, 'typed')">
          <label class="sr-only" :for="`${baseId}-draft`">{{ t('aiHelper.typeReplyLabel') }}</label>
          <input
            :id="`${baseId}-draft`"
            ref="draftInput"
            v-model="draft"
            class="input cjk-text"
            lang="zh-CN"
            autocomplete="off"
            :maxlength="MAX_REPLY"
            :disabled="!canSend"
            :placeholder="blocked ? t('aiHelper.talkBlockedPlaceholder') : t('aiHelper.typeReplyPlaceholder')"
          />
          <button class="btn btn-primary" type="submit" :disabled="!canSend || !draft.trim()" :aria-busy="thinking">
            {{ t('aiHelper.send') }}
          </button>
        </form>
        <div v-else class="ai-talk-dock ai-talk-voice-dock" :class="{ 'is-listening': listening }">
          <button v-if="listening" class="btn btn-secondary is-start" type="button" @click="speech.cancelListening">
            {{ t('common.cancel') }}
          </button>
          <button
            ref="micButton"
            class="ai-talk-mic"
            :class="{ 'is-listening': listening }"
            type="button"
            :disabled="!listening && !canStartMic"
            :aria-label="micLabel"
            :aria-describedby="`${baseId}-mic-status`"
            data-testid="ai-talk-mic"
            @click="useMicrophone"
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
        </div>
        <button
          v-if="typing && hasMicMode"
          class="ai-talk-hint-action"
          type="button"
          :disabled="!canStartMic"
          @click="retryMicrophone"
        >
          {{ t('aiHelper.retryMicrophone') }}
        </button>
        <p v-else :id="`${baseId}-mic-status`" class="ai-talk-hint" role="status" data-testid="ai-talk-status">
          <template v-if="listening">{{ t('aiHelper.hintListening') }}</template>
          <template v-else-if="notHeard">{{ t('aiHelper.micNoSpeech') }}</template>
          <template v-else-if="typing && !blocked">{{ t('aiHelper.hintTyping') }}</template>
          <template v-else-if="hasLiveVideo || !blocked">{{ t('aiHelper.hintMic') }}</template>
        </p>
      </div>
    </div>

    <aside class="ai-talk-rail">
      <div class="ai-talk-scenarios">
        <h3 class="ai-talk-rail-title">{{ t('aiHelper.scenario') }}</h3>
        <p v-if="liveCall" class="ai-talk-current-scenario">{{ t(currentScenario.labelKey) }}</p>
        <FilterChips
          v-else
          :model-value="scenario"
          :options="scenarioOptions"
          :label="t('aiHelper.scenario')"
          :disabled="thinking || listening"
          @update:model-value="chooseScenario"
        />
      </div>

      <div class="ai-talk-history">
        <h3 :id="`${baseId}-conversation`" class="ai-talk-rail-title">{{ t('aiHelper.conversation') }}</h3>
        <ol ref="transcript" class="ai-transcript" tabindex="0" :aria-labelledby="`${baseId}-conversation`">
          <li v-for="entry in entries" :key="entry.id" :class="{ 'is-student': entry.speaker === 'STUDENT' }">
            <span v-if="entry.speaker === 'XIAOMEI'" class="ai-transcript-who" lang="zh-CN">小美</span>
            <span v-else class="ai-transcript-who is-you">{{ t('aiHelper.you') }}</span>
            <span class="ai-transcript-line" lang="zh-CN">{{ entry.chinese }}</span>
            <span v-if="entry.natural" class="ai-transcript-tip">
              {{ t('aiHelper.tip') }}: <span lang="zh-CN">{{ entry.natural }}</span>
            </span>
          </li>
        </ol>
      </div>

      <div class="ai-talk-end">
        <button
          v-if="!liveCall"
          class="btn btn-secondary btn-block"
          type="button"
          :disabled="!canEnd || thinking || listening"
          :aria-describedby="`${baseId}-end`"
          data-testid="ai-talk-end"
          @click="endConversation"
        >
          {{ t('aiHelper.endConversation') }}
        </button>
        <p v-if="!liveCall" :id="`${baseId}-end`" class="field-hint" data-testid="ai-talk-end-hint">
          {{ canEnd ? t('aiHelper.repliesDoneHint') : t('aiHelper.replyTwiceHint') }}
        </p>
        <AiNotice :text="aiNotice" :live-video="hasLiveVideo" />
      </div>
    </aside>
  </section>

  <ConfirmDialog
    v-if="mic.asking.value"
    :title="t('aiHelper.micAskTitle')"
    :message="t('aiHelper.micAskTalk')"
    :confirm-label="t('common.allowMicrophone')"
    @confirm="mic.confirm"
    @cancel="chooseTyping"
  />
  <AiReportDialog
    v-if="reportTarget"
    :payload="reportTarget.payload"
    :title="t(reportTarget.titleKey)"
    :attached="t(reportTarget.attachedKey)"
    @close="reportTarget = null"
    @sent="onReported"
  />
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
import '@/styles/ai-talk.css'

import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiTalkMessages } from '@/plugins/aiTalkMessages'
import { appLocale } from '@/plugins/i18n'
import type { XiaomeiScenario, XiaomeiTurn } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatClock } from '@/utils/format'
import { redactPersonalData } from '@/utils/personalDataRedactor'
import { serverMessageOr } from '@/utils/serverMessages'
import { truncateText } from '@/utils/truncateText'

import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import AiTalkSummary from './AiTalkSummary.vue'
import XiaomeiAvatar, { type XiaomeiState } from './XiaomeiAvatar.vue'
import XiaomeiLiveVideo from './XiaomeiLiveVideo.vue'
import { revealResult, type AiBlockedReason } from './aiHelper'
import { useXiaomeiSummaryMemory } from './useXiaomeiSummaryMemory'
import { useXiaomeiVoiceReply } from './useXiaomeiVoiceReply'
import {
  XIAOMEI_SCENARIOS,
  buildLineReportTarget,
  buildSummaryReportTarget,
  xiaomeiTurns,
  type TalkEntry,
  type TalkReportTarget,
} from './xiaomeiTalk'

const props = defineProps<{
  blocked: AiBlockedReason
  aiNotice: string
  tavusDeploymentId?: string
}>()
const emit = defineEmits<{
  used: []
  reported: []
  toast: [message: string, action?: { label: string; run: () => void }]
}>()

const MAX_REPLY = 200
const REPLIES_FOR_SUMMARY = 2

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiTalkMessages] })
const speech = useMandarinSpeech()
const baseId = useId()
const isUnmounted = useIsUnmounted()

const scenario = ref<XiaomeiScenario>('MEET_XIAOMEI')
const entries = ref<TalkEntry[]>([])
const ended = ref(false)
const minutes = ref(1)
const thinking = ref(false)
const draft = ref('')
const draftInput = ref<HTMLInputElement | null>(null)
const micButton = ref<HTMLButtonElement | null>(null)
const hangupButton = ref<HTMLButtonElement | null>(null)
const transcript = ref<HTMLOListElement | null>(null)
const summaryView = ref<InstanceType<typeof AiTalkSummary> | null>(null)
const replyDock = ref<HTMLDivElement | null>(null)
const liveCall = ref<{ deploymentId: string; context: string; greeting: string; connected: boolean } | null>(null)
const typedMode = ref(false)
const showPinyin = ref(true)
const showTranslation = ref(true)
const voiceOn = ref(true)
const slow = ref(false)
const error = ref<string | null>(null)
const reportTarget = ref<TalkReportTarget | null>(null)
const pendingScenario = ref<XiaomeiScenario | null>(null)
let elapsedMs = 0
let activeSince: number | null = null
let nextId = 1
let conversation = 0
let onScreen = true

function startActiveClock(): void {
  if (activeSince === null && onScreen && !ended.value) activeSince = Date.now()
}

function pauseActiveClock(): void {
  if (activeSince !== null) {
    elapsedMs += Math.max(0, Date.now() - activeSince)
    activeSince = null
  }
}

const {
  summary,
  summaryLoading,
  summaryError,
  summaryReported,
  memory,
  memoryError,
  reset: resetSummaryMemory,
  loadSummary,
  forgetNote,
} = useXiaomeiSummaryMemory({
  getConversation: () => conversation,
  getScenario: () => scenario.value,
  getTurns: () => xiaomeiTurns(entries.value),
  isEndedOnScreen: () => ended.value && onScreen,
  hasSummaryView: () => Boolean(summaryView.value),
  t,
  onUsed: () => {
    if (!isUnmounted()) emit('used')
  },
  onToast: (message, action) => {
    if (!isUnmounted()) emit('toast', message, action)
  },
})

const hasLiveVideo = computed(() => Boolean(props.tavusDeploymentId))
const hasMicMode = computed(() => hasLiveVideo.value || speech.canListen)
const typing = computed(() => typedMode.value || !hasMicMode.value)

const currentScenario = computed(() => XIAOMEI_SCENARIOS.find((option) => option.key === scenario.value)!)
const studentEntries = computed(() => entries.value.filter((entry) => entry.speaker === 'STUDENT'))
const canEnd = computed(() => studentEntries.value.length >= REPLIES_FOR_SUMMARY)
const currentEntry = computed(() => entries.value.filter((entry) => entry.speaker === 'XIAOMEI').at(-1)!)
const currentTranslation = computed(() =>
  currentEntry.value.ai ? currentEntry.value.translation : t(currentScenario.value.opener.translationKey),
)
const lastReply = computed(() => studentEntries.value.at(-1))
const liveReplyAnnouncement = computed(() => {
  if (thinking.value || liveCall.value || !currentEntry.value.ai) return ''
  const parts = [currentEntry.value.chinese]
  if (lastReply.value?.natural) {
    parts.push(`${t('aiHelper.moreNatural')} ${lastReply.value.natural}`)
    if (lastReply.value.tip) parts.push(lastReply.value.tip)
  }
  return parts.join(' ')
})
const scenarioOptions = computed(() =>
  XIAOMEI_SCENARIOS.map((option) => ({ value: option.key, label: t(option.labelKey) })),
)

function stopAudio(): void {
  speech.cancelListening()
  speech.stopSpeaking()
}

async function showLatest(): Promise<void> {
  await nextTick()
  if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
}

async function openLiveVideo(): Promise<void> {
  const deploymentId = props.tavusDeploymentId
  if (!deploymentId) return
  startActiveClock()
  stopAudio()
  error.value = null
  const earlier = studentEntries.value.length
    ? xiaomeiTurns(entries.value).map((turn) => ({
        speaker: turn.speaker,
        text: redactPersonalData(turn.text),
      }))
    : []
  const situation = t(currentScenario.value.labelKey)
  const context = `The learner is practising beginner Mandarin in this scenario: ${situation}. Keep the role-play going in short, simple Mandarin.`
  liveCall.value = {
    deploymentId,
    context: earlier.length ? `${context} Continue from these previous turns: ${JSON.stringify(earlier)}` : context,
    greeting: earlier.length ? '我们接着聊吧。' : currentScenario.value.opener.chinese,
    connected: false,
  }
  await nextTick()
  hangupButton.value?.focus({ preventScroll: true })
}

async function closeLiveVideo(): Promise<void> {
  if (!liveCall.value) return
  liveCall.value = null
  await nextTick()
  const replyControl = typing.value ? draftInput.value : micButton.value
  replyControl?.focus({ preventScroll: true })
}

function liveFailed(code?: string): void {
  if (!liveCall.value) return
  error.value = t(
    code === 'MICROPHONE_BLOCKED_BY_PAGE' ? 'aiHelper.liveVideoMicBlocked' : 'aiHelper.liveVideoLoadError',
  )
  if (!props.blocked) typedMode.value = true
  void closeLiveVideo()
}

function addLiveTurn(speaker: XiaomeiTurn['speaker'], chinese: string): void {
  if (!liveCall.value) return
  const isXiaomei = speaker === 'XIAOMEI'
  if (isXiaomei && entries.value.length === 1 && chinese === entries.value[0]?.chinese) return
  entries.value.push({
    id: nextId++,
    speaker,
    chinese,
    ai: isXiaomei,
    via: isXiaomei ? undefined : 'voice',
  })
  void showLatest()
}

const listening = speech.listening
const canSend = computed(() => !props.blocked && !thinking.value && !listening.value && !liveCall.value)
const canStartMic = computed(() => (hasLiveVideo.value ? !thinking.value : canSend.value))

const { mic, listenSeconds, notHeard, chooseTyping, sendHeard } = useXiaomeiVoiceReply({
  speech,
  canStartMic,
  typedMode,
  isActiveOnScreen: () => onScreen && !ended.value,
  onError: (message) => {
    error.value = message
  },
  onSendVoice: (heard) => void send(heard, 'voice'),
  focusDraft: () => draftInput.value?.focus(),
  t,
})

function useMicrophone(): void {
  if (listening.value) return sendHeard()
  if (!canStartMic.value) return
  startActiveClock()
  if (hasLiveVideo.value) void openLiveVideo()
  else void mic.request()
}

async function retryMicrophone(): Promise<void> {
  if (!canStartMic.value) return
  typedMode.value = false
  error.value = null
  useMicrophone()
  if (!hasLiveVideo.value) {
    await nextTick()
    micButton.value?.focus()
  }
}

const micLabel = computed(() => {
  if (listening.value) return t('aiHelper.stopListening')
  return t(hasLiveVideo.value ? 'aiHelper.liveVideoStart' : 'aiHelper.speakReply')
})
const avatarState = computed<XiaomeiState | 'thinking'>(() => {
  if (thinking.value) return 'thinking'
  if (listening.value) return 'listening'
  if (speech.speaking.value) return 'speaking'
  return props.blocked && !hasLiveVideo.value ? 'away' : 'idle'
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

function restart(key: XiaomeiScenario): void {
  mic.cancel()
  conversation += 1
  thinking.value = false
  liveCall.value = null
  stopAudio()
  notHeard.value = false
  scenario.value = key
  const { opener } = currentScenario.value
  entries.value = [{ id: nextId++, speaker: 'XIAOMEI', ai: false, chinese: opener.chinese, pinyin: opener.pinyin }]
  elapsedMs = 0
  activeSince = null
  ended.value = false
  resetSummaryMemory()
  pendingScenario.value = null
  reportTarget.value = null
  error.value = null
  draft.value = ''
}

restart(scenario.value)

function chooseScenario(key: XiaomeiScenario): void {
  if (key === scenario.value) return
  speech.cancelListening()
  if (studentEntries.value.length) pendingScenario.value = key
  else restart(key)
}

async function send(text: string, via: TalkEntry['via']): Promise<void> {
  const message = truncateText(text.trim(), MAX_REPLY)
  if (!message || !canSend.value) return
  const hadReplyFocus = via === 'voice' || Boolean(replyDock.value?.contains(document.activeElement))
  startActiveClock()
  const mine = conversation
  const sessionVersion = getAuthSessionVersion()
  const stillCurrent = () => mine === conversation && sessionVersion === getAuthSessionVersion()
  error.value = null
  notHeard.value = false
  const earlier = xiaomeiTurns(entries.value)
  entries.value.push({ id: nextId++, speaker: 'STUDENT', ai: false, chinese: message, via })
  const student = entries.value.at(-1)!
  const takeBack = (problem: string): void => {
    entries.value = entries.value.filter((entry) => entry.id !== student.id)
    if (via === 'typed') draft.value = message
    error.value = problem
  }
  draft.value = ''
  thinking.value = true
  void showLatest()
  try {
    const result = await aiHelperApi.xiaomeiReply({
      scenario: scenario.value,
      turns: earlier,
      message,
      languagePreference: appLocale.value,
    })
    if (!stillCurrent()) {
      if (mine === conversation) entries.value = entries.value.filter((entry) => entry.id !== student.id)
      return
    }
    if (result.status !== 'SUCCESS' || !result.reply) {
      takeBack(serverMessageOr(result.message, t('aiHelper.xiaomeiError')))
      return
    }
    student.natural = result.naturalVersion ?? null
    student.tip = result.tip ?? null
    entries.value.push({ id: nextId++, speaker: 'XIAOMEI', ai: true, ...result.reply })
    if (voiceOn.value && onScreen) speech.speak(result.reply.chinese, slow.value)
  } catch (reason) {
    if (stillCurrent()) takeBack(resolveApiError(reason, t('aiHelper.xiaomeiError')))
    else if (mine === conversation) entries.value = entries.value.filter((entry) => entry.id !== student.id)
  } finally {
    if (!isUnmounted() && sessionVersion === getAuthSessionVersion()) emit('used')
    if (mine === conversation) {
      thinking.value = false
      if (sessionVersion === getAuthSessionVersion()) {
        await showLatest()
        if (
          hadReplyFocus &&
          onScreen &&
          !ended.value &&
          !props.blocked &&
          (!document.activeElement || document.activeElement === document.body)
        ) {
          const replyControl = typing.value ? draftInput.value : micButton.value
          replyControl?.focus({ preventScroll: true })
        }
      }
    }
  }
}

function toggleVoice(): void {
  voiceOn.value = !voiceOn.value
  if (!voiceOn.value) speech.stopSpeaking()
}

function reportLine(): void {
  reportTarget.value = buildLineReportTarget(entries.value, currentEntry.value, t('aiHelper.you'))
}

function reportSummary(): void {
  if (!summary.value) return
  reportTarget.value = buildSummaryReportTarget(entries.value, summary.value, t('aiHelper.you'))
}

function onReported(): void {
  const target = reportTarget.value
  if (target?.targetId === 'summary') {
    summaryReported.value = true
  } else if (target) {
    const entry = entries.value.find((item) => item.id === target.targetId)
    if (entry) entry.reported = true
  }
  reportTarget.value = null
  emit('reported')
}

async function endConversation(): Promise<void> {
  mic.cancel()
  speech.stopSpeaking()
  pauseActiveClock()
  minutes.value = Math.max(1, Math.round(elapsedMs / 60_000))
  ended.value = true
  void loadSummary()
  await nextTick()
  if (!isUnmounted()) revealResult(summaryView.value?.$el as Element | undefined)
}

onBeforeUnmount(() => {
  pauseActiveClock()
  onScreen = false
  conversation += 1
})
onActivated(() => {
  onScreen = true
  if (elapsedMs > 0 && !ended.value) activeSince = Date.now()
})
onDeactivated(() => {
  pauseActiveClock()
  onScreen = false
  liveCall.value = null
})
</script>
