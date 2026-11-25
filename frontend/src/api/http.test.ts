import type { AxiosAdapter, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  AUTH_INVALIDATED_EVENT,
  SERVICE_UNAVAILABLE_EVENT,
  getAccessToken,
  http,
  refreshAccessToken,
  setAccessToken,
} from './http'
import { axiosHttpError, axiosResponse, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'
import { deferred } from '@/test/helpers'
import { storageKeys } from '@/utils/storage'

const APP_SESSION_TOKEN = 'app-session-token'
const BUSINESS_LOGIN_HISTORY_URL = '/students/auth/login-history'
const HOME_URL = '/students/home'
const EXTERNAL_BASE_URL = 'https://example.invalid/api'
const EXTERNAL_PROTECTED_URL = 'https://example.invalid/protected'
const EXTERNAL_URL = 'https://example.invalid/external'
const FRESH_TOKEN = 'fresh-token'
const PROTOCOL_RELATIVE_EXTERNAL_URL = '//example.invalid/external'
const REFRESH_URL = '/auth/refresh'
const ROTATED_TOKEN = 'rotated-token'
const SECRET_TOKEN = 'secret-token'
const STALE_TOKEN = 'stale-token'

restoreAxiosAdapterAfterEach()

afterEach(() => {
  setAccessToken(null)
  Reflect.deleteProperty(navigator, 'locks')
})

const bearer = (token: string) => `Bearer ${token}`

function authExpired(config: InternalAxiosRequestConfig, message = 'expired'): never {
  throw axiosHttpError(config, 401, { success: false, message })
}

function authorization(config: InternalAxiosRequestConfig): string | null {
  return config.headers.get('Authorization')?.toString() ?? null
}

function ok<T>(config: InternalAxiosRequestConfig, data: T) {
  return axiosResponse(config, 200, data)
}

function tokenResponse(config: InternalAxiosRequestConfig, token: string) {
  return ok(config, { success: true, data: { token } })
}

function otherTabStartsRefresh(): void {
  window.dispatchEvent(new StorageEvent('storage', { key: storageKeys.refreshMarker, newValue: `${Date.now()}-other` }))
}

async function expectExternalRequestWithoutCredentials(url: string, requestConfig?: AxiosRequestConfig) {
  let externalAuthorization: string | null = 'not-called'
  let externalWithCredentials: boolean | undefined

  http.defaults.adapter = (async (config) => {
    externalAuthorization = authorization(config)
    externalWithCredentials = config.withCredentials
    return ok(config, { ok: true })
  }) satisfies AxiosAdapter

  setAccessToken(SECRET_TOKEN)

  await http.get(url, requestConfig)

  expect(externalAuthorization).toBeNull()
  expect(externalWithCredentials).toBe(false)
}

describe('http auth refresh interceptor', () => {
  it('coalesces concurrent 401 refreshes and retries with the rotated access token', async () => {
    const protectedAuthorizations: Array<string | null> = []
    let refreshCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, FRESH_TOKEN)
      }

      if (config.url === HOME_URL) {
        protectedAuthorizations.push(authorization(config))
        if (protectedAuthorizations.length <= 2) {
          return authExpired(config)
        }
        return ok(config, { success: true, data: { ok: true } })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)

    await Promise.all([http.get(HOME_URL), http.get(HOME_URL)])

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
        return businessCalls === 1 ? authExpired(config) : ok(config, { success: true, data: { ok: true } })
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
    await expectExternalRequestWithoutCredentials('/external', { baseURL: EXTERNAL_BASE_URL })
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

      if (config.url === HOME_URL) {
        return authExpired(config)
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)

    await expect(http.get(HOME_URL)).rejects.toMatchObject({ response: { status: 401 } })

    expect(getAccessToken()).toBeNull()
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })

  it('gives up after one retry: a request that still gets a 401 invalidates the session', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    let refreshCalls = 0
    let protectedCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, FRESH_TOKEN)
      }

      protectedCalls += 1
      return authExpired(config)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)

    await expect(http.get(HOME_URL)).rejects.toMatchObject({ response: { status: 401 } })

    expect(refreshCalls).toBe(1)
    expect(protectedCalls).toBe(2)
    expect(getAccessToken()).toBeNull()
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
  })

  it('treats a rejected sign-in as a wrong password: no refresh and no auth-invalidated event', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    let refreshCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, 'unexpected-token')
      }

      return authExpired(config, 'Invalid email or password.')
    }) satisfies AxiosAdapter

    await expect(http.post('/auth/login', {})).rejects.toMatchObject({ response: { status: 401 } })

    expect(refreshCalls).toBe(0)
    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it('passes a 401 from /auth/me straight back to the auth store without refreshing or invalidating', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    let refreshCalls = 0

    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) {
        refreshCalls += 1
        return tokenResponse(config, 'unexpected-token')
      }

      return authExpired(config, 'Please sign in to continue.')
    }) satisfies AxiosAdapter

    setAccessToken(FRESH_TOKEN)
    await expect(http.get('/auth/me')).rejects.toMatchObject({ response: { status: 401 } })

    expect(refreshCalls).toBe(0)
    expect(dispatchEvent).not.toHaveBeenCalled()
    expect(getAccessToken()).toBe(FRESH_TOKEN)
  })
})

