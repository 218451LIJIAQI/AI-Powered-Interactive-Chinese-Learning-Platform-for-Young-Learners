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
const TRANSPORT_INTERNAL_MESSAGES = new Set(['Request failed', 'Network Error'])

const localizedMessages = {
  en: {
    fallback: 'We could not complete that request. Please try again.',
    unauthorized: 'Please sign in again to continue.',
    forbidden: 'You do not have permission to do that.',
    server: 'The learning service is having trouble right now. Please try again shortly.',
    timeout: 'The learning service took too long to respond. Please try again.',
    network: 'We cannot reach the learning service right now. Check your connection and try again.',
    requestFailed: 'Request failed',
  },
  ms: {
    fallback: 'Permintaan itu tidak dapat diselesaikan. Sila cuba lagi.',
    unauthorized: 'Sila log masuk semula untuk meneruskan.',
    forbidden: 'Anda tiada kebenaran untuk melakukan tindakan itu.',
    server: 'Perkhidmatan pembelajaran sedang menghadapi masalah. Sila cuba sebentar lagi.',
    timeout: 'Perkhidmatan pembelajaran mengambil masa terlalu lama untuk bertindak balas. Sila cuba lagi.',
    network: 'Perkhidmatan pembelajaran tidak dapat dicapai. Semak sambungan anda dan cuba lagi.',
    requestFailed: 'Permintaan gagal',
  },
  zh: {
    fallback: '无法完成该请求，请重试。',
    unauthorized: '请重新登录后继续。',
    forbidden: '你无权执行此操作。',
    server: '学习服务目前出现问题，请稍后重试。',
    timeout: '学习服务响应超时，请重试。',
    network: '目前无法连接学习服务，请检查网络连接后重试。',
    requestFailed: '请求失败',
  },
} as const

function currentMessages(): (typeof localizedMessages)[keyof typeof localizedMessages] {
  const language = typeof document === 'undefined' ? 'en' : document.documentElement.lang.toLowerCase()
  if (language.startsWith('zh')) return localizedMessages.zh
  if (language.startsWith('ms')) return localizedMessages.ms
  return localizedMessages.en
}

export function defaultRequestError(): string {
  return currentMessages().requestFailed
}

function cleanServerMessage(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }

  const trimmed = value.replace(UNSAFE_MESSAGE_CHARACTERS, ' ').replace(/\s+/g, ' ').trim()
  if (!trimmed || TRANSPORT_INTERNAL_MESSAGES.has(trimmed)) {
    return null
  }

  return trimmed.length > MAX_USER_MESSAGE_LENGTH ? `${trimmed.slice(0, MAX_USER_MESSAGE_LENGTH - 3)}...` : trimmed
}

export function resolveApiError(reason: unknown, fallback?: string): string {
  const messages = currentMessages()
  const resolvedFallback = fallback ?? messages.fallback
  const payload = reason as ApiErrorPayload | null
  const serverMessage = cleanServerMessage(payload?.response?.data?.message)
  const serverError = cleanServerMessage(payload?.response?.data?.errors?.[0])

  if (serverMessage) return serverMessage
  if (serverError) return serverError

  const status = payload?.response?.status
  if (status === 401) return messages.unauthorized
  if (status === 403) return messages.forbidden
  if (status === 404) return resolvedFallback
  if (status && status >= 500) return messages.server

  const rawMessage = payload?.message ?? ''
  const timedOut = payload?.code === 'ECONNABORTED' || /timeout/i.test(rawMessage)
  if (timedOut) return messages.timeout

  if (payload?.request && !payload?.response) {
    return messages.network
  }

  return resolvedFallback
}

export function isUnauthorizedApiError(reason: unknown): boolean {
  return (reason as ApiErrorPayload | null)?.response?.status === 401
}
