import { apiClient } from '../services/apiClient'

/**
 * Parse the filename out of a `Content-Disposition` header. Handles both the
 * RFC 5987 UTF-8 form (`filename*=UTF-8''...`) and the plain quoted form
 * (`filename="..."`), with UTF-8 decoded values taking precedence because they
 * correctly represent non-ASCII names. Returns `null` when the header is
 * missing or neither form is present so callers can fall back to a
 * known-safe filename.
 *
 * Exported for unit tests — production callers should use
 * `downloadProtectedFile` instead.
 */
export function extractFileName(contentDisposition?: string): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      // Malformed percent-encoding: fall through to the quoted form below.
    }
  }

  const quotedMatch = contentDisposition.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }

  return null
}

export async function downloadProtectedFile(url: string, fallbackFileName: string) {
  const response = await apiClient.get<Blob>(url, {
    responseType: 'blob',
  })

  const blobUrl = window.URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  const fileName = extractFileName(response.headers['content-disposition']) ?? fallbackFileName

  anchor.href = blobUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.URL.revokeObjectURL(blobUrl)
}
