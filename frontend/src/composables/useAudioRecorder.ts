import { computed, getCurrentScope, onScopeDispose, ref, shallowRef } from 'vue'

import {
  baseMimeType,
  browserRecorderEnvironment,
  detectRecorderSupport,
  MAX_RECORDING_SECONDS,
  pickRecorderMimeType,
  recordingFileName,
  type RecorderEnvironment,
} from '@/utils/practiceUpload'

export type AudioRecorderStatus = 'idle' | 'requesting' | 'recording' | 'recorded' | 'unsupported' | 'denied' | 'error'
export type AudioRecorderError = 'unsupported' | 'denied' | 'noDevice' | 'failed' | 'empty'

interface MediaRecorderLike {
  readonly state: string
  readonly mimeType?: string
  ondataavailable: ((event: { data: Blob }) => void) | null
  onstop: (() => void) | null
  start(timeslice?: number): void
  stop(): void
}

interface MediaRecorderConstructor {
  new (stream: MediaStream, options?: { mimeType?: string }): MediaRecorderLike
  isTypeSupported?: (type: string) => boolean
}

export interface AudioRecorderEnvironment extends Omit<RecorderEnvironment, 'MediaRecorder'> {
  MediaRecorder?: MediaRecorderConstructor | null
}

export interface AudioRecorderOptions {
  /** Injected for tests; defaults to `navigator.mediaDevices` / `window.MediaRecorder`. */
  env?: AudioRecorderEnvironment
  urlApi?: { createObjectURL?: (blob: Blob) => string; revokeObjectURL?: (url: string) => void }
  maxSeconds?: number
  now?: () => number
}

const DENIED_ERRORS = new Set(['NotAllowedError', 'SecurityError', 'PermissionDeniedError'])
const NO_DEVICE_ERRORS = new Set(['NotFoundError', 'DevicesNotFoundError', 'OverconstrainedError', 'NotReadableError'])

/** Maps a getUserMedia rejection to a recorder error code. */
export function classifyRecorderError(reason: unknown): AudioRecorderError {
  const name = (reason as { name?: unknown } | null)?.name
  if (typeof name === 'string' && DENIED_ERRORS.has(name)) return 'denied'
  if (typeof name === 'string' && NO_DEVICE_ERRORS.has(name)) return 'noDevice'
  return 'failed'
}

/**
 * Browser microphone recording with MediaRecorder (feature-detected). The recording is kept locally as a File
 * (WEBM) with an object URL for preview; every microphone track is stopped as soon as recording ends.
 * When recording is unavailable or permission is denied, callers fall back to a file input.
 */
