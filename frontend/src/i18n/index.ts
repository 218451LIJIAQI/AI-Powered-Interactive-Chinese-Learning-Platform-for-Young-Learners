import { createI18n } from 'vue-i18n'

import en from './messages/en'
import ms from './messages/ms'
import phase12En from './messages/phase12/en'
import phase12Ms from './messages/phase12/ms'
import phase12Zh from './messages/phase12/zh'
import zh from './messages/zh'

export type SupportedLocale = 'en' | 'ms' | 'zh'

export type MessageTree = Record<string, any>

function mergeMessages(base: MessageTree, extension: MessageTree): MessageTree {
  const merged: MessageTree = { ...base }

  for (const [key, value] of Object.entries(extension)) {
    const currentValue = merged[key]

    if (Array.isArray(value)) {
      merged[key] = value
      continue
    }

    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      currentValue &&
      typeof currentValue === 'object' &&
      !Array.isArray(currentValue)
    ) {
      merged[key] = mergeMessages(currentValue as MessageTree, value as MessageTree)
      continue
    }

    merged[key] = value
  }

  return merged
}

const messages = {
  en: mergeMessages(en as MessageTree, phase12En as MessageTree),
  ms: mergeMessages(ms as MessageTree, phase12Ms as MessageTree),
  zh: mergeMessages(zh as MessageTree, phase12Zh as MessageTree),
} as Record<SupportedLocale, MessageTree>

export const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages,
}) as any
