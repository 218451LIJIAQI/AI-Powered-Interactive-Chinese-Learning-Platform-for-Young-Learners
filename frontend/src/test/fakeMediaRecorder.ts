import { RECORDER_MIME_TYPE } from '@/composables/useAudioRecorder'

/**
 * MediaRecorder stand-in for the recorder tests: a browser that records RECORDER_MIME_TYPE. It keeps every instance
 * (`test/setup.ts` resets `instances` after each test); stop() delivers one chunk of audio and then fires `stop`.
 */
export class FakeMediaRecorder {
  static instances: FakeMediaRecorder[] = []
  static isTypeSupported = (type: string): boolean => type === RECORDER_MIME_TYPE
  state = 'inactive'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  onerror: (() => void) | null = null
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
