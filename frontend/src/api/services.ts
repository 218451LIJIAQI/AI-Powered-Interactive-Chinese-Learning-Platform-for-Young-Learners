/**
 * The shared request layer (envelope unwrapping, downloads, the AI chat stream) and the account, notification,
 * system and teacher AI clients. The other domains have their own client modules (classes, learn, ops, plan, ai).
 */
import type { AxiosRequestConfig } from 'axios'

import {
  AUTHORIZATION_HEADER,
  bearer,
  getAccessToken,
  http,
  invalidateAuthSession,
  joinUrl,
  refreshAccessToken,
} from './http'
import { appEnv } from '@/config/env'
import type {
  AiAvailabilityResponse,
  AiChatResponse,
  AiQuizGeneratorResponse,
  ApiResponse,
  AppLanguage,
  AuthResponse,
  CurrentUserResponse,
  HealthStatusResponse,
  NotificationResponse,
  NotificationUnreadCountResponse,
  PageResponse,
  PublicPlatformSettingsResponse,
  RegistrationResponse,
  RewardSummaryResponse,
  NotificationStatus,
} from '@/types/api'
import { apiClientMessage } from '@/plugins/apiClientMessages'
import { cleanServerMessage } from '@/utils/apiError'
import type { PasswordChangeForm } from '@/utils/password'

const AI_STREAM_CONNECT_TIMEOUT_MS = 30_000
/** The backend gives the AI provider up to 30 s (SRS FRAI09): the browser waits longer than that, not 20 s. */
const AI_REQUEST_CONFIG = { timeout: 45_000 }
const CONTENT_DISPOSITION_ASCII_FILENAME_PATTERN = /filename="?([^";]+)"?/i
const CONTENT_DISPOSITION_UTF8_FILENAME_PATTERN = /filename\*=UTF-8''([^;\s]+)/i
const JSON_CONTENT_TYPE = 'application/json'

type AiStreamHandlers = {
  onDelta?: (chunk: string) => void
  /** Cancels the request and stops reading the stream; the call then rejects with a DOMException named `AbortError`. */
  signal?: AbortSignal
}

export type DownloadResult = {
  blob: Blob
  fileName: string | null
}

/** 0-based page index and page size of a paged list request. */
type PageQuery = { page?: number; size?: number }
/** Shaped like an axios error (`response.status`, `response.data`) so utils/apiError.ts reads both the same way. */
class ApiEnvelopeError extends Error {
  response: { status?: number; data: { message: string; errors: string[] } }

  constructor(message: string, errors: string[] = [], status?: number) {
    super(message)
    this.name = 'ApiEnvelopeError'
    this.response = { status, data: { message, errors } }
  }
}

function cleanErrorList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.map(cleanServerMessage).filter((message): message is string => Boolean(message))
    : []
}

/* Client-side fallback messages are localised (EN / MS / zh-CN) in plugins/apiClientMessages.ts. */
function fallbackSseHttpMessage(status: number): string {
  if (status === 401) return apiClientMessage('unauthorized')
  if (status === 429) return apiClientMessage('rateLimited')
  if (status >= 500) return apiClientMessage('server')
  return apiClientMessage('streamFallback')
}

/** The server's JSON error body when there is a readable one, otherwise the message for the HTTP status. */
async function sseHttpError(response: Response): Promise<ApiEnvelopeError> {
  const isJson = (response.headers.get('content-type') ?? '').toLowerCase().includes(JSON_CONTENT_TYPE)
  const payload: { message?: unknown; errors?: unknown } | null = isJson
    ? await response.json().catch(() => null)
    : null
  return new ApiEnvelopeError(
    cleanServerMessage(payload?.message) ?? fallbackSseHttpMessage(response.status),
    cleanErrorList(payload?.errors),
    response.status,
  )
}

/** The `data` of a successful `ApiResponse` envelope; a failed or malformed envelope throws an ApiEnvelopeError. */
async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const payload = (await request).data

  if (!payload || typeof payload !== 'object' || !('success' in payload)) {
    throw new ApiEnvelopeError(apiClientMessage('unexpectedFormat'))
  }

  if (payload.success === false) {
    throw new ApiEnvelopeError(payload.message || apiClientMessage('rejected'), payload.errors ?? [])
  }

  if (!('data' in payload)) {
    throw new ApiEnvelopeError(apiClientMessage('missingData'))
  }

  return payload.data
}

const get = <T>(url: string, config?: AxiosRequestConfig) => unwrap<T>(http.get(url, config))
const post = <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(http.post(url, body, config))
const put = <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(http.put(url, body, config))
const patch = <T>(url: string, body?: unknown) => unwrap<T>(http.patch(url, body))
const del = <T>(url: string) => unwrap<T>(http.delete(url))

