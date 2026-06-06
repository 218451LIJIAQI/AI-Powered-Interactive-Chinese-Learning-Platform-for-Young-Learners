import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AUTH_INVALIDATED_EVENT, getAccessToken, http, setAccessToken } from './http'

const APP_SESSION_TOKEN = 'app-session-token'
const BUSINESS_LOGIN_HISTORY_URL = '/students/auth/login-history'
const DASHBOARD_URL = '/students/dashboard'
const EXTERNAL_BASE_URL = 'https://example.invalid/api'
const EXTERNAL_PROTECTED_URL = 'https://example.invalid/protected'
const EXTERNAL_URL = 'https://example.invalid/external'
const FRESH_TOKEN = 'fresh-token'
const PROTOCOL_RELATIVE_EXTERNAL_URL = '//example.invalid/external'
const REFRESH_URL = '/auth/refresh'
const ROTATED_TOKEN = 'rotated-token'
const SECRET_TOKEN = 'secret-token'
const STALE_TOKEN = 'stale-token'

const originalAdapter = http.defaults.adapter

const bearer = (token: string) => `Bearer ${token}`

function apiResponse<T>(config: InternalAxiosRequestConfig, status: number, data: T): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status >= 400 ? 'Error' : 'OK',
    headers: {},
    config,
  }
}

function adapterResponse<T>(
  config: InternalAxiosRequestConfig,
  status: number,
  data: T,
): AxiosResponse<T> {
  const response = apiResponse(config, status, data)
  if (status >= 400) {
    throw new AxiosError(
      `Request failed with status code ${status}`,
      status === 401 ? AxiosError.ERR_BAD_REQUEST : undefined,
      config,
      undefined,
      response,
    )
  }
  return response
}

function authExpired(config: InternalAxiosRequestConfig, message = 'expired') {
  return adapterResponse(config, 401, { success: false, message })
}

function authorization(config: InternalAxiosRequestConfig): string | null {
  const headers = config.headers as unknown as { get?: (name: string) => string | null; Authorization?: string }
  return headers.get?.('Authorization') ?? headers.Authorization ?? null
}

function ok<T>(config: InternalAxiosRequestConfig, data: T) {
  return adapterResponse(config, 200, data)
}

function tokenResponse(config: InternalAxiosRequestConfig, token: string) {
  return ok(config, { success: true, data: { token } })
}

async function expectExternalRequestWithoutCredentials(url: string) {
  let externalAuthorization: string | null = 'not-called'
  let externalWithCredentials: boolean | undefined

  http.defaults.adapter = (async (config) => {
    externalAuthorization = authorization(config)
    externalWithCredentials = config.withCredentials
    return ok(config, { ok: true })
  }) satisfies AxiosAdapter

  setAccessToken(SECRET_TOKEN)

  await http.get(url)

  expect(externalAuthorization).toBeNull()
  expect(externalWithCredentials).toBe(false)
}

describe('http auth refresh interceptor', () => {
  afterEach(() => {
    http.defaults.adapter = originalAdapter
    setAccessToken(null)
    vi.restoreAllMocks()
  })

  it('coalesces concurrent 401 refreshes and retries with the rotated access token', async () => {
    const protectedAuthorizations: Array<string | null> = []
    let refreshCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, FRESH_TOKEN)
      }

      if (config.url === DASHBOARD_URL) {
        protectedAuthorizations.push(authorization(config))
        if (protectedAuthorizations.length <= 2) {
          return authExpired(config)
        }
        return ok(config, { success: true, data: { ok: true } })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)

    await Promise.all([http.get(DASHBOARD_URL), http.get(DASHBOARD_URL)])

    expect(refreshCalls).toBe(1)
    expect(getAccessToken()).toBe(FRESH_TOKEN)
    expect(protectedAuthorizations).toEqual([
      bearer(STALE_TOKEN),
      bearer(STALE_TOKEN),
      bearer(FRESH_TOKEN),
      bearer(FRESH_TOKEN),
    ])
  })

  it('does not mistake business URLs containing auth-login-like text for auth session endpoints', async () => {
    let refreshCalls = 0
    let businessCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, ROTATED_TOKEN)
      }

      if (config.url === BUSINESS_LOGIN_HISTORY_URL) {
        businessCalls += 1
        return businessCalls === 1
          ? authExpired(config)
          : ok(config, { success: true, data: { ok: true } })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)

    await expect(http.get(BUSINESS_LOGIN_HISTORY_URL)).resolves.toMatchObject({ status: 200 })

    expect(refreshCalls).toBe(1)
    expect(businessCalls).toBe(2)
    expect(getAccessToken()).toBe(ROTATED_TOKEN)
  })

  it('does not attach the access token to an absolute URL outside the configured API', async () => {
    await expectExternalRequestWithoutCredentials(EXTERNAL_URL)
  })

  it('does not attach credentials to a protocol-relative external URL', async () => {
    await expectExternalRequestWithoutCredentials(PROTOCOL_RELATIVE_EXTERNAL_URL)
  })

  it('does not attach credentials when a relative URL overrides baseURL to an external origin', async () => {
    let externalAuthorization: string | null = 'not-called'
    let externalWithCredentials: boolean | undefined

    http.defaults.adapter = (async (config) => {
      externalAuthorization = authorization(config)
      externalWithCredentials = config.withCredentials
      return ok(config, { ok: true })
    }) satisfies AxiosAdapter

    setAccessToken(SECRET_TOKEN)

    await http.get('/external', { baseURL: EXTERNAL_BASE_URL })

    expect(externalAuthorization).toBeNull()
    expect(externalWithCredentials).toBe(false)
  })

  it('does not refresh or invalidate the app session for an external 401 response', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    let refreshCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, 'unexpected-token')
      }

      if (config.url === EXTERNAL_PROTECTED_URL) {
        return authExpired(config, 'external unauthorized')
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(APP_SESSION_TOKEN)

    await expect(http.get(EXTERNAL_PROTECTED_URL)).rejects.toMatchObject({ response: { status: 401 } })

    expect(refreshCalls).toBe(0)
    expect(getAccessToken()).toBe(APP_SESSION_TOKEN)
    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it('dispatches a single auth-invalidated browser event when refresh fails for a protected endpoint', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        return authExpired(config, 'refresh expired')
      }

      if (config.url === DASHBOARD_URL) {
        return authExpired(config)
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)

    await expect(http.get(DASHBOARD_URL)).rejects.toMatchObject({ response: { status: 401 } })

    expect(getAccessToken()).toBeNull()
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })
})
