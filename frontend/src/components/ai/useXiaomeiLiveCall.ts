import { nextTick, ref, type Ref } from 'vue'

import type { XiaomeiTurn } from '@/types/ai'

import { xiaomeiTurns, type TalkEntry } from './xiaomeiReport'

export interface LiveCallState {
  context: string
  greeting: string
  connected: boolean
}

export interface UseXiaomeiLiveCallOptions {
  entries: Ref<TalkEntry[]>
  nextEntryId: () => number
  hasStudentTurns: () => boolean
  getScenarioLabel: () => string
  getScenarioOpener: () => string
  isBlocked: () => boolean
  stopAudio: () => void
  onError: (message: string | null) => void
  onEnableTypedFallback: () => void
  onTurnAdded: () => void
  focusHangup: () => void
  focusReplyControl: () => void
  t: (key: string) => string
}

/** Owns the Tavus live-video call session state, greeting/context construction, and live transcript ingestion. */
export function useXiaomeiLiveCall(options: UseXiaomeiLiveCallOptions) {
  const liveCall = ref<LiveCallState | null>(null)

  async function openLiveVideo(): Promise<void> {
    options.stopAudio()
    options.onError(null)
    const earlier = options.hasStudentTurns() ? xiaomeiTurns(options.entries.value) : []
    const situation = options.getScenarioLabel()
    const context = `The learner is practising beginner Mandarin in this scenario: ${situation}. Keep the role-play going in short, simple Mandarin.`
    liveCall.value = {
      context: earlier.length ? `${context} Continue from these previous turns: ${JSON.stringify(earlier)}` : context,
      greeting: earlier.length ? '我们接着聊吧。' : options.getScenarioOpener(),
      connected: false,
    }
    await nextTick()
    options.focusHangup()
  }

  function liveConnected(): void {
    if (liveCall.value) liveCall.value.connected = true
  }

  async function closeLiveVideo(): Promise<void> {
    if (!liveCall.value) return
    liveCall.value = null
    await nextTick()
    options.focusReplyControl()
  }

  function liveFailed(code?: string): void {
    if (!liveCall.value) return
    options.onError(
      options.t(code === 'MICROPHONE_BLOCKED_BY_PAGE' ? 'aiHelper.liveVideoMicBlocked' : 'aiHelper.liveVideoLoadError'),
    )
    if (!options.isBlocked()) options.onEnableTypedFallback()
    void closeLiveVideo()
  }

  function addLiveTurn(speaker: XiaomeiTurn['speaker'], chinese: string): void {
    if (!liveCall.value) return
    const isXiaomei = speaker === 'XIAOMEI'
    if (isXiaomei && options.entries.value.length === 1 && chinese === options.entries.value[0]?.chinese) return
    options.entries.value.push({
      id: options.nextEntryId(),
      speaker,
      chinese,
      ai: isXiaomei,
      via: isXiaomei ? undefined : 'voice',
    })
    options.onTurnAdded()
  }

  function clearLiveCall(): void {
    liveCall.value = null
  }

  return {
    liveCall,
    openLiveVideo,
    liveConnected,
    closeLiveVideo,
    liveFailed,
    addLiveTurn,
    clearLiveCall,
  }
}
