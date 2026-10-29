import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { withSetup } from '@/test/helpers'

import { useMandarinSpeech } from './useMandarinSpeech'

type Handler<T> = ((event: T) => void) | null

/** Web Speech recognition stand-in: keeps the last instance so a test can play what the browser hears. */
class FakeRecognition {
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
}

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

const synthesis = { speak: vi.fn(), cancel: vi.fn(), getVoices: vi.fn(() => [{ lang: 'zh-CN', name: 'Mandarin' }]) }
const scope = window as unknown as Record<string, unknown>

describe('useMandarinSpeech', () => {
  beforeEach(() => {
    FakeRecognition.last = null
    scope.webkitSpeechRecognition = FakeRecognition
    scope.speechSynthesis = synthesis
    scope.SpeechSynthesisUtterance = FakeUtterance
  })

  afterEach(() => {
    delete scope.webkitSpeechRecognition
    delete scope.speechSynthesis
    delete scope.SpeechSynthesisUtterance
  })

  it('reads a line aloud in Mandarin, slower on request', () => {
    const { result: speech } = withSetup(() => useMandarinSpeech())

    speech.speak('你好！', true)

    const utterance = synthesis.speak.mock.calls[0]![0] as FakeUtterance
    expect(utterance.text).toBe('你好！')
    expect(utterance.lang).toBe('zh-CN')
    expect(utterance.rate).toBeLessThan(0.95)
    expect(utterance.voice).toEqual({ lang: 'zh-CN', name: 'Mandarin' })
    expect(speech.speaking.value).toBe(true)
    utterance.onend?.()
    expect(speech.speaking.value).toBe(false)
  })

  it('writes down what the student says in Chinese, final and interim parts together', () => {
    const { result: speech } = withSetup(() => useMandarinSpeech())

    expect(speech.canListen).toBe(true)
    expect(speech.listen()).toBe(true)
    const recognition = FakeRecognition.last!
    expect(recognition.lang).toBe('zh-CN')
    expect(speech.listening.value).toBe(true)

    recognition.onresult?.({
      resultIndex: 0,
      results: [
        { isFinal: true, 0: { transcript: '我想' } },
        { isFinal: false, 0: { transcript: '喝茶' } },
      ],
    })
    expect(speech.heard.value).toBe('我想喝茶')

    recognition.stop()
    expect(speech.listening.value).toBe(false)
    expect(speech.heard.value).toBe('我想喝茶')
  })

  it('reports a blocked microphone and forgets what was heard on cancel', () => {
    const { result: speech } = withSetup(() => useMandarinSpeech())

    speech.listen()
    const recognition = FakeRecognition.last!
    recognition.onerror?.({ error: 'not-allowed' })
    expect(speech.recognitionError.value).toBe('denied')

    speech.listen()
    FakeRecognition.last!.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: '你好' } }] })
    speech.cancelListening()
    expect(speech.heard.value).toBe('')
    expect(speech.listening.value).toBe(false)
  })

  it('says it cannot listen when the browser has no speech recognition', () => {
    delete scope.webkitSpeechRecognition
    const { result: speech } = withSetup(() => useMandarinSpeech())

    expect(speech.canListen).toBe(false)
    expect(speech.listen()).toBe(false)
  })
})
