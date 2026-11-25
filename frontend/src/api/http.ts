/**
 * Access tokens stay in memory. A protected request can retry once with a fresh token.
 * Rejected credentials end the session. Service failures leave it available for retry.
 * Browser tabs share the refresh cookie and use Web Locks, when available, to take turns rotating it.
 */
import axios, { CanceledError, type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { apiClientMessage } from '@/plugins/apiClientMessages'
import { ApiEnvelopeError } from '@/utils/apiError'
import { safeSetItem, sessionOwner, storageKeys } from '@/utils/storage'
import type { ApiResponse, AuthResponse } from '@/types/api'

/** Build-time `VITE_API_BASE_URL` (`.env`); the API is same-origin unless set otherwise. */
const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export const AUTH_INVALIDATED_EVENT = 'mandarin-spark:auth-invalidated'
/**
 * The API answered 503 (or a proxy 502/504). The UI then asks the health endpoint and opens the maintenance page only
 * when that fails too (router/redirects.ts): a 503 about one feature leaves the user on their page.
 */
export const SERVICE_UNAVAILABLE_EVENT = 'mandarin-spark:service-unavailable'

export const AUTHORIZATION_HEADER = 'Authorization'
const REFRESH_ENDPOINT = '/auth/refresh'
/** Their 401 is the answer itself (wrong password, rejected sign-in hydration, expired refresh cookie, already-signed-out logout), not a reason to refresh or invalidate a concurrent sign-in. */
const AUTH_SESSION_ENDPOINTS = new Set(['/auth/login', '/auth/logout', '/auth/me', REFRESH_ENDPOINT])
/** The check behind the maintenance page: its own 503 is never reported, so the check cannot start itself again. */
const HEALTH_ENDPOINT = '/health'
/**
 * Public endpoints that do not require an authenticated session and must not send a bearer token or have their
 * already-committed 2xx responses canceled if another tab signs in or out while the request is in flight.
 */
const PUBLIC_UNAUTHENTICATED_ENDPOINTS = new Set([
  '/auth/register/student',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/reset-password/check',
  '/auth/confirm-email',
  '/auth/confirm-signup-email',
  '/auth/admin-invite/check',
  '/auth/admin-invite/accept',
])
const REFRESH_LOCK_NAME = 'mandarin-spark.auth-refresh'
/** A 401 this soon after another tab started a refresh may come from that tab rotating the shared cookie. */
const RECENT_OTHER_TAB_REFRESH_MS = 10_000
/** Time for the other tab's response to store the new refresh cookie before the one retry. */
const OTHER_TAB_COOKIE_SETTLE_MS = 300

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20_000,
  withCredentials: true,
})

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null
// Explicit sign-in, sign-out and password-token replacement end ownership of older requests.
// A routine refresh rotates the token within the same session and does not advance this version.
let authSessionVersion = 0
/** When another tab last started a refresh, learnt from the marker it writes to localStorage (see postRefresh). */
let otherTabRefreshAt = Number.NEGATIVE_INFINITY

type RetriableRequestConfig = InternalAxiosRequestConfig & { _refreshRetried?: boolean; _authSessionVersion?: number }

export function setAccessToken(token: string | null): void {
  authSessionVersion += 1
  refreshPromise = null
  otherTabRefreshAt = Number.NEGATIVE_INFINITY
  accessToken = token
}

export function getAuthSessionVersion(): number {
  return authSessionVersion
}

export function getAccessToken(): string | null {
  return accessToken
}

export function bearer(token: string): string {
  return `Bearer ${token}`
}

/** Whether a trusted API path is a public unauthenticated endpoint (no bearer header or session-version cancellation). */
function isPublicUnauthenticatedPath(path: string): boolean {
  return path === HEALTH_ENDPOINT || PUBLIC_UNAUTHENTICATED_ENDPOINTS.has(path) || path.startsWith('/public/')
}

/** Normalized `sub` claim of a 3-part JWT, or null for non-JWT test tokens or unreadable payloads. */
function jwtSubject(token: string | null): string | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return null
  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0))
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { sub?: unknown }
    return typeof payload.sub === 'string' && payload.sub.trim() ? payload.sub.trim().toLowerCase() : null
  } catch {
    return null
  }
}