/** Envelope-unwrapping helpers for the per-domain API modules (api/learn.ts, api/classes.ts, ...). */
export { get as apiGet, post as apiPost, put as apiPut, del as apiDelete, download as apiDownload }

/** One server-sent event: its `event:` name ('message' when absent) and its `data:` lines; null without data. */
function parseSseEvent(rawEvent: string): { event: string; data: string } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of rawEvent.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice('event:'.length).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice('data:'.length))
  }
  return dataLines.length ? { event, data: dataLines.join('\n') } : null
}

function parseSseCompletePayload(data: string): AiChatResponse {
  try {
    return JSON.parse(data) as AiChatResponse
  } catch {
    throw new ApiEnvelopeError(apiClientMessage('streamIncomplete'))
  }
}

function streamAbortError(): DOMException {
  return new DOMException('The AI chat stream was cancelled.', 'AbortError')
}

async function readSseStream(response: Response, handlers: AiStreamHandlers): Promise<AiChatResponse> {
  if (!response.body) {
    throw new ApiEnvelopeError(apiClientMessage('streamMissingBody'))
  }

  const { signal } = handlers
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finalPayload: AiChatResponse | null = null

  // Cancelling the reader settles a pending read() and closes the connection.
  const cancelRead = () => {
    void reader.cancel().catch(() => undefined)
  }
  const throwIfAborted = () => {
    if (!signal?.aborted) return
    cancelRead()
    throw streamAbortError()
  }
  signal?.addEventListener('abort', cancelRead, { once: true })

  const processEvent = (rawEvent: string) => {
    const parsed = parseSseEvent(rawEvent)
    if (parsed?.event === 'delta') {
      handlers.onDelta?.(parsed.data)
    } else if (parsed?.event === 'complete') {
      finalPayload = parseSseCompletePayload(parsed.data)
    } else if (parsed?.event === 'error') {
      throw new ApiEnvelopeError(parsed.data || apiClientMessage('streamStopped'))
    }
  }

  try {
    while (true) {
      throwIfAborted()
      const { value, done } = await reader.read()
      throwIfAborted()
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() ?? ''

      events.forEach(processEvent)

      if (done) break
    }
  } finally {
    signal?.removeEventListener('abort', cancelRead)
    reader.releaseLock()
  }

  processEvent(buffer)

  if (!finalPayload) {
    throw new ApiEnvelopeError(apiClientMessage('streamIncomplete'))
  }
  return finalPayload
}

function aiStreamHeaders(): Headers {
  const headers = new Headers({ Accept: 'text/event-stream', 'Content-Type': JSON_CONTENT_TYPE })
  const token = getAccessToken()
  if (token) {
    headers.set(AUTHORIZATION_HEADER, bearer(token))
  }
  return headers
}

async function sendAiChatStreamRequest(payload: AiChatPayload, signal?: AbortSignal): Promise<Response> {
  if (signal?.aborted) {
    throw streamAbortError()
  }

  // One controller serves both the connect timeout and the caller's signal.
  const abortController = new AbortController()
  let timedOut = false
  const timeoutId = globalThis.setTimeout(() => {
    timedOut = true
    abortController.abort()
  }, AI_STREAM_CONNECT_TIMEOUT_MS)
  const abortFromCaller = () => abortController.abort()
  signal?.addEventListener('abort', abortFromCaller, { once: true })

  try {
    return await fetch(joinUrl(appEnv.apiBaseUrl, '/ai/chat/stream'), {
      method: 'POST',
      credentials: 'include',
      headers: aiStreamHeaders(),
      signal: abortController.signal,
      body: JSON.stringify(payload),
    })
  } catch (error) {
    if (signal?.aborted) {
      throw streamAbortError()
    }

    if (timedOut) {
      throw new ApiEnvelopeError(apiClientMessage('streamTimeout'))
    }

    throw error
  } finally {
    globalThis.clearTimeout(timeoutId)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

async function postSseChat(payload: AiChatPayload, handlers: AiStreamHandlers = {}): Promise<AiChatResponse> {
  let response = await sendAiChatStreamRequest(payload, handlers.signal)
  if (response.status === 401) {
    const refreshedToken = await refreshAccessToken()
    if (!refreshedToken) {
      invalidateAuthSession()
      throw await sseHttpError(response)
    }

    response = await sendAiChatStreamRequest(payload, handlers.signal)
    if (response.status === 401) {
      invalidateAuthSession()
    }
  }

  if (!response.ok) {
    throw await sseHttpError(response)
  }

  return readSseStream(response, handlers)
}

/** The file name of a Content-Disposition header (the UTF-8 form first), or null. */
function parseContentDispositionFileName(contentDisposition: string | null): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(CONTENT_DISPOSITION_UTF8_FILENAME_PATTERN)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }

  const asciiMatch = contentDisposition.match(CONTENT_DISPOSITION_ASCII_FILENAME_PATTERN)
  return asciiMatch?.[1] ?? null
}

