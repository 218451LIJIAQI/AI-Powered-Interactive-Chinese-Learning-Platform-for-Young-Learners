import { ref, toValue } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import type { DownloadResult } from '@/api/request'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useToast } from '@/composables/useToast'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'

/** Downloads one filtered log at a time and reports the outcome in the language shown when it finishes. */
export function useCsvExport(options: {
  load: () => Promise<DownloadResult>
  fileName: string | (() => string)
  successMessage: () => string
  failureMessage: () => string
}) {
  const exporting = ref(false)
  const exportError = ref<string | null>(null)
  const { toast, showToast } = useToast()
  const isUnmounted = useIsUnmounted()

  async function exportCsv(): Promise<void> {
    if (exporting.value) return
    const sessionVersion = getAuthSessionVersion()
    exporting.value = true
    exportError.value = null
    try {
      const download = await options.load()
      if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
      saveBlobDownload(download, toValue(options.fileName))
      showToast(options.successMessage())
    } catch (failure) {
      if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
      exportError.value = resolveApiError(failure, options.failureMessage())
    } finally {
      if (!isUnmounted()) exporting.value = false
    }
  }

  return { exporting, exportError, toast, exportCsv }
}
