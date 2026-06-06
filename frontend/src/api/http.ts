/**
 * Shared axios instance used by every API service module.
 *
 * Access tokens stay in memory only. The first trusted API 401 attempts
 * a refresh-cookie rotation and retries once; failed refreshes notify
 * the UI so it can clear its in-memory session.
 */
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { appEnv } from '@/config/env'
import { getSessionStorage, safeRemoveItem, storageKeys } from '@/utils/storage'
import type { ApiResponse, AuthResponse } from '@/types/api'

export const AUTH_INVALIDATED_EVENT = 'mandarin-spark:auth-invalidated'

const API_TIMEOUT_MS = 20_000
const AUTHORIZATION_HEADER = 'Authorization'
const AUTH_SESSION_ENDPOINTS = new Set(['/auth/login', '/auth/refresh'])
const FALLBACK_ORIGIN = 'http://localhost'
const REFRESH_ENDPOINT = '/auth/refresh'
const ABSOLUTE_OR_PROTOCOL_RELATIVE_URL_PATTERN = /^(?:[a-z][a-z\d+.-]*:)?\/\//i
const ABSOLUTE_URL_PROTOCOL_PATTERN = /^[a-z][a-z\d+.-]*:/i
const LEADING_SLASHES_PATTERN = /^\/+/
const REQUEST_PATH_SPLIT_PATTERN = /[?#]/
const TRAILING_SLASHES_PATTERN = /\/+$/

export const http = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: API_TIMEOUT_MS,
  withCredentials: true,
})

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

interface RefreshControlConfig {
  _refreshRetried?: boolean
}

type RetriableRequestConfig = InternalAxiosRequestConfig & RefreshControlConfig
type MutableRequestHeaders = InternalAxiosRequestConfig['headers'] & {
  Authorization?: string
  authorization?: string
  delete?: (name: string) => unknown
  set?: (name: string, value: string) => unknown
}

export function setAccessToken(token: string | null): void {
  accessToken = token
  safeRemoveItem(storageKeys.token)
  safeRemoveItem(storageKeys.token, getSessionStorage())
}

export function getAccessToken(): string | null {
  return accessToken
}

function bearer(token: string): string {
  return `Bearer ${token}`
}

function browserOrigin(): string {
  return typeof window === 'undefined' ? FALLBACK_ORIGIN : window.location.origin
}

function clearAuthorizationHeader(config: InternalAxiosRequestConfig): void {
  const headers = config.headers as MutableRequestHeaders
  if (typeof headers.delete === 'function') {
    headers.delete(AUTHORIZATION_HEADER)
    return
  }

  delete headers.Authorization
  delete headers.authorization
}

function setAuthorizationHeader(config: InternalAxiosRequestConfig, token: string): void {
  const headers = config.headers as MutableRequestHeaders
  if (typeof headers.set === 'function') {
    headers.set(AUTHORIZATION_HEADER, bearer(token))
    return
  }

  headers.Authorization = bearer(token)
}

function normalizePath(value: string | undefined): string {
  if (!value) {
    return ''
  }

  try {
    if (ABSOLUTE_URL_PROTOCOL_PATTERN.test(value)) {
      return new URL(value).pathname
    }
  } catch {
    return ''
  }

  const path = value.split(REQUEST_PATH_SPLIT_PATTERN, 1)[0] ?? ''
  return path.startsWith('/') ? path : `/${path}`
}

function stripApiBasePath(path: string): string {
  const basePath = normalizePath(appEnv.apiBaseUrl).replace(TRAILING_SLASHES_PATTERN, '')
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

function resolveAxiosRequestUrl(config: InternalAxiosRequestConfig): string {
  const requestUrl = config.url ?? ''
  if (ABSOLUTE_OR_PROTOCOL_RELATIVE_URL_PATTERN.test(requestUrl)) {
    return requestUrl
  }

  const baseUrl = config.baseURL ?? appEnv.apiBaseUrl
  if (!baseUrl) {
    return requestUrl
  }

  return `${baseUrl.replace(TRAILING_SLASHES_PATTERN, '')}/${requestUrl.replace(LEADING_SLASHES_PATTERN, '')}`
}

function isTrustedApiTarget(config: InternalAxiosRequestConfig): boolean {
  try {
    const trustedApiBase = new URL(appEnv.apiBaseUrl, browserOrigin())
    const target = new URL(resolveAxiosRequestUrl(config), browserOrigin())
    const basePath = trustedApiBase.pathname.replace(TRAILING_SLASHES_PATTERN, '')
    return (
      target.origin === trustedApiBase.origin &&
      (!basePath || basePath === '/' || target.pathname === basePath || target.pathname.startsWith(`${basePath}/`))
    )
  } catch {
    return false
  }
}

function dispatchAuthInvalidated(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_INVALIDATED_EVENT))
  }
}

export function invalidateAuthSession(): void {
  setAccessToken(null)
  dispatchAuthInvalidated()
}

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = http
      .post<ApiResponse<AuthResponse>>(REFRESH_ENDPOINT, undefined, {
        _refreshRetried: true,
      } as RetriableRequestConfig)
      .then(({ data }) => {
        const token = data.data?.token ?? null
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
  if (!isTrustedApiTarget(config)) {
    config.withCredentials = false
    clearAuthorizationHeader(config)
    return config
  }

  if (accessToken) {
    setAuthorizationHeader(config, accessToken)
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const originalRequest = error.config as RetriableRequestConfig | undefined
      const isTrustedTarget = originalRequest ? isTrustedApiTarget(originalRequest) : false
      const isAuthEndpoint = isAuthSessionEndpoint(originalRequest)
      const shouldAttemptRefresh =
        originalRequest && isTrustedTarget && !originalRequest._refreshRetried && !isAuthEndpoint

      if (shouldAttemptRefresh) {
        const refreshedToken = await refreshAccessToken()
        if (refreshedToken) {
          originalRequest._refreshRetried = true
          setAuthorizationHeader(originalRequest, refreshedToken)
          return http(originalRequest)
        }
      }

      if (isTrustedTarget && !isAuthEndpoint) {
        invalidateAuthSession()
      } else if (isTrustedTarget) {
        setAccessToken(null)
      }
    }

    return Promise.reject(error)
  },
)
