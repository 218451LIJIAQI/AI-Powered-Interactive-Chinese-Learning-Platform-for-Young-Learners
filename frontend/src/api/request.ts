/** Low-level REST envelope and protected blob download helpers shared by all domain API modules. */
import type { AxiosRequestConfig } from 'axios'

import { apiClientMessage } from '@/plugins/apiClientMessages'
import type { ApiResponse } from '@/types/api'
import { ApiEnvelopeError, cleanServerMessage } from '@/utils/apiError'

import { http } from './http'

const CONTENT_DISPOSITION_ASCII_FILENAME_PATTERN = /filename=(?:"([^"]+)"|([^;\s]+))/i
const CONTENT_DISPOSITION_UTF8_FILENAME_PATTERN = /filename\*=UTF-8''([^;\s]+)/i
const JSON_CONTENT_TYPE_PATTERN = /application\/(?:[^;\s]+\+)?json/i

export type DownloadResult = {
  blob: Blob
  fileName: string | null
}

/**
 * The `data` of a successful `ApiResponse` envelope. The backend answers every failure with an error status, which
 * axios rejects; a 2xx answer that is no envelope at all (a proxy's page) throws an ApiEnvelopeError.
 */
async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const payload = (await request).data

  if (payload?.success === false) {
    throw new ApiEnvelopeError(cleanServerMessage(payload.message) ?? apiClientMessage('fallback'))
  }
  if (payload?.success !== true || !('data' in payload)) {
    throw new ApiEnvelopeError(apiClientMessage('unexpectedFormat'))
  }

  return payload.data
}

export const apiGet = <T>(url: string, config?: AxiosRequestConfig) => unwrap<T>(http.get(url, config))
export const apiPost = <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
  unwrap<T>(http.post(url, body, config))
export const apiPut = <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
  unwrap<T>(http.put(url, body, config))
export const apiPatch = <T>(url: string, body?: unknown) => unwrap<T>(http.patch(url, body))
export const apiDelete = <T>(url: string) => unwrap<T>(http.delete(url))

/** The file name of a Content-Disposition header (the UTF-8 form first), or null. */
function parseContentDispositionFileName(contentDisposition: unknown): string | null {
  if (typeof contentDisposition !== 'string') {
    return null
  }

  const utf8Match = contentDisposition.match(CONTENT_DISPOSITION_UTF8_FILENAME_PATTERN)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }

  const asciiMatch = contentDisposition.match(CONTENT_DISPOSITION_ASCII_FILENAME_PATTERN)
  return asciiMatch?.[1] ?? asciiMatch?.[2] ?? null
}

/** A failed blob download receives the server's JSON error as a Blob; parse it so its message can be shown. */
async function readBlobErrorBody(error: unknown): Promise<never> {
  const response = (error as { response?: { data?: unknown; headers?: Record<string, unknown> } } | null)?.response
  const contentType =
    (response?.data instanceof Blob && response.data.type) ||
    (typeof response?.headers?.['content-type'] === 'string' ? response.headers['content-type'] : '')
  if (response?.data instanceof Blob && JSON_CONTENT_TYPE_PATTERN.test(contentType)) {
    try {
      response.data = JSON.parse(await response.data.text()) as unknown
    } catch {
      // Not readable JSON: the message for the HTTP status is shown instead.
    }
  }
  throw error
}

/** A protected file fetched with the bearer token as a Blob, with its file name. */
export async function apiDownload(url: string, config?: AxiosRequestConfig): Promise<DownloadResult> {
  const response = await http.get<Blob>(url, { ...config, responseType: 'blob' }).catch(readBlobErrorBody)
  return { blob: response.data, fileName: parseContentDispositionFileName(response.headers['content-disposition']) }
}
