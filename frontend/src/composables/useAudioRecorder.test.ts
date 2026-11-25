import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, watch } from 'vue'

import { FakeMediaRecorder } from '@/test/fakeMediaRecorder'
import { deferred } from '@/test/helpers'

import { MAX_RECORDING_SECONDS, recordingFileName, useAudioRecorder } from './useAudioRecorder'

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
})

describe('useAudioRecorder', () => {
  it('reports unsupported (file-upload fallback) without getUserMedia or MediaRecorder', () => {
    const noDevices = setup({ getUserMedia: undefined })
    expect(noDevices.recorder.supported).toBe(false)

    const noRecorder = setup({ MediaRecorder: undefined })
    expect(noRecorder.recorder.supported).toBe(false)
  })

  it.each([undefined, () => false])(
    'keeps the microphone closed when WebM support cannot be confirmed',
    async (isTypeSupported) => {
      const { recorder, getUserMedia } = setup({ MediaRecorder: { isTypeSupported } })
      expect(recorder.supported).toBe(false)
      await recorder.start()
      expect(getUserMedia).not.toHaveBeenCalled()
      expect(recorder.status.value).toBe('error')
    },
  )

  it('records, stops every track and exposes a WEBM file with a preview URL', async () => {
    const { recorder, track, getUserMedia } = setup()
    expect(recorder.supported).toBe(true)

    await recorder.start()
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

  it('keeps the recording when Stop is pressed again before the recorder has delivered its data', async () => {
    // A browser's recorder is inactive as soon as it is stopped; its data and `stop` event come a moment later.
    class BrowserRecorder extends FakeMediaRecorder {
      override stop(): void {
        this.state = 'inactive'
        queueMicrotask(() => super.stop())
      }
    }
    const { recorder } = setup({ MediaRecorder: BrowserRecorder })
    await recorder.start()
    const statuses: string[] = []
    watch(recorder.status, (status) => statuses.push(status), { flush: 'sync' })

    recorder.stop()
    recorder.stop()
    await Promise.resolve()

    expect(statuses).toEqual(['recorded'])
    expect(recorder.recording.value?.size).toBeGreaterThan(0)
  })

  it('stops automatically at the maximum length', async () => {
    const { recorder } = setup()
    await recorder.start()
    vi.advanceTimersByTime(MAX_RECORDING_SECONDS * 1_000 + 250)
    expect(recorder.status.value).toBe('recorded')
    expect(recorder.elapsedSeconds.value).toBe(MAX_RECORDING_SECONDS)
  })

  it('releases the microphone on a recorder error and discards the final partial data', async () => {
    const { recorder, track } = setup()
    await recorder.start()
    const active = FakeMediaRecorder.instances[0]!
    active.state = 'inactive'
    active.onerror?.()
    active.stop()

    expect(recorder.status.value).toBe('error')
    expect(recorder.recording.value).toBeNull()
    expect(recorder.previewUrl.value).toBeNull()
    expect(track.stop).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('reports a denied microphone permission so the page can offer a file upload', async () => {
    const { recorder } = setup({
      getUserMedia: () => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })),
    })
    await recorder.start()
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
    const microphone = deferred<MediaStream>()
    const { recorder, scope } = setup({ getUserMedia: () => microphone.promise })

    const started = recorder.start()
    expect(recorder.status.value).toBe('requesting')
    scope.stop()
    microphone.resolve(stream)

    await started
    expect(track.stop).toHaveBeenCalled()
    expect(FakeMediaRecorder.instances).toHaveLength(0)
  })

  it('calls off a start that is still waiting for the microphone when it is stopped', async () => {
    const { track, stream } = fakeStream()
    const microphone = deferred<MediaStream>()
    const { recorder } = setup({ getUserMedia: () => microphone.promise })

    const started = recorder.start()
    recorder.stop()
    expect(recorder.status.value).toBe('idle')
    microphone.resolve(stream)

    await started
    expect(track.stop).toHaveBeenCalled()
    expect(FakeMediaRecorder.instances).toHaveLength(0)
    expect(recorder.status.value).toBe('idle')
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

describe('recordingFileName', () => {
  it('stamps the recording with the local date and time', () => {
    expect(recordingFileName(new Date(2026, 4, 18, 3, 5))).toBe('pronunciation-20260518-0305.webm')
  })
})