describe('http 503 answers (maintenance)', () => {
  /** Answers every request with `status` and returns the names of the browser events dispatched meanwhile. */
  function answerWith(status: number): string[] {
    const events: string[] = []
    vi.spyOn(window, 'dispatchEvent').mockImplementation((event) => {
      events.push(event.type)
      return true
    })
    http.defaults.adapter = (async (config) => {
      throw axiosHttpError(config, status, { success: false, message: 'unavailable' })
    }) satisfies AxiosAdapter
    return events
  }

  // For a gateway timeout or a service unavailable response, the health check decides whether to show maintenance.
  it.each([502, 503, 504])('reports a %s once, keeps the session and still rejects the request', async (status) => {
    const events = answerWith(status)
    setAccessToken(APP_SESSION_TOKEN)

    await expect(http.get(HOME_URL)).rejects.toMatchObject({ response: { status } })

    expect(events).toEqual([SERVICE_UNAVAILABLE_EVENT])
    expect(getAccessToken()).toBe(APP_SESSION_TOKEN)
  })

  it('reports it for a visitor’s request and for the session refresh too', async () => {
    const events = answerWith(503)

    await expect(http.post('/public/support', {})).rejects.toMatchObject({ response: { status: 503 } })
    setAccessToken(APP_SESSION_TOKEN)
    await expect(refreshAccessToken()).rejects.toMatchObject({ response: { status: 503 } })
    expect(getAccessToken()).toBe(APP_SESSION_TOKEN)

    expect(events).toEqual([SERVICE_UNAVAILABLE_EVENT, SERVICE_UNAVAILABLE_EVENT])
  })

  it('does not report the health check itself, so checking cannot start another check', async () => {
    const events = answerWith(503)

    await expect(http.get('/health')).rejects.toMatchObject({ response: { status: 503 } })
    await expect(http.get('/health?probe=1')).rejects.toMatchObject({ response: { status: 503 } })
    await expect(http.get(new URL('/api/v1/health?probe=1', window.location.origin).href)).rejects.toMatchObject({
      response: { status: 503 },
    })

    expect(events).toEqual([])
  })

  it('does not report another origin’s 503', async () => {
    const events = answerWith(503)

    await expect(http.get(EXTERNAL_URL)).rejects.toMatchObject({ response: { status: 503 } })

    expect(events).toEqual([])
  })

  it('accepts a public health response across a sign-in without presenting either account’s credentials', async () => {
    const release = deferred<void>()
    http.defaults.adapter = async (config) => {
      expect(authorization(config)).toBeNull()
      expect(config.withCredentials).toBe(false)
      await release.promise
      return ok(config, { status: 'UP' })
    }
    setAccessToken(STALE_TOKEN)
    const check = http.get('/health')
    setAccessToken(FRESH_TOKEN)
    release.resolve()
    await expect(check).resolves.toMatchObject({ data: { status: 'UP' } })
  })

  it('does not report a 500, an error of one request', async () => {
    const events = answerWith(500)

    await expect(http.get(HOME_URL)).rejects.toMatchObject({ response: { status: 500 } })

    expect(events).toEqual([])
  })
})

