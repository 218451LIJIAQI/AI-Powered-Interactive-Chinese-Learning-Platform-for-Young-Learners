import type { DownloadResult } from '@/api/request'
import { saveBlobDownload } from '@/utils/download'

/** How long an opened file's object URL stays valid (the new tab has loaded it by then). */
const OBJECT_URL_LIFETIME_MS = 60_000

/** PDFs, PNGs and JPEGs can open in a preview tab. */
export function canPreviewFile(blob: Blob): boolean {
  const [mimeType] = blob.type.split(';', 1)
  return ['application/pdf', 'image/png', 'image/jpeg'].includes(mimeType.trim().toLowerCase())
}

/**
 * Opens an authorised file (a support screenshot) in a new tab when the browser can show it (PDF, PNG, JPG) and saves
 * it otherwise. The tab is opened before the download starts, inside the click, so pop-up blockers allow it.
 */
export async function openFileDownload(load: () => Promise<DownloadResult>, fallbackName: string): Promise<void> {
  const tab = window.open('', '_blank')
  if (tab) tab.opener = null
  try {
    const download = await load()
    if (tab && !tab.closed && canPreviewFile(download.blob)) {
      const url = URL.createObjectURL(download.blob)
      window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_LIFETIME_MS)
      tab.location.href = url
      return
    }
    if (tab && !tab.closed) tab.close()
    saveBlobDownload(download, fallbackName)
  } catch (failure) {
    if (tab && !tab.closed) tab.close()
    throw failure
  }
}