/** The resolved path within this API, or null when the request goes elsewhere. */
function trustedApiPath(config: InternalAxiosRequestConfig): string | null {
  try {
    const trustedApiBase = new URL(API_BASE_URL, window.location.origin)
    // The URL axios sends the request to (its baseURL and url, either of which a request may override).
    const target = new URL(http.getUri(config), window.location.origin)
    const basePath = trustedApiBase.pathname.replace(/\/+$/, '')
    if (target.origin !== trustedApiBase.origin) return null
    if (target.pathname === basePath) return '/'
    return target.pathname.startsWith(`${basePath}/`) ? target.pathname.slice(basePath.length) : null
  } catch {
    return null
  }
}

export function requireCurrentSession(version: number): void {
  if (version !== authSessionVersion) throw new CanceledError('The request session has ended.')
}

export function invalidateAuthSession(expectedVersion: number): void {
  if (expectedVersion !== authSessionVersion) return
  setAccessToken(null)
  window.dispatchEvent(new CustomEvent(AUTH_INVALIDATED_EVENT))
}

// A storage event reaches every tab except the one that wrote the marker.
window.addEventListener('storage', (event) => {
  if (event.key === storageKeys.refreshMarker && event.newValue) otherTabRefreshAt = Date.now()
})

async function postRefresh(): Promise<string | null> {
  // A sign-out ends local access even if its request could not clear the HttpOnly cookie.
  if (sessionOwner() === null) return null
  safeSetItem(storageKeys.refreshMarker, `${Date.now()}-${Math.random()}`)
  const { data } = await http.post<ApiResponse<AuthResponse>>(REFRESH_ENDPOINT)
  const token = data?.data?.token
  if (data?.success !== true || typeof token !== 'string' || !token.trim()) {
    throw new ApiEnvelopeError(apiClientMessage('unexpectedFormat'))
  }
  return token
}

/**
 * Another tab may have rotated the shared refresh cookie while this request was on its way: the server answers the
 * just-rotated cookie with a plain 401, and the browser holds the new cookie once that tab's response has arrived.
 * Only then is the refresh sent once more.
 */
async function refreshOnce(version: number): Promise<string | null> {
  // A refresh can spend time waiting for another tab's Web Lock.
  if (version !== authSessionVersion) return null
  try {
    return await postRefresh()
  } catch (error) {
    if (version !== authSessionVersion) return null
    const otherTabRefreshedRecently = Date.now() - otherTabRefreshAt <= RECENT_OTHER_TAB_REFRESH_MS
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !otherTabRefreshedRecently) throw error
    await new Promise((resolve) => setTimeout(resolve, OTHER_TAB_COOKIE_SETTLE_MS))
    return version === authSessionVersion ? postRefresh() : null
  }
}

async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  // Web Locks exist only in a secure context (HTTPS or localhost), and browsers with restricted site-storage
  // permissions can throw or reject with SecurityError or NotSupportedError; fall back to single-tab refresh in both cases.
  let locks: LockManager | undefined
  try {
    locks = navigator.locks as LockManager | undefined
  } catch {
    return task()
  }
  if (!locks?.request) return task()
  let invoked = false
  const run = () => {
    invoked = true
    return task()
  }
  try {
    return await locks.request(REFRESH_LOCK_NAME, run)
  } catch (error: unknown) {
    const name = (error as { name?: unknown } | null)?.name
    if (!invoked && (name === 'SecurityError' || name === 'NotSupportedError')) {
      return task()
    }
    throw error
  }
}

/**
 * Trades the refresh cookie for a new access token. A 401 caller supplies the Authorization it sent so a late
 * rejection can reuse a token already rotated in this session. Calls in one tab share a request, and tabs take turns
 * (Web Locks), so the next tab presents the cookie the previous one has just received. An ended session returns null.
 * A transport or service failure rejects, leaving the session available for retry.
 */
