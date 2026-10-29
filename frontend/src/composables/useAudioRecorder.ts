import { computed, getCurrentScope, onScopeDispose, ref, shallowRef } from 'vue'

import {
  detectRecorderSupport,
  MAX_RECORDING_SECONDS,
  RECORDER_MIME_TYPE,
  RECORDING_FILE_TYPE,
  recordingFileName,
} from '@/utils/practiceUpload'

type AudioRecorderStatus = 'idle' | 'requesting' | 'recording' | 'recorded' | 'denied' | 'error'

interface MediaRecorderLike {
  readonly state: string
  ondataavailable: ((event: { data: Blob }) => void) | null
  onstop: (() => void) | null
  start(timeslice?: number): void
  stop(): void
}

interface MediaRecorderConstructor {
  new (stream: MediaStream, options?: { mimeType?: string }): MediaRecorderLike
  isTypeSupported?: (type: string) => boolean
}

const DENIED_ERRORS = new Set(['NotAllowedError', 'SecurityError', 'PermissionDeniedError'])

/** True when getUserMedia was refused the microphone (rather than finding none or failing). */
function isPermissionDenied(reason: unknown): boolean {
  const name = (reason as { name?: unknown } | null)?.name
  return typeof name === 'string' && DENIED_ERRORS.has(name)
}

/**
 * Browser microphone recording with MediaRecorder (feature-detected). The recording is kept locally as a File
 * (WEBM) with an object URL for preview; every microphone track is stopped as soon as recording ends.
 * When recording is unavailable or permission is denied, callers fall back to a file input.
 */
export function useAudioRecorder() {
  // Both are missing in some browsers (and getUserMedia outside a secure context).
  const mediaDevices = navigator.mediaDevices as MediaDevices | undefined
  const Recorder = (window as { MediaRecorder?: MediaRecorderConstructor }).MediaRecorder
  const supported = detectRecorderSupport({ mediaDevices, MediaRecorder: Recorder })

  const status = ref<AudioRecorderStatus>('idle')
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
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }

  function clearRecording(): void {
    revokePreview()
    recording.value = null
    elapsedSeconds.value = 0
  }

  function updateElapsed(): void {
    elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - startedAt) / 1000))
  }

  function tick(): void {
    updateElapsed()
    if (elapsedSeconds.value >= MAX_RECORDING_SECONDS) stop()
  }

  function finish(): void {
    clearTimer()
    stopTracks()
    recorder = null
    const parts = chunks
    chunks = []
    if (!parts.length) {
      status.value = 'error'
      return
    }
    const file = new File(parts, recordingFileName(), { type: RECORDING_FILE_TYPE })
    recording.value = file
    previewUrl.value = URL.createObjectURL(file)
    status.value = 'recorded'
  }

  async function start(): Promise<boolean> {
    if (!supported) return false
    if (status.value === 'recording' || status.value === 'requesting') return false

    clearRecording()
    status.value = 'requesting'
    const attempt = ++startAttempt

    try {
      const acquired = await mediaDevices!.getUserMedia({ audio: true })
      if (attempt !== startAttempt) {
        // reset() or dispose() ran while the permission prompt was open: release the late stream at once.
        acquired.getTracks().forEach((track) => track.stop())
        return false
      }
      stream = acquired
    } catch (reason) {
      if (attempt !== startAttempt) return false
      stopTracks()
      status.value = isPermissionDenied(reason) ? 'denied' : 'error'
      return false
    }

    try {
      const created = new Recorder!(stream, { mimeType: RECORDER_MIME_TYPE })
      chunks = []
      created.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) chunks.push(event.data)
      }
      created.onstop = finish
      // start() can also throw (e.g. the stream became inactive): the microphone must then be released too.
      created.start()
      recorder = created
    } catch {
      stopTracks()
      recorder = null
      status.value = 'error'
      return false
    }

    startedAt = Date.now()
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
    status.value = 'idle'
  }

  function dispose(): void {
    abortActive()
    revokePreview()
  }

  if (getCurrentScope()) onScopeDispose(dispose)

  return {
    supported,
    status,
    elapsedSeconds,
    recording,
    previewUrl,
    isRecording: computed(() => status.value === 'recording'),
    isBusy: computed(() => status.value === 'recording' || status.value === 'requesting'),
    start,
    stop,
    reset,
  }
}
