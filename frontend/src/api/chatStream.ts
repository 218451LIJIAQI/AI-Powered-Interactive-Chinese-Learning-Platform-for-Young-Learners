import {
  AUTHORIZATION_HEADER,
  bearer,
  getAccessToken,
  getAuthSessionVersion,
  http,
  invalidateAuthSession,
  refreshAccessToken,
  reportServiceDown,
  requireCurrentSession,
} from './http'
import { AI_RESULT_STATUSES, type AiChatResponse, type AppLanguage } from '@/types/api'
import { apiClientMessage } from '@/plugins/apiClientMessages'
import { ApiEnvelopeError, cleanServerMessage } from '@/utils/apiError'

const AI_CHAT_STREAM_PATH = '/ai/chat/stream'
/** Allow the server's 45 s stream deadline and network transit, both for headers and for the response body. */
const AI_STREAM_TIMEOUT_MS = 50_000
const JSON_CONTENT_TYPE = 'application/json'
const JSON_CONTENT_TYPE_PATTERN = /application\/(?:[^;\s]+\+)?json/i

interface AiChatPayload {
  message: string
  languagePreference?: AppLanguage
  sessionId?: number
}

type AiStreamHandlers = {
  onDelta?: (chunk: string) => void
  /** Cancels the request and stops reading the stream; the call then rejects with a DOMException named `AbortError`. */
  signal?: AbortSignal
}

/** The server's JSON error body when there is a readable one, otherwise the message for the HTTP status. */
async function sseHttpError(response: Response): Promise<ApiEnvelopeError> {
  const { status } = response
  const isJson = JSON_CONTENT_TYPE_PATTERN.test(response.headers.get('content-type') ?? '')
  if (!isJson) void response.body?.cancel().catch(() => undefined)
  const payload: { message?: unknown } | null = isJson ? await response.json().catch(() => null) : null
  const fallbackKey =
    status === 401
      ? 'unauthorized'
      : status === 403
        ? 'forbidden'
        : status === 429
          ? 'rateLimited'
          : status >= 500
            ? 'server'
            : 'streamFallback'
  return new ApiEnvelopeError(cleanServerMessage(payload?.message) ?? apiClientMessage(fallbackKey), status)
}

/**
 * One server-sent event: its `event:` name ('message' when absent) and its `data:` lines; null without data.
 * Spring's `SseEmitter` writes `data:<payload>` with no space separator, so any leading space belongs to the streamed token.
 */
function parseSseEvent(rawEvent: string): { event: string; data: string } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of rawEvent.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice('event:'.length).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice('data:'.length))
  }
  return dataLines.length ? { event, data: dataLines.join('\n') } : null
}

function streamAbortError(): DOMException {
  return new DOMException('The AI chat stream was cancelled.', 'AbortError')
}

function isChatResponse(value: unknown): value is AiChatResponse {
  if (!value || typeof value !== 'object') return false
  const validId = (id: unknown) => id == null || (typeof id === 'number' && Number.isSafeInteger(id) && id > 0)
  return (
    'aiResponse' in value &&
    typeof value.aiResponse === 'string' &&
    'status' in value &&
    AI_RESULT_STATUSES.some((status) => status === value.status) &&
    (!('sessionId' in value) || validId(value.sessionId)) &&
    (!('chatMessageId' in value) || validId(value.chatMessageId))
  )
}

