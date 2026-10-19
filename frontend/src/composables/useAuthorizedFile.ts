import { onBeforeUnmount, ref } from 'vue'

import { resolveApiError } from '@/utils/apiError'

interface AuthorizedDownload {
  blob: Blob
  contentType: string | null
  fileName: string | null
}

type ObjectUrlApi = Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'>

/**
 * Loads a protected file (practice-submission audio / image) through the authenticated http client as a Blob and
 * exposes it as an object URL. Tokens never go into URLs; the object URL is revoked on reload and unmount.
 */
export function useAuthorizedFile(urlApi: ObjectUrlApi = URL) {
  const objectUrl = ref<string | null>(null)
  const contentType = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let sequence = 0

  function release(): void {
    if (objectUrl.value) {
      urlApi.revokeObjectURL(objectUrl.value)
    }
    objectUrl.value = null
    contentType.value = null
  }

  async function load(loader: () => Promise<AuthorizedDownload>, errorFallback: string): Promise<boolean> {
    const current = ++sequence
    release()
    loading.value = true
    error.value = null
    try {
      const download = await loader()
      if (current !== sequence) return false
      objectUrl.value = urlApi.createObjectURL(download.blob)
      contentType.value = download.contentType ?? (download.blob.type || null)
      return true
    } catch (reason) {
      if (current !== sequence) return false
      error.value = resolveApiError(reason, errorFallback)
      return false
    } finally {
      if (current === sequence) loading.value = false
    }
  }

  function reset(): void {
    sequence += 1
    release()
    loading.value = false
    error.value = null
  }

  onBeforeUnmount(reset)

  return { objectUrl, contentType, loading, error, load, reset }
}
