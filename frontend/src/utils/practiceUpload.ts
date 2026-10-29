/**
 * Recordings and handwriting photos sent to `POST /ai/practice-checks`, and browser recording support. Pure helpers so
 * they can be unit-tested; the file is checked here with the same rules the server applies.
 */

type UploadKind = 'audio' | 'image'

/** Server limit for AI audio / image uploads (backend `aiAudioMaxFileSize` / `aiImageMaxFileSize`). */
export const MAX_PRACTICE_UPLOAD_BYTES = 10 * 1024 * 1024
/** Same limit as the target text of a practice check (`targetText` @Size). */
export const MAX_PRACTICE_TARGET_LENGTH = 100
/** Upper bound of a single browser recording. */
export const MAX_RECORDING_SECONDS = 60

/**
 * Formats the server accepts (UploadStorageSupport): it requires both an allowed extension and an allowed content
 * type, so the same pair of lists is checked here before uploading.
 */
const ACCEPTED_MIME_TYPES: Record<UploadKind, ReadonlySet<string>> = {
  audio: new Set(['audio/webm', 'video/webm', 'audio/wav', 'audio/x-wav', 'audio/mpeg', 'audio/mp3']),
  image: new Set(['image/png', 'image/jpeg']),
}
const ACCEPTED_EXTENSIONS: Record<UploadKind, readonly string[]> = {
  audio: ['.webm', '.wav', '.mp3', '.mpeg'],
  image: ['.png', '.jpg', '.jpeg'],
}

/** `accept` attribute for the file inputs. */
export const UPLOAD_ACCEPT: Record<UploadKind, string> = {
  audio: 'audio/*,video/webm,.webm,.wav,.mp3,.mpeg',
  image: 'image/*,.png,.jpg,.jpeg',
}

/**
 * The one format recorded in the browser: WEBM with Opus. The current Chrome, Edge and Firefox all record it, and the
 * server accepts WEBM; any other browser falls back to choosing a file.
 */
export const RECORDER_MIME_TYPE = 'audio/webm;codecs=opus'
/** Content type of the recorded file (the server checks the base type). */
export const RECORDING_FILE_TYPE = 'audio/webm'

type UploadFileIssue = 'fileType' | 'fileEmpty' | 'fileTooLarge'

function baseMimeType(value: string | null | undefined): string {
  return (value ?? '').split(';')[0]?.trim().toLowerCase() ?? ''
}

function fileExtension(name: string | null | undefined): string {
  const lower = (name ?? '').toLowerCase()
  const dot = lower.lastIndexOf('.')
  return dot >= 0 ? lower.slice(dot) : ''
}

/** True when both the content type and the extension are in a format the server accepts. */
function isAcceptedUploadType(file: Pick<File, 'name' | 'type'>, kind: UploadKind): boolean {
  return (
    ACCEPTED_MIME_TYPES[kind].has(baseMimeType(file.type)) &&
    ACCEPTED_EXTENSIONS[kind].includes(fileExtension(file.name))
  )
}

/** Why the server would refuse this file, or null when it is fine. */
export function practiceFileIssue(
  file: Pick<File, 'name' | 'size' | 'type'>,
  kind: UploadKind,
): UploadFileIssue | null {
  if (!isAcceptedUploadType(file, kind)) return 'fileType'
  if (file.size <= 0) return 'fileEmpty'
  if (file.size > MAX_PRACTICE_UPLOAD_BYTES) return 'fileTooLarge'
  return null
}

interface RecorderEnvironment {
  mediaDevices?: { getUserMedia?: (constraints: MediaStreamConstraints) => Promise<MediaStream> } | null
  MediaRecorder?: { isTypeSupported?: (type: string) => boolean } | null
}

/** In-browser recording needs getUserMedia and a MediaRecorder that can record RECORDER_MIME_TYPE. */
export function detectRecorderSupport(env: RecorderEnvironment): boolean {
  if (typeof env.mediaDevices?.getUserMedia !== 'function') return false
  return env.MediaRecorder?.isTypeSupported?.(RECORDER_MIME_TYPE) === true
}

/** e.g. `pronunciation-20260518-0300.webm` (local time). */
export function recordingFileName(now: Date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`
  return `pronunciation-${stamp}.webm`
}
