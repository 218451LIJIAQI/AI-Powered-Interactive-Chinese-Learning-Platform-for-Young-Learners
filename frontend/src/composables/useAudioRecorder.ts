import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'

import {
  MAX_RECORDING_SECONDS,
  RECORDER_MIME_TYPE,
  RECORDING_FILE_TYPE,
  recordingFileName,
} from '@/utils/practiceUpload'

type AudioRecorderStatus = 'idle' | 'requesting' | 'recording' | 'recorded' | 'denied' | 'error'

/** True when getUserMedia was refused the microphone (rather than finding none or failing). */
function isPermissionDenied(reason: unknown): boolean {
  const name = (reason as { name?: unknown } | null)?.name
  return name === 'NotAllowedError' || name === 'SecurityError'
}

/**
 * Browser microphone recording with MediaRecorder (feature-detected). The recording is kept locally as a File
 * (WEBM) with an object URL for preview; every microphone track is stopped as soon as recording ends.
 * When recording is unavailable or permission is denied, callers fall back to a file input.
 */
export function useAudioRecorder() {
  // Both are missing in some browsers (and getUserMedia outside a secure context).
  const mediaDevices = navigator.mediaDevices as MediaDevices | undefined
  const Recorder = window.MediaRecorder as typeof MediaRecorder | undefined
  const supported = Boolean(mediaDevices?.getUserMedia && Recorder?.isTypeSupported?.(RECORDER_MIME_TYPE))

  const status = ref<AudioRecorderStatus>('idle')
  const elapsedSeconds = ref(0)
  const recording = shallowRef<File | null>(null)
  const previewUrl = ref<string | null>(null)

  let stream: MediaStream | null = null
  let recorder: MediaRecorder | null = null
  let timer: ReturnType<typeof setInterval> | undefined
  let startedAt = 0
  /** Incremented by every start() and reset(), so a getUserMedia result that arrives late can be discarded. */
  let startAttempt = 0

  function stopTracks(): void {
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
  }

  function clearRecording(): void {
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
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

  function finish(chunks: Blob[]): void {
    clearInterval(timer)
    stopTracks()
    recorder = null
    if (!chunks.length) {
      status.value = 'error'
      return
    }
    const file = new File(chunks, recordingFileName(), { type: RECORDING_FILE_TYPE })
    recording.value = file
    previewUrl.value = URL.createObjectURL(file)
    status.value = 'recorded'
  }

  async function start(): Promise<void> {
    if (status.value === 'recording' || status.value === 'requesting') return

    clearRecording()
    if (!supported || !mediaDevices || !Recorder) {
      status.value = 'error'
      return
    }
    status.value = 'requesting'
    const attempt = ++startAttempt

    try {
      const acquired = await mediaDevices.getUserMedia({ audio: true })
      if (attempt !== startAttempt) {
        // reset() ran while the permission prompt was open: release the late stream at once.
        acquired.getTracks().forEach((track) => track.stop())
        return
      }
      stream = acquired
    } catch (reason) {
      if (attempt === startAttempt) status.value = isPermissionDenied(reason) ? 'denied' : 'error'
      return
    }

    let created: MediaRecorder | null = null
    try {
      created = new Recorder(stream, { mimeType: RECORDER_MIME_TYPE })
      const chunks: Blob[] = []
      created.ondataavailable = (event) => {
        if (event.data.size > 0) chunks.push(event.data)
      }
      created.onstop = () => finish(chunks)
      created.onerror = () => {
        if (recorder !== created) return
        // A failed recorder can still deliver a final chunk and a stop event.
        reset()
        status.value = 'error'
      }
      // Assign before start() so synchronous onerror callbacks observe this active recorder.
      recorder = created
      created.start()
      // A synchronous onerror handler clears recorder via reset(); do not overwrite the failed state.
      if (recorder !== created) return
    } catch {
      if (created) {
        created.ondataavailable = null
        created.onstop = null
        created.onerror = null
        if (recorder === created) recorder = null
      }
      stopTracks()
      status.value = 'error'
      return
    }

    startedAt = Date.now()
    status.value = 'recording'
    timer = setInterval(tick, 250)
  }

  /**
   * Stops the recording; the File and preview URL are set once the recorder has flushed its data (its `stop` event). A
   * start still waiting for the microphone is called off, so the microphone does not come on after the student stopped
   * or left.
   */
  function stop(): void {
    if (status.value === 'requesting') {
      reset()
      return
    }
    // An inactive recorder is already stopping (Stop pressed twice, or at the time limit): its `stop` event finishes.
    if (!recorder || recorder.state === 'inactive') return
    updateElapsed()
    clearInterval(timer)
    try {
      recorder.stop()
    } catch {
      reset()
      status.value = 'error'
    }
  }

  /** Discards the current recording (re-record) and releases the microphone. */
  function reset(): void {
    startAttempt += 1
    clearInterval(timer)
    const active = recorder
    recorder = null
    try {
      if (active) {
        // Detach first so a late `stop` event cannot overwrite a newer recording.
        active.ondataavailable = null
        active.onstop = null
        active.onerror = null
        if (active.state !== 'inactive') active.stop()
      }
    } catch {
      // The underlying media track or device may already have ended.
    } finally {
      stopTracks()
      clearRecording()
      status.value = 'idle'
    }
  }

  onScopeDispose(reset)

  return {
    supported,
    status: readonly(status),
    elapsedSeconds: readonly(elapsedSeconds),
    recording: readonly(recording),
    previewUrl: readonly(previewUrl),
    isRecording: computed(() => status.value === 'recording'),
    isBusy: computed(() => status.value === 'recording' || status.value === 'requesting'),
    start,
    stop,
    reset,
  }
}
