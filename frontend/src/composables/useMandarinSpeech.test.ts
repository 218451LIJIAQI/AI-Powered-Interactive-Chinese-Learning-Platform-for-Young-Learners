import { beforeEach, describe, expect, it, vi } from 'vitest'

import { FakeRecognition, stubSpeech } from '@/test/fakeSpeech'
import { withSetup } from '@/test/helpers'

import { useMandarinReadAloud, useMandarinSpeech } from './useMandarinSpeech'

let synthesis: ReturnType<typeof stubSpeech>

describe('useMandarinSpeech', () => {
  beforeEach(() => {
    vi.stubGlobal('webkitSpeechRecognition', FakeRecognition)
    synthesis = stubSpeech([{ lang: 'zh-CN', name: 'Mandarin' }])
  })

  it('reads a line aloud in Mandarin, slower on request', () => {
    const { result: speech } = withSetup(useMandarinReadAloud)

    speech.speak('你好！')
    const normal = synthesis.utterance(0)
    speech.speak('你好！', true)
    const slow = synthesis.utterance(1)

    expect(slow.text).toBe('你好！')
    expect(slow.lang).toBe('zh-CN')
    expect(slow.rate).toBeLessThan(normal.rate)
    expect(slow.voice).toEqual({ lang: 'zh-CN', name: 'Mandarin' })
    expect(speech.speaking.value).toBe(true)
    slow.onend?.()
    expect(speech.speaking.value).toBe(false)
  })

  it('says when it reads nothing, and leaves the line being read as it was', () => {
    const { result: speech } = withSetup(useMandarinReadAloud)
    expect(speech.speak('你好！')).toBe(true)
    const reading = synthesis.utterance()

    expect(speech.speak('\u00a0')).toBe(false)
    expect(synthesis.speak).toHaveBeenCalledOnce()
    expect(speech.speaking.value).toBe(true)
    reading.onend?.()
    expect(speech.speaking.value).toBe(false)
  })

  it('keeps speaking while the next line is read, whatever the line it cut off reports late', () => {
    const { result: speech } = withSetup(useMandarinReadAloud)
    speech.speak('第一句。')
    const first = synthesis.utterance()
    first.onstart?.()

    speech.speak('第二句。')
    const second = synthesis.utterance()
    second.onstart?.()
    // The browser ends (or fails) the line it cancelled only after the next one has started.
    first.onend?.()
    first.onerror?.()
    expect(speech.speaking.value).toBe(true)

    second.onend?.()
    expect(speech.speaking.value).toBe(false)
  })

  it('stays stopped when the browser cancels the old line and refuses its replacement', () => {
    const { result: speech } = withSetup(useMandarinReadAloud)
    expect(speech.speak('第一句。')).toBe(true)
    synthesis.speak.mockImplementationOnce(() => {
      throw new Error('Voice unavailable')
    })

    expect(speech.speak('第二句。')).toBe(false)
    expect(speech.speaking.value).toBe(false)
  })

  it('keeps the terminal state when the browser finishes during speak()', () => {
    const { result: speech } = withSetup(useMandarinReadAloud)
    synthesis.speak.mockImplementationOnce((utterance) => utterance.onend?.())
    expect(speech.speak('你好。')).toBe(true)
    expect(speech.speaking.value).toBe(false)
  })

  it('stops at once on Stop, and the stopped line cannot start the state again', () => {
    const { result: speech } = withSetup(useMandarinSpeech)
    speech.speak('你好！')
    const stopped = synthesis.utterance()

    speech.stopSpeaking()
    expect(synthesis.cancel).toHaveBeenCalledTimes(2)
    expect(speech.speaking.value).toBe(false)
    stopped.onstart?.()
    expect(speech.speaking.value).toBe(false)

    speech.speak('再见！')
    stopped.onerror?.()
    expect(speech.speaking.value).toBe(true)
    synthesis.utterance().onend?.()
    expect(speech.speaking.value).toBe(false)
  })

  it('resets a displaced reader and does not cancel active speech when an idle sibling disposes', () => {
    const first = withSetup(useMandarinReadAloud)
    const second = withSetup(useMandarinReadAloud)

    first.result.speak('你好！')
    expect(first.result.speaking.value).toBe(true)
    expect(second.result.speaking.value).toBe(false)

    // Unmounting an idle reader must not interrupt audio playing in another mounted reader.
    second.wrapper.unmount()
    expect(synthesis.cancel).toHaveBeenCalledTimes(1)
    expect(first.result.speaking.value).toBe(true)

    const third = withSetup(useMandarinReadAloud)
    third.result.speak('谢谢！')
    expect(first.result.speaking.value).toBe(false)
    expect(third.result.speaking.value).toBe(true)

    // Unmounting the active reader stops its playback.
    third.wrapper.unmount()
    expect(third.result.speaking.value).toBe(false)
    expect(synthesis.cancel).toHaveBeenCalledTimes(3)
  })

  it('writes down what the student says in Chinese, final and interim parts together', () => {
    const { result: speech } = withSetup(useMandarinSpeech)

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
    const { result: speech } = withSetup(useMandarinSpeech)

    expect(speech.listen()).toBe(true)
    const recognition = FakeRecognition.last!
    recognition.onerror?.({ error: 'not-allowed' })
    expect(speech.recognitionError.value).toBe('denied')
    expect(speech.listening.value).toBe(false)

    expect(speech.listen()).toBe(true)
    expect(FakeRecognition.last).not.toBe(recognition)
    FakeRecognition.last!.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: '你好' } }] })
    speech.cancelListening()
    expect(speech.heard.value).toBe('')
    expect(speech.listening.value).toBe(false)
  })

  it.each([
    ['service-not-allowed', 'denied'],
    ['no-speech', 'noSpeech'],
    ['network', 'failed'],
    ['audio-capture', 'failed'],
  ])('reports the recognition error %s as %s', (error, reported) => {
    const { result: speech } = withSetup(useMandarinSpeech)
    speech.listen()
    FakeRecognition.last!.onerror?.({ error })
    expect(speech.recognitionError.value).toBe(reported)
  })

  it('says it cannot listen when the browser has no speech recognition', () => {
    Reflect.deleteProperty(window, 'webkitSpeechRecognition')
    const { result: speech } = withSetup(useMandarinSpeech)

    expect(speech.canListen).toBe(false)
    expect(speech.listen()).toBe(false)
  })

  it('keeps unchanged interim results when a later result changes or disappears', () => {
    const { result: speech } = withSetup(useMandarinSpeech)
    speech.listen()
    const recognition = FakeRecognition.last!
    const first = { isFinal: false, 0: { transcript: '我想' } }
    recognition.onresult?.({
      resultIndex: 0,
      results: [first, { isFinal: false, 0: { transcript: '喝茶' } }],
    })
    recognition.onresult?.({
      resultIndex: 1,
      results: [first, { isFinal: false, 0: { transcript: '喝水' } }],
    })
    expect(speech.heard.value).toBe('我想喝水')

    recognition.onresult?.({ resultIndex: 1, results: [first] })
    expect(speech.heard.value).toBe('我想')
  })

  it('ignores late results, errors and end events from a cancelled session', () => {
    const { result: speech } = withSetup(useMandarinSpeech)
    speech.listen()
    const old = FakeRecognition.last!
    speech.cancelListening()
    old.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: '旧的' } }] })
    expect(speech.heard.value).toBe('')

    speech.listen()
    const current = FakeRecognition.last!
    current.onresult?.({ resultIndex: 0, results: [{ isFinal: false, 0: { transcript: '新的' } }] })
    old.onerror?.({ error: 'not-allowed' })
    old.onend?.()
    expect(speech.listening.value).toBe(true)
    expect(speech.recognitionError.value).toBeNull()
    expect(speech.heard.value).toBe('新的')

    speech.cancelListening()
    expect(current.aborted).toBe(true)
  })

  it('ignores recognition events after unmounting', () => {
    const { result: speech, wrapper } = withSetup(useMandarinSpeech)
    speech.listen()
    const recognition = FakeRecognition.last!
    wrapper.unmount()
    recognition.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: '迟到' } }] })
    expect(recognition.aborted).toBe(true)
    expect(speech.heard.value).toBe('')
    expect(speech.listening.value).toBe(false)
  })
})
