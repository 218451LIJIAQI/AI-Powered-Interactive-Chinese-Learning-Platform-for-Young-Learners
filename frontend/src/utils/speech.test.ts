import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { canSpeak, speakChinese, stopSpeaking } from './speech'

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

const voices = [
  { lang: 'en-US', name: 'English' },
  { lang: 'zh-TW', name: 'Taiwan' },
  { lang: 'zh-CN', name: 'Mandarin' },
]
const synthesis = { speak: vi.fn(), cancel: vi.fn(), getVoices: vi.fn(() => voices) }
const scope = window as unknown as Record<string, unknown>

function lastUtterance(): FakeUtterance {
  return synthesis.speak.mock.calls.at(-1)![0] as FakeUtterance
}

describe('speech', () => {
  beforeEach(() => {
    scope.speechSynthesis = synthesis
    scope.SpeechSynthesisUtterance = FakeUtterance
  })

  afterEach(() => {
    delete scope.speechSynthesis
    delete scope.SpeechSynthesisUtterance
  })

  it('reads Chinese with a zh-CN voice at the normal or the slow rate, after cancelling earlier speech', () => {
    expect(canSpeak()).toBe(true)

    expect(speakChinese('你好')).toBe(true)
    expect(synthesis.cancel).toHaveBeenCalledBefore(synthesis.speak)
    expect(lastUtterance()).toMatchObject({ text: '你好', lang: 'zh-CN', rate: 0.9, voice: voices[2] })

    speakChinese('你好', true)
    expect(lastUtterance().rate).toBe(0.55)
  })

  it('falls back to any Chinese voice, then to the browser default', () => {
    synthesis.getVoices.mockReturnValueOnce([voices[0]!, voices[1]!])
    speakChinese('谢谢')
    expect(lastUtterance().voice).toBe(voices[1])

    synthesis.getVoices.mockReturnValueOnce([voices[0]!])
    speakChinese('谢谢')
    expect(lastUtterance().voice).toBeNull()
  })

  it('ignores empty text', () => {
    expect(speakChinese('  ')).toBe(false)
    expect(synthesis.speak).not.toHaveBeenCalled()
  })

  it('reports when reading starts and stops', () => {
    const onSpeaking = vi.fn()
    speakChinese('再见', false, onSpeaking)

    lastUtterance().onstart?.()
    lastUtterance().onend?.()
    lastUtterance().onerror?.()
    expect(onSpeaking.mock.calls).toEqual([[true], [false], [false]])
  })

  it('does nothing where the browser has no speech synthesis', () => {
    delete scope.speechSynthesis

    expect(canSpeak()).toBe(false)
    expect(speakChinese('你好')).toBe(false)
    expect(() => stopSpeaking()).not.toThrow()
  })
})
