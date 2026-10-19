import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AUTH_INVALIDATED_EVENT, http, setAccessToken } from './http'
import { aiApi } from './services'

const CHAT_MESSAGE = 'hello'
const CREATED_AT = '2026-05-26T00:00:00.000Z'
const FRESH_TOKEN = 'fresh-token'
const INCOMPLETE_STREAM_MESSAGE = 'The AI stream ended before the final response arrived.'
const MALFORMED_FINAL_RESPONSE_MESSAGE = 'The AI stream returned a malformed final response.'
const RATE_LIMIT_MESSAGE = 'You are doing that too often. Please wait a moment and try again.'
const REFRESH_ENDPOINT = '/auth/refresh'
const REFRESH_EXPIRED_MESSAGE = 'refresh expired'
const SIGN_IN_AGAIN_MESSAGE = 'Please sign in again to continue.'
const STALE_TOKEN = 'stale-token'
const STREAM_TIMEOUT_MESSAGE = 'The AI stream took too long to respond. Please try again.'
const STREAM_TIMEOUT_MS = 30_000

const originalAdapter = http.defaults.adapter

type ChatStreamPayload = {
  aiResponse: string
  createdAt: string
  language: string
  sessionId: number
  status: string
  userMessage: string
}

const bearer = (token: string) => `Bearer ${token}`
const chatRequest = () => ({ message: CHAT_MESSAGE })

function chatPayload(overrides: Partial<ChatStreamPayload> = {}): ChatStreamPayload {
  return {
    sessionId: 7,
    userMessage: CHAT_MESSAGE,
    aiResponse: 'ni hao',
    language: 'zh-CN',
    createdAt: CREATED_AT,
    status: 'SUCCESS',
    ...overrides,
  }
}

function completeEvent(payload: ChatStreamPayload): string {
  return `event: complete\ndata: ${JSON.stringify(payload)}`
}

function mockFetchResponse(response: Response): void {
  window.fetch = vi.fn().mockResolvedValue(response)
}

function sseResponse(body: string, init: ResponseInit = {}): Response {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
    ...init,
  })
}

function streamErrorExpectation(message: string, status?: number) {
  return expect(aiApi.chatStream(chatRequest())).rejects.toMatchObject({
    response: {
      ...(status == null ? {} : { status }),
      data: { message },
    },
  })
}

function apiResponse<T>(config: InternalAxiosRequestConfig, status: number, data: T): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status >= 400 ? 'Error' : 'OK',
    headers: {},
    config,
  }
}

function throwAxiosStatus<T>(config: InternalAxiosRequestConfig, status: number, data: T): never {
  const response = apiResponse(config, status, data)
  throw new AxiosError(
    `Request failed with status code ${status}`,
    AxiosError.ERR_BAD_REQUEST,
    config,
    undefined,
    response,
  )
}

function unexpectedRequest(config: InternalAxiosRequestConfig): Error {
  return new Error(`Unexpected request ${config.url}`)
}

function mockRefreshFailure(): void {
  http.defaults.adapter = (async (config) => {
    if (config.url === REFRESH_ENDPOINT) {
      throwAxiosStatus(config, 401, { success: false, message: REFRESH_EXPIRED_MESSAGE })
    }

    throw unexpectedRequest(config)
  }) satisfies AxiosAdapter
}

function mockRefreshSuccess(token = FRESH_TOKEN): void {
  http.defaults.adapter = (async (config) => {
    if (config.url === REFRESH_ENDPOINT) {
      return apiResponse(config, 200, { success: true, data: { token } })
    }

    throw unexpectedRequest(config)
  }) satisfies AxiosAdapter
}

describe('aiApi.chatStream', () => {
  const originalFetch = window.fetch

  afterEach(() => {
    vi.useRealTimers()
    window.fetch = originalFetch
    http.defaults.adapter = originalAdapter
    setAccessToken(null)
    vi.restoreAllMocks()
  })

  it('processes the final SSE event even when the stream omits a trailing blank line', async () => {
    const payload = chatPayload()
    mockFetchResponse(sseResponse(`event: delta\ndata: ni\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()
    const onComplete = vi.fn()

    const result = await aiApi.chatStream(chatRequest(), { onDelta, onComplete })

    expect(result).toEqual(payload)
    expect(onDelta).toHaveBeenCalledWith('ni')
    expect(onComplete).toHaveBeenCalledWith(payload)
  })

  it('processes multiline SSE data fields as a single event payload', async () => {
    const payload = chatPayload({
      aiResponse: 'line one\nline two',
      language: 'en',
    })
    mockFetchResponse(sseResponse(`event: delta\ndata: line one\ndata: line two\n\n${completeEvent(payload)}`))
    const onDelta = vi.fn()

    await aiApi.chatStream(chatRequest(), { onDelta })

    expect(onDelta).toHaveBeenCalledWith('line one\nline two')
  })

  it('maps malformed complete events to a friendly API envelope error', async () => {
    mockFetchResponse(sseResponse('event: complete\ndata: {not-json}'))

    await streamErrorExpectation(MALFORMED_FINAL_RESPONSE_MESSAGE)
  })

  it('preserves backend JSON errors for failed stream requests', async () => {
    const backendError = {
      message: 'AI safety policy blocked this prompt.',
      errors: ['Try a learning question instead.'],
      fieldErrors: { message: ['Please use classroom language.'] },
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
    window.fetch = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        }),
    )

    const request = streamErrorExpectation(STREAM_TIMEOUT_MESSAGE)
    await vi.advanceTimersByTimeAsync(STREAM_TIMEOUT_MS)
    await request
  })

  it('invalidates the browser auth session when stream refresh cannot recover a 401', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    mockFetchResponse(new Response('', { status: 401 }))
    mockRefreshFailure()

    await streamErrorExpectation(SIGN_IN_AGAIN_MESSAGE, 401)

    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })

  it('retries a stream once with the rotated access token after a 401', async () => {
    const authorizations: Array<string | null> = []
    const payload = chatPayload({
      sessionId: 8,
      aiResponse: '你好',
    })

    setAccessToken(STALE_TOKEN)
    window.fetch = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      const headers = init?.headers as Headers
      authorizations.push(headers.get('Authorization'))
      return Promise.resolve(
        authorizations.length === 1 ? new Response('', { status: 401 }) : sseResponse(completeEvent(payload)),
      )
    })
    mockRefreshSuccess()

    await expect(aiApi.chatStream(chatRequest())).resolves.toEqual(payload)

    expect(authorizations).toEqual([bearer(STALE_TOKEN), bearer(FRESH_TOKEN)])
  })

  it('reports an incomplete stream when no final complete event arrives', async () => {
    mockFetchResponse(sseResponse('event: delta\ndata: partial\n\n'))

    await streamErrorExpectation(INCOMPLETE_STREAM_MESSAGE)
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
    window.fetch = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        }),
    )
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
        streamController.enqueue(encoder.encode('event: delta\ndata: ni\n\n'))
      },
      cancel() {
        cancelled = true
      },
    })
    mockFetchResponse(new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }))
    const controller = new AbortController()
    const onComplete = vi.fn()
    const onDelta = vi.fn(() => controller.abort())

    await expect(
      aiApi.chatStream(chatRequest(), { onDelta, onComplete, signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' })

    expect(onDelta).toHaveBeenCalledWith('ni')
    expect(onComplete).not.toHaveBeenCalled()
    expect(cancelled).toBe(true)
  })
})
