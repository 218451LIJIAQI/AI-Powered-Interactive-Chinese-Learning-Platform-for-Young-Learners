import { createI18n } from 'vue-i18n'

import en from './messages/en'
import ms from './messages/ms'
import phase12En from './messages/phase12/en'
import phase12Ms from './messages/phase12/ms'
import phase12Zh from './messages/phase12/zh'
import zh from './messages/zh'

export type SupportedLocale = 'en' | 'ms' | 'zh'

// Vue I18n message trees are deeply nested with mixed value types (strings, nested
// objects, occasional arrays for plural forms), so we intentionally use `any` here.
// Stricter generics trigger TS2589 ("Type instantiation is excessively deep") in
// downstream consumers because vue-i18n's generic parameters are recursive.
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

// We intentionally cast to `any` here because vue-i18n's composition mode
// generic surface returns a union of legacy/composer types that triggers
// TS2589 ("Type instantiation is excessively deep") in downstream consumers
// such as router/index.ts. Consumers always treat this as a non-legacy
// composition-mode I18n instance (`i18n.global.t`, `i18n.global.locale.value`).
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages,
}) as any
