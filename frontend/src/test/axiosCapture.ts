import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach } from 'vitest'

import { http } from '@/api/http'

/** Puts the shared axios adapter back after every test of the calling file (tests replace it to answer requests). */
export function restoreAxiosAdapterAfterEach(): void {
  const originalAdapter = http.defaults.adapter
  afterEach(() => {
    http.defaults.adapter = originalAdapter
  })
}

/** One request seen by the capture adapter; `data` is the parsed JSON body when there is one. */
interface CapturedRequest {
  method: string
  url: string
  data?: unknown
  params?: unknown
}

/** A plain axios response for an adapter to return or throw. */
export function axiosResponse<T>(config: InternalAxiosRequestConfig, status: number, data: T): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status >= 400 ? 'Error' : 'OK',
    headers: {},
    config,
  }
}

/** What axios rejects with when the server answers a 4xx `status` with `data`. */
export function axiosHttpError<T>(config: InternalAxiosRequestConfig, status: number, data: T): AxiosError {
  const response = axiosResponse(config, status, data)
  return new AxiosError(
    `Request failed with status code ${status}`,
    AxiosError.ERR_BAD_REQUEST,
    config,
    undefined,
    response,
  )
}

function parseData(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

/**
 * Replaces the shared axios adapter: records every request and answers each with a success envelope around `data`.
 * See restoreAxiosAdapterAfterEach.
 */
export function installAxiosCapture(data: unknown): CapturedRequest[] {
  const calls: CapturedRequest[] = []
  http.defaults.adapter = (async (config) => {
    calls.push({
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      data: parseData(config.data),
      params: config.params,
    })
    return axiosResponse(config, 200, { success: true, data })
  }) satisfies AxiosAdapter
  return calls
}
