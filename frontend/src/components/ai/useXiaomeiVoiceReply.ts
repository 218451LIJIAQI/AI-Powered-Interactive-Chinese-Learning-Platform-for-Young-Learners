import { nextTick, onScopeDispose, ref, watch, type ComputedRef, type Ref } from 'vue'

import type { useMandarinSpeech } from '@/composables/useMandarinSpeech'
import { useMicPermission } from '@/composables/useMicPermission'

interface UseXiaomeiVoiceReplyOptions {
  speech: ReturnType<typeof useMandarinSpeech>
  canStartMic: ComputedRef<boolean>
  typedMode: Ref<boolean>
  isActiveOnScreen: () => boolean
  onError: (message: string | null) => void
  onSendVoice: (text: string) => void
  focusDraft: () => void
  t: (key: string) => string
}

/**
 * Owns Xiaomei's speech-recognition reply cycle: mic permission prompt, elapsed listen timer, no-speech notice,
 * typed fallback switching, and recognition event watchers.
 */
export function useXiaomeiVoiceReply(options: UseXiaomeiVoiceReplyOptions) {
  const { speech, typedMode } = options
  const notHeard = ref(false)
  const listenSeconds = ref(0)
  let listenTimer: ReturnType<typeof setInterval> | undefined

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
      sendHeard()
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

  watch(speech.listening, (active) => {
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
    listenSeconds,
    notHeard,
    chooseTyping,
    sendHeard,
  }
}
