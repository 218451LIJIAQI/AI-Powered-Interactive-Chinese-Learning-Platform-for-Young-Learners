import { config, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

// Every mounted component gets the app's i18n plugin (as in main.ts).
config.global.plugins = [i18n]
enableAutoUnmount(afterEach)
afterEach(() => {
  vi.useRealTimers()
})

// Each test starts with empty browser storage, English (document and i18n), and no local Tavus key from .env.local.
beforeEach(() => {
  window.localStorage.clear()
  window.sessionStorage.clear()
  document.documentElement.lang = 'en'
  i18n.global.locale.value = 'en'
  vi.stubEnv('VITE_TAVUS_DEPLOYMENT_ID', '')
})
