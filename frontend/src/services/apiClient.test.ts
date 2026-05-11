import type { AxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { APP_EVENTS } from '../constants/appEvents'
import { STORAGE_KEYS } from '../constants/storageKeys'
import { apiClient } from './apiClient'

const TOKEN_KEY = STORAGE_KEYS.AUTH_TOKEN

/**
 * axios stores its registered interceptors in an internal `handlers` array.
 * Reaching into it lets us run the registered functions deterministically in
 * unit tests (instead of trying to mock the network layer).
 */
type AxiosHandler = {
  fulfilled?: (value: unknown) => unknown
  rejected?: (reason: unknown) => unknown
}

function getRequestHandlers(): AxiosHandler[] {
  return (apiClient.interceptors.request as unknown as { handlers: AxiosHandler[] }).handlers
}

function getResponseHandlers(): AxiosHandler[] {
  return (apiClient.interceptors.response as unknown as { handlers: AxiosHandler[] }).handlers
}

function getRequestFulfilled(): NonNullable<AxiosHandler['fulfilled']> {
  const handler = getRequestHandlers().find((h) => typeof h?.fulfilled === 'function')
  if (!handler?.fulfilled) {
    throw new Error('No fulfilled request interceptor registered')
  }
  return handler.fulfilled
}

function getResponseRejected(): NonNullable<AxiosHandler['rejected']> {
  const handler = getResponseHandlers().find((h) => typeof h?.rejected === 'function')
  if (!handler?.rejected) {
    throw new Error('No rejected response interceptor registered')
  }
  return handler.rejected
}

function buildRequestConfig(overrides: Partial<AxiosRequestConfig> = {}): AxiosRequestConfig {
  return {
    headers: {},
    url: '/auth/me',
    ...overrides,
  }
}

describe('apiClient request interceptor', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('attaches the Authorization header when a token is present', async () => {
    localStorage.setItem(TOKEN_KEY, 'test-token-123')

    const fulfilled = getRequestFulfilled()
    const result = (await fulfilled(buildRequestConfig())) as AxiosRequestConfig

    expect(result.headers?.Authorization).toBe('Bearer test-token-123')
  })

  it('does not attach an Authorization header when no token is stored', async () => {
    const fulfilled = getRequestFulfilled()
    const result = (await fulfilled(buildRequestConfig({ url: '/auth/login' }))) as AxiosRequestConfig

    expect(result.headers?.Authorization).toBeUndefined()
  })

  it('rejects requests whose URL contains an invalid numeric path segment', async () => {
    const fulfilled = getRequestFulfilled()
    const baseConfig = buildRequestConfig({ url: '/students/lessons/NaN' })

    expect(() => fulfilled(baseConfig)).toThrow('Invalid numeric route or query parameter.')
  })

  it('rejects requests whose query params contain non-finite numbers', async () => {
    const fulfilled = getRequestFulfilled()
    const baseConfig = buildRequestConfig({
      url: '/public/courses',
      params: { page: Number.NaN, size: 12 },
    })

    expect(() => fulfilled(baseConfig)).toThrow('Invalid numeric route or query parameter.')
  })
})

describe('apiClient response interceptor', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem(TOKEN_KEY, 'will-be-cleared')
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('clears the stored token on a 401 response from a non-auth endpoint', async () => {
    const rejected = getResponseRejected()
    const error = {
      response: { status: 401 },
      config: { url: '/students/dashboard' },
    }

    await expect(rejected(error)).rejects.toBe(error)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('does not clear the stored token on a 401 from /auth/login', async () => {
    const rejected = getResponseRejected()
    const error = {
      response: { status: 401 },
      config: { url: '/auth/login' },
    }

    await expect(rejected(error)).rejects.toBe(error)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('will-be-cleared')
  })

  it('dispatches the app:forbidden custom event on a 403 response', async () => {
    const rejected = getResponseRejected()
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')

    const error = {
      response: { status: 403 },
      config: { url: '/admin/users' },
    }

    await expect(rejected(error)).rejects.toBe(error)

    const dispatched = dispatchSpy.mock.calls.find(
      ([event]) => (event as Event).type === APP_EVENTS.FORBIDDEN,
    )
    expect(dispatched).toBeDefined()

    dispatchSpy.mockRestore()
  })
})
