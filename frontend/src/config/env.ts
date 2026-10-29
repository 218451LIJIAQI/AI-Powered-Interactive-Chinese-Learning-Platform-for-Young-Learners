const DEFAULT_API_BASE_URL = '/api/v1'
const API_BASE_ENV_KEY = 'VITE_API_BASE_URL'
const UNSAFE_URL_CODE_POINT_PATTERN = /[\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff]/
const SAFE_API_PROTOCOLS = new Set(['http:', 'https:'])

function apiBaseUrlError(message: string, options?: ErrorOptions): Error {
  return new Error(`${API_BASE_ENV_KEY} ${message}`, options)
}

export function normalizeApiBaseUrl(value: string | undefined): string {
  const trimmed = value?.trim() || DEFAULT_API_BASE_URL

  if (UNSAFE_URL_CODE_POINT_PATTERN.test(trimmed) || trimmed.includes('\\')) {
    throw apiBaseUrlError('contains unsafe URL characters.')
  }

  if (trimmed.startsWith('//')) {
    throw apiBaseUrlError('must not be protocol-relative.')
  }

  if (trimmed.startsWith('/')) {
    return trimmed.replace(/\/+$/, '') || '/'
  }

  let url: URL
  try {
    url = new URL(trimmed)
  } catch (error) {
    throw apiBaseUrlError('must be an absolute http(s) URL or a same-origin path.', { cause: error })
  }
  if (!SAFE_API_PROTOCOLS.has(url.protocol)) {
    throw apiBaseUrlError('must use http or https.')
  }
  if (url.username || url.password) {
    throw apiBaseUrlError('must not contain credentials.')
  }
  if (url.search || url.hash) {
    throw apiBaseUrlError('must not contain query strings or fragments.')
  }
  return url.href.replace(/\/+$/, '')
}

export const appEnv = {
  apiBaseUrl: normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
  sentryDsn: import.meta.env.VITE_SENTRY_DSN || '',
  release: import.meta.env.VITE_APP_RELEASE || '',
  environment: import.meta.env.MODE,
} as const
