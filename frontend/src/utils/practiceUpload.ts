import type {
  AiPronunciationFeedbackResponse,
  AiWritingFeedbackResponse,
  PageResponse,
  PracticeSubmissionResponse,
  PracticeSubmissionType,
} from '@/types/api'

/**
 * Practice submission uploads (D7 / D7a): pronunciation recordings and handwriting images sent to
 * `POST /ai/pronunciation-feedback` and `POST /ai/writing-feedback`. Pure helpers so they can be unit-tested.
 */

export type UploadKind = 'audio' | 'image'

/** What an upload form hands back to the page after a successful upload. */
export type PracticeUploadResult =
  | { kind: 'audio'; target: string; response: AiPronunciationFeedbackResponse }
  | { kind: 'image'; target: string; response: AiWritingFeedbackResponse }

/** Lesson choice offered by the upload forms (from `GET /students/progress` lessons). */
export interface PracticeLessonOption {
  lessonId: number
  lessonTitle: string
}

/** Server limit for AI audio / image uploads (backend `aiAudioMaxFileSize` / `aiImageMaxFileSize`). */
export const MAX_PRACTICE_UPLOAD_BYTES = 10 * 1024 * 1024
/** practice_submissions.target_text is VARCHAR(200). */
export const MAX_TARGET_TEXT_LENGTH = 200
export const MAX_PINYIN_LENGTH = 200
export const MAX_TONE_LENGTH = 50
/** Upper bound of a single browser recording. */
export const MAX_RECORDING_SECONDS = 60

/** Formats the server stores (AiFileStorageService): WEBM / WAV / MP3 and PNG / JPEG. */
const ACCEPTED_MIME_TYPES: Record<UploadKind, ReadonlySet<string>> = {
  audio: new Set(['audio/webm', 'audio/wav', 'audio/x-wav', 'audio/wave', 'audio/mpeg', 'audio/mp3']),
  image: new Set(['image/png', 'image/jpeg', 'image/jpg']),
}
const ACCEPTED_EXTENSIONS: Record<UploadKind, readonly string[]> = {
  audio: ['.webm', '.wav', '.mp3'],
  image: ['.png', '.jpg', '.jpeg'],
}

/** `accept` attribute for the file inputs. */
export const UPLOAD_ACCEPT: Record<UploadKind, string> = {
  audio: 'audio/*,.webm,.wav,.mp3',
  image: 'image/*,.png,.jpg,.jpeg',
}

/**
 * Recorder formats in preference order. Only WEBM is offered because the server accepts WEBM/WAV/MP3 and
 * browsers record WEBM (Chrome, Edge, Firefox, recent Safari); other browsers fall back to a file upload.
 */
const RECORDER_MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm'] as const

type UploadIssueCode =
  | 'fileRequired'
  | 'fileEmpty'
  | 'fileType'
  | 'fileTooLarge'
  | 'targetRequired'
  | 'targetTooLong'
  | 'pinyinTooLong'
  | 'toneTooLong'
  | 'lessonInvalid'

export interface UploadIssue {
  field: 'file' | 'target' | 'pinyin' | 'tone' | 'lesson'
  code: UploadIssueCode
}

export interface UploadValidationInput {
  kind: UploadKind
  file: Pick<File, 'name' | 'size' | 'type'> | null | undefined
  target: string | null | undefined
  pinyin?: string | null
  tone?: string | null
  /** Raw lesson value from the form ('' / null = no lesson). */
  lessonId?: string | number | null
  maxBytes?: number
}

export function baseMimeType(value: string | null | undefined): string {
  return (value ?? '').split(';')[0]?.trim().toLowerCase() ?? ''
}

function fileExtension(name: string | null | undefined): string {
  const lower = (name ?? '').toLowerCase()
  const dot = lower.lastIndexOf('.')
  return dot >= 0 ? lower.slice(dot) : ''
}

