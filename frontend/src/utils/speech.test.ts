import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { stubSpeech } from '@/test/fakeSpeech'

import { mandarinVoice, speakChinese, stopSpeaking, supportsSpeechSynthesis } from './speech'

const voices = [
  { lang: 'en-US', name: 'English' },
  { lang: 'zh-TW', name: 'Taiwan' },
  { lang: 'zh-CN', name: 'Mandarin' },
]
let synthesis: ReturnType<typeof stubSpeech>

describe('speech', () => {
  beforeEach(() => {
    synthesis = stubSpeech(voices)
  })

  afterEach(() => {
    stopSpeaking()
  })

  it('reads Chinese with a zh-CN voice at the normal or the slow rate, after cancelling earlier speech', () => {
    expect(supportsSpeechSynthesis()).toBe(true)

    expect(speakChinese('你好')).toBe(true)
    expect(synthesis.cancel).toHaveBeenCalledBefore(synthesis.speak)
    expect(synthesis.utterance()).toMatchObject({ text: '你好', lang: 'zh-CN', rate: 0.9, voice: voices[2] })

    speakChinese('你好', true)
    expect(synthesis.utterance().rate).toBe(0.55)
  })

  it('falls back to another Mandarin voice, then to the browser default when no Chinese voice is listed', () => {
    synthesis.getVoices.mockReturnValueOnce([voices[0]!, voices[1]!])
    speakChinese('谢谢')
    expect(synthesis.utterance().voice).toBe(voices[1])

    synthesis.getVoices.mockReturnValueOnce([voices[0]!])
    speakChinese('谢谢')
    expect(synthesis.utterance().voice).toBeNull()
  })

  it('prefers mainland Mandarin regardless of tag casing or platform separators', () => {
    const mainland = { lang: 'ZH_hans_CN', name: 'Mainland Mandarin' }
    synthesis.getVoices.mockReturnValue([voices[1]!, mainland])
    expect(speakChinese('你好')).toBe(true)
    expect(synthesis.utterance().voice).toBe(mainland)
  })

  it.each(['cmn', 'cmn_Hant_TW', 'zh-cmn-Hans', 'zh-TW', 'zh-SG'])(
    'recognises %s as Mandarin when a Cantonese voice is listed first',
    (lang) => {
      const mandarin = { lang }
      synthesis.getVoices.mockReturnValue([{ lang: 'zh-HK' }, mandarin])
      expect(mandarinVoice()).toBe(mandarin)
      expect(speakChinese('谢谢')).toBe(true)
      expect(synthesis.utterance().voice).toBe(mandarin)
    },
  )

  it.each(['ZH_HK', 'zh-Hant-HK', 'zh-MO', 'yue', 'yue-HK', 'zh-yue'])(
    'does not offer or play %s as Mandarin, even as the browser default',
    (lang) => {
      synthesis.getVoices.mockReturnValue([{ lang }])
      expect(mandarinVoice()).toBeNull()
      expect(speakChinese('谢谢')).toBe(false)
      expect(synthesis.speak).not.toHaveBeenCalled()
    },
  )

  it('distinguishes an installed voice from on-demand playback', () => {
    expect(mandarinVoice()).toBe(voices[2])

    synthesis.getVoices.mockReturnValueOnce([voices[0]!, voices[1]!])
    expect(mandarinVoice()).toBe(voices[1])

    // No Chinese voice, or a list the browser has not filled in yet.
    synthesis.getVoices.mockReturnValueOnce([voices[0]!])
    expect(mandarinVoice()).toBe('default')
    synthesis.getVoices.mockReturnValueOnce([])
    expect(mandarinVoice()).toBe('default')
  })

  it('ignores empty text', () => {
    expect(speakChinese('  ')).toBe(false)
    expect(synthesis.speak).not.toHaveBeenCalled()
  })

  it('reports when reading starts and emits the terminal stop state at most once', () => {
    const onSpeaking = vi.fn()
    speakChinese('再见', false, onSpeaking)

    synthesis.utterance().onstart?.()
    synthesis.utterance().onend?.()
    synthesis.utterance().onerror?.()
    expect(onSpeaking.mock.calls).toEqual([
      [true, 'started'],
      [false, 'ended'],
    ])
  })

  it('cancels playback via stopSpeaking, notifies the active listener and ignores late events', () => {
    const onSpeaking = vi.fn()
    speakChinese('你好', false, onSpeaking)
    const utterance = synthesis.utterance()
    utterance.onstart?.()

    stopSpeaking()
    expect(synthesis.cancel).toHaveBeenCalledTimes(2)
    expect(onSpeaking.mock.calls).toEqual([
      [true, 'started'],
      [false, 'cancelled'],
    ])

    utterance.onstart?.()
    utterance.onend?.()
    utterance.onerror?.()
    expect(onSpeaking).toHaveBeenCalledTimes(2)
  })

  it('notifies a displaced listener with cancelled and suppresses its late callbacks when new speech starts', () => {
    const firstListener = vi.fn()
    const secondListener = vi.fn()

    speakChinese('第一句', false, firstListener)
    const firstUtterance = synthesis.utterance()
    firstUtterance.onstart?.()

    speakChinese('第二句', false, secondListener)
    const secondUtterance = synthesis.utterance()
    expect(firstListener.mock.calls).toEqual([
      [true, 'started'],
      [false, 'cancelled'],
    ])

    firstUtterance.onend?.()
    firstUtterance.onerror?.()
    expect(firstListener).toHaveBeenCalledTimes(2)

    secondUtterance.onstart?.()
    secondUtterance.onend?.()
    expect(secondListener.mock.calls).toEqual([
      [true, 'started'],
      [false, 'ended'],
    ])
  })

  it('does nothing where the browser has no speech synthesis', () => {
    Reflect.deleteProperty(window, 'speechSynthesis')

    expect(supportsSpeechSynthesis()).toBe(false)
    expect(mandarinVoice()).toBeNull()
    expect(speakChinese('你好')).toBe(false)
    expect(() => stopSpeaking()).not.toThrow()
  })

  it('keeps speech started by the previous reader’s cancellation handler', () => {
    speakChinese('第一句', false, (_, reason) => {
      if (reason === 'cancelled') speakChinese('第二句')
    })
    stopSpeaking()
    expect(synthesis.utterance().text).toBe('第二句')
    expect(synthesis.cancel.mock.invocationCallOrder.at(-1)).toBeLessThan(
      synthesis.speak.mock.invocationCallOrder.at(-1)!,
    )
  })
})
