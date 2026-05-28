import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AUTH_INVALIDATED_EVENT, http, setAccessToken } from './http'
import { aiApi } from './services'

const originalAdapter = http.defaults.adapter

function sseResponse(body: string, init: ResponseInit = {}) {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
    ...init,
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
    const payload = {
      sessionId: 7,
      userMessage: 'hello',
      aiResponse: 'ni hao',
      language: 'zh',
      createdAt: '2026-05-26T00:00:00.000Z',
      status: 'SUCCESS',
    }
    window.fetch = vi
      .fn()
      .mockResolvedValue(sseResponse(`event: delta\ndata: ni\n\nevent: complete\ndata: ${JSON.stringify(payload)}`))
    const onDelta = vi.fn()
    const onComplete = vi.fn()

    const result = await aiApi.chatStream({ message: 'hello' }, { onDelta, onComplete })

    expect(result).toEqual(payload)
    expect(onDelta).toHaveBeenCalledWith('ni')
    expect(onComplete).toHaveBeenCalledWith(payload)
  })

  it('processes multiline SSE data fields as a single event payload', async () => {
    const payload = {
      sessionId: 7,
      userMessage: 'hello',
      aiResponse: 'line one\nline two',
      language: 'en',
      createdAt: '2026-05-26T00:00:00.000Z',
      status: 'SUCCESS',
    }
    window.fetch = vi
      .fn()
      .mockResolvedValue(
        sseResponse(
          `event: delta\ndata: line one\ndata: line two\n\nevent: complete\ndata: ${JSON.stringify(payload)}`,
        ),
      )
    const onDelta = vi.fn()

    await aiApi.chatStream({ message: 'hello' }, { onDelta })

    expect(onDelta).toHaveBeenCalledWith('line one\nline two')
  })

  it('maps malformed complete events to a friendly API envelope error', async () => {
    window.fetch = vi.fn().mockResolvedValue(sseResponse('event: complete\ndata: {not-json}'))

    await expect(aiApi.chatStream({ message: 'hello' })).rejects.toMatchObject({
      response: {
        data: {
          message: 'The AI stream returned a malformed final response.',
        },
      },
    })
  })

  it('preserves backend JSON errors for failed stream requests', async () => {
    window.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          message: 'AI safety policy blocked this prompt.',
          errors: ['Try a learning question instead.'],
          fieldErrors: { message: ['Please use classroom language.'] },
        }),
        {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    await expect(aiApi.chatStream({ message: 'unsafe' })).rejects.toMatchObject({
      response: {
        status: 400,
        data: {
          message: 'AI safety policy blocked this prompt.',
          errors: ['Try a learning question instead.'],
          fieldErrors: { message: ['Please use classroom language.'] },
        },
      },
    })
  })

  it('uses a stable rate-limit message when the stream endpoint returns plain text', async () => {
    window.fetch = vi.fn().mockResolvedValue(new Response('Too many requests', { status: 429 }))

    await expect(aiApi.chatStream({ message: 'hello' })).rejects.toMatchObject({
      response: {
        status: 429,
        data: {
          message: 'You are doing that too often. Please wait a moment and try again.',
        },
      },
    })
  })

  it('aborts a stream request that never receives an initial response', async () => {
    vi.useFakeTimers()
    window.fetch = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        }),
    )

    const request = expect(aiApi.chatStream({ message: 'hello' })).rejects.toMatchObject({
      response: {
        data: {
          message: 'The AI stream took too long to respond. Please try again.',
        },
      },
    })
    await vi.advanceTimersByTimeAsync(30000)
    await request
  })

  it('invalidates the browser auth session when stream refresh cannot recover a 401', async () => {
    const dispatchEvent = vi.spyOn(window, 'dispatchEvent')
    window.fetch = vi.fn().mockResolvedValue(new Response('', { status: 401 }))
    http.defaults.adapter = (async (config) => {
      if (config.url === '/auth/refresh') {
        throwAxiosStatus(config, 401, { success: false, message: 'refresh expired' })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    await expect(aiApi.chatStream({ message: 'hello' })).rejects.toMatchObject({
      response: {
        status: 401,
        data: {
          message: 'Please sign in again to continue.',
        },
      },
    })

    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: AUTH_INVALIDATED_EVENT })
  })

  it('retries a stream once with the rotated access token after a 401', async () => {
    const authorizations: Array<string | null> = []
    const payload = {
      sessionId: 8,
      userMessage: 'hello',
      aiResponse: '你好',
      language: 'zh',
      createdAt: '2026-05-26T00:00:00.000Z',
      status: 'SUCCESS',
    }

    setAccessToken('stale-token')
    window.fetch = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      const headers = init?.headers as Headers
      authorizations.push(headers.get('Authorization'))
      return Promise.resolve(
        authorizations.length === 1
          ? new Response('', { status: 401 })
          : sseResponse(`event: complete\ndata: ${JSON.stringify(payload)}`),
      )
    })
    http.defaults.adapter = (async (config) => {
      if (config.url === '/auth/refresh') {
        return apiResponse(config, 200, { success: true, data: { token: 'fresh-token' } })
      }

      throw new Error(`Unexpected request ${config.url}`)
    }) satisfies AxiosAdapter

    await expect(aiApi.chatStream({ message: 'hello' })).resolves.toEqual(payload)

    expect(authorizations).toEqual(['Bearer stale-token', 'Bearer fresh-token'])
  })

  it('reports an incomplete stream when no final complete event arrives', async () => {
    window.fetch = vi.fn().mockResolvedValue(sseResponse('event: delta\ndata: partial\n\n'))

    await expect(aiApi.chatStream({ message: 'hello' })).rejects.toMatchObject({
      response: {
        data: {
          message: 'The AI stream ended before the final response arrived.',
        },
      },
    })
  })
})
