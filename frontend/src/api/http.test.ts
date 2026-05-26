import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AUTH_INVALIDATED_EVENT, getAccessToken, http, setAccessToken } from './http'

const originalAdapter = http.defaults.adapter

function apiResponse<T>(config: InternalAxiosRequestConfig, status: number, data: T): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status >= 400 ? 'Error' : 'OK',
    headers: {},
    config,
  }
}

function adapterResponse<T>(config: InternalAxiosRequestConfig, status: number, data: T) {
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

function authorization(config: InternalAxiosRequestConfig): string | null {
  const headers = config.headers as unknown as { get?: (name: string) => string | null; Authorization?: string }
  return headers.get?.('Authorization') ?? headers.Authorization ?? null
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
      if (config.url === '/auth/refresh') {
        refreshCalls += 1
        return adapterResponse(config, 200, { success: true, data: { token: 'fresh-token' } })
      }

      if (config.url === '/students/dashboard') {
        protectedAuthorizations.push(authorization(config))
        if (protectedAuthorizations.length <= 2) {
          return adapterResponse(config, 401, { success: false, message: 'expired' })
        }
        return adapterResponse(config, 200, { success: true, data: { ok: true } })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken('stale-token')

    await Promise.all([http.get('/students/dashboard'), http.get('/students/dashboard')])

    expect(refreshCalls).toBe(1)
    expect(getAccessToken()).toBe('fresh-token')
    expect(protectedAuthorizations).toEqual([
      'Bearer stale-token',
      'Bearer stale-token',
      'Bearer fresh-token',
      'Bearer fresh-token',
    ])
  })

  it('does not mistake business URLs containing auth-login-like text for auth session endpoints', async () => {
    let refreshCalls = 0
    let businessCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === '/auth/refresh') {
        refreshCalls += 1
        return adapterResponse(config, 200, { success: true, data: { token: 'rotated-token' } })
      }

      if (config.url === '/students/auth/login-history') {
        businessCalls += 1
        return businessCalls === 1
          ? adapterResponse(config, 401, { success: false, message: 'expired' })
          : adapterResponse(config, 200, { success: true, data: { ok: true } })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken('stale-token')

    await expect(http.get('/students/auth/login-history')).resolves.toMatchObject({ status: 200 })

    expect(refreshCalls).toBe(1)
    expect(businessCalls).toBe(2)
    expect(getAccessToken()).toBe('rotated-token')
  })

  it('dispatches a single auth-invalidated browser event when refresh fails for a protected endpoint', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')

    http.defaults.adapter = (async (config) => {
      if (config.url === '/auth/refresh') {
        return adapterResponse(config, 401, { success: false, message: 'refresh expired' })
      }

      if (config.url === '/students/dashboard') {
        return adapterResponse(config, 401, { success: false, message: 'expired' })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken('stale-token')

    await expect(http.get('/students/dashboard')).rejects.toMatchObject({ response: { status: 401 } })

    expect(getAccessToken()).toBeNull()
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })
})
