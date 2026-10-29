import { getCurrentScope, onScopeDispose, readonly, ref } from 'vue'

import { canSpeak as speechAvailable, speakChinese, stopSpeaking as cancelSpeech } from '@/utils/speech'

/** The parts of the Web Speech API recognition object used here (it is still prefixed in Chrome and Edge). */
interface RecognitionResultLike {
  readonly isFinal: boolean
  readonly 0: { readonly transcript: string }
}

interface RecognitionEventLike {
  readonly resultIndex: number
  readonly results: ArrayLike<RecognitionResultLike>
}

interface RecognitionLike {
  lang: string
  interimResults: boolean
  continuous: boolean
  maxAlternatives: number
  onresult: ((event: RecognitionEventLike) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void
  abort(): void
}

type RecognitionConstructor = new () => RecognitionLike

type RecognitionError = 'denied' | 'noSpeech' | 'failed'

const MANDARIN = 'zh-CN'

function recognitionConstructor(): RecognitionConstructor | null {
  const scope = window as unknown as {
    SpeechRecognition?: RecognitionConstructor
    webkitSpeechRecognition?: RecognitionConstructor
  }
  return scope.SpeechRecognition ?? scope.webkitSpeechRecognition ?? null
}

/**
 * Mandarin speech in the browser: Xiaomei's lines read aloud (utils/speech.ts, normal or slow) with a `speaking`
 * state, and the student's spoken reply written down with the Web Speech API (`lang = zh-CN`) where the browser
 * supports it. The voice never leaves the browser through this app: only the text it produces is sent, like a typed
 * reply.
 */
export function useMandarinSpeech() {
  const canSpeak = speechAvailable()
  const Recognition = typeof window !== 'undefined' ? recognitionConstructor() : null
  const canListen = Recognition !== null

  const speaking = ref(false)
  const listening = ref(false)
  /** What was heard so far: the final part followed by the part the browser may still change. */
  const heard = ref('')
  const recognitionError = ref<RecognitionError | null>(null)
  let recognition: RecognitionLike | null = null
  let finalText = ''

  function speak(text: string, slow = false): void {
    if (speakChinese(text, slow, (value) => (speaking.value = value))) speaking.value = true
  }

  function stopSpeaking(): void {
    cancelSpeech()
    speaking.value = false
  }

  /** Starts listening; `heard` fills in as the student speaks. Returns false when the browser cannot listen. */
  function listen(): boolean {
    if (!Recognition || listening.value) return false
    stopSpeaking()
    finalText = ''
    heard.value = ''
    recognitionError.value = null
    const created = new Recognition()
    created.lang = MANDARIN
    created.interimResults = true
    created.continuous = true
    created.maxAlternatives = 1
    created.onresult = (event) => {
      let interim = ''
      for (let index = event.resultIndex; index < event.results.length; index++) {
        const result = event.results[index]
        if (result.isFinal) finalText += result[0].transcript
        else interim += result[0].transcript
      }
      heard.value = (finalText + interim).trim()
    }
    created.onerror = (event) => {
      if (event.error === 'aborted') return
      recognitionError.value =
        event.error === 'not-allowed' || event.error === 'service-not-allowed'
          ? 'denied'
          : event.error === 'no-speech'
            ? 'noSpeech'
            : 'failed'
    }
    created.onend = () => {
      listening.value = false
      recognition = null
    }
    try {
      created.start()
    } catch {
      recognitionError.value = 'failed'
      return false
    }
    recognition = created
    listening.value = true
    return true
  }

  /** Stops listening and forgets what was heard. */
  function cancelListening(): void {
    recognition?.abort()
    recognition = null
    listening.value = false
    heard.value = ''
    finalText = ''
  }

  function dispose(): void {
    cancelListening()
    stopSpeaking()
  }

  if (getCurrentScope()) onScopeDispose(dispose)

  return {
    canSpeak,
    canListen,
    speaking: readonly(speaking),
    listening: readonly(listening),
    heard: readonly(heard),
    recognitionError: readonly(recognitionError),
    speak,
    stopSpeaking,
    listen,
    cancelListening,
  }
}
