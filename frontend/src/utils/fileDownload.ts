import { apiClient } from '../services/apiClient'

function extractFileName(contentDisposition?: string) {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
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
