import { useI18n } from 'vue-i18n'

import { APP_LANGUAGES, type AppLanguage } from '@/types/api'

type MessageBundle = Record<AppLanguage, object>
type MessageSource = MessageBundle | readonly MessageBundle[]

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function mergeRecord(target: Record<string, unknown>, source: Record<string, unknown>): void {
  for (const [key, value] of Object.entries(source)) {
    const existing = target[key]
    if (isPlainRecord(existing) && isPlainRecord(value)) {
      mergeRecord(existing, value)
    } else if (isPlainRecord(value)) {
      const copy: Record<string, unknown> = {}
      mergeRecord(copy, value)
      target[key] = copy
    } else {
      target[key] = value
    }
  }
}

/** Mount each feature bundle (or list of split sub-bundles) under its namespace for every app language. */
export function useLocalMessages(bundles: Record<string, MessageSource>, scope: 'local' | 'isolated' = 'local') {
  const messages: Record<AppLanguage, Record<string, object>> = { en: {}, ms: {}, 'zh-CN': {} }
  for (const [namespace, source] of Object.entries(bundles)) {
    const list = Array.isArray(source) ? source : [source]
    for (const locale of APP_LANGUAGES) {
      if (list.length === 1) {
        messages[locale][namespace] = list[0]![locale]
      } else {
        const merged: Record<string, unknown> = {}
        for (const bundle of list) mergeRecord(merged, bundle[locale] as Record<string, unknown>)
        messages[locale][namespace] = merged
      }
    }
  }
  const composer = useI18n({ messages, useScope: scope })
  // A global key (nav.*, chrome.*) is expected to miss here and resolve at the root, which still warns about a key
  // that is missing everywhere. (A local scope copies these flags from the root, so they are set after creation.)
  if (scope === 'local') {
    composer.missingWarn = false
    composer.fallbackWarn = false
  }
  return composer
}
