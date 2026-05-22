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

function cleanServerMessage(value: unknown) {
  if (typeof value !== 'string') {
    return null
  }

  const trimmed = value.trim()
  if (!trimmed || trimmed === 'Request failed' || trimmed === 'Network Error') {
    return null
  }

  return trimmed
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
