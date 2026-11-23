import { vi } from 'vitest'

type Handler<T> = ((event: T) => void) | null

/** Web Speech recognition stand-in: keeps the last instance so a test can play what the browser hears. */
export class FakeRecognition {
  static last: FakeRecognition | null = null
  lang = ''
  interimResults = false
  continuous = false
  maxAlternatives = 0
  onresult: Handler<{ resultIndex: number; results: { isFinal: boolean; 0: { transcript: string } }[] }> = null
  onerror: Handler<{ error: string }> = null
  onend: (() => void) | null = null
  aborted = false

  start(): void {
    FakeRecognition.last = this
  }

  stop(): void {
    this.onend?.()
  }

  abort(): void {
    this.aborted = true
  }

  /** The browser hears `text` as one final result. */
  hear(text: string): void {
    this.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: text } }] })
  }
}

/** Speech synthesis utterance stand-in: a test plays its start, end and error events itself. */
class FakeUtterance {
  lang = ''
  rate = 1
  voice: unknown = null
  onstart: (() => void) | null = null
  onend: (() => void) | null = null
  onerror: (() => void) | null = null
  readonly text: string

  constructor(text: string) {
    this.text = text
  }
}

/** Install the browser speech stand-ins and let the test drive a particular utterance or change the voices. */
export function stubSpeech(voices: { lang: string; name?: string }[] = []) {
  const events = new EventTarget()
  const synthesis = {
    speak: vi.fn<(utterance: FakeUtterance) => void>(),
    cancel: vi.fn(),
    getVoices: vi.fn(() => voices),
    addEventListener: vi.fn((event: string, listener: () => void) => events.addEventListener(event, listener)),
    removeEventListener: vi.fn((event: string, listener: () => void) => events.removeEventListener(event, listener)),
  }
  vi.stubGlobal('speechSynthesis', synthesis)
  vi.stubGlobal('SpeechSynthesisUtterance', FakeUtterance)
  return {
    ...synthesis,
    utterance: (index = -1) => synthesis.speak.mock.calls.at(index)![0],
    setVoices(next: typeof voices) {
      synthesis.getVoices.mockReturnValue(next)
      events.dispatchEvent(new Event('voiceschanged'))
    },
  }
}
