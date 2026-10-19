import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

import { classifyRecorderError, useAudioRecorder, type AudioRecorderEnvironment } from './useAudioRecorder'

class FakeRecorder {
  static instances: FakeRecorder[] = []
  static isTypeSupported = vi.fn((type: string) => type === 'audio/webm')
  state = 'inactive'
  mimeType: string
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  options: { mimeType?: string } | undefined

  constructor(_stream: MediaStream, options?: { mimeType?: string }) {
    this.options = options
    this.mimeType = options?.mimeType ?? ''
    FakeRecorder.instances.push(this)
  }

  start(): void {
    this.state = 'recording'
  }

  stop(): void {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['voice'], { type: 'audio/webm;codecs=opus' }) })
    this.onstop?.()
  }
}

function fakeStream() {
  const track = { stop: vi.fn() }
  return { track, stream: { getTracks: () => [track] } as unknown as MediaStream }
}

function setup(envOverrides: Partial<AudioRecorderEnvironment> = {}) {
  const { track, stream } = fakeStream()
  const getUserMedia = vi.fn(() => Promise.resolve(stream))
  const urlApi = { createObjectURL: vi.fn(() => 'blob:recording-1'), revokeObjectURL: vi.fn() }
  let clock = 1_000
  const scope = effectScope()
  const recorder = scope.run(() =>
    useAudioRecorder({
      env: { mediaDevices: { getUserMedia }, MediaRecorder: FakeRecorder, ...envOverrides },
      urlApi,
      maxSeconds: 5,
      now: () => clock,
    }),
  )!
  return {
    recorder,
    track,
    getUserMedia,
    urlApi,
    scope,
    advance(ms: number) {
      clock += ms
      vi.advanceTimersByTime(ms)
    },
  }
}

beforeEach(() => {
  vi.useFakeTimers()
  FakeRecorder.instances = []
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useAudioRecorder', () => {
  it('reports unsupported (file-upload fallback) without getUserMedia or MediaRecorder', async () => {
    const noDevices = setup({ mediaDevices: undefined })
    expect(noDevices.recorder.supported).toBe(false)
    expect(noDevices.recorder.status.value).toBe('unsupported')
    await expect(noDevices.recorder.start()).resolves.toBe(false)

    const noRecorder = setup({ MediaRecorder: null })
    expect(noRecorder.recorder.supported).toBe(false)
  })

  it('records, stops every track and exposes a WEBM file with a preview URL', async () => {
    const { recorder, track, getUserMedia, urlApi, advance } = setup()
    expect(recorder.supported).toBe(true)
    expect(recorder.mimeType).toBe('audio/webm')

    await expect(recorder.start()).resolves.toBe(true)
    expect(getUserMedia).toHaveBeenCalledWith({ audio: true })
    expect(recorder.status.value).toBe('recording')
    expect(FakeRecorder.instances[0]?.options).toEqual({ mimeType: 'audio/webm' })

    advance(2_000)
    expect(recorder.elapsedSeconds.value).toBe(2)

    recorder.stop()
    expect(recorder.status.value).toBe('recorded')
    expect(track.stop).toHaveBeenCalled()
    expect(recorder.recording.value?.type).toBe('audio/webm')
    expect(recorder.recording.value?.name).toMatch(/^pronunciation-\d{8}-\d{4}\.webm$/)
    expect(recorder.previewUrl.value).toBe('blob:recording-1')
    expect(urlApi.createObjectURL).toHaveBeenCalledWith(recorder.recording.value)
  })

  it('stops automatically at the maximum length', async () => {
    const { recorder, advance } = setup()
    await recorder.start()
    advance(5_250)
    expect(recorder.status.value).toBe('recorded')
    expect(recorder.elapsedSeconds.value).toBe(5)
  })

  it('reports a denied microphone permission so the page can offer a file upload', async () => {
    const { recorder } = setup({
      mediaDevices: { getUserMedia: () => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })) },
    })
    await expect(recorder.start()).resolves.toBe(false)
    expect(recorder.status.value).toBe('denied')
    expect(recorder.error.value).toBe('denied')
  })

  it('re-record discards the previous recording and revokes its URL', async () => {
    const { recorder, urlApi } = setup()
    await recorder.start()
    recorder.stop()
    recorder.reset()
    expect(urlApi.revokeObjectURL).toHaveBeenCalledWith('blob:recording-1')
    expect(recorder.recording.value).toBeNull()
    expect(recorder.status.value).toBe('idle')
  })

  it('releases the microphone when its scope is disposed mid-recording', async () => {
    const { recorder, track, scope } = setup()
    await recorder.start()
    scope.stop()
    expect(track.stop).toHaveBeenCalled()
    expect(recorder.recording.value).toBeNull()
  })

  it('stops a microphone stream that arrives after the scope was disposed', async () => {
    const { track, stream } = fakeStream()
    let grant: (value: MediaStream) => void = () => undefined
    const { recorder, scope } = setup({
      mediaDevices: { getUserMedia: () => new Promise<MediaStream>((resolve) => (grant = resolve)) },
    })

    const started = recorder.start()
    expect(recorder.status.value).toBe('requesting')
    scope.stop()
    grant(stream)

    await expect(started).resolves.toBe(false)
    expect(track.stop).toHaveBeenCalled()
    expect(FakeRecorder.instances).toHaveLength(0)
  })
})

describe('classifyRecorderError', () => {
  it('maps getUserMedia errors', () => {
    expect(classifyRecorderError({ name: 'SecurityError' })).toBe('denied')
    expect(classifyRecorderError({ name: 'NotFoundError' })).toBe('noDevice')
    expect(classifyRecorderError(new Error('boom'))).toBe('failed')
  })
})
