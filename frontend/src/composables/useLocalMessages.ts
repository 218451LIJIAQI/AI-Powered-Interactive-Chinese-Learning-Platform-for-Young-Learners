import { useI18n } from 'vue-i18n'

import type { AppLanguage } from '@/types/api'

type MessageBundle = Record<AppLanguage, object>

const LOCALES: readonly AppLanguage[] = ['en', 'ms', 'zh-CN']

/**
 * Local i18n scope with each bundle mounted under its namespace in every locale: after
 * `useLocalMessages({ adminUsers: adminUsersMessages })`, `t('adminUsers.title')` resolves (global keys still do).
 */
export function useLocalMessages(bundles: Record<string, MessageBundle>) {
  const messages: Record<AppLanguage, Record<string, object>> = { en: {}, ms: {}, 'zh-CN': {} }
  for (const [namespace, bundle] of Object.entries(bundles)) {
    for (const locale of LOCALES) messages[locale][namespace] = bundle[locale]
  }
  const composer = useI18n({ messages })
  // A global key (nav.*, chrome.*) is expected to miss here and resolve at the root, which still warns about a key
  // that is missing everywhere. (A local scope copies these flags from the root, so they are set after creation.)
  composer.missingWarn = false
  composer.fallbackWarn = false
  return composer
}