/** A failed blob download receives the server's JSON error as a Blob; parse it so its message can be shown. */
async function readBlobErrorBody(error: unknown): Promise<never> {
  const response = (error as { response?: { data?: unknown } } | null)?.response
  if (response?.data instanceof Blob && response.data.type.toLowerCase().includes(JSON_CONTENT_TYPE)) {
    try {
      response.data = JSON.parse(await response.data.text()) as unknown
    } catch {
      // Not readable JSON: the message for the HTTP status is shown instead.
    }
  }
  throw error
}

/** A protected file fetched with the bearer token as a Blob, with its file name. */
async function download(url: string, config?: AxiosRequestConfig): Promise<DownloadResult> {
  const response = await http.get<Blob>(url, { ...config, responseType: 'blob' }).catch(readBlobErrorBody)
  const disposition: unknown = response.headers['content-disposition']
  return {
    blob: response.data,
    fileName: parseContentDispositionFileName(typeof disposition === 'string' ? disposition : null),
  }
}

interface AiChatPayload {
  message: string
  languagePreference?: AppLanguage
  sessionId?: number
}

export interface NotificationQuery extends PageQuery {
  /** Omit for all notifications. */
  status?: NotificationStatus
}

/** Request body of the teacher AI quiz draft. */
interface AiQuizPayload {
  topic: string
  level?: string
  numberOfQuestions?: number
  languagePreference?: AppLanguage
}

export const authApi = {
  /** `keepSignedIn` false: the refresh cookie ends with the browser session ("Keep me signed in" unticked). */
  login(payload: { email: string; password: string; keepSignedIn?: boolean }) {
    return post<AuthResponse>('/auth/login', payload)
  },
  registerStudent(payload: {
    fullName: string
    email: string
    password: string
    confirmPassword: string
    preferredLanguage?: AppLanguage
  }) {
    return post<RegistrationResponse>('/auth/register/student', payload)
  },
  registerTeacher(payload: {
    fullName: string
    email: string
    password: string
    confirmPassword: string
    /** Required by the server: 20 to 1000 characters after trimming (same rule as the sign-up form). */
    bio: string
    preferredLanguage?: AppLanguage
  }) {
    return post<RegistrationResponse>('/auth/register/teacher', payload)
  },
  me() {
    return get<CurrentUserResponse>('/auth/me')
  },
  logout() {
    return post<void>('/auth/logout')
  },
  forgotPassword(email: string) {
    return post<void>('/auth/forgot-password', { email })
  },
  resetPassword(payload: { token: string; password: string; confirmPassword: string }) {
    return post<void>('/auth/reset-password', payload)
  },
}

/** Password change for every signed-in role (FRAUTH08); the rest of the Account page is in api/ops.ts. */
export const accountApi = {
  /** Returns a fresh access token; the server revokes every other session and sets a new refresh cookie. */
  changePassword(payload: PasswordChangeForm) {
    return post<AuthResponse>('/users/me/password', payload)
  },
}

export const publicApi = {
  platformSettings() {
    return get<PublicPlatformSettingsResponse>('/public/platform-settings')
  },
}

export const notificationsApi = {
  /** Paged list for the notifications page; `status` filters UNREAD / READ (omit for all). */
  page(query: NotificationQuery = {}) {
    return get<PageResponse<NotificationResponse>>('/notifications', { params: query })
  },
  unreadCount() {
    return get<NotificationUnreadCountResponse>('/notifications/unread-count')
  },
  markAsRead(id: number) {
    return patch<NotificationResponse>(`/notifications/${id}/read`)
  },
  markAllRead() {
    return patch<void>('/notifications/read-all')
  },
}

export const systemApi = {
  health() {
    return get<HealthStatusResponse>('/health')
  },
}

export const studentApi = {
  rewards() {
    return get<RewardSummaryResponse>('/students/rewards')
  },
}

/** Ask SparkBot's streamed answer; every other AI Helper call is in api/ai.ts. */
export const aiApi = {
  chatStream: postSseChat,
}

export const teacherApi = {
  /* AI-assisted drafts (FRAI05): nothing is saved; the teacher edits and saves through the normal flow. */
  aiAvailability() {
    return get<AiAvailabilityResponse>('/teachers/ai/availability')
  },
  aiQuizDraft(payload: AiQuizPayload) {
    return post<AiQuizGeneratorResponse>('/teachers/ai/quiz-draft', payload, AI_REQUEST_CONFIG)
  },
}
