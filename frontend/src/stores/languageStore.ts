import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

import { STORAGE_KEYS } from '../constants/storageKeys'
import { i18n, type SupportedLocale } from '../i18n'
import { safeGetItem, safeSetItem } from '../utils/safeStorage'

const SUPPORTED_LOCALES: readonly SupportedLocale[] = ['en', 'ms', 'zh']

function readStoredLocale(): SupportedLocale {
  const raw = safeGetItem(STORAGE_KEYS.LOCALE)
  if (raw && (SUPPORTED_LOCALES as readonly string[]).includes(raw)) {
    return raw as SupportedLocale
  }
  return 'en'
}

export const useLanguageStore = defineStore('language', () => {
  const locale = ref<SupportedLocale>(readStoredLocale())

  watch(
    locale,
    (value) => {
      i18n.global.locale.value = value
      safeSetItem(STORAGE_KEYS.LOCALE, value)
    },
    { immediate: true },
  )

  function setLocale(value: SupportedLocale) {
    if (!(SUPPORTED_LOCALES as readonly string[]).includes(value)) {
      return
    }
    locale.value = value
  }

  return {
    locale,
    setLocale,
  }
})