export async function refreshAccessToken(
  rejectedAuthorization?: string | null,
  options?: { fresh?: boolean },
): Promise<string | null> {
  const version = authSessionVersion
  if (options?.fresh && refreshPromise) {
    await refreshPromise.catch(() => null)
    if (version !== authSessionVersion) return null
  }
  if (refreshPromise) {
    return refreshPromise
  }
  if (
    !options?.fresh &&
    rejectedAuthorization !== undefined &&
    accessToken &&
    rejectedAuthorization !== bearer(accessToken)
  ) {
    return sessionOwner() === null ? null : accessToken
  }
  const expectedSubject = jwtSubject(accessToken)
  const pending = withRefreshLock(() => refreshOnce(version))
    .then((token) => {
      if (version !== authSessionVersion) return null
      if (sessionOwner() === null) {
        accessToken = null
        return null
      }
      // Guard against silent cross-account credential swap when another tab overwrote the shared refresh cookie
      // and localStorage notifications were unavailable, delayed, or cleared.
      const nextSubject = jwtSubject(token)
      if (expectedSubject !== null && nextSubject !== null && expectedSubject !== nextSubject) {
        accessToken = null
        return null
      }
      accessToken = token
      return accessToken
    })
    .catch((error: unknown) => {
      if (version !== authSessionVersion) return null
      if (!axios.isAxiosError(error) || error.response?.status !== 401) throw error
      accessToken = null
      return null
    })
    .finally(() => {
      if (refreshPromise === pending) refreshPromise = null
    })

  refreshPromise = pending
  return pending
}

http.interceptors.request.use(
  (config: RetriableRequestConfig) => {
    const path = trustedApiPath(config)
    // External origins and public unauthenticated endpoints (including health probes and single-use email/reset links)
    // must not carry bearer credentials or have already-committed responses canceled by cross-tab session changes.
    if (path === null || isPublicUnauthenticatedPath(path)) {
      config.withCredentials = false
      config.headers.delete(AUTHORIZATION_HEADER)
    } else {
      config._authSessionVersion ??= authSessionVersion
      requireCurrentSession(config._authSessionVersion)
      // Refresh authenticates via the HttpOnly cookie; skip sending an expired or stale bearer header.
      if (path === REFRESH_ENDPOINT) {
        config.headers.delete(AUTHORIZATION_HEADER)
      } else if (accessToken) {
        config.headers.set(AUTHORIZATION_HEADER, bearer(accessToken))
      }
    }
    return config
  },
  (error: unknown) => {
    throw error
  },
  // Capture the session when the caller starts the request, before any subsequent sign-in.
  { synchronous: true },
)

/**
 * Answers that can mean the whole service is down: 503 from the backend, and 502/504 from a proxy in front of a backend
 * that is not answering. They only start the health check; the maintenance page opens when that check fails too.
 */
const SERVICE_DOWN_STATUSES: ReadonlySet<number> = new Set([502, 503, 504])

/**
 * Ask the UI to check service health after a possible outage on an API request.
 */
export function reportServiceDown(status: number): void {
  if (SERVICE_DOWN_STATUSES.has(status)) {
    window.dispatchEvent(new CustomEvent(SERVICE_UNAVAILABLE_EVENT))
  }
}

http.interceptors.response.use(
  (response) => {
    const { _authSessionVersion: version } = response.config as RetriableRequestConfig
    if (version !== undefined) requireCurrentSession(version)
    return response
  },
  async (error: AxiosError) => {
    const request = error.config as RetriableRequestConfig | undefined
    const status = error.response?.status
    // Only answers from this app's API are handled here; anything else is passed on untouched.
    if (status === undefined || !request) throw error
    const path = trustedApiPath(request)
    if (path === null || path === HEALTH_ENDPOINT) throw error
    reportServiceDown(status)
    if (status !== 401 || AUTH_SESSION_ENDPOINTS.has(path) || isPublicUnauthenticatedPath(path)) throw error

    const version = request._authSessionVersion
    if (version !== authSessionVersion) throw error

    if (!request._refreshRetried) {
      const authorization = request.headers.get(AUTHORIZATION_HEADER)?.toString() ?? null
      const refreshedToken = await refreshAccessToken(authorization)
      if (version !== authSessionVersion) throw error
      if (refreshedToken) {
        request._refreshRetried = true
        return http(request)
      }
    }

    invalidateAuthSession(version)
    throw error
  },
)