/** True when the file is an audio/* (or image/*) file in a format the server stores. */
export function isAcceptedUploadType(file: Pick<File, 'name' | 'type'>, kind: UploadKind): boolean {
  const mime = baseMimeType(file.type)
  if (mime) return mime.startsWith(`${kind}/`) && ACCEPTED_MIME_TYPES[kind].has(mime)
  return ACCEPTED_EXTENSIONS[kind].includes(fileExtension(file.name))
}

/** Positive integer lesson id, or null for "no lesson" / invalid input. */
export function parseLessonId(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) return null
  const text = String(value).trim()
  if (!/^\d+$/.test(text)) return null
  const id = Number(text)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

/** Client-side checks before an upload; returns every problem found (empty = valid). */
export function validateUpload(input: UploadValidationInput): UploadIssue[] {
  const issues: UploadIssue[] = []
  const maxBytes = input.maxBytes ?? MAX_PRACTICE_UPLOAD_BYTES
  const file = input.file

  if (!file) issues.push({ field: 'file', code: 'fileRequired' })
  else if (!isAcceptedUploadType(file, input.kind)) issues.push({ field: 'file', code: 'fileType' })
  else if (file.size <= 0) issues.push({ field: 'file', code: 'fileEmpty' })
  else if (file.size > maxBytes) issues.push({ field: 'file', code: 'fileTooLarge' })

  const target = (input.target ?? '').trim()
  if (!target) issues.push({ field: 'target', code: 'targetRequired' })
  else if ([...target].length > MAX_TARGET_TEXT_LENGTH) issues.push({ field: 'target', code: 'targetTooLong' })

  if ((input.pinyin ?? '').trim().length > MAX_PINYIN_LENGTH) issues.push({ field: 'pinyin', code: 'pinyinTooLong' })
  if ((input.tone ?? '').trim().length > MAX_TONE_LENGTH) issues.push({ field: 'tone', code: 'toneTooLong' })

  const rawLesson = input.lessonId
  if (rawLesson !== null && rawLesson !== undefined && String(rawLesson).trim() !== '' && !parseLessonId(rawLesson)) {
    issues.push({ field: 'lesson', code: 'lessonInvalid' })
  }

  return issues
}

export interface PronunciationUploadInput {
  file: Blob
  fileName?: string
  targetText: string
  pinyin?: string | null
  tone?: string | null
  languagePreference: string
  lessonId?: string | number | null
}

export interface WritingUploadInput {
  file: Blob
  fileName?: string
  targetCharacter: string
  languagePreference: string
  lessonId?: string | number | null
}

function appendFile(formData: FormData, file: Blob, fileName?: string): void {
  const name = fileName ?? (file instanceof File ? file.name : 'upload')
  formData.append('file', file, name)
}

function appendOptional(formData: FormData, key: string, value: string | null | undefined): void {
  const trimmed = (value ?? '').trim()
  if (trimmed) formData.append(key, trimmed)
}

/** Multipart body for `POST /ai/pronunciation-feedback` (file, targetText, pinyin?, tone?, languagePreference, lessonId?). */
export function buildPronunciationFormData(input: PronunciationUploadInput): FormData {
  const formData = new FormData()
  appendFile(formData, input.file, input.fileName)
  formData.append('targetText', input.targetText.trim())
  appendOptional(formData, 'pinyin', input.pinyin)
  appendOptional(formData, 'tone', input.tone)
  formData.append('languagePreference', input.languagePreference)
  const lessonId = parseLessonId(input.lessonId)
  if (lessonId) formData.append('lessonId', String(lessonId))
  return formData
}

/** Multipart body for `POST /ai/writing-feedback` (file, targetCharacter, languagePreference, lessonId?). */
export function buildWritingFormData(input: WritingUploadInput): FormData {
  const formData = new FormData()
  appendFile(formData, input.file, input.fileName)
  formData.append('targetCharacter', input.targetCharacter.trim())
  formData.append('languagePreference', input.languagePreference)
  const lessonId = parseLessonId(input.lessonId)
  if (lessonId) formData.append('lessonId', String(lessonId))
  return formData
}

