import { createI18n } from 'vue-i18n'

import en from './messages/en'
import ms from './messages/ms'
import zh from './messages/zh'

export type SupportedLocale = 'en' | 'ms' | 'zh'

export const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages: {
    en,
    ms,
    zh,
  },
})