describe('refreshAccessToken across tabs (shared refresh cookie)', () => {
  /** Answers /auth/refresh with the given statuses in order (200 carries FRESH_TOKEN) and counts the calls. */
  function answerRefresh(...statuses: number[]): { calls: number } {
    const counter = { calls: 0 }
    http.defaults.adapter = (async (config) => {
      if (config.url !== REFRESH_URL) throw new Error(`Unexpected request ${config.url}`)
      // The refresh cookie is only ever presented with POST (the backend route is POST /auth/refresh).
      if (config.method !== 'post') throw new Error(`Unexpected ${config.method} ${config.url}`)
      const status = statuses[counter.calls] ?? 401
      counter.calls += 1
      return status === 200 ? tokenResponse(config, FRESH_TOKEN) : authExpired(config, 'refresh rejected')
    }) satisfies AxiosAdapter
    return counter
  }

  it('takes the refresh lock so tabs refresh one after another', async () => {
    const lockNames: string[] = []
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: {
        request: (name: string, task: () => Promise<unknown>) => {
          lockNames.push(name)
          return task()
        },
      },
    })
    const refresh = answerRefresh(200)

    await expect(refreshAccessToken()).resolves.toBe(FRESH_TOKEN)

    expect(lockNames).toEqual(['mandarin-spark.auth-refresh'])
    expect(refresh.calls).toBe(1)
    expect(getAccessToken()).toBe(FRESH_TOKEN)
  })

  it('shares one request between calls in the same tab when Web Locks are unavailable', async () => {
    const refresh = answerRefresh(200)

    const tokens = await Promise.all([refreshAccessToken(), refreshAccessToken()])

    expect(tokens).toEqual([FRESH_TOKEN, FRESH_TOKEN])
    expect(refresh.calls).toBe(1)
    // Other tabs learn from the marker that the refresh cookie is about to rotate.
    expect(window.localStorage.getItem(storageKeys.refreshMarker)).not.toBeNull()
  })

  it('does not repeat a rejected refresh when no other tab is refreshing', async () => {
    const refresh = answerRefresh(401)

    await expect(refreshAccessToken()).resolves.toBeNull()

    expect(refresh.calls).toBe(1)
    expect(getAccessToken()).toBeNull()
  })

  it('honours a saved sign-out even when the browser still has its refresh cookie', async () => {
    const refresh = answerRefresh(200)
    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: null, at: Date.now() }))

    await expect(refreshAccessToken()).resolves.toBeNull()
    expect(refresh.calls).toBe(0)

    window.localStorage.setItem(storageKeys.sessionOwner, JSON.stringify({ userId: 7, at: Date.now() }))
    await expect(refreshAccessToken()).resolves.toBe(FRESH_TOKEN)
    expect(refresh.calls).toBe(1)
  })

  it('retries once when the 401 follows a refresh started in another tab', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(0)
    const refresh = answerRefresh(401, 200)
    otherTabStartsRefresh()

    const pending = refreshAccessToken()
    await vi.advanceTimersByTimeAsync(300)
    await expect(pending).resolves.toBe(FRESH_TOKEN)

    expect(refresh.calls).toBe(2)
    expect(getAccessToken()).toBe(FRESH_TOKEN)
  })

  it('retries only once', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(0)
    const refresh = answerRefresh(401, 401, 200)
    otherTabStartsRefresh()

    const pending = refreshAccessToken()
    await vi.advanceTimersByTimeAsync(300)
    await expect(pending).resolves.toBeNull()

    expect(refresh.calls).toBe(2)
  })
})

