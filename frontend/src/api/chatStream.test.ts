import type { AxiosAdapter } from 'axios'
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
import { apiChatStream } from './chatStream'
import { axiosHttpError, axiosResponse, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'
import { deferred } from '@/test/helpers'
import type { AiChatResponse } from '@/types/ai'

const CHAT_MESSAGE = 'hello'
const FRESH_TOKEN = 'fresh-token'
const STREAM_UNFINISHED_MESSAGE = 'The AI helper could not finish this answer. Please try again.'
const RATE_LIMIT_MESSAGE = 'You are doing that too often. Please wait a moment and try again.'
const REFRESH_ENDPOINT = '/auth/refresh'
const REFRESH_EXPIRED_MESSAGE = 'refresh expired'
const SERVER_TROUBLE_MESSAGE = 'The learning service is having trouble right now. Please try again shortly.'
const SIGN_IN_AGAIN_MESSAGE = 'Please sign in again to continue.'
const STALE_TOKEN = 'stale-token'
const STREAM_TIMEOUT_MESSAGE = 'The AI helper took too long to answer. Please try again.'
const STREAM_TIMEOUT_MS = 50_000

restoreAxiosAdapterAfterEach()

const bearer = (token: string) => `Bearer ${token}`
const chatRequest = () => ({ message: CHAT_MESSAGE })

function chatPayload(overrides: Partial<AiChatResponse> = {}): AiChatResponse {
  return {
    sessionId: 7,
    aiResponse: 'ni hao',
    status: 'SUCCESS',
    ...overrides,
  }
}

function completeEvent(payload: AiChatResponse): string {
  return `event:complete\ndata:${JSON.stringify(payload)}`
}

function mockFetchResponse(response: Response): void {
  window.fetch = vi.fn().mockResolvedValue(response)
}

/** A connection that never answers; it rejects with an AbortError once its signal aborts. */
function mockHangingFetch(): void {
  window.fetch = vi.fn(
    (_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      }),
  )
}

