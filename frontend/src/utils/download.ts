interface BlobDownloadPayload {
  blob: Blob
  fileName?: string | null
}

export function saveBlobDownload(download: BlobDownloadPayload, fallbackName: string) {
  const objectUrl = URL.createObjectURL(download.blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = download.fileName?.trim() || fallbackName
  anchor.rel = 'noopener'
  anchor.style.display = 'none'
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}
