import type { RouteLocationNormalizedLoaded } from 'vue-router'

import { appLocale } from '@/plugins/i18n'
import { pageTitleMessages } from '@/plugins/pageTitleMessages'

const BRAND = 'MandarinSpark'

/** The title of a route in the current i18n language (`pageTitleMessages`), or '' for a route without one. */
export function routeTitle(route: Pick<RouteLocationNormalizedLoaded, 'name'>): string {
  const titles: Readonly<Record<string, string>> = pageTitleMessages[appLocale.value]
  return typeof route.name === 'string' ? (titles[route.name] ?? '') : ''
}

/**
 * Sets the browser tab title of a route in the current i18n language (FRLANG02): the localised route title, or the
 * brand name alone for a route without one.
 */
export function applyDocumentTitle(route: Pick<RouteLocationNormalizedLoaded, 'name'>): void {
  const title = routeTitle(route)
  document.title = !title || title === BRAND ? BRAND : `${title} | ${BRAND}`
}
