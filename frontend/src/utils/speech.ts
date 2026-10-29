/**
 * "Listen" buttons: Mandarin read aloud by the browser's speech synthesis (zh-CN), at normal or slow speed. Nothing
 * is recorded or sent anywhere. Where the browser has no speech synthesis, the buttons are hidden.
 */
const NORMAL_RATE = 0.9
const SLOW_RATE = 0.55

export function canSpeak(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window
}

/** A Mandarin voice when the device has one (the browser's default otherwise). */
function chineseVoice(): SpeechSynthesisVoice | null {
  const voices = window.speechSynthesis.getVoices()
  return (
    voices.find((voice) => voice.lang === 'zh-CN') ??
    voices.find((voice) => voice.lang.toLowerCase().startsWith('zh')) ??
    null
  )
}

/**
 * Reads `text` aloud, cancelling whatever was still being read; false when speech is not available or there is nothing
 * to read. `onSpeaking` hears true when reading starts and false when it ends or fails.
 */
export function speakChinese(text: string, slow = false, onSpeaking?: (speaking: boolean) => void): boolean {
  if (!canSpeak() || !text.trim()) return false
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = slow ? SLOW_RATE : NORMAL_RATE
  const voice = chineseVoice()
  if (voice) utterance.voice = voice
  if (onSpeaking) {
    utterance.onstart = () => onSpeaking(true)
    utterance.onend = () => onSpeaking(false)
    utterance.onerror = () => onSpeaking(false)
  }
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utterance)
  return true
}

/** Stops reading (speech keeps going after a component is gone unless it is cancelled). */
export function stopSpeaking(): void {
  if (canSpeak()) window.speechSynthesis.cancel()
}
