import type { RouteLocationNormalizedLoaded } from 'vue-router'

import { i18n } from '@/plugins/i18n'
import { pageTitleMessages } from '@/plugins/pageTitleMessages'
import type { AppLanguage } from '@/types/api'
import { normalizeAppLanguage } from '@/utils/locale'

const BRAND = 'MandarinSpark'

type TitledRoute = Pick<RouteLocationNormalizedLoaded, 'name' | 'meta'>

function currentLocale(): AppLanguage {
  return normalizeAppLanguage(String(i18n.global.locale.value)) ?? 'en'
}

/**
 * Browser tab title for a route in the given (default: current i18n) language (FR-I18N-05):
 * the localised route title, then the English `meta.title`, then the brand name alone.
 */
export function localizedPageTitle(route: TitledRoute, locale: AppLanguage = currentLocale()): string {
  const name = typeof route.name === 'string' ? route.name : ''
  const titles: Readonly<Record<string, string>> = pageTitleMessages[locale]
  const title = titles[name] ?? route.meta.title
  if (!title || title === BRAND) return BRAND
  return `${title} | ${BRAND}`
}

export function applyDocumentTitle(route: TitledRoute): void {
  if (typeof document === 'undefined') return
  document.title = localizedPageTitle(route)
}
