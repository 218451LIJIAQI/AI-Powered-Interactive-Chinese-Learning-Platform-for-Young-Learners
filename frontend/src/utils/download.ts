/**
 * Triggers a browser file-save dialog for a Blob fetched via the API
 * (e.g. recorded video, payment receipt, teacher verification document).
 *
 * <h2>Why a helper</h2>
 * Stitching together `URL.createObjectURL`, a hidden `<a download>`,
 * click(), and timely `revokeObjectURL` is fiddly and easy to get
 * wrong. Centralising it here means every download surface shares
 * one tested implementation and the same hardened filename sanitiser.
 *
 * <h2>Filename safety</h2>
 * {@link sanitizeDownloadFileName} strips control characters and
 * shell-tricky path separators before they reach the OS save dialog
 * as defense in depth on top of whatever the backend already does.
 */
interface BlobDownloadPayload {
  blob: Blob
  fileName?: string | null
}

const MAX_DOWNLOAD_FILE_NAME_LENGTH = 180
const WINDOWS_RESERVED_DEVICE_NAMES = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?$/i

export function saveBlobDownload(download: BlobDownloadPayload, fallbackName: string) {
  if (typeof document === 'undefined' || typeof window === 'undefined') {
    throw new Error('Browser downloads are not available in this environment.')
  }

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

export function sanitizeDownloadFileName(fileName: string | null | undefined, fallbackName: string) {
  const candidate = limitFileNameLength(
    (fileName?.trim() || fallbackName.trim() || 'download')
      .replace(/[\u0000-\u001f\u007f]/g, '_')
      .replace(/[\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff]/g, '')
      .replace(/[\\/:"*?<>|]+/g, '_')
      .replace(/\s+/g, ' ')
      .replace(/^\.+/, '')
      .replace(/[. ]+$/g, '')
      .trim(),
  )
  return candidate === '' || WINDOWS_RESERVED_DEVICE_NAMES.test(candidate) ? 'download' : candidate
}

function limitFileNameLength(fileName: string) {
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
