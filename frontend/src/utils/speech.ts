/**
 * Asks the browser to read Mandarin aloud (zh-CN) at normal or slow speed.
 */
const NORMAL_RATE = 0.9
const SLOW_RATE = 0.55

export type SpeechStateReason = 'started' | 'ended' | 'error' | 'cancelled'
export type SpeechStateListener = (speaking: boolean, reason: SpeechStateReason) => void

interface ActiveSpeech {
  readonly id: number
  /** Kept in module scope until completion/cancellation so Chromium does not prematurely GC the utterance. */
  readonly utterance: SpeechSynthesisUtterance
  readonly onSpeaking?: SpeechStateListener
}

/** Generation counter so late end/error callbacks from a cancelled utterance cannot clobber a newer utterance. */
let activeSpeechId = 0
let activeSpeech: ActiveSpeech | null = null

function clearActiveSpeech(reason: Exclude<SpeechStateReason, 'started'>, id?: number): void {
  if (!activeSpeech || (id !== undefined && activeSpeech.id !== id)) return
  const previous = activeSpeech
  activeSpeech = null
  previous.onSpeaking?.(false, reason)
}

export function supportsSpeechSynthesis(): boolean {
  return Boolean(window.speechSynthesis) && typeof window.SpeechSynthesisUtterance === 'function'
}

/**
 * An installed Mandarin voice, 'default' when the browser may load zh-CN on demand, or null if it cannot play
 * Mandarin. With only another Chinese voice installed, some browsers would silently substitute it for zh-CN.
 */
export function mandarinVoice(): SpeechSynthesisVoice | 'default' | null {
  if (!supportsSpeechSynthesis()) return null
  let fallback: SpeechSynthesisVoice | null = null
  let otherChinese = false
  for (const voice of window.speechSynthesis.getVoices()) {
    const tag = voice.lang.toLowerCase().replaceAll('_', '-')
    const explicitMandarin = /^(?:zh-)?cmn(?:-|$)/.test(tag)
    const regionalMandarin = /^zh(?:-han[st])?(?:-(?:cn|my|sg|tw)(?:-|$)|$)/.test(tag)
    if (explicitMandarin || regionalMandarin) {
      if (/-cn(?:-|$)/.test(tag)) return voice
      fallback ??= voice
    } else if (/^(?:zh|yue)(?:-|$)/.test(tag)) {
      otherChinese = true
    }
  }
  return fallback ?? (otherChinese ? null : 'default')
}

/**
 * Reads `text` aloud, cancelling earlier speech. Returns false for empty text, unavailable speech or a known Chinese
 * voice list without Mandarin. `onSpeaking` hears true on start and false at most once on end, failure or cancellation.
 */
export function speakChinese(text: string, slow = false, onSpeaking?: SpeechStateListener): boolean {
  if (!text.trim()) return false
  const voice = mandarinVoice()
  if (!voice) return false
  const speechId = ++activeSpeechId
  clearActiveSpeech('cancelled')
  if (activeSpeechId !== speechId) return false
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = slow ? SLOW_RATE : NORMAL_RATE
  if (voice !== 'default') utterance.voice = voice
  activeSpeech = { id: speechId, utterance, onSpeaking }
  utterance.onstart = () => {
    if (activeSpeech?.id === speechId) activeSpeech.onSpeaking?.(true, 'started')
  }
  utterance.onend = () => {
    clearActiveSpeech('ended', speechId)
  }
  utterance.onerror = () => {
    clearActiveSpeech('error', speechId)
  }
  try {
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utterance)
  } catch {
    clearActiveSpeech('error', speechId)
    return false
  }
  return true
}

/** Stops reading (speech keeps going after a component is gone unless it is cancelled). */
export function stopSpeaking(): void {
  const stopped = ++activeSpeechId
  clearActiveSpeech('cancelled')
  if (activeSpeechId === stopped && supportsSpeechSynthesis()) window.speechSynthesis.cancel()
}
