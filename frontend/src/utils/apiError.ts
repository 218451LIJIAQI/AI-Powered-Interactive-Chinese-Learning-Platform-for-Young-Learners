import { apiClientMessage } from '@/plugins/apiClientMessages'
import { documentLanguage } from '@/utils/locale'
import { localizeServerMessage, messageKey } from '@/utils/serverMessages'
import { truncateText } from '@/utils/truncateText'

/** Gives REST envelope and streaming errors the same status and message shape as an HTTP failure. */
export class ApiEnvelopeError extends Error {
  readonly response: { status?: number; data: { message: string } }

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'ApiEnvelopeError'
    this.response = { status, data: { message } }
  }
}

type ApiErrorPayload = {
  response?: {
    status?: number
    data?: {
      message?: unknown
    }
  }
  request?: unknown
  code?: string
}

const MAX_USER_MESSAGE_LENGTH = 280
// eslint-disable-next-line no-control-regex
const UNSAFE_MESSAGE_CHARACTERS = /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g
const TRANSPORT_INTERNAL_MESSAGES = new Set(['Request failed', 'Network Error'])

/**
 * A server message fit to show: control and bidi characters removed, spaces collapsed, at most 280 characters; null
 * for a non-string, an empty text or a bare transport error ("Network Error").
 */
export function cleanServerMessage(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }

  const trimmed = value.replace(UNSAFE_MESSAGE_CHARACTERS, ' ').replace(/\s+/g, ' ').trim()
  if (!trimmed || TRANSPORT_INTERNAL_MESSAGES.has(trimmed)) {
    return null
  }

  return trimmed.length > MAX_USER_MESSAGE_LENGTH ? `${truncateText(trimmed, MAX_USER_MESSAGE_LENGTH - 3)}...` : trimmed
}

/**
 * The message to show for a failed request (SDD Table 6.3). A readable server message wins; otherwise the HTTP status
 * or transport failure picks a generic message. `fallback` is the caller's text for a missing item (404) and for any
 * other failure without a more specific message.
 */
export function resolveApiError(reason: unknown, fallback?: string): string {
  const payload = reason as ApiErrorPayload | null
  const serverMessage = cleanServerMessage(payload?.response?.data?.message)

  // FRLANG02: known backend messages are shown in the interface language; others stay as sent.
  if (serverMessage) return localizeServerMessage(serverMessage, documentLanguage())

  const status = payload?.response?.status
  if (status === 401) return apiClientMessage('unauthorized')
  if (status === 403) return apiClientMessage('forbidden')
  if (status === 404) return fallback ?? apiClientMessage('notFound')
  if (status === 429) return apiClientMessage('rateLimited')
  if (status && status >= 500) return apiClientMessage('server')

  // Axios marks a timed-out request with this code.
  if (payload?.code === 'ECONNABORTED') return apiClientMessage('timeout')

  if (payload?.request && !payload?.response) {
    return apiClientMessage('network')
  }

  return fallback ?? apiClientMessage('fallback')
}

/** HTTP status of a failed request, or null for network errors and non-HTTP failures. */
export function apiErrorStatus(reason: unknown): number | null {
  return (reason as ApiErrorPayload | null)?.response?.status ?? null
}

/**
 * True when a failed request is the server's refusal with `message` (its English text), for a refusal a page explains
 * in its own words. Matched as a message is matched for translation: case, repeated spaces and a final full stop do
 * not matter.
 */
export function hasServerMessage(reason: unknown, message: string): boolean {
  const sent = cleanServerMessage((reason as ApiErrorPayload | null)?.response?.data?.message)
  return sent !== null && messageKey(sent) === messageKey(message)
}
