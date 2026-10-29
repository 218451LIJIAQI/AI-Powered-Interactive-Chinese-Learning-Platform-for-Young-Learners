import type { RouteLocationNormalizedLoaded } from 'vue-router'

import { i18n } from '@/plugins/i18n'
import { pageTitleMessages } from '@/plugins/pageTitleMessages'
import type { AppLanguage } from '@/types/api'
import { normalizeAppLanguage } from '@/utils/locale'

const BRAND = 'MandarinSpark'

/** The localised title of a route (`pageTitleMessages`), or '' for a route without one. */
export function routeTitle(route: Pick<RouteLocationNormalizedLoaded, 'name'>, locale: AppLanguage): string {
  const titles: Readonly<Record<string, string>> = pageTitleMessages[locale]
  return typeof route.name === 'string' ? (titles[route.name] ?? '') : ''
}

/**
 * Sets the browser tab title of a route in the current i18n language (FRLANG02): the localised route title, or the
 * brand name alone for a route without one.
 */
export function applyDocumentTitle(route: Pick<RouteLocationNormalizedLoaded, 'name'>): void {
  const title = routeTitle(route, normalizeAppLanguage(String(i18n.global.locale.value)) ?? 'en')
  document.title = !title || title === BRAND ? BRAND : `${title} | ${BRAND}`
}
