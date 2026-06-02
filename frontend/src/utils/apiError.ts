/**
 * Centralised translation of `axios`-style error payloads into
 * learner-friendly text. Every API caller funnels through
 * {@link resolveApiError} so the same error shape always produces the
 * same on-screen message — preventing inconsistent tone across pages.
 *
 * The resolution order is:
 * 1. Backend-supplied {@code response.data.message} (sanitised by the
 *    {@code GlobalExceptionHandler}'s {@code FriendlyErrorMessageResolver}).
 * 2. First entry in {@code response.data.errors[]} (validation case).
 * 3. HTTP-status-based fallbacks (401, 403, 404, 5xx).
 * 4. Timeout / network detection from axios codes.
 * 5. Caller-supplied {@code fallback} string.
 *
 * Strings like {@code "Request failed"} and {@code "Network Error"}
 * are dropped because they leak axios internals to learners.
 */
type ApiErrorPayload = {
  response?: {
    status?: number
    data?: {
      message?: unknown
      errors?: unknown[]
    }
  }
  request?: unknown
  code?: string
  message?: string
}

const MAX_USER_MESSAGE_LENGTH = 280
const UNSAFE_MESSAGE_CHARACTERS = /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g

function cleanServerMessage(value: unknown) {
  if (typeof value !== 'string') {
    return null
  }

  const trimmed = value.replace(UNSAFE_MESSAGE_CHARACTERS, ' ').replace(/\s+/g, ' ').trim()
  if (!trimmed || trimmed === 'Request failed' || trimmed === 'Network Error') {
    return null
  }

  return trimmed.length > MAX_USER_MESSAGE_LENGTH ? `${trimmed.slice(0, MAX_USER_MESSAGE_LENGTH - 3)}...` : trimmed
}

export function resolveApiError(reason: unknown, fallback = 'We could not complete that request. Please try again.') {
  const payload = reason as ApiErrorPayload | null
  const serverMessage = cleanServerMessage(payload?.response?.data?.message)
  const serverError = cleanServerMessage(payload?.response?.data?.errors?.[0])

  if (serverMessage) return serverMessage
  if (serverError) return serverError

  const status = payload?.response?.status
  if (status === 401) return 'Please sign in again to continue.'
  if (status === 403) return 'You do not have permission to do that.'
  if (status === 404) return fallback
  if (status && status >= 500) return 'The learning service is having trouble right now. Please try again shortly.'

  const rawMessage = payload?.message ?? ''
  const timedOut = payload?.code === 'ECONNABORTED' || /timeout/i.test(rawMessage)
  if (timedOut) return 'The learning service took too long to respond. Please try again.'

  if (payload?.request && !payload?.response) {
    return 'We cannot reach the learning service right now. Check your connection and try again.'
  }

  return fallback
}

export function isUnauthorizedApiError(reason: unknown) {
  return (reason as ApiErrorPayload | null)?.response?.status === 401
}
