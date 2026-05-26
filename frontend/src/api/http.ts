/**
 * Shared axios instance used by every API service module.
 *
 * <h2>Token attachment</h2>
 * <p>The access token is kept in memory only. Login / refresh responses
 * update this module-level value; nothing writes the bearer token to
 * localStorage or sessionStorage.</p>
 *
 * <h2>401 handling</h2>
 * <p>The first 401 attempts a refresh-cookie rotation, then retries the
 * original request with the new access token. If refresh fails, the
 * store is notified so the UI can clear the in-memory session.</p>
 *
 * <h2>Timeout</h2>
 * <p>20 seconds is an upper bound chosen for the AI endpoints; faster
 * calls return well before this and slow timeouts are surfaced as
 * a learner-friendly "took too long" message by {@code apiError.ts}.</p>
 */
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { appEnv } from '@/config/env'
import { getSessionStorage, safeRemoveItem, storageKeys } from '@/utils/storage'
import type { ApiResponse, AuthResponse } from '@/types/api'

export const AUTH_INVALIDATED_EVENT = 'mandarin-spark:auth-invalidated'

export const http = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: 20000,
  withCredentials: true,
})

const AUTH_SESSION_ENDPOINTS = new Set(['/auth/login', '/auth/refresh'])
let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _refreshRetried?: boolean
}

export function setAccessToken(token: string | null) {
  accessToken = token
  safeRemoveItem(storageKeys.token)
  safeRemoveItem(storageKeys.token, getSessionStorage())
}

export function getAccessToken() {
  return accessToken
}

function normalizePath(value: string | undefined): string {
  if (!value) {
    return ''
  }

  try {
    if (/^[a-z][a-z\d+.-]*:/i.test(value)) {
      return new URL(value).pathname
    }
  } catch {
    return ''
  }

  const path = value.split(/[?#]/, 1)[0] ?? ''
  return path.startsWith('/') ? path : `/${path}`
}

function stripApiBasePath(path: string): string {
  const basePath = normalizePath(appEnv.apiBaseUrl).replace(/\/+$/, '')
  if (!basePath || basePath === '/') {
    return path
  }

  if (path === basePath) {
    return '/'
  }

  return path.startsWith(`${basePath}/`) ? path.slice(basePath.length) : path
}

function isAuthSessionEndpoint(config: RetriableRequestConfig | undefined): boolean {
  return AUTH_SESSION_ENDPOINTS.has(stripApiBasePath(normalizePath(config?.url)))
}

function dispatchAuthInvalidated() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_INVALIDATED_EVENT))
  }
}

export function invalidateAuthSession() {
  setAccessToken(null)
  dispatchAuthInvalidated()
}

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = http
      .post<ApiResponse<AuthResponse>>('/auth/refresh', undefined, { _refreshRetried: true } as RetriableRequestConfig)
      .then((response) => {
        const token = response.data.data?.token ?? null
        setAccessToken(token)
        return token
      })
      .catch(() => {
        setAccessToken(null)
        return null
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const originalRequest = error.config as RetriableRequestConfig | undefined
      const isAuthEndpoint = isAuthSessionEndpoint(originalRequest)
      const shouldAttemptRefresh = originalRequest && !originalRequest._refreshRetried && !isAuthEndpoint

      if (shouldAttemptRefresh) {
        const refreshedToken = await refreshAccessToken()
        if (refreshedToken) {
          originalRequest._refreshRetried = true
          originalRequest.headers.Authorization = `Bearer ${refreshedToken}`
          return http(originalRequest)
        }
      }

      if (!isAuthEndpoint) {
        invalidateAuthSession()
      } else {
        setAccessToken(null)
      }
    }

    return Promise.reject(error)
  },
)
