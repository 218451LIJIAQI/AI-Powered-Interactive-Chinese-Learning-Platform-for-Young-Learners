import { computed, shallowRef, watch, type Ref, type ShallowRef, type WatchHandle } from 'vue'
import { useRouter, type RouteLocationNormalized, type RouteLocationRaw, type Router } from 'vue-router'

/**
 * Normalises a route transition to its initial navigation target so multi-hop redirects, redirect-to-duplicate
 * navigations (`beforeEach` skipped for the redirected location), and redirect errors still match the `beforeEach`
 * entry that started the transition.
 */
function navigationRoot(to: RouteLocationNormalized): RouteLocationNormalized {
  return (to.redirectedFrom as RouteLocationNormalized | undefined) ?? to
}

const pendingByRouter = new WeakMap<Router, ShallowRef<RouteLocationNormalized | undefined>>()

function routerPending(router: Router): ShallowRef<RouteLocationNormalized | undefined> {
  let pending = pendingByRouter.get(router)
  if (!pending) {
    pending = shallowRef<RouteLocationNormalized>()
    const navigationEnded = (to: RouteLocationNormalized): void => {
      if (navigationRoot(to) === pending!.value) pending!.value = undefined
    }
    router.beforeEach((to) => {
      pending!.value = navigationRoot(to)
    })
    router.afterEach(navigationEnded)
    router.onError((_error, to) => navigationEnded(to))
    pendingByRouter.set(router, pending)
  }
  return pending
}

/** Automatic changes to this page's address wait while a destination is loading, including when a guard cancels it. */
export function usePageActive() {
  const router = useRouter()
  const pageName = router.currentRoute.value.name
  const pending = routerPending(router)
  return computed(() => !pending.value && router.currentRoute.value.name === pageName)
}

/** Defer a watcher's automatic URL update until the visitor stays on this page. */
export function pauseDuringNavigation(
  watcher: WatchHandle,
  pageActive: Readonly<Ref<boolean>> = usePageActive(),
): void {
  watch(pageActive, (active) => (active ? watcher.resume() : watcher.pause()), { flush: 'sync', immediate: true })
}

/** Resolve a redirect from loaded page data while the user is still on that page. */
export function usePageRedirect(
  destination: () => RouteLocationRaw | null,
  pageActive: Readonly<Ref<boolean>> = usePageActive(),
): void {
  const router = useRouter()
  watch(
    [pageActive, destination],
    ([active, target]) => {
      if (active && target) void router.replace(target)
    },
    { immediate: true },
  )
}

/**
 * Completes navigation after a session-ending action (sign-out, email change, password reset). When the user stayed on
 * the page, pushes to `target`; if they navigated to a protected route while the action was in flight, replaces that
 * route so they are never stranded signed out on a protected page.
 */
export async function finishSessionEndNavigation(
  router: Router,
  target: RouteLocationRaw,
  stillOnPage: boolean,
): Promise<void> {
  if (stillOnPage) {
    await router.push(target)
  } else if (router.currentRoute.value.meta.roles?.length) {
    await router.replace(target).catch(() => undefined)
  }
}
