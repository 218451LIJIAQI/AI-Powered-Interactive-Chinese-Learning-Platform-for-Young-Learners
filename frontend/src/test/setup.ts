import { config } from '@vue/test-utils'
import { beforeEach } from 'vitest'

import { i18n } from '@/plugins/i18n'

// Every mounted component gets the app's i18n plugin (as in main.ts).
config.global.plugins = [i18n]

// Each test starts with empty browser storage and English (document and i18n), whatever an earlier test did.
beforeEach(() => {
  window.localStorage.clear()
  window.sessionStorage.clear()
  document.documentElement.lang = 'en'
  i18n.global.locale.value = 'en'
})
