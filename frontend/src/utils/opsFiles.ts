import type { DownloadResult } from '@/api/services'
import { saveBlobDownload } from '@/utils/download'

const PREVIEWABLE_TYPES = /^(application\/pdf|image\/(png|jpeg))\b/i
/** How long an opened file's object URL stays valid (the new tab has loaded it by then). */
const OBJECT_URL_LIFETIME_MS = 60_000

/**
 * Opens an authorised file (a verification document, a support screenshot) in a new tab when the browser can show it
 * (PDF, PNG, JPG) and saves it otherwise. The tab is opened before the download starts, inside the click, so pop-up
 * blockers allow it.
 */
export async function openFileDownload(load: () => Promise<DownloadResult>, fallbackName: string): Promise<void> {
  const tab = window.open('', '_blank')
  if (tab) tab.opener = null
  try {
    const download = await load()
    if (tab && PREVIEWABLE_TYPES.test(download.blob.type)) {
      const url = URL.createObjectURL(download.blob)
      tab.location.href = url
      window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_LIFETIME_MS)
      return
    }
    tab?.close()
    saveBlobDownload(download, fallbackName)
  } catch (failure) {
    tab?.close()
    throw failure
  }
}