describe('requests that outlive their access-token session', () => {
  it('keeps the caller’s credentials and discards a successful response after an account change', async () => {
    const release = deferred<void>()
    const credentials: Array<string | null> = []
    http.defaults.adapter = async (config) => {
      credentials.push(authorization(config))
      await release.promise
      return ok(config, { success: true, data: { name: 'First account' } })
    }
    setAccessToken(STALE_TOKEN)
    const firstAccount = http.get(HOME_URL)
    setAccessToken(FRESH_TOKEN)
    release.resolve()

    await expect(firstAccount).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(credentials).toEqual([bearer(STALE_TOKEN)])
  })

  it.each([
    [null, true],
    [null, false],
    [FRESH_TOKEN, true],
    [FRESH_TOKEN, false],
  ] as const)('ignores a pending refresh after token becomes %s (success=%s)', async (nextToken, succeeds) => {
    const gate = deferred<void>()
    http.defaults.adapter = (async (config) => {
      await gate.promise
      return succeeds ? tokenResponse(config, ROTATED_TOKEN) : authExpired(config)
    }) satisfies AxiosAdapter
    setAccessToken(STALE_TOKEN)
    const refresh = refreshAccessToken()
    await flushPromises()

    setAccessToken(nextToken)
    gate.resolve()

    await expect(refresh).resolves.toBeNull()
    expect(getAccessToken()).toBe(nextToken)
  })

  it('does not replay an old write with the next signed-in account', async () => {
    const gate = deferred<void>()
    const requests: string[] = []
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    http.defaults.adapter = (async (config) => {
      requests.push(config.url ?? '')
      if (config.url === REFRESH_URL) return tokenResponse(config, ROTATED_TOKEN)
      await gate.promise
      return authExpired(config)
    }) satisfies AxiosAdapter
    setAccessToken(STALE_TOKEN)
    const result = expect(http.put('/users/me/profile', { fullName: 'Previous account' })).rejects.toMatchObject({
      response: { status: 401 },
    })
    await flushPromises()
    setAccessToken(FRESH_TOKEN)
    gate.resolve()
    await result

    expect(requests).toEqual(['/users/me/profile'])
    expect(getAccessToken()).toBe(FRESH_TOKEN)
    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it('does not clear a newer session when an old sign-in fails', async () => {
    const gate = deferred<void>()
    http.defaults.adapter = (async (config) => {
      await gate.promise
      return authExpired(config)
    }) satisfies AxiosAdapter
    const result = expect(http.post('/auth/login', {})).rejects.toMatchObject({ response: { status: 401 } })
    await flushPromises()
    setAccessToken(FRESH_TOKEN)
    gate.resolve()
    await result
    expect(getAccessToken()).toBe(FRESH_TOKEN)
  })

  it('does not invalidate a newer session when an old retry fails', async () => {
    const gate = deferred<void>()
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    let protectedCalls = 0
    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) return tokenResponse(config, ROTATED_TOKEN)
      protectedCalls += 1
      if (protectedCalls > 1) await gate.promise
      return authExpired(config)
    }) satisfies AxiosAdapter
    setAccessToken(STALE_TOKEN)
    const result = expect(http.get(HOME_URL)).rejects.toMatchObject({ response: { status: 401 } })
    await flushPromises()
    expect(protectedCalls).toBe(2)
    setAccessToken(FRESH_TOKEN)
    gate.resolve()
    await result
    expect(getAccessToken()).toBe(FRESH_TOKEN)
    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it('does not send a refresh queued behind another tab after sign-out', async () => {
    const gate = deferred<void>()
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: {
        request: async (_name: string, task: () => Promise<unknown>) => {
          await gate.promise
          return task()
        },
      },
    })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => tokenResponse(config, ROTATED_TOKEN))
    http.defaults.adapter = adapter
    setAccessToken(STALE_TOKEN)
    const refresh = refreshAccessToken()
    setAccessToken(null)
    gate.resolve()
    await expect(refresh).resolves.toBeNull()
    expect(adapter).not.toHaveBeenCalled()
    expect(getAccessToken()).toBeNull()
  })

  it('keeps the new refresh shared after an older refresh settles', async () => {
    const oldGate = deferred<void>()
    const newGate = deferred<void>()
    let calls = 0
    http.defaults.adapter = (async (config) => {
      const first = ++calls === 1
      await (first ? oldGate : newGate).promise
      return tokenResponse(config, first ? STALE_TOKEN : ROTATED_TOKEN)
    }) satisfies AxiosAdapter
    setAccessToken(STALE_TOKEN)
    const oldRefresh = refreshAccessToken()
    await flushPromises()
    setAccessToken(FRESH_TOKEN)
    const newRefresh = refreshAccessToken()
    await flushPromises()
    oldGate.resolve()
    await oldRefresh
    const sharedNewRefresh = refreshAccessToken()
    await flushPromises()
    newGate.resolve()
    const tokens = await Promise.all([oldRefresh, newRefresh, sharedNewRefresh])

    expect(tokens).toEqual([null, ROTATED_TOKEN, ROTATED_TOKEN])
    expect(calls).toBe(2)
    expect(getAccessToken()).toBe(ROTATED_TOKEN)
  })

  it('invalidates once when concurrent requests share a failed refresh', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    http.defaults.adapter = (async (config) => authExpired(config)) satisfies AxiosAdapter
    setAccessToken(STALE_TOKEN)
    await Promise.allSettled([http.get(HOME_URL), http.get(HOME_URL)])
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
  })

  it('cancels a retry if the session changes before the request interceptor runs', async () => {
    const gate = deferred<void>()
    let attempts = 0
    let sentWrites = 0
    const interceptor = http.interceptors.request.use(async (config) => {
      if (config.url === HOME_URL && ++attempts === 2) await gate.promise
      return config
    })
    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) return tokenResponse(config, ROTATED_TOKEN)
      sentWrites += 1
      return authExpired(config)
    }) satisfies AxiosAdapter
    try {
      setAccessToken(STALE_TOKEN)
      const result = expect(http.post(HOME_URL, { value: 'old write' })).rejects.toMatchObject({ code: 'ERR_CANCELED' })
      await flushPromises()
      expect(attempts).toBe(2)
      setAccessToken(FRESH_TOKEN)
      gate.resolve()
      await result
      expect(sentWrites).toBe(1)
      expect(getAccessToken()).toBe(FRESH_TOKEN)
    } finally {
      gate.resolve()
      http.interceptors.request.eject(interceptor)
    }
  })

  it('does not retry refresh after sign-out during the other-tab cookie settling delay', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(0)
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => authExpired(config))
    http.defaults.adapter = adapter
    setAccessToken(STALE_TOKEN)
    otherTabStartsRefresh()
    const refresh = refreshAccessToken()
    await flushPromises()
    expect(adapter).toHaveBeenCalledOnce()
    setAccessToken(null)
    await vi.advanceTimersByTimeAsync(300)
    await expect(refresh).resolves.toBeNull()
    expect(adapter).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
  })

  it('falls back to a direct refresh when navigator.locks.request rejects with SecurityError', async () => {
    const request = vi.fn(() => Promise.reject(new DOMException('Storage access denied', 'SecurityError')))
    const originalLocks = Object.getOwnPropertyDescriptor(navigator, 'locks')
    Object.defineProperty(navigator, 'locks', { value: { request }, configurable: true })
    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) return tokenResponse(config, FRESH_TOKEN)
      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter
    try {
      setAccessToken(STALE_TOKEN)
      await expect(refreshAccessToken()).resolves.toBe(FRESH_TOKEN)
      expect(request).toHaveBeenCalledOnce()
      expect(getAccessToken()).toBe(FRESH_TOKEN)
    } finally {
      if (originalLocks) Object.defineProperty(navigator, 'locks', originalLocks)
      else Reflect.deleteProperty(navigator, 'locks')
    }
  })

  it('rejects a refreshed JWT whose subject belongs to a different account than the active tab token', async () => {
    const fakeJwt = (sub: string) => {
      const encode = (obj: object) =>
        btoa(JSON.stringify(obj)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
      return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub })}.sig`
    }
    const aliceToken = fakeJwt('alice@example.com')
    const bobToken = fakeJwt('bob@example.com')
    http.defaults.adapter = (async (config) => {
      if (config.url === REFRESH_URL) return tokenResponse(config, bobToken)
      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    setAccessToken(aliceToken)
    await expect(refreshAccessToken()).resolves.toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('does not attach credentials to public unauthenticated endpoints or cancel them on session change', async () => {
    const release = deferred<void>()
    let sentAuth: string | null = 'unset'
    http.defaults.adapter = (async (config) => {
      sentAuth = authorization(config)
      await release.promise
      return ok(config, { success: true, data: { confirmed: true } })
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)
    const pending = http.post('/auth/confirm-email', { token: 'abc' })
    setAccessToken(FRESH_TOKEN)
    release.resolve()

    await expect(pending).resolves.toMatchObject({ status: 200 })
    expect(sentAuth).toBeNull()
  })

  it('aborts a fresh refresh if the session changes while awaiting an in-flight refresh, and coalesces concurrent fresh waiters', async () => {
    const firstGate = deferred<void>()
    const secondGate = deferred<void>()
    let calls = 0
    http.defaults.adapter = (async (config) => {
      if (config.url !== REFRESH_URL) throw new Error(`Unexpected request ${config.url}`)
      const callNumber = ++calls
      await (callNumber === 1 ? firstGate : secondGate).promise
      return tokenResponse(config, callNumber === 1 ? FRESH_TOKEN : ROTATED_TOKEN)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)
    const initial = refreshAccessToken()
    const staleFresh = refreshAccessToken(undefined, { fresh: true })
    setAccessToken(null)
    firstGate.resolve()
    await expect(initial).resolves.toBeNull()
    await expect(staleFresh).resolves.toBeNull()
    expect(calls).toBe(1)

    calls = 0
    const nextFirstGate = deferred<void>()
    const nextSecondGate = deferred<void>()
    http.defaults.adapter = (async (config) => {
      if (config.url !== REFRESH_URL) throw new Error(`Unexpected request ${config.url}`)
      const callNumber = ++calls
      await (callNumber === 1 ? nextFirstGate : nextSecondGate).promise
      return tokenResponse(config, callNumber === 1 ? FRESH_TOKEN : ROTATED_TOKEN)
    }) satisfies AxiosAdapter

    setAccessToken(STALE_TOKEN)
    const inflight = refreshAccessToken()
    const freshA = refreshAccessToken(undefined, { fresh: true })
    const freshB = refreshAccessToken(undefined, { fresh: true })
    nextFirstGate.resolve()
    await expect(inflight).resolves.toBe(FRESH_TOKEN)
    await flushPromises()
    expect(calls).toBe(2)
    nextSecondGate.resolve()
    await expect(Promise.all([freshA, freshB])).resolves.toEqual([ROTATED_TOKEN, ROTATED_TOKEN])
    expect(calls).toBe(2)
  })
})
