import { nextTick, ref, watch } from 'vue'
import { createMemoryHistory, createRouter, type RouteLocationRaw, type Router } from 'vue-router'
import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { withSetup } from '@/test/helpers'

import { pauseDuringNavigation, usePageActive, usePageRedirect } from './usePageNavigation'

const Dummy = { template: '<div />' }

async function mountOnPageA(
  extraRoutes: Parameters<typeof createRouter>[0]['routes'] = [],
  beforeMount?: (router: Router) => void,
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/a', name: 'page-a', component: Dummy },
      { path: '/b', name: 'page-b', component: Dummy },
      ...extraRoutes,
    ],
  })
  await router.push('/a')
  await router.isReady()
  beforeMount?.(router)

  const { result: pageActive, wrapper } = withSetup(() => usePageActive(), [router])
  return { router, wrapper, pageActive }
}

describe('usePageActive', () => {
  it('pauses while leaving and clears pending after navigation finishes or aborts', async () => {
    let releaseGuard!: (allow: boolean) => void
    const { router, wrapper, pageActive } = await mountOnPageA()
    expect(pageActive.value).toBe(true)

    router.beforeEach((to) => {
      if (to.name === 'page-b') {
        return new Promise<boolean>((resolve) => {
          releaseGuard = resolve
        })
      }
      return true
    })

    const aborted = router.push('/b')
    await flushPromises()
    expect(pageActive.value).toBe(false)

    releaseGuard(false)
    await aborted
    expect(pageActive.value).toBe(true)

    const committed = router.push('/b')
    await flushPromises()
    expect(pageActive.value).toBe(false)

    releaseGuard(true)
    await committed
    expect(pageActive.value).toBe(false)
    wrapper.unmount()
  })

  it('recovers when a guard redirects back to the current route (NAVIGATION_DUPLICATED)', async () => {
    const { router, wrapper, pageActive } = await mountOnPageA([
      { path: '/bounce', name: 'bounce', beforeEnter: () => '/a', component: Dummy },
    ])
    expect(pageActive.value).toBe(true)

    await router.push('/bounce')
    expect(router.currentRoute.value.name).toBe('page-a')
    expect(pageActive.value).toBe(true)
    wrapper.unmount()
  })

  it('recovers when a redirected navigation errors before usePageActive beforeEach runs for the target', async () => {
    const { router, wrapper, pageActive } = await mountOnPageA(
      [
        { path: '/redir-fail', name: 'redir-fail', beforeEnter: () => '/fail-target', component: Dummy },
        { path: '/fail-target', name: 'fail-target', component: Dummy },
      ],
      (r) => {
        // Registered before usePageActive mounts so it throws for /fail-target before usePageActive's beforeEach runs.
        r.beforeEach((to) => {
          if (to.name === 'fail-target') throw new Error('boom')
          return true
        })
      },
    )

    await expect(router.push('/redir-fail')).rejects.toThrow('boom')
    expect(pageActive.value).toBe(true)
    wrapper.unmount()
  })

  it('ignores an aborted earlier navigation once a newer navigation supersedes it', async () => {
    let releaseFirst!: (allow: boolean) => void
    let releaseSecond!: (allow: boolean) => void
    const { router, wrapper, pageActive } = await mountOnPageA([{ path: '/c', name: 'page-c', component: Dummy }])

    router.beforeEach((to) => {
      if (to.name === 'page-b') {
        return new Promise<boolean>((resolve) => {
          releaseFirst = resolve
        })
      }
      if (to.name === 'page-c') {
        return new Promise<boolean>((resolve) => {
          releaseSecond = resolve
        })
      }
      return true
    })

    const first = router.push('/b')
    await flushPromises()
    expect(pageActive.value).toBe(false)

    const second = router.push('/c')
    await flushPromises()
    releaseFirst(false)
    await first
    // The second navigation is still pending, so pageActive must stay false until it completes.
    expect(pageActive.value).toBe(false)

    releaseSecond(false)
    await second
    expect(pageActive.value).toBe(true)
    wrapper.unmount()
  })

  it('pauseDuringNavigation pauses immediately when mounted inactive and resumes when active again', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/a', name: 'page-a', component: Dummy },
        { path: '/b', name: 'page-b', component: Dummy },
      ],
    })
    await router.push('/a')
    await router.isReady()

    const source = ref(0)
    const active = ref(false)
    const fired = vi.fn()
    const { wrapper } = withSetup(() => {
      const watcher = watch(source, fired)
      pauseDuringNavigation(watcher, active)
    }, [router])

    source.value = 1
    await nextTick()
    expect(fired).not.toHaveBeenCalled()

    active.value = true
    source.value = 2
    await nextTick()
    expect(fired).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('usePageRedirect defers redirect while navigating away and fires only if the page stays active', async () => {
    let releaseGuard!: (allow: boolean) => void
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/a', name: 'page-a', component: Dummy },
        { path: '/b', name: 'page-b', component: Dummy },
        { path: '/target', name: 'target', component: Dummy },
      ],
    })
    await router.push('/a')
    await router.isReady()

    const target = ref<RouteLocationRaw | null>(null)
    const { wrapper } = withSetup(() => usePageRedirect(() => target.value), [router])

    router.beforeEach((to) => {
      if (to.name === 'page-b') {
        return new Promise<boolean>((resolve) => {
          releaseGuard = resolve
        })
      }
      return true
    })

    // Start navigating away; target becomes ready mid-navigation -> must not hijack navigation.
    const leaving = router.push('/b')
    await flushPromises()
    target.value = '/target'
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('page-a')

    // Guard cancels navigation -> page stays active -> deferred redirect now runs.
    releaseGuard(false)
    await leaving
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('target')
    wrapper.unmount()
  })
})
