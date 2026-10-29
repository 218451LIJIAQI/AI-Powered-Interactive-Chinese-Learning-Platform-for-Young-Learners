import type { AxiosAdapter } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AUTH_INVALIDATED_EVENT, http, setAccessToken } from './http'
import { aiApi } from './services'
import { appEnv } from '@/config/env'
import { axiosHttpError, axiosResponse, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'
import type { AiChatResponse } from '@/types/api'

const CHAT_MESSAGE = 'hello'
const FRESH_TOKEN = 'fresh-token'
const STREAM_UNFINISHED_MESSAGE = 'The AI helper could not finish this answer. Please try again.'
const RATE_LIMIT_MESSAGE = 'You are doing that too often. Please wait a moment and try again.'
const REFRESH_ENDPOINT = '/auth/refresh'
const REFRESH_EXPIRED_MESSAGE = 'refresh expired'
const SIGN_IN_AGAIN_MESSAGE = 'Please sign in again to continue.'
const STALE_TOKEN = 'stale-token'
const STREAM_TIMEOUT_MESSAGE = 'The AI helper took too long to answer. Please try again.'
const STREAM_TIMEOUT_MS = 30_000

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
  return expect(aiApi.chatStream(chatRequest())).rejects.toMatchObject({
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

describe('aiApi.chatStream', () => {
  const originalFetch = window.fetch

  afterEach(() => {
    vi.useRealTimers()
    window.fetch = originalFetch
    setAccessToken(null)
  })

  it('processes the final SSE event even when the stream omits a trailing blank line', async () => {
    const payload = chatPayload()
    mockFetchResponse(sseResponse(`event:delta\ndata:ni\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    const result = await aiApi.chatStream(chatRequest(), { onDelta })

    expect(result).toEqual(payload)
    expect(onDelta).toHaveBeenCalledWith('ni')
  })

  it('processes multiline SSE data fields as a single event payload', async () => {
    const payload = chatPayload({
      aiResponse: 'line one\nline two',
    })
    mockFetchResponse(sseResponse(`event:delta\ndata:line one\ndata:line two\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    await aiApi.chatStream(chatRequest(), { onDelta })

    expect(onDelta).toHaveBeenCalledWith('line one\nline two')
  })

  it('keeps the leading space of a streamed chunk (Spring writes data: without a separator)', async () => {
    const payload = chatPayload({ aiResponse: 'Hello world' })
    mockFetchResponse(sseResponse(`event:delta\ndata:Hello\n\nevent:delta\ndata: world\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    await aiApi.chatStream(chatRequest(), { onDelta })

    expect(onDelta).toHaveBeenNthCalledWith(1, 'Hello')
    expect(onDelta).toHaveBeenNthCalledWith(2, ' world')
  })

  it('maps malformed complete events to a friendly API envelope error', async () => {
    mockFetchResponse(sseResponse('event:complete\ndata:{not-json}'))

    await streamErrorExpectation(STREAM_UNFINISHED_MESSAGE)
  })

  it('preserves backend JSON errors for failed stream requests', async () => {
    const backendError = {
      message: 'AI safety policy blocked this prompt.',
      errors: ['Try a learning question instead.'],
    }
    mockFetchResponse(
      new Response(JSON.stringify(backendError), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(aiApi.chatStream({ message: 'unsafe' })).rejects.toMatchObject({
      response: {
        status: 400,
        data: backendError,
      },
    })
  })

  it('uses a stable rate-limit message when the stream endpoint returns plain text', async () => {
    mockFetchResponse(new Response('Too many requests', { status: 429 }))

    await streamErrorExpectation(RATE_LIMIT_MESSAGE, 429)
  })

  it('aborts a stream request that never receives an initial response', async () => {
    vi.useFakeTimers()
    mockHangingFetch()

    const request = streamErrorExpectation(STREAM_TIMEOUT_MESSAGE)
    await vi.advanceTimersByTimeAsync(STREAM_TIMEOUT_MS)
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

    await expect(aiApi.chatStream(chatRequest())).resolves.toEqual(payload)

    expect(authorizations).toEqual([bearer(STALE_TOKEN), bearer(FRESH_TOKEN)])
    // The configured API base (default /api/v1, already without a trailing slash) joined with the stream path.
    const streamUrl = `${appEnv.apiBaseUrl}/ai/chat/stream`
    expect(urls).toEqual([streamUrl, streamUrl])
  })

  it('reports an incomplete stream when no final complete event arrives', async () => {
    mockFetchResponse(sseResponse('event:delta\ndata:partial\n\n'))

    await streamErrorExpectation(STREAM_UNFINISHED_MESSAGE)
  })

  it('does not send the request when the caller signal is already aborted', async () => {
    window.fetch = vi.fn()
    const controller = new AbortController()
    controller.abort()

    await expect(aiApi.chatStream(chatRequest(), { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })
    expect(window.fetch).not.toHaveBeenCalled()
  })

  it('cancels a pending connection with the caller signal instead of reporting a timeout', async () => {
    mockHangingFetch()
    const controller = new AbortController()

    const request = aiApi.chatStream(chatRequest(), { signal: controller.signal })
    controller.abort()

    await expect(request).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('stops reading the stream when the caller aborts after the first delta', async () => {
    const encoder = new TextEncoder()
    let cancelled = false
    const body = new ReadableStream<Uint8Array>({
      start(streamController) {
        streamController.enqueue(encoder.encode('event:delta\ndata:ni\n\n'))
      },
      cancel() {
        cancelled = true
      },
    })
    mockFetchResponse(sseResponse(body))
    const controller = new AbortController()
    const onDelta = vi.fn(() => controller.abort())

    await expect(aiApi.chatStream(chatRequest(), { onDelta, signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })

    expect(onDelta).toHaveBeenCalledWith('ni')
    expect(cancelled).toBe(true)
  })
})
