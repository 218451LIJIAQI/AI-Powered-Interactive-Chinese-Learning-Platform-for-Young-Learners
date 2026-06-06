const TRUE_BOOLEAN_VALUES = new Set(['1', 'true', 'yes', 'on'])
const DEFAULT_API_BASE_URL = '/api/v1'
const API_BASE_ENV_KEY = 'VITE_API_BASE_URL'
const UNSAFE_URL_CODE_POINT_PATTERN = /[\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff]/
const SAFE_API_PROTOCOLS = new Set(['http:', 'https:'])

function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  const normalized = value?.trim().toLowerCase()

  if (!normalized) {
    return fallback
  }

  return TRUE_BOOLEAN_VALUES.has(normalized)
}

function apiBaseUrlError(message: string, cause?: unknown): Error {
  const errorMessage = `${API_BASE_ENV_KEY} ${message}`
  return cause === undefined ? new Error(errorMessage) : new Error(errorMessage, { cause })
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

  try {
    const url = new URL(trimmed)
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
  } catch (error) {
    if (error instanceof Error && error.message.startsWith(API_BASE_ENV_KEY)) {
      throw error
    }
    throw apiBaseUrlError('must be an absolute http(s) URL or a same-origin path.', error)
  }
}

export const appEnv = {
  apiBaseUrl: normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
  useDemoData: parseBoolean(import.meta.env.VITE_USE_DEMO_DATA, false),
  enablePreviewMode: parseBoolean(import.meta.env.VITE_ENABLE_PREVIEW_MODE, false),
  sentryDsn: import.meta.env.VITE_SENTRY_DSN || '',
  release: import.meta.env.VITE_APP_RELEASE || import.meta.env.VITE_GIT_SHA || '',
  environment: import.meta.env.MODE || 'local',
  appName: 'MandarinSpark',
} as const
