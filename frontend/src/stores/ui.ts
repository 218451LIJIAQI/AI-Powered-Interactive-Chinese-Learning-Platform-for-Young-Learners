import { defineStore } from 'pinia'

import { i18n } from '@/plugins/i18n'
import type { AppLanguage } from '@/types/api'
import { safeGetItem, safeSetItem, storageKeys } from '@/utils/storage'

function readPersistedLocale(): AppLanguage {
  const locale = safeGetItem(storageKeys.language)
  return locale === 'en' || locale === 'ms' || locale === 'zh' ? locale : 'en'
}

export const useUiStore = defineStore('ui', {
  state: () => ({
    locale: readPersistedLocale(),
    mobileNavOpen: false,
  }),
  actions: {
    setLocale(locale: AppLanguage) {
      this.locale = locale
      i18n.global.locale.value = locale
      safeSetItem(storageKeys.language, locale)
    },
    toggleMobileNav(force?: boolean) {
      this.mobileNavOpen = typeof force === 'boolean' ? force : !this.mobileNavOpen
    },
  },
})
