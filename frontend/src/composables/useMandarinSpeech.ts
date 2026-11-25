import { computed, onDeactivated, onScopeDispose, readonly, ref, shallowRef } from 'vue'

import { mandarinVoice, speakChinese, stopSpeaking as cancelSpeech, type SpeechStateListener } from '@/utils/speech'

/** The parts of the Web Speech API recognition object used here (it is still prefixed in Chrome and Edge). */
interface RecognitionResultLike {
  readonly 0: { readonly transcript: string }
}

interface RecognitionEventLike {
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

/** The Web Speech API errors told apart; any other is `failed` (and `aborted`, a session cancelled here, is ignored). */
const RECOGNITION_ERRORS: ReadonlyMap<string, RecognitionError> = new Map<string, RecognitionError>([
  ['not-allowed', 'denied'],
  ['service-not-allowed', 'denied'],
  ['no-speech', 'noSpeech'],
])

/** Browsers may list voices after the first render, or change them when the user installs another voice. */
function useMandarinVoices() {
  const voice = shallowRef(mandarinVoice())
  const synthesis = window.speechSynthesis
  const refresh = () => {
    voice.value = mandarinVoice()
  }
  synthesis?.addEventListener?.('voiceschanged', refresh)
  onScopeDispose(() => synthesis?.removeEventListener?.('voiceschanged', refresh))
  return {
    canSpeak: computed(() => voice.value !== null),
    hasInstalledVoice: computed(() => voice.value !== null && voice.value !== 'default'),
  }
}

/** Reads Mandarin aloud and tracks whether the current line is still playing. */
export function useMandarinReadAloud() {
  const voices = useMandarinVoices()
  const speaking = ref(false)

  /** Reads `text` aloud; false when nothing is read (no speech in this browser, or nothing but spaces to read). */
  function speak(text: string, slow = false, onSpeaking?: SpeechStateListener): boolean {
    let finished = false
    const accepted = speakChinese(text, slow, (playing, reason) => {
      finished = !playing
      speaking.value = playing
      onSpeaking?.(playing, reason)
    })
    // Queued speech can be stopped before its first event. A synchronous end or error is already final.
    if (accepted && !finished) speaking.value = true
    return accepted
  }

  function stopSpeaking(): void {
    if (speaking.value) cancelSpeech()
  }

  onDeactivated(stopSpeaking)
  onScopeDispose(stopSpeaking)
  return { ...voices, speaking: readonly(speaking), speak, stopSpeaking }
}

/**
 * Mandarin speech in the browser: Xiaomei's lines read aloud (utils/speech.ts, normal or slow) with a `speaking`
 * state, and the student's spoken reply written down with the Web Speech API (`lang = zh-CN`) where the browser
 * supports it. The voice never leaves the browser through this app: only the text it produces is sent, like a typed
 * reply.
 */
export function useMandarinSpeech() {
  const browser = window as unknown as {
    SpeechRecognition?: RecognitionConstructor
    webkitSpeechRecognition?: RecognitionConstructor
  }
  const Recognition = browser.SpeechRecognition ?? browser.webkitSpeechRecognition ?? null
  const canListen = Recognition !== null

  const recognition = shallowRef<RecognitionLike | null>(null)
  /** What was heard so far: the final part followed by the part the browser may still change. */
  const heard = ref('')
  const recognitionError = ref<RecognitionError | null>(null)
  onDeactivated(cancelListening)
  onScopeDispose(cancelListening)
  const voice = useMandarinReadAloud()

  /** Starts listening; `heard` fills in as the student speaks. Returns false when the browser cannot listen. */
  function listen(): boolean {
    if (!Recognition || recognition.value) return false
    // Opening the microphone intentionally silences any active read-aloud across the page.
    cancelSpeech()
    heard.value = ''
    recognitionError.value = null
    const created = new Recognition()
    created.lang = 'zh-CN'
    created.interimResults = true
    created.continuous = true
    created.maxAlternatives = 1
    created.onresult = (event) => {
      if (recognition.value !== created) return
      // Each event carries the full session, including unchanged and revised interim results.
      heard.value = Array.from(event.results, (result) => result[0]?.transcript ?? '')
        .join('')
        .trim()
    }
    created.onerror = (event) => {
      if (recognition.value !== created || event.error === 'aborted') return
      recognitionError.value = RECOGNITION_ERRORS.get(event.error) ?? 'failed'
      recognition.value = null
      heard.value = ''
      try {
        created.abort()
      } catch {
        // Browser recognition instances may throw InvalidStateError if already stopped.
      }
    }
    created.onend = () => {
      if (recognition.value === created) recognition.value = null
    }
    // Assign before start() so synchronous onerror/onend callbacks from start() observe this active instance.
    recognition.value = created
    try {
      created.start()
    } catch {
      if (recognition.value === created) recognition.value = null
      if (recognitionError.value === null) recognitionError.value = 'failed'
      return false
    }
    return recognition.value === created
  }

  /** Stops listening and forgets what was heard. */
  function cancelListening(): void {
    const active = recognition.value
    recognition.value = null
    heard.value = ''
    try {
      active?.abort()
    } catch {
      // Browser recognition instances may throw InvalidStateError if already stopped during teardown.
    }
  }

  return {
    ...voice,
    canListen,
    listening: computed(() => recognition.value !== null),
    heard: readonly(heard),
    recognitionError: readonly(recognitionError),
    listen,
    cancelListening,
  }
}
