import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from './apiClient'

const TOKEN_KEY = 'ai-chinese-learning-token'

describe('apiClient request interceptor', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('attaches the Authorization header when a token is present', async () => {
    localStorage.setItem(TOKEN_KEY, 'test-token-123')

    // Walk through the registered request interceptor manually.
    // axios stores handlers in an internal `handlers` array.
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (config: unknown) => unknown }>
    }).handlers
    const fulfilled = handlers.find((handler) => typeof handler?.fulfilled === 'function')

    expect(fulfilled).toBeDefined()

    const baseConfig = { headers: {} as Record<string, string>, url: '/auth/me' }
    const result = (await fulfilled!.fulfilled!(baseConfig)) as { headers: Record<string, string> }

    expect(result.headers.Authorization).toBe('Bearer test-token-123')
  })

  it('does not attach an Authorization header when no token is stored', async () => {
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (config: unknown) => unknown }>
    }).handlers
    const fulfilled = handlers.find((handler) => typeof handler?.fulfilled === 'function')

    const baseConfig = { headers: {} as Record<string, string>, url: '/auth/login' }
    const result = (await fulfilled!.fulfilled!(baseConfig)) as { headers: Record<string, string> }

    expect(result.headers.Authorization).toBeUndefined()
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
    const handlers = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected?: (error: unknown) => unknown }>
    }).handlers
    const rejected = handlers.find((handler) => typeof handler?.rejected === 'function')

    expect(rejected).toBeDefined()

    const error = {
      response: { status: 401 },
      config: { url: '/students/dashboard' },
    }

    await expect(rejected!.rejected!(error)).rejects.toBe(error)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('does not clear the stored token on a 401 from /auth/login', async () => {
    const handlers = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected?: (error: unknown) => unknown }>
    }).handlers
    const rejected = handlers.find((handler) => typeof handler?.rejected === 'function')

    const error = {
      response: { status: 401 },
      config: { url: '/auth/login' },
    }

    await expect(rejected!.rejected!(error)).rejects.toBe(error)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('will-be-cleared')
  })

  it('dispatches the app:forbidden custom event on a 403 response', async () => {
    const handlers = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected?: (error: unknown) => unknown }>
    }).handlers
    const rejected = handlers.find((handler) => typeof handler?.rejected === 'function')

    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')

    const error = {
      response: { status: 403 },
      config: { url: '/admin/users' },
    }

    await expect(rejected!.rejected!(error)).rejects.toBe(error)

    expect(dispatchSpy).toHaveBeenCalled()
    const dispatched = dispatchSpy.mock.calls.find(([event]) => (event as Event).type === 'app:forbidden')
    expect(dispatched).toBeDefined()

    dispatchSpy.mockRestore()
  })
})
