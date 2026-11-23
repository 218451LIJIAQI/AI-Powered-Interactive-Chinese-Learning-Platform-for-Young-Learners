import { computed, onScopeDispose, shallowRef, watch, type Ref, type WatchHandle } from 'vue'
import { useRouter, type RouteLocationNormalized, type RouteLocationRaw } from 'vue-router'

/**
 * Normalises a route transition to its initial navigation target so multi-hop redirects, redirect-to-duplicate
 * navigations (`beforeEach` skipped for the redirected location), and redirect errors still match the `beforeEach`
 * entry that started the transition.
 */
function navigationRoot(to: RouteLocationNormalized): RouteLocationNormalized {
  return (to.redirectedFrom as RouteLocationNormalized | undefined) ?? to
}

/** Automatic changes to this page's address wait while a destination is loading, including when a guard cancels it. */
export function usePageActive() {
  const router = useRouter()
  const pageName = router.currentRoute.value.name
  const pending = shallowRef<RouteLocationNormalized>()

  onScopeDispose(
    router.beforeEach((to) => {
      pending.value = navigationRoot(to)
    }),
  )
  function navigationEnded(to: RouteLocationNormalized): void {
    if (navigationRoot(to) === pending.value) pending.value = undefined
  }
  onScopeDispose(router.afterEach(navigationEnded))
  onScopeDispose(router.onError((_error, to) => navigationEnded(to)))
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
