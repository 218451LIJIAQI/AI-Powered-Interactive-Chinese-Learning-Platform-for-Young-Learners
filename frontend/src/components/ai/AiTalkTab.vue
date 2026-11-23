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
      <div v-if="liveCall && hasLiveVideo" class="ai-talk-live">
        <div class="ai-talk-live-video">
          <XiaomeiLiveVideo
            :deployment-id="tavusDeploymentId!"
            :context="liveCall.context"
            :greeting="liveCall.greeting"
            @connected="liveConnected"
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
          <button
            ref="hangupButton"
            class="btn btn-secondary btn-sm"
            type="button"
            data-testid="ai-talk-end"
            @click="closeLiveVideo"
          >
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
        <XiaomeiAvatar class="ai-talk-face" :state="avatarState" />
        <p class="ai-talk-name">
          <span class="ai-talk-seal" aria-hidden="true"><IconGlyph name="about" :size="12" /></span>
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
              {{ showTranslation ? t('aiHelper.hideEnglish') : t('aiHelper.showEnglish') }}
            </button>
            <button
              v-if="currentEntry.ai && !currentEntry.reported"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="thinking"
              @click="reportLine"
            >
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

      <div v-if="!liveCall" class="ai-talk-reply">
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

    <AiTalkRail
      ref="talkRail"
      :scenario="scenario"
      :current-scenario-label="t(currentScenario.labelKey)"
      :scenario-options="scenarioOptions"
      :entries="entries"
      :live-call="Boolean(liveCall)"
      :has-live-video="hasLiveVideo"
      :thinking="thinking"
      :listening="listening"
      :can-end="canEnd"
      :ai-notice="aiNotice"
      @choose-scenario="chooseScenario"
      @end="endConversation"
    />
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
    :subtitle="reportTarget.leadKey ? t(reportTarget.leadKey) : undefined"
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
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiTalkMessages } from '@/plugins/aiTalkMessages'
import { appLocale } from '@/plugins/i18n'
import type { XiaomeiScenario } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatClock } from '@/utils/format'
import { serverMessageOr } from '@/utils/serverMessages'
import { truncateText } from '@/utils/truncateText'

import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import AiTalkRail from './AiTalkRail.vue'
import AiTalkSummary from './AiTalkSummary.vue'
import XiaomeiAvatar, { type XiaomeiState } from './XiaomeiAvatar.vue'
import XiaomeiLiveVideo from './XiaomeiLiveVideo.vue'
import { revealResult, XIAOMEI_SCENARIOS, type AiBlockedReason } from './aiHelper'
import { useXiaomeiLiveCall } from './useXiaomeiLiveCall'
import { useXiaomeiSummaryMemory } from './useXiaomeiSummaryMemory'
import { useXiaomeiVoiceReply } from './useXiaomeiVoiceReply'
import {
  buildLineReportTarget,
  buildSummaryReportTarget,
  xiaomeiTurns,
  type TalkEntry,
  type TalkReportTarget,
} from './xiaomeiReport'

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
const talkRail = ref<InstanceType<typeof AiTalkRail> | null>(null)
const summaryView = ref<InstanceType<typeof AiTalkSummary> | null>(null)
const typedMode = ref(false)
const showPinyin = ref(true)
const showTranslation = ref(true)
const voiceOn = ref(true)
const slow = ref(false)
const error = ref<string | null>(null)
const reportTarget = ref<TalkReportTarget | null>(null)
const pendingScenario = ref<XiaomeiScenario | null>(null)
let startedAt = 0
let nextId = 1
let conversation = 0
let onScreen = true

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
const scenarioOptions = computed(() =>
  XIAOMEI_SCENARIOS.map((option) => ({ value: option.key, label: t(option.labelKey) })),
)

function stopAudio(): void {
  speech.cancelListening()
  speech.stopSpeaking()
}

async function showLatest(): Promise<void> {
  await nextTick()
  talkRail.value?.scrollToBottom()
}

const { liveCall, openLiveVideo, liveConnected, closeLiveVideo, liveFailed, addLiveTurn, clearLiveCall } =
  useXiaomeiLiveCall({
    entries,
    nextEntryId: () => nextId++,
    hasStudentTurns: () => studentEntries.value.length > 0,
    getScenarioLabel: () => t(currentScenario.value.labelKey),
    getScenarioOpener: () => currentScenario.value.opener.chinese,
    isBlocked: () => Boolean(props.blocked),
    stopAudio,
    onError: (message) => {
      error.value = message
    },
    onEnableTypedFallback: () => {
      typedMode.value = true
    },
    onTurnAdded: () => void showLatest(),
    focusHangup: () => hangupButton.value?.focus({ preventScroll: true }),
    focusReplyControl: () => {
      const replyControl = typing.value ? draftInput.value : micButton.value
      replyControl?.focus({ preventScroll: true })
    },
    t,
  })

const canSend = computed(() => !props.blocked && !thinking.value && !speech.listening.value && !liveCall.value)
const canStartMic = computed(() => (hasLiveVideo.value ? !thinking.value : canSend.value))

const { mic, listening, listenSeconds, notHeard, chooseTyping, retryMicrophone, useMicrophone, sendHeard } =
  useXiaomeiVoiceReply({
    speech,
    hasLiveVideo,
    canStartMic,
    typedMode,
    isActiveOnScreen: () => onScreen && !ended.value,
    onError: (message) => {
      error.value = message
    },
    onSendVoice: (heard) => void send(heard, 'voice'),
    onOpenLiveVideo: () => void openLiveVideo(),
    focusDraft: () => draftInput.value?.focus(),
    focusMic: () => micButton.value?.focus(),
    t,
  })

const micLabel = computed(() => {
  if (listening.value) return t('aiHelper.stopListening')
  return t(hasLiveVideo.value ? 'aiHelper.liveVideoStart' : 'aiHelper.speakReply')
})
const avatarState = computed<XiaomeiState>(() => {
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
  clearLiveCall()
  stopAudio()
  notHeard.value = false
  scenario.value = key
  const { opener } = currentScenario.value
  entries.value = [{ id: nextId++, speaker: 'XIAOMEI', ai: false, chinese: opener.chinese, pinyin: opener.pinyin }]
  startedAt = Date.now()
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
    if (!stillCurrent()) return
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
  } finally {
    if (!isUnmounted() && sessionVersion === getAuthSessionVersion()) emit('used')
    if (mine === conversation) {
      thinking.value = false
      if (sessionVersion === getAuthSessionVersion()) await showLatest()
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
  minutes.value = Math.max(1, Math.round((Date.now() - startedAt) / 60_000))
  ended.value = true
  void loadSummary()
  await nextTick()
  if (!isUnmounted()) revealResult(summaryView.value?.$el as Element | undefined)
}

onBeforeUnmount(() => {
  onScreen = false
  conversation += 1
})
onActivated(() => {
  onScreen = true
})
onDeactivated(() => {
  onScreen = false
  clearLiveCall()
  stopAudio()
})
</script>
