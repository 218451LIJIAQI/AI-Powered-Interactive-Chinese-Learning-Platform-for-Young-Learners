import { createRouter, createWebHistory } from 'vue-router'

import { clearClassNotice, reconcileClassNoticeNavigation } from '@/composables/useClassNotice'
import { useAuthStore } from '@/stores/auth'

import { resolveNavigation } from './guard'
import { applyDocumentTitle } from './pageTitle'
import { sessionExpiredRedirect } from './redirects'
import { routes } from './routes'

const SAFE_HASH_SELECTOR_PATTERN = /^#[A-Za-z][A-Za-z0-9_-]*$/

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    // Query-only changes update the current page's tab, filter or selection where the user is working.
    if (to.path === from.path && to.hash === from.hash) return false
    // Only a plain id is used as a selector (a crafted hash cannot inject one).
    if (SAFE_HASH_SELECTOR_PATTERN.test(to.hash)) {
      const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
      // The router scrolls by coordinates, so html's scroll-padding (which clears the sticky header) is applied here.
      const top = parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 0
      return { el: to.hash, top, behavior: reduceMotion ? 'auto' : 'smooth' }
    }
    return { top: 0 }
  },
})

router.beforeEach((to) => resolveNavigation(to, useAuthStore()))

// Defense in depth: if another tab signs out or a background 401 invalidates the session while a protected
// route's lazy chunk is still downloading, catch it right before commit instead of mounting DashboardLayout unauthenticated.
router.beforeResolve((to) => (useAuthStore().isAuthenticated ? true : (sessionExpiredRedirect(to) ?? true)))

// FRLANG02: the tab title follows the committed route and the interface language (App.vue re-applies it on change).
router.afterEach((to, _from, failure) => {
  reconcileClassNoticeNavigation(to.name, Boolean(failure))
  if (!failure) applyDocumentTitle(to)
})
router.onError(() => {
  clearClassNotice()
})

export { router }