/** First recorder format the browser supports, or null when none of the server-accepted formats can be recorded. */
export function pickRecorderMimeType(isTypeSupported: ((type: string) => boolean) | null | undefined): string | null {
  if (typeof isTypeSupported !== 'function') return null
  for (const candidate of RECORDER_MIME_CANDIDATES) {
    try {
      if (isTypeSupported(candidate)) return candidate
    } catch {
      // Some browsers throw for unknown types; treat as unsupported.
    }
  }
  return null
}

export interface RecorderEnvironment {
  mediaDevices?: { getUserMedia?: (constraints: MediaStreamConstraints) => Promise<MediaStream> } | null
  MediaRecorder?: { isTypeSupported?: (type: string) => boolean } | null
}

/** Reads the recorder APIs from the current browser (safe outside a browser). */
export function browserRecorderEnvironment(): RecorderEnvironment {
  if (typeof window === 'undefined') return {}
  const globalWindow = window as unknown as { MediaRecorder?: RecorderEnvironment['MediaRecorder'] }
  return {
    mediaDevices: typeof navigator === 'undefined' ? null : (navigator.mediaDevices ?? null),
    MediaRecorder: globalWindow.MediaRecorder ?? null,
  }
}

/** In-browser recording needs getUserMedia, MediaRecorder and a recordable server-accepted format. */
export function detectRecorderSupport(env: RecorderEnvironment): boolean {
  if (typeof env.mediaDevices?.getUserMedia !== 'function') return false
  if (!env.MediaRecorder) return false
  return pickRecorderMimeType(env.MediaRecorder.isTypeSupported?.bind(env.MediaRecorder)) !== null
}

function extensionForMimeType(mimeType: string | null | undefined): string {
  switch (baseMimeType(mimeType)) {
    case 'audio/wav':
    case 'audio/x-wav':
    case 'audio/wave':
      return '.wav'
    case 'audio/mpeg':
    case 'audio/mp3':
      return '.mp3'
    default:
      return '.webm'
  }
}

/** e.g. `pronunciation-20260518-0300.webm` (local time). */
export function recordingFileName(mimeType: string | null | undefined, now: Date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`
  return `pronunciation-${stamp}${extensionForMimeType(mimeType)}`
}

/** "0:07" style recording timer. */
export function formatRecordingTime(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(Number.isFinite(totalSeconds) ? totalSeconds : 0))
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`
}

/** Human readable file size (KB / MB). */
export function formatFileSize(bytes: number | null | undefined): string {
  const value = typeof bytes === 'number' && Number.isFinite(bytes) && bytes > 0 ? bytes : 0
  if (value >= 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${Math.max(1, Math.round(value / 1024))} KB`
}

export function submissionMediaKind(type: PracticeSubmissionType | string | null | undefined): UploadKind {
  return type === 'HANDWRITING_IMAGE' ? 'image' : 'audio'
}

/** Client-side paging of the preview fixtures (newest first, optional type filter). */
export function pagePracticeSubmissions(
  items: readonly PracticeSubmissionResponse[],
  type: PracticeSubmissionType | null | undefined,
  page: number,
  size: number,
): PageResponse<PracticeSubmissionResponse> {
  const filtered = items
    .filter((item) => !type || item.submissionType === type)
    .sort((a, b) => Date.parse(b.submittedAt) - Date.parse(a.submittedAt))
  const safeSize = Math.max(1, Math.floor(size))
  const totalPages = Math.max(1, Math.ceil(filtered.length / safeSize))
  const safePage = Math.min(Math.max(0, Math.floor(page)), totalPages - 1)
  return {
    items: filtered.slice(safePage * safeSize, safePage * safeSize + safeSize),
    page: safePage,
    size: safeSize,
    totalItems: filtered.length,
    totalPages,
  }
}
