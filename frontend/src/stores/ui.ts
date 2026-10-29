import { defineStore } from 'pinia'

import { i18n } from '@/plugins/i18n'
import type { AppLanguage } from '@/types/api'
import { normalizeAppLanguage } from '@/utils/locale'
import { readPersistedLocale, safeSetItem, storageKeys } from '@/utils/storage'

export const useUiStore = defineStore('ui', {
  state: () => ({
    locale: readPersistedLocale(),
  }),
  actions: {
    setLocale(locale: AppLanguage): void {
      this.locale = locale
      i18n.global.locale.value = locale
      document.documentElement.lang = locale
      safeSetItem(storageKeys.language, locale)
    },
    /** Applies an account's preferredLanguage (legacy Chinese code is mapped to zh-CN); an unknown value is ignored. */
    applyPreferredLanguage(value: string | null | undefined): void {
      const locale = normalizeAppLanguage(value)
      if (locale) this.setLocale(locale)
    },
  },
})
