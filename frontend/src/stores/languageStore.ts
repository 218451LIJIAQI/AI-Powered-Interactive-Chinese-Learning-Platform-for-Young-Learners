import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

import { i18n, type SupportedLocale } from '../i18n'

const LANGUAGE_KEY = 'ai-chinese-learning-locale'

export const useLanguageStore = defineStore('language', () => {
  const locale = ref<SupportedLocale>((localStorage.getItem(LANGUAGE_KEY) as SupportedLocale) || 'en')

  watch(
    locale,
    (value) => {
      i18n.global.locale.value = value
      localStorage.setItem(LANGUAGE_KEY, value)
    },
    { immediate: true },
  )

  function setLocale(value: SupportedLocale) {
    locale.value = value
  }

  return {
    locale,
    setLocale,
  }
})
