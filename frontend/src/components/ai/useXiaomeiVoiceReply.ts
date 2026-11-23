import { computed, nextTick, onScopeDispose, ref, watch, type ComputedRef, type Ref } from 'vue'

import type { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { useMicPermission } from '@/composables/useMicPermission'

export interface UseXiaomeiVoiceReplyOptions {
  speech: ReturnType<typeof useMandarinSpeech>
  hasLiveVideo: ComputedRef<boolean>
  canStartMic: ComputedRef<boolean>
  typedMode?: Ref<boolean>
  isActiveOnScreen: () => boolean
  onError: (message: string | null) => void
  onSendVoice: (text: string) => void
  onOpenLiveVideo: () => void
  focusDraft: () => void
  focusMic: () => void
  t: (key: string) => string
}

/**
 * Owns Xiaomei's speech-recognition reply cycle: mic permission prompt, elapsed listen timer, no-speech notice,
 * typed fallback switching, and recognition event watchers.
 */
export function useXiaomeiVoiceReply(options: UseXiaomeiVoiceReplyOptions) {
  const { speech } = options
  const notHeard = ref(false)
  const typedMode = options.typedMode ?? ref(false)
  const listenSeconds = ref(0)
  let listenTimer: ReturnType<typeof setInterval> | undefined

  const listening = speech.listening
  const hasMicMode = computed(() => options.hasLiveVideo.value || speech.canListen)
  const typing = computed(() => typedMode.value || !hasMicMode.value)

  const mic = useMicPermission(startListening)

  function clearListenTimer(): void {
    clearInterval(listenTimer)
    listenTimer = undefined
  }

  async function chooseTyping(): Promise<void> {
    mic.cancel()
    typedMode.value = true
    notHeard.value = false
    await nextTick()
    options.focusDraft()
  }

  async function retryMicrophone(): Promise<void> {
    if (!options.canStartMic.value) return
    typedMode.value = false
    options.onError(null)
    useMicrophone()
    await nextTick()
    options.focusMic()
  }

  function useMicrophone(): void {
    if (listening.value) return sendHeard()
    if (!options.canStartMic.value) return
    if (options.hasLiveVideo.value) options.onOpenLiveVideo()
    else void mic.request()
  }

  function startListening(): void {
    if (!options.isActiveOnScreen() || !options.canStartMic.value) return
    options.onError(null)
    notHeard.value = false
    if (!speech.listen() && !speech.recognitionError.value) {
      options.onError(options.t('aiHelper.micUnavailable'))
      void chooseTyping()
    }
  }

  function sendHeard(): void {
    const heard = speech.heard.value
    speech.cancelListening()
    if (heard.trim()) options.onSendVoice(heard)
    else notHeard.value = true
  }

  function handleRecognitionError(problem: NonNullable<typeof speech.recognitionError.value>): void {
    clearListenTimer()
    if (problem === 'noSpeech') {
      notHeard.value = true
      return
    }
    speech.cancelListening()
    options.onError(options.t(problem === 'denied' ? 'aiHelper.micDenied' : 'aiHelper.micUnavailable'))
    void chooseTyping()
  }

  watch(
    speech.recognitionError,
    (problem) => {
      if (problem) handleRecognitionError(problem)
    },
    { flush: 'sync' },
  )

  watch(listening, (active) => {
    clearListenTimer()
    if (active) {
      listenSeconds.value = 0
      listenTimer = setInterval(() => (listenSeconds.value += 1), 1000)
      return
    }
    const heard = speech.heard.value.trim()
    if (heard && !speech.recognitionError.value) options.onSendVoice(heard)
  })

  onScopeDispose(clearListenTimer)

  return {
    mic,
    listening,
    listenSeconds,
    notHeard,
    typedMode,
    hasMicMode,
    typing,
    chooseTyping,
    retryMicrophone,
    useMicrophone,
    sendHeard,
  }
}