async function readSseStream(
  response: Response,
  signal: AbortSignal,
  sessionVersion: number,
  onDelta?: AiStreamHandlers['onDelta'],
): Promise<AiChatResponse> {
  if (signal.aborted || sessionVersion !== getAuthSessionVersion()) {
    void response.body?.cancel().catch(() => undefined)
    signal.throwIfAborted()
    requireCurrentSession(sessionVersion)
  }
  if (!response.body) {
    throw new ApiEnvelopeError(apiClientMessage('streamMissingBody'))
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  // Cancelling the reader settles a pending read() and closes the connection.
  const cancelRead = () => {
    void reader.cancel().catch(() => undefined)
  }
  signal.addEventListener('abort', cancelRead, { once: true })

  const processEvent = (rawEvent: string): AiChatResponse | undefined => {
    signal.throwIfAborted()
    requireCurrentSession(sessionVersion)
    const parsed = parseSseEvent(rawEvent)
    if (parsed?.event === 'delta') {
      onDelta?.(parsed.data)
    } else if (parsed?.event === 'complete') {
      let payload: unknown
      try {
        payload = JSON.parse(parsed.data)
      } catch {
        throw new ApiEnvelopeError(apiClientMessage('streamIncomplete'))
      }
      if (!isChatResponse(payload)) throw new ApiEnvelopeError(apiClientMessage('streamIncomplete'))
      return payload
    } else if (parsed?.event === 'error') {
      throw new ApiEnvelopeError(cleanServerMessage(parsed.data) ?? apiClientMessage('streamStopped'))
    }
  }

  try {
    while (true) {
      signal.throwIfAborted()
      requireCurrentSession(sessionVersion)
      let chunk: ReadableStreamReadResult<Uint8Array>
      try {
        chunk = await reader.read()
      } catch (error) {
        if (error instanceof TypeError) throw new ApiEnvelopeError(apiClientMessage('network'))
        throw error
      }
      const { value, done } = chunk
      signal.throwIfAborted()
      buffer += decoder.decode(value, { stream: !done })
      const events = buffer.split(/\r?\n\r?\n/)
      if (!done) buffer = events.pop() ?? ''

      for (const event of events) {
        const complete = processEvent(event)
        if (complete) return complete
      }
      if (done) {
        signal.throwIfAborted()
        requireCurrentSession(sessionVersion)
        throw new ApiEnvelopeError(apiClientMessage('streamIncomplete'))
      }
    }
  } finally {
    signal.removeEventListener('abort', cancelRead)
    // Parsing or a delta handler can fail before the server has closed the body.
    cancelRead()
    reader.releaseLock()
  }
}

async function sendAiChatStreamRequest(
  payload: AiChatPayload,
  signal: AbortSignal,
  token: string | null,
): Promise<Response> {
  signal.throwIfAborted()
  const headers = new Headers({ Accept: 'text/event-stream', 'Content-Type': JSON_CONTENT_TYPE })
  if (token) headers.set(AUTHORIZATION_HEADER, bearer(token))

  try {
    return await fetch(http.getUri({ url: AI_CHAT_STREAM_PATH }), {
      method: 'POST',
      credentials: 'include',
      headers,
      signal,
      body: JSON.stringify(payload),
    })
  } catch (error) {
    if (error instanceof TypeError) throw new ApiEnvelopeError(apiClientMessage('network'))
    throw error
  }
}

/** Stop waiting when this stream ends, while other requests may still share the refresh. */
function refreshStreamToken(signal: AbortSignal, rejectedToken: string | null): Promise<string | null> {
  signal.throwIfAborted()
  return new Promise((resolve, reject) => {
    const abort = () => reject(streamAbortError())
    signal.addEventListener('abort', abort, { once: true })
    void refreshAccessToken(rejectedToken ? bearer(rejectedToken) : null)
      .then(resolve, reject)
      .finally(() => signal.removeEventListener('abort', abort))
  })
}

export async function apiChatStream(payload: AiChatPayload, handlers: AiStreamHandlers = {}): Promise<AiChatResponse> {
  const version = getAuthSessionVersion()
  if (handlers.signal?.aborted) throw streamAbortError()
  // One deadline covers connection setup, the optional retry and the whole response body (including error JSON).
  const abortController = new AbortController()
  const signal = abortController.signal
  const timeoutId = globalThis.setTimeout(() => abortController.abort(), AI_STREAM_TIMEOUT_MS)
  const abortFromCaller = () => abortController.abort()
  handlers.signal?.addEventListener('abort', abortFromCaller, { once: true })

  try {
    const token = getAccessToken()
    let response = await sendAiChatStreamRequest(payload, signal, token)
    // A protected stream can retry once, while its original account session is still current.
    if (response.status === 401 && version === getAuthSessionVersion()) {
      const refreshedToken = await refreshStreamToken(signal, token).catch((error: unknown) => {
        void response.body?.cancel().catch(() => undefined)
        throw error
      })
      if (refreshedToken && version === getAuthSessionVersion()) {
        void response.body?.cancel().catch(() => undefined)
        response = await sendAiChatStreamRequest(payload, signal, refreshedToken)
      }
      if (response.status === 401) invalidateAuthSession(version)
    }
    if (!response.ok) {
      reportServiceDown(response.status)
      throw await sseHttpError(response)
    }
    return await readSseStream(response, signal, version, handlers.onDelta)
  } catch (error) {
    if (handlers.signal?.aborted) throw streamAbortError()
    if (signal.aborted) throw new ApiEnvelopeError(apiClientMessage('streamTimeout'))
    throw error
  } finally {
    globalThis.clearTimeout(timeoutId)
    handlers.signal?.removeEventListener('abort', abortFromCaller)
  }
}
