/**
 * Shared axios instance used by every API service module.
 *
 * Access tokens stay in memory only. The first trusted API 401 attempts
 * a refresh-cookie rotation and retries once; failed refreshes notify
 * the UI so it can clear its in-memory session. Refreshes are taken in
 * turn across the browser's tabs, which share one refresh cookie.
 */
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { appEnv } from '@/config/env'
import { getSessionStorage, safeRemoveItem, safeSetItem, storageKeys } from '@/utils/storage'
import type { ApiResponse, AuthResponse } from '@/types/api'

export const AUTH_INVALIDATED_EVENT = 'mandarin-spark:auth-invalidated'

const API_TIMEOUT_MS = 20_000
export const AUTHORIZATION_HEADER = 'Authorization'
const AUTH_SESSION_ENDPOINTS = new Set(['/auth/login', '/auth/refresh'])
const REFRESH_ENDPOINT = '/auth/refresh'
const REFRESH_LOCK_NAME = 'mandarin-spark.auth-refresh'
/** A 401 this soon after another tab started a refresh may come from that tab rotating the shared cookie. */
const RECENT_OTHER_TAB_REFRESH_MS = 10_000
/** Time for the other tab's response to store the new refresh cookie before the one retry. */
const OTHER_TAB_COOKIE_SETTLE_MS = 300
const ABSOLUTE_OR_PROTOCOL_RELATIVE_URL_PATTERN = /^(?:[a-z][a-z\d+.-]*:)?\/\//i
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

type RetriableRequestConfig = InternalAxiosRequestConfig & { _refreshRetried?: boolean }

export function setAccessToken(token: string | null): void {
  accessToken = token
  safeRemoveItem(storageKeys.token)
  safeRemoveItem(storageKeys.token, getSessionStorage())
}

export function getAccessToken(): string | null {
  return accessToken
}

export function bearer(token: string): string {
  return `Bearer ${token}`
}

/** Joins a request path onto a base URL with exactly one slash between them. */
export function joinUrl(baseUrl: string, path: string): string {
  return `${baseUrl.replace(TRAILING_SLASHES_PATTERN, '')}/${path.replace(LEADING_SLASHES_PATTERN, '')}`
}

/* Interceptors always receive AxiosHeaders, whose names are case-insensitive. */
function setAuthorizationHeader(config: InternalAxiosRequestConfig, token: string): void {
  config.headers.set(AUTHORIZATION_HEADER, bearer(token))
}

/** Every request goes out with a path relative to the API base, e.g. /auth/refresh or /students/dashboard?page=1. */
function isAuthSessionEndpoint(config: InternalAxiosRequestConfig): boolean {
  return AUTH_SESSION_ENDPOINTS.has((config.url ?? '').split(REQUEST_PATH_SPLIT_PATTERN, 1)[0])
}

function resolveAxiosRequestUrl(config: InternalAxiosRequestConfig): string {
  const requestUrl = config.url ?? ''
  const baseUrl = config.baseURL ?? appEnv.apiBaseUrl
  return ABSOLUTE_OR_PROTOCOL_RELATIVE_URL_PATTERN.test(requestUrl) || !baseUrl
    ? requestUrl
    : joinUrl(baseUrl, requestUrl)
}

function isTrustedApiTarget(config: InternalAxiosRequestConfig): boolean {
  try {
    const trustedApiBase = new URL(appEnv.apiBaseUrl, window.location.origin)
    const target = new URL(resolveAxiosRequestUrl(config), window.location.origin)
    const basePath = trustedApiBase.pathname.replace(TRAILING_SLASHES_PATTERN, '')
    return (
      target.origin === trustedApiBase.origin &&
      (!basePath || target.pathname === basePath || target.pathname.startsWith(`${basePath}/`))
    )
  } catch {
    return false
  }
}

export function invalidateAuthSession(): void {
  setAccessToken(null)
  window.dispatchEvent(new CustomEvent(AUTH_INVALIDATED_EVENT))
}

/** When another tab last started a refresh (module memory; learnt from its localStorage marker, see below). */
let otherTabRefreshAt = Number.NEGATIVE_INFINITY

function noteOtherTabRefresh(event: StorageEvent): void {
  if (event.key === storageKeys.refreshMarker && event.newValue) {
    otherTabRefreshAt = Date.now()
  }
}

// A storage event reaches every tab except the one that wrote the marker.
window.addEventListener('storage', noteOtherTabRefresh)

function announceRefresh(): void {
  safeSetItem(storageKeys.refreshMarker, `${Date.now()}-${Math.random()}`)
}

function otherTabRefreshedRecently(): boolean {
  return Date.now() - otherTabRefreshAt <= RECENT_OTHER_TAB_REFRESH_MS
}

async function postRefresh(): Promise<string | null> {
  announceRefresh()
  const { data } = await http.post<ApiResponse<AuthResponse>>(REFRESH_ENDPOINT, undefined, {
    _refreshRetried: true,
  } as RetriableRequestConfig)
  return data.data?.token ?? null
}

/**
 * Another tab may have rotated the shared refresh cookie while this request was on its way: the server answers the
 * just-rotated cookie with a plain 401, and the browser holds the new cookie once that tab's response has arrived.
 * Only then is the refresh sent once more.
 */
async function refreshOnce(): Promise<string | null> {
  try {
    return await postRefresh()
  } catch (error) {
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !otherTabRefreshedRecently()) throw error
    await new Promise((resolve) => setTimeout(resolve, OTHER_TAB_COOKIE_SETTLE_MS))
    return postRefresh()
  }
}

/** Runs one refresh at a time across every tab of this origin; without Web Locks each tab still sends only one. */
function withRefreshLock(task: () => Promise<string | null>): Promise<string | null> {
  // Web Locks exist only in a secure context (HTTPS or localhost), not on a plain-HTTP LAN address.
  const locks = navigator.locks as LockManager | undefined
  return locks ? locks.request(REFRESH_LOCK_NAME, task) : task()
}

/**
 * Trades the refresh cookie for a new access token. Calls in one tab share a single request, and tabs take turns
 * (Web Locks), so the next tab presents the cookie the previous one has just received instead of the rotated one.
 */
export async function refreshAccessToken(): Promise<string | null> {
  refreshPromise ??= withRefreshLock(refreshOnce)
    .then((token) => {
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

  return refreshPromise
}

http.interceptors.request.use((config) => {
  if (!isTrustedApiTarget(config)) {
    config.withCredentials = false
    config.headers.delete(AUTHORIZATION_HEADER)
  } else if (accessToken) {
    setAuthorizationHeader(config, accessToken)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetriableRequestConfig | undefined
    // Only a 401 from this app's API concerns the session; another origin's 401 is passed on untouched.
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !request || !isTrustedApiTarget(request)) {
      return Promise.reject(error)
    }

    if (isAuthSessionEndpoint(request)) {
      setAccessToken(null)
      return Promise.reject(error)
    }

    if (!request._refreshRetried) {
      const refreshedToken = await refreshAccessToken()
      if (refreshedToken) {
        request._refreshRetried = true
        setAuthorizationHeader(request, refreshedToken)
        return http(request)
      }
    }

    invalidateAuthSession()
    return Promise.reject(error)
  },
)
