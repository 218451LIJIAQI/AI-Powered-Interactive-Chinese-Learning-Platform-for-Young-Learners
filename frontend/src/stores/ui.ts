import { defineStore } from 'pinia'

import { i18n } from '@/plugins/i18n'
import type { AppLanguage } from '@/types/api'
import { readPersistedLocale, safeSetItem, storageKeys } from '@/utils/storage'

export const useUiStore = defineStore('ui', {
  state: () => ({
    locale: readPersistedLocale(),
  }),
  actions: {
    setLocale(locale: AppLanguage): void {
      this.locale = locale
      i18n.global.locale.value = locale
      safeSetItem(storageKeys.language, locale)
    },
  },
})
