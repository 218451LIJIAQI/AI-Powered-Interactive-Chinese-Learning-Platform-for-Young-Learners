import { truncateText } from '@/utils/truncateText'

interface BlobDownloadPayload {
  blob: Blob
  fileName?: string | null
}

const MAX_DOWNLOAD_FILE_NAME_LENGTH = 180
/** How long an opened file's object URL stays valid (the new tab has loaded it by then). */
const PREVIEW_URL_LIFETIME_MS = 60_000
const WINDOWS_RESERVED_DEVICE_NAMES = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?$/i

/** PDFs, PNGs and JPEGs can open in a preview tab. */
export function canPreviewFile(blob: Blob): boolean {
  const [mimeType] = blob.type.split(';', 1)
  return ['application/pdf', 'image/png', 'image/jpeg'].includes(mimeType.trim().toLowerCase())
}

/**
 * Opens an authorised file (a support screenshot) in a new tab when the browser can show it (PDF, PNG, JPG) and saves
 * it otherwise. The tab is opened before the download starts, inside the click, so pop-up blockers allow it.
 */
export async function openFileDownload(load: () => Promise<BlobDownloadPayload>, fallbackName: string): Promise<void> {
  const tab = window.open('', '_blank')
  if (tab) tab.opener = null
  let previewed = false
  let download: BlobDownloadPayload
  try {
    download = await load()
    if (tab && !tab.closed && canPreviewFile(download.blob)) {
      const url = URL.createObjectURL(download.blob)
      try {
        tab.location.href = url
      } catch (error) {
        URL.revokeObjectURL(url)
        throw error
      }
      window.setTimeout(() => URL.revokeObjectURL(url), PREVIEW_URL_LIFETIME_MS)
      previewed = true
      return
    }
  } finally {
    if (!previewed && tab && !tab.closed) tab.close()
  }
  saveBlobDownload(download, fallbackName)
}

/**
 * Saves a fetched file through a hidden download link, under its own name or else `fallbackName`, made safe for every
 * operating system ("download" when nothing usable is left).
 */
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

function sanitizeCandidate(raw: string): string | null {
  const candidate = limitFileNameLength(normalizeFileNameCandidate(raw.trim()))
  return candidate === '' || WINDOWS_RESERVED_DEVICE_NAMES.test(candidate) ? null : candidate
}

function sanitizeDownloadFileName(fileName: string | null | undefined, fallbackName: string): string {
  return sanitizeCandidate(fileName ?? '') ?? sanitizeCandidate(fallbackName) ?? 'download'
}

function normalizeFileNameCandidate(fileName: string): string {
  return (
    fileName
      // eslint-disable-next-line no-control-regex
      .replace(/[\u0000-\u001f\u007f-\u009f]/g, '_')
      .replace(/\p{Cf}/gu, '')
      .replace(/[\\/:"*?<>|]+/g, '_')
      .replace(/\s+/g, ' ')
      .replace(/^[. ]+/, '')
      .replace(/[. ]+$/g, '')
      .trim()
  )
}

/** A name over the limit is cut short; a short extension (".pdf") stays on its end. */
function limitFileNameLength(fileName: string): string {
  if (fileName.length <= MAX_DOWNLOAD_FILE_NAME_LENGTH) return fileName
  const dotIndex = fileName.lastIndexOf('.')
  const extension = dotIndex > 0 && fileName.length - dotIndex <= 16 ? fileName.slice(dotIndex) : ''
  const stem = truncateText(fileName, MAX_DOWNLOAD_FILE_NAME_LENGTH - extension.length)
    .replace(/[. ]+$/g, '')
    .trim()
  return stem ? `${stem}${extension}` : ''
}