export function useAudioRecorder(options: AudioRecorderOptions = {}) {
  const env: AudioRecorderEnvironment =
    options.env ?? (browserRecorderEnvironment() as unknown as AudioRecorderEnvironment)
  const urlApi = options.urlApi ?? (typeof URL === 'undefined' ? {} : URL)
  const maxSeconds = options.maxSeconds ?? MAX_RECORDING_SECONDS
  const now = options.now ?? (() => Date.now())

  const supported = detectRecorderSupport(env)
  const mimeType = supported ? pickRecorderMimeType(env.MediaRecorder?.isTypeSupported?.bind(env.MediaRecorder)) : null

  const status = ref<AudioRecorderStatus>(supported ? 'idle' : 'unsupported')
  const error = ref<AudioRecorderError | null>(supported ? null : 'unsupported')
  const elapsedSeconds = ref(0)
  const recording = shallowRef<File | null>(null)
  const previewUrl = ref<string | null>(null)

  let stream: MediaStream | null = null
  let recorder: MediaRecorderLike | null = null
  let chunks: Blob[] = []
  let timer: ReturnType<typeof setInterval> | null = null
  let startedAt = 0
  /** Incremented by every start() and abort, so a getUserMedia result that arrives late can be discarded. */
  let startAttempt = 0

  function stopTracks(): void {
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
  }

  function clearTimer(): void {
    if (timer !== null) clearInterval(timer)
    timer = null
  }

  function revokePreview(): void {
    if (previewUrl.value && typeof urlApi.revokeObjectURL === 'function') urlApi.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }

  function clearRecording(): void {
    revokePreview()
    recording.value = null
    elapsedSeconds.value = 0
  }

  function updateElapsed(): void {
    elapsedSeconds.value = Math.max(0, Math.floor((now() - startedAt) / 1000))
  }

  function tick(): void {
    updateElapsed()
    if (elapsedSeconds.value >= maxSeconds) stop()
  }

  function finish(): void {
    clearTimer()
    stopTracks()
    const finished = recorder
    recorder = null
    const parts = chunks
    chunks = []
    const type = baseMimeType(mimeType || finished?.mimeType) || 'audio/webm'
    if (!parts.length) {
      status.value = 'error'
      error.value = 'empty'
      return
    }
    const file = new File(parts, recordingFileName(type), { type })
    recording.value = file
    previewUrl.value = typeof urlApi.createObjectURL === 'function' ? urlApi.createObjectURL(file) : null
    error.value = null
    status.value = 'recorded'
  }

  async function start(): Promise<boolean> {
    if (!supported) {
      status.value = 'unsupported'
      error.value = 'unsupported'
      return false
    }
    if (status.value === 'recording' || status.value === 'requesting') return false

    clearRecording()
    error.value = null
    status.value = 'requesting'
    const attempt = ++startAttempt

    try {
      const acquired = await env.mediaDevices!.getUserMedia!({ audio: true })
      if (attempt !== startAttempt) {
        // reset() or dispose() ran while the permission prompt was open: release the late stream at once.
        acquired.getTracks().forEach((track) => track.stop())
        return false
      }
      stream = acquired
    } catch (reason) {
      if (attempt !== startAttempt) return false
      stopTracks()
      const code = classifyRecorderError(reason)
      error.value = code
      status.value = code === 'denied' ? 'denied' : 'error'
      return false
    }

    try {
      const Recorder = env.MediaRecorder as MediaRecorderConstructor
      recorder = new Recorder(stream, mimeType ? { mimeType } : undefined)
    } catch {
      stopTracks()
      recorder = null
      error.value = 'failed'
      status.value = 'error'
      return false
    }

    chunks = []
    recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) chunks.push(event.data)
    }
    recorder.onstop = finish
    recorder.start()
    startedAt = now()
    elapsedSeconds.value = 0
    status.value = 'recording'
    timer = setInterval(tick, 250)
    return true
  }

  /** Stops the recording; the File and preview URL are set once the recorder has flushed its data. */
  function stop(): void {
    if (!recorder) return
    updateElapsed()
    clearTimer()
    if (recorder.state !== 'inactive') recorder.stop()
    else finish()
  }

  function abortActive(): void {
    startAttempt += 1
    clearTimer()
    const active = recorder
    recorder = null
    if (active) {
      // Detach first so a late `stop` event cannot overwrite a newer recording.
      active.ondataavailable = null
      active.onstop = null
      if (active.state !== 'inactive') active.stop()
    }
    chunks = []
    stopTracks()
  }

  /** Discards the current recording (re-record). */
  function reset(): void {
    abortActive()
    clearRecording()
    status.value = supported ? 'idle' : 'unsupported'
    error.value = supported ? null : 'unsupported'
  }

  function dispose(): void {
    abortActive()
    revokePreview()
  }

  if (getCurrentScope()) onScopeDispose(dispose)

  return {
    supported,
    mimeType,
    maxSeconds,
    status,
    error,
    elapsedSeconds,
    recording,
    previewUrl,
    isRecording: computed(() => status.value === 'recording'),
    isBusy: computed(() => status.value === 'recording' || status.value === 'requesting'),
    start,
    stop,
    reset,
    dispose,
  }
}