function sseResponse(body: BodyInit): Response {
  return new Response(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

function streamErrorExpectation(message: string, status?: number) {
  return expect(apiChatStream(chatRequest())).rejects.toMatchObject({
    response: {
      ...(status == null ? {} : { status }),
      data: { message },
    },
  })
}

/** Answers only the refresh endpoint: with a new access token, or with a 401 when `token` is null. */
function mockRefresh(token: string | null): void {
  http.defaults.adapter = (async (config) => {
    if (config.url !== REFRESH_ENDPOINT) throw new Error(`Unexpected request ${config.url}`)
    if (token === null) throw axiosHttpError(config, 401, { success: false, message: REFRESH_EXPIRED_MESSAGE })
    return axiosResponse(config, 200, { success: true, data: { token } })
  }) satisfies AxiosAdapter
}

describe('apiChatStream', () => {
  const originalFetch = window.fetch

  afterEach(() => {
    window.fetch = originalFetch
    setAccessToken(null)
  })

  it('processes the final SSE event even when the stream omits a trailing blank line', async () => {
    const payload = chatPayload()
    mockFetchResponse(sseResponse(`event:delta\ndata:ni\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    const result = await apiChatStream(chatRequest(), { onDelta })

    expect(result).toEqual(payload)
    expect(onDelta).toHaveBeenCalledWith('ni')
  })

  it('processes multiline SSE data fields as a single event payload', async () => {
    const payload = chatPayload({
      aiResponse: 'line one\nline two',
    })
    mockFetchResponse(sseResponse(`event:delta\ndata:line one\ndata:line two\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    await apiChatStream(chatRequest(), { onDelta })

    expect(onDelta).toHaveBeenCalledWith('line one\nline two')
  })

  it('finishes at the complete event even if the server keeps the connection open', async () => {
    const payload = chatPayload()
    const cancel = vi.fn()
    const onDelta = vi.fn()
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(`${completeEvent(payload)}\n\nevent:delta\ndata:late\n\n`))
      },
      cancel,
    })
    mockFetchResponse(sseResponse(body))

    await expect(apiChatStream(chatRequest(), { onDelta })).resolves.toEqual(payload)
    expect(onDelta).not.toHaveBeenCalled()
    expect(cancel).toHaveBeenCalledOnce()
    expect(body.locked).toBe(false)
  })

  it('times out a stalled response body after the headers have arrived', async () => {
    vi.useFakeTimers()
    const cancel = vi.fn()
    const body = new ReadableStream<Uint8Array>({ cancel })
    const headers = deferred<Response>()
    window.fetch = vi.fn().mockReturnValue(headers.promise)
    const failure = streamErrorExpectation(STREAM_TIMEOUT_MESSAGE)

    await vi.advanceTimersByTimeAsync(40_000)
    headers.resolve(sseResponse(body))
    await vi.advanceTimersByTimeAsync(STREAM_TIMEOUT_MS - 40_000)
    await failure
    expect(cancel).toHaveBeenCalledOnce()
    expect(body.locked).toBe(false)
  })

  it('keeps the leading space of a streamed chunk (Spring writes data: without a separator)', async () => {
    const payload = chatPayload({ aiResponse: 'Hello world' })
    mockFetchResponse(sseResponse(`event:delta\ndata:Hello\n\nevent:delta\ndata: world\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    await apiChatStream(chatRequest(), { onDelta })

    expect(onDelta).toHaveBeenNthCalledWith(1, 'Hello')
    expect(onDelta).toHaveBeenNthCalledWith(2, ' world')
  })

  it.each([
    '{not-json}',
    '[]',
    '{"aiResponse":42,"status":"SUCCESS"}',
    '{"aiResponse":"ok","status":"UNKNOWN"}',
    '{"aiResponse":"ok","status":"SUCCESS","sessionId":"7"}',
  ])('rejects a malformed completion event (%s)', async (data) => {
    mockFetchResponse(sseResponse(`event:complete\ndata:${data}`))

    await streamErrorExpectation(STREAM_UNFINISHED_MESSAGE)
  })

  it('preserves the backend JSON message for failed stream requests', async () => {
    const message = 'AI safety policy blocked this prompt.'
    mockFetchResponse(
      new Response(JSON.stringify({ message }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiChatStream({ message: 'unsafe' })).rejects.toMatchObject({
      response: {
        status: 400,
        data: { message },
      },
    })
  })

  it('uses a stable rate-limit message when the stream endpoint returns plain text', async () => {
    mockFetchResponse(new Response('Too many requests', { status: 429 }))

    await streamErrorExpectation(RATE_LIMIT_MESSAGE, 429)
  })

  it('maps plain-text 403 responses to forbidden and parses +json error bodies', async () => {
    mockFetchResponse(new Response('Forbidden', { status: 403 }))
    await streamErrorExpectation('You do not have permission to do that.', 403)

    const detail = 'Problem detail message.'
    mockFetchResponse(
      new Response(JSON.stringify({ message: detail }), {
        status: 403,
        headers: { 'Content-Type': 'application/problem+json; charset=utf-8' },
      }),
    )
    await streamErrorExpectation(detail, 403)
  })

  it('does not disguise a TypeError thrown inside onDelta as a network failure', async () => {
    mockFetchResponse(sseResponse(`event:delta\ndata:ni\n\n${completeEvent(chatPayload())}`))
    const handlerBug = new TypeError('cannot read property of undefined')

    await expect(
      apiChatStream(chatRequest(), {
        onDelta: () => {
          throw handlerBug
        },
      }),
    ).rejects.toBe(handlerBug)
  })

  // The stream is sent with fetch, outside the axios interceptor that reports the other API requests.
  it.each([502, 503, 504])(
    'reports a %s of the stream like any API answer, so the health check runs',
    async (status) => {
      const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
      mockFetchResponse(new Response('', { status }))

      await streamErrorExpectation(SERVER_TROUBLE_MESSAGE, status)

      expect(dispatchEvent).toHaveBeenCalledTimes(1)
      expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: SERVICE_UNAVAILABLE_EVENT })
    },
  )

  it('does not report a stream that fails on its own', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    mockFetchResponse(new Response('', { status: 500 }))

    await streamErrorExpectation(SERVER_TROUBLE_MESSAGE, 500)

    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it('aborts a stream request that never receives an initial response', async () => {
    vi.useFakeTimers()
    mockHangingFetch()

    const timedOut = vi.fn()
    const request = streamErrorExpectation(STREAM_TIMEOUT_MESSAGE).then(timedOut)
    const serverStreamTimeoutMs = 45_000
    await vi.advanceTimersByTimeAsync(serverStreamTimeoutMs)
    expect(timedOut).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(STREAM_TIMEOUT_MS - serverStreamTimeoutMs)
    await request
  })

  it('invalidates the browser auth session when stream refresh cannot recover a 401', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    mockFetchResponse(new Response('', { status: 401 }))
    mockRefresh(null)

    await streamErrorExpectation(SIGN_IN_AGAIN_MESSAGE, 401)

    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })

  it('retries a stream once with the rotated access token after a 401', async () => {
    const authorizations: Array<string | null> = []
    const urls: string[] = []
    const payload = chatPayload({
      sessionId: 8,
      aiResponse: '你好',
    })

    setAccessToken(STALE_TOKEN)
    window.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const headers = init?.headers as Headers
      urls.push(String(input))
      authorizations.push(headers.get('Authorization'))
      return Promise.resolve(
        authorizations.length === 1 ? new Response('', { status: 401 }) : sseResponse(completeEvent(payload)),
      )
    })
    mockRefresh(FRESH_TOKEN)

    await expect(apiChatStream(chatRequest())).resolves.toEqual(payload)

    expect(authorizations).toEqual([bearer(STALE_TOKEN), bearer(FRESH_TOKEN)])
    // The configured API base (default /api/v1, already without a trailing slash) joined with the stream path.
    const streamUrl = `${http.defaults.baseURL}/ai/chat/stream`
    expect(urls).toEqual([streamUrl, streamUrl])
  })

  it('retries late API and stream 401s with the token already rotated in their session', async () => {
    setAccessToken(STALE_TOKEN)
    const lateResponse = deferred<void>()
    const issueToken = vi.fn(() => ({ token: FRESH_TOKEN }))
    const apiHeaders: Array<string | null> = []
    const streamHeaders: Array<string | null> = []
    http.defaults.adapter = async (config) => {
      if (config.url === REFRESH_ENDPOINT) return axiosResponse(config, 200, { success: true, data: issueToken() })
      const authorization = config.headers.get('Authorization')?.toString() ?? null
      apiHeaders.push(authorization)
      if (authorization === bearer(STALE_TOKEN)) {
        await lateResponse.promise
        throw axiosHttpError(config, 401, {})
      }
      return axiosResponse(config, 200, { ready: true })
    }
    window.fetch = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
      const authorization = new Headers(init?.headers).get('Authorization')
      streamHeaders.push(authorization)
      if (authorization === bearer(STALE_TOKEN)) {
        await lateResponse.promise
        return new Response(null, { status: 401 })
      }
      return sseResponse(completeEvent(chatPayload()))
    })

    const api = http.get('/users/me')
    const chat = apiChatStream(chatRequest())
    await refreshAccessToken()
    lateResponse.resolve()

    await expect(api).resolves.toMatchObject({ data: { ready: true } })
    await expect(chat).resolves.toEqual(chatPayload())
    expect(issueToken).toHaveBeenCalledOnce()
    expect(apiHeaders).toEqual([bearer(STALE_TOKEN), bearer(FRESH_TOKEN)])
    expect(streamHeaders).toEqual(apiHeaders)
  })

  it('can cancel its wait for a shared refresh without retrying the closed stream', async () => {
    const gate = deferred<void>()
    setAccessToken(STALE_TOKEN)
    mockFetchResponse(new Response('', { status: 401 }))
    http.defaults.adapter = async (config) => {
      await gate.promise
      return axiosResponse(config, 200, { success: true, data: { token: FRESH_TOKEN } })
    }
    const controller = new AbortController()
    const result = expect(apiChatStream(chatRequest(), { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })
    await flushPromises()
    controller.abort()
    await result
    gate.resolve()
    await flushPromises()
    expect(window.fetch).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBe(FRESH_TOKEN)
  })

  it('invalidates the browser auth session when the retried stream still gets a 401', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    mockFetchResponse(new Response('', { status: 401 }))
    mockRefresh(FRESH_TOKEN)

    await streamErrorExpectation(SIGN_IN_AGAIN_MESSAGE, 401)

    expect(window.fetch).toHaveBeenCalledTimes(2)
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })

  it('reports an incomplete stream when no final complete event arrives', async () => {
    mockFetchResponse(sseResponse('event:delta\ndata:partial\n\n'))

    await streamErrorExpectation(STREAM_UNFINISHED_MESSAGE)
  })

  it('closes the stream before delivering another event after the account changes', async () => {
    const cancel = vi.fn()
    const onDelta = vi.fn(() => setAccessToken(FRESH_TOKEN))
    const events = `event:delta\ndata:first\n\nevent:delta\ndata:old account\n\n${completeEvent(chatPayload())}\n\n`
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(events))
      },
      cancel,
    })
    mockFetchResponse(sseResponse(body))
    setAccessToken(STALE_TOKEN)

    await expect(apiChatStream(chatRequest(), { onDelta })).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(onDelta).toHaveBeenCalledExactlyOnceWith('first')
    expect(cancel).toHaveBeenCalledOnce()
    expect(body.locked).toBe(false)
  })

  it.each([null, FRESH_TOKEN])('does not refresh an old stream after the token becomes %s', async (nextToken) => {
    const answer = deferred<Response>()
    window.fetch = vi
      .fn()
      .mockReturnValueOnce(answer.promise)
      .mockResolvedValue(sseResponse(completeEvent(chatPayload())))
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    mockRefresh('unexpected-token')
    setAccessToken(STALE_TOKEN)
    const result = expect(apiChatStream(chatRequest())).rejects.toMatchObject({ response: { status: 401 } })
    setAccessToken(nextToken)
    answer.resolve(new Response('', { status: 401 }))
    await result

    expect(window.fetch).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe(nextToken)
    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it.each([true, false])(
    'does not retry or invalidate after a session changes during stream refresh (success=%s)',
    async (succeeds) => {
      const refresh = deferred<void>()
      mockFetchResponse(new Response('', { status: 401 }))
      const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
      http.defaults.adapter = (async (config) => {
        await refresh.promise
        if (!succeeds) throw axiosHttpError(config, 401, {})
        return axiosResponse(config, 200, { success: true, data: { token: 'unexpected-token' } })
      }) satisfies AxiosAdapter
      setAccessToken(STALE_TOKEN)
      const result = expect(apiChatStream(chatRequest())).rejects.toMatchObject({ response: { status: 401 } })
      await flushPromises()
      setAccessToken(FRESH_TOKEN)
      refresh.resolve()
      await result

      expect(window.fetch).toHaveBeenCalledTimes(1)
      expect(getAccessToken()).toBe(FRESH_TOKEN)
      expect(dispatchEvent).not.toHaveBeenCalled()
    },
  )

  it('does not invalidate a newer session when the retried stream returns 401', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    const answer = deferred<Response>()
    window.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockReturnValueOnce(answer.promise)
    mockRefresh(FRESH_TOKEN)
    setAccessToken(STALE_TOKEN)
    const result = expect(apiChatStream(chatRequest())).rejects.toMatchObject({ response: { status: 401 } })
    await flushPromises()
    setAccessToken('new-session-token')
    answer.resolve(new Response('', { status: 401 }))
    await result

    expect(window.fetch).toHaveBeenCalledTimes(2)
    expect(getAccessToken()).toBe('new-session-token')
    expect(dispatchEvent).not.toHaveBeenCalled()
  })

  it('reports the message of an error event', async () => {
    const message = 'SparkBot could not answer that.'
    mockFetchResponse(sseResponse(`event:delta\ndata:partial\n\nevent:error\ndata:${message}\n\n`))

    await streamErrorExpectation(message)
  })

  it.each([
    ['event:complete\ndata:{not-json}', STREAM_UNFINISHED_MESSAGE],
    ['event:error\ndata:SparkBot could not answer that.', 'SparkBot could not answer that.'],
  ])('cancels the open response body after a terminal event fails', async (event, message) => {
    const cancel = vi.fn()
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(`${event}\n\n`))
      },
      cancel,
    })
    mockFetchResponse(sseResponse(body))

    await streamErrorExpectation(message)

    expect(cancel).toHaveBeenCalledOnce()
    expect(body.locked).toBe(false)
  })

  it('does not send the request when the caller signal is already aborted', async () => {
    window.fetch = vi.fn()
    const controller = new AbortController()
    controller.abort()

    await expect(apiChatStream(chatRequest(), { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })
    expect(window.fetch).not.toHaveBeenCalled()
  })

  it('cancels a pending connection with the caller signal instead of reporting a timeout', async () => {
    mockHangingFetch()
    const controller = new AbortController()

    const request = apiChatStream(chatRequest(), { signal: controller.signal })
    controller.abort()

    await expect(request).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('stops reading the stream when the caller aborts after the first delta', async () => {
    const encoder = new TextEncoder()
    let cancelled = false
    const body = new ReadableStream<Uint8Array>({
      start(streamController) {
        streamController.enqueue(encoder.encode('event:delta\ndata:ni\n\nevent:delta\ndata: hao\n\n'))
      },
      cancel() {
        cancelled = true
      },
    })
    mockFetchResponse(sseResponse(body))
    const controller = new AbortController()
    const onDelta = vi.fn(() => controller.abort())

    await expect(apiChatStream(chatRequest(), { onDelta, signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })

    expect(onDelta).toHaveBeenCalledExactlyOnceWith('ni')
    expect(cancelled).toBe(true)
  })

  it('rejects a 200 response with no body stream as unstarted, or canceled if the session changed first', async () => {
    mockFetchResponse(new Response(null, { status: 200 }))
    await streamErrorExpectation('The AI helper could not start an answer. Please try again.')

    const answer = deferred<Response>()
    window.fetch = vi.fn().mockReturnValueOnce(answer.promise)
    setAccessToken(STALE_TOKEN)
    const request = apiChatStream(chatRequest())
    setAccessToken(FRESH_TOKEN)
    answer.resolve(new Response(null, { status: 200 }))
    await expect(request).rejects.toMatchObject({ code: 'ERR_CANCELED' })
  })

  it('cancels an open 200 response body when the session changes before the stream reader starts', async () => {
    const cancel = vi.fn()
    const body = new ReadableStream<Uint8Array>({ cancel })
    const answer = deferred<Response>()
    window.fetch = vi.fn().mockReturnValueOnce(answer.promise)
    setAccessToken(STALE_TOKEN)

    const request = apiChatStream(chatRequest())
    setAccessToken(FRESH_TOKEN)
    answer.resolve(sseResponse(body))

    await expect(request).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(cancel).toHaveBeenCalledOnce()
    expect(body.locked).toBe(false)
  })

  it('rejects as canceled if the session changes before the final stream chunk closes without trailing newline', async () => {
    const secondChunk = deferred<void>()
    const encoder = new TextEncoder()
    const payload = chatPayload()
    const body = new ReadableStream<Uint8Array>({
      async start(controller) {
        controller.enqueue(encoder.encode(completeEvent(payload)))
        await secondChunk.promise
        controller.close()
      },
    })
    mockFetchResponse(sseResponse(body))
    setAccessToken(STALE_TOKEN)

    const request = apiChatStream(chatRequest())
    await flushPromises()
    setAccessToken(FRESH_TOKEN)
    secondChunk.resolve()

    await expect(request).rejects.toMatchObject({ code: 'ERR_CANCELED' })
  })

  it('maps a fetch TypeError into a network error rather than leaking a raw TypeError', async () => {
    window.fetch = vi.fn().mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await streamErrorExpectation('We cannot reach the learning service right now. Check your connection and try again.')
  })
})
