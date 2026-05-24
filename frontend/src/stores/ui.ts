/**
 * Pinia store for cross-cutting UI state that doesn't belong to a
 * specific persona or page: the user's chosen UI language and the
 * mobile-nav drawer open/closed flag.
 *
 * <p>Locale changes flow through {@code i18n.global.locale.value}
 * so every component re-renders with the new translations without
 * a page reload. Persistence is to {@code localStorage} (intentional
 * — language preference should survive browser restarts unlike auth
 * tokens).</p>
 */
import { defineStore } from 'pinia'

import { i18n } from '@/plugins/i18n'
import type { AppLanguage } from '@/types/api'
import { readPersistedLocale, safeSetItem, storageKeys } from '@/utils/storage'

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
