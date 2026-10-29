import { apiClientMessage } from '@/plugins/apiClientMessages'
import { documentLanguage } from '@/utils/locale'
import { localizeServerMessage } from '@/utils/serverMessages'

type ApiErrorPayload = {
  response?: {
    status?: number
    data?: {
      message?: unknown
      errors?: unknown[]
      fieldErrors?: Record<string, unknown>
    }
  }
  request?: unknown
  code?: string
  message?: string
}

const MAX_USER_MESSAGE_LENGTH = 280
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

  return trimmed.length > MAX_USER_MESSAGE_LENGTH ? `${trimmed.slice(0, MAX_USER_MESSAGE_LENGTH - 3)}...` : trimmed
}

/**
 * The message to show for a failed request (SDD Table 6.3). A readable server message wins; otherwise the HTTP status
 * or transport failure picks a generic message. `fallback` is the caller's text for a missing item (404) and for any
 * other failure without a more specific message.
 */
export function resolveApiError(reason: unknown, fallback?: string): string {
  const payload = reason as ApiErrorPayload | null
  const serverMessage = cleanServerMessage(payload?.response?.data?.message)
  const serverError = cleanServerMessage(payload?.response?.data?.errors?.[0])

  // FRLANG02: known backend messages are shown in the interface language; others stay as sent.
  const locale = documentLanguage()
  if (serverMessage) return localizeServerMessage(serverMessage, locale)
  if (serverError) return localizeServerMessage(serverError, locale)

  const status = payload?.response?.status
  if (status === 401) return apiClientMessage('unauthorized')
  if (status === 403) return apiClientMessage('forbidden')
  if (status === 404) return fallback ?? apiClientMessage('notFound')
  if (status === 429) return apiClientMessage('rateLimited')
  if (status && status >= 500) return apiClientMessage('server')

  const rawMessage = payload?.message ?? ''
  const timedOut = payload?.code === 'ECONNABORTED' || /timeout/i.test(rawMessage)
  if (timedOut) return apiClientMessage('timeout')

  if (payload?.request && !payload?.response) {
    return apiClientMessage('network')
  }

  return fallback ?? apiClientMessage('fallback')
}

/**
 * The first server validation message for one request field (`fieldErrors` of a 400 answer), in the interface language,
 * or null when the server sent none for that field. Lets a form show the server's rule next to the field it concerns.
 */
export function apiFieldError(reason: unknown, field: string): string | null {
  const messages = (reason as ApiErrorPayload | null)?.response?.data?.fieldErrors?.[field]
  const message = Array.isArray(messages) ? cleanServerMessage(messages[0]) : null
  return message ? localizeServerMessage(message, documentLanguage()) : null
}

/** HTTP status of a failed request, or null for network errors and non-HTTP failures. */
export function apiErrorStatus(reason: unknown): number | null {
  return (reason as ApiErrorPayload | null)?.response?.status ?? null
}
