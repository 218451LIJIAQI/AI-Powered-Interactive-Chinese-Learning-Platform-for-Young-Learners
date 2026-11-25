import { isCancel } from 'axios'

import { apiClientMessage } from '@/plugins/apiClientMessages'
import { documentLanguage } from '@/utils/locale'
import { cleanServerMessage, localizeServerMessage, messageKey } from '@/utils/serverMessages'

export { cleanServerMessage }

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

/** Maps an HTTP status code to its generic SDD Table 6.3 message, or null when no status-specific message applies. */
export function httpStatusMessage(status: number | undefined, fallback?: string): string | null {
  if (status === 401) return apiClientMessage('unauthorized')
  if (status === 403) return apiClientMessage('forbidden')
  if (status === 404) return fallback ?? apiClientMessage('notFound')
  if (status === 429) return apiClientMessage('rateLimited')
  if (status && status >= 500) return apiClientMessage('server')
  return null
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

  const statusMessage = httpStatusMessage(payload?.response?.status, fallback)
  if (statusMessage) return statusMessage

  // Axios marks a timed-out request with ECONNABORTED or ETIMEDOUT.
  if (payload?.code === 'ECONNABORTED' || payload?.code === 'ETIMEDOUT') return apiClientMessage('timeout')

  if (isNetworkError(reason)) {
    return apiClientMessage('network')
  }

  return fallback ?? apiClientMessage('fallback')
}

/** True when a request failed at the transport layer with no HTTP response, and was neither cancelled nor timed out. */
export function isNetworkError(reason: unknown): boolean {
  if (isCancel(reason)) return false
  const payload = reason as ApiErrorPayload | null
  if (payload?.code === 'ERR_CANCELED' || payload?.code === 'ECONNABORTED' || payload?.code === 'ETIMEDOUT') {
    return false
  }
  return Boolean(payload?.request) && !payload?.response
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
