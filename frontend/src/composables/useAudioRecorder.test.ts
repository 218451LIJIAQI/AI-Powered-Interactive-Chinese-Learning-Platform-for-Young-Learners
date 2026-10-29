import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

import { MAX_RECORDING_SECONDS, RECORDER_MIME_TYPE } from '@/utils/practiceUpload'

import { useAudioRecorder } from './useAudioRecorder'

/**
 * MediaRecorder stand-in for the recorder tests: a browser that records RECORDER_MIME_TYPE. It keeps every instance
 * (reset `instances` before each test); stop() delivers one chunk of audio and then fires `stop`.
 */
class FakeMediaRecorder {
  static instances: FakeMediaRecorder[] = []
  static isTypeSupported = (type: string): boolean => type === RECORDER_MIME_TYPE
  state = 'inactive'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  readonly options: { mimeType?: string } | undefined

  constructor(_stream: MediaStream, options?: { mimeType?: string }) {
    this.options = options
    FakeMediaRecorder.instances.push(this)
  }

  start(): void {
    this.state = 'recording'
  }

  stop(): void {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['voice'], { type: RECORDER_MIME_TYPE }) })
    this.onstop?.()
  }
}

function fakeStream() {
  const track = { stop: vi.fn() }
  return { track, stream: { getTracks: () => [track] } as unknown as MediaStream }
}

// jsdom has no object URLs; each test file runs in its own environment, so the stub needs no restore.
const urlApi = { createObjectURL: vi.fn(() => 'blob:recording-1'), revokeObjectURL: vi.fn() }
Object.assign(URL, urlApi)

/** A browser with these recorder APIs (`undefined` leaves one out); the recorder is created in its own scope. */
function setup(browser: { getUserMedia?: () => Promise<MediaStream>; MediaRecorder?: unknown } = {}) {
  const { track, stream } = fakeStream()
  const getUserMedia = vi.fn(() => Promise.resolve(stream))
  const apis = { getUserMedia, MediaRecorder: FakeMediaRecorder, ...browser }
  vi.stubGlobal('navigator', { mediaDevices: apis.getUserMedia ? { getUserMedia: apis.getUserMedia } : undefined })
  vi.stubGlobal('MediaRecorder', apis.MediaRecorder)
  const scope = effectScope()
  const recorder = scope.run(() => useAudioRecorder())!
  return { recorder, track, getUserMedia, scope }
}

beforeEach(() => {
  vi.useFakeTimers()
  FakeMediaRecorder.instances = []
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useAudioRecorder', () => {
  it('reports unsupported (file-upload fallback) without getUserMedia or MediaRecorder', async () => {
    const noDevices = setup({ getUserMedia: undefined })
    expect(noDevices.recorder.supported).toBe(false)
    await expect(noDevices.recorder.start()).resolves.toBe(false)

    const noRecorder = setup({ MediaRecorder: undefined })
    expect(noRecorder.recorder.supported).toBe(false)

    // Browsers that cannot record WEBM/Opus (none of the supported ones) get the file upload too.
    const noWebm = setup({
      MediaRecorder: Object.assign(class extends FakeMediaRecorder {}, { isTypeSupported: () => false }),
    })
    expect(noWebm.recorder.supported).toBe(false)
  })

  it('records, stops every track and exposes a WEBM file with a preview URL', async () => {
    const { recorder, track, getUserMedia } = setup()
    expect(recorder.supported).toBe(true)

    await expect(recorder.start()).resolves.toBe(true)
    expect(getUserMedia).toHaveBeenCalledWith({ audio: true })
    expect(recorder.status.value).toBe('recording')
    expect(FakeMediaRecorder.instances[0]?.options).toEqual({ mimeType: 'audio/webm;codecs=opus' })

    vi.advanceTimersByTime(2_000)
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
    const { recorder } = setup()
    await recorder.start()
    vi.advanceTimersByTime(MAX_RECORDING_SECONDS * 1_000 + 250)
    expect(recorder.status.value).toBe('recorded')
    expect(recorder.elapsedSeconds.value).toBe(MAX_RECORDING_SECONDS)
  })

  it('reports a denied microphone permission so the page can offer a file upload', async () => {
    const { recorder } = setup({
      getUserMedia: () => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })),
    })
    await expect(recorder.start()).resolves.toBe(false)
    expect(recorder.status.value).toBe('denied')
  })

  it('re-record discards the previous recording and revokes its URL', async () => {
    const { recorder } = setup()
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
      getUserMedia: () => new Promise<MediaStream>((resolve) => (grant = resolve)),
    })

    const started = recorder.start()
    expect(recorder.status.value).toBe('requesting')
    scope.stop()
    grant(stream)

    await expect(started).resolves.toBe(false)
    expect(track.stop).toHaveBeenCalled()
    expect(FakeMediaRecorder.instances).toHaveLength(0)
  })
})

describe('microphone errors', () => {
  it('tells a refused permission apart from any other getUserMedia failure', async () => {
    const statusAfter = async (reason: Error) => {
      const { recorder } = setup({ getUserMedia: () => Promise.reject(reason) })
      await recorder.start()
      return recorder.status.value
    }
    expect(await statusAfter(Object.assign(new Error('no'), { name: 'SecurityError' }))).toBe('denied')
    expect(await statusAfter(Object.assign(new Error('no'), { name: 'NotFoundError' }))).toBe('error')
    expect(await statusAfter(new Error('boom'))).toBe('error')
  })
})
