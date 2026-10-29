import { publicApi } from '@/api/services'
import type { PublicPlatformSettingsResponse } from '@/types/api'

let sharedSettings: Promise<PublicPlatformSettingsResponse | null> | null = null

/**
 * The public platform settings (admin AI disclaimer, configured AI model), loaded once per page load and shared by
 * every caller. A failed load gives null so callers keep their translated defaults; after a failure
 * the next caller tries again.
 */
export function loadPublicPlatformSettings(): Promise<PublicPlatformSettingsResponse | null> {
  if (!sharedSettings) {
    sharedSettings = Promise.resolve()
      .then(() => publicApi.platformSettings())
      .catch(() => {
        sharedSettings = null
        return null
      })
  }
  return sharedSettings
}
