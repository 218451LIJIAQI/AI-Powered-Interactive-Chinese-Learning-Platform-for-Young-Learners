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
 * — defence in depth on top of whatever the backend already does.
 */
interface BlobDownloadPayload {
  blob: Blob
  fileName?: string | null
}

export function saveBlobDownload(download: BlobDownloadPayload, fallbackName: string) {
  const objectUrl = URL.createObjectURL(download.blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = sanitizeDownloadFileName(download.fileName, fallbackName)
  anchor.rel = 'noopener'
  anchor.style.display = 'none'
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

function sanitizeDownloadFileName(fileName: string | null | undefined, fallbackName: string) {
  const candidate = (fileName?.trim() || fallbackName.trim() || 'download')
    .replace(/[\u0000-\u001f\u007f]/g, '_')
    .replace(/[\\/:"*?<>|]+/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
  return candidate === '' || candidate === '.' || candidate === '..' ? 'download' : candidate
}
