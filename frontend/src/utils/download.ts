interface BlobDownloadPayload {
  blob: Blob
  fileName?: string | null
}

const MAX_DOWNLOAD_FILE_NAME_LENGTH = 180
const WINDOWS_RESERVED_DEVICE_NAMES = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?$/i

export function saveBlobDownload(download: BlobDownloadPayload, fallbackName: string): void {
  const objectUrl = URL.createObjectURL(download.blob)
  const anchor = document.createElement('a')
  try {
    anchor.href = objectUrl
    anchor.download = sanitizeDownloadFileName(download.fileName, fallbackName)
    anchor.rel = 'noopener'
    anchor.style.display = 'none'
    document.body.append(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  }
}

function sanitizeDownloadFileName(fileName: string | null | undefined, fallbackName: string): string {
  const candidate = limitFileNameLength(normalizeFileNameCandidate(fileName?.trim() || fallbackName.trim()))
  return candidate === '' || WINDOWS_RESERVED_DEVICE_NAMES.test(candidate) ? 'download' : candidate
}

function normalizeFileNameCandidate(fileName: string): string {
  return fileName
    .replace(/[\u0000-\u001f\u007f]/g, '_')
    .replace(/[\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff]/g, '')
    .replace(/[\\/:"*?<>|]+/g, '_')
    .replace(/\s+/g, ' ')
    .replace(/^\.+/, '')
    .replace(/[. ]+$/g, '')
    .trim()
}

function limitFileNameLength(fileName: string): string {
  if (fileName.length <= MAX_DOWNLOAD_FILE_NAME_LENGTH) {
    return fileName
  }

  const dotIndex = fileName.lastIndexOf('.')
  const hasUsableExtension = dotIndex > 0 && fileName.length - dotIndex <= 16
  if (!hasUsableExtension) {
    return fileName.slice(0, MAX_DOWNLOAD_FILE_NAME_LENGTH).trim()
  }

  const extension = fileName.slice(dotIndex)
  return `${fileName.slice(0, MAX_DOWNLOAD_FILE_NAME_LENGTH - extension.length).trim()}${extension}`
}
