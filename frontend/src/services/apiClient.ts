import axios from 'axios'

import { APP_EVENTS, dispatchAppEvent } from '../constants/appEvents'
import { STORAGE_KEYS } from '../constants/storageKeys'
import { safeGetItem, safeRemoveItem } from '../utils/safeStorage'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

const PUBLIC_PROTECTED_PATH_PREFIXES = [
  '/student',
  '/teacher',
  '/admin',
  '/notifications',
  '/support-tickets',
  '/payment',
]

const AUTH_ENDPOINT_FRAGMENTS = ['/auth/login', '/auth/register', '/auth/forgot-password', '/auth/reset-password']
const INVALID_NUMERIC_SEGMENT_PATTERN = /(^|[/?=&])(NaN|Infinity|-Infinity)(?=$|[/?=&])/

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

function hasInvalidNumericValue(value: unknown): boolean {
  if (typeof value === 'number') {
    return !Number.isFinite(value)
  }

  if (Array.isArray(value)) {
    return value.some((item) => hasInvalidNumericValue(item))
  }

  if (value && typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).some((item) => hasInvalidNumericValue(item))
  }

  return false
}

apiClient.interceptors.request.use((config) => {
  const token = safeGetItem(STORAGE_KEYS.AUTH_TOKEN)

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  const requestUrl = typeof config.url === 'string' ? config.url : ''
  if (INVALID_NUMERIC_SEGMENT_PATTERN.test(requestUrl) || hasInvalidNumericValue(config.params)) {
    throw new Error('Invalid numeric route or query parameter.')
  }

  return config
})

function isProtectedClientPath(pathname: string) {
  return PUBLIC_PROTECTED_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix))
}

function isAuthRequestUrl(requestUrl: string) {
  return AUTH_ENDPOINT_FRAGMENTS.some((fragment) => requestUrl.includes(fragment))
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    const requestUrl = typeof error.config?.url === 'string' ? error.config.url : ''

    // 401 + non-auth request = session expired. Drop the stale token and bounce
    // the user back to /login with a redirect= query so they land back where
    // they were after re-authenticating.
    if (status === 401 && !isAuthRequestUrl(requestUrl)) {
      safeRemoveItem(STORAGE_KEYS.AUTH_TOKEN)

      if (
        typeof window !== 'undefined' &&
        window.location.pathname !== '/login' &&
        isProtectedClientPath(window.location.pathname)
      ) {
        const redirect = `${window.location.pathname}${window.location.search}`
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
      }
    }

    if (status === 403) {
      // Emit the event WITHOUT a hard-coded English detail so that App.vue can
      // render the correctly translated fallback message based on the active
      // locale.
      dispatchAppEvent(APP_EVENTS.FORBIDDEN)
    }

    return Promise.reject(error)
  },
)
