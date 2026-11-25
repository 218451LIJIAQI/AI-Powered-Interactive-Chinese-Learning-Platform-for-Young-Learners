import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, type Component } from 'vue'
import { createMemoryHistory, createRouter, type RouteRecordRaw, type Router } from 'vue-router'

import { routes } from '@/router/routes'

const Blank = defineComponent({ render: () => h('div') })

/**
 * The app's routes with a blank component for every page and layout: the page under test is mounted directly, and a
 * navigation never loads the real (lazy) components.
 */
function blankRoutes(records: readonly RouteRecordRaw[]): RouteRecordRaw[] {
  return records.map(
    (record) =>
      ({
        ...record,
        ...(record.component ? { component: Blank } : {}),
        ...(record.children ? { children: blankRoutes(record.children) } : {}),
      }) as RouteRecordRaw,
  )
}

const pageRoutes = blankRoutes(routes)

/**
 * A memory router with the app's paths and route names, without its navigation guards. It is already at `path` when
 * it resolves, so the page's `useRoute()` sees its params on the first render.
 */
export async function testRouter(path: string): Promise<Router> {
  const router = createRouter({ history: createMemoryHistory(), routes: pageRoutes })
  await router.push(path)
  await router.isReady()
  return router
}

/**
 * Mounts a page at `path` with that router and waits for its first requests; dialogs and toasts render in place
 * (Teleport stubbed) so the wrapper finds them.
 */
export async function mountAtRoute(
  page: Component,
  path: string,
  options: {
    attachTo?: HTMLElement
    stubs?: Record<string, Component | boolean>
  } = {},
) {
  const router = await testRouter(path)
  const wrapper = mount(page, {
    attachTo: options.attachTo,
    global: { plugins: [router], stubs: { teleport: true, ...options.stubs } },
  })
  await flushPromises()
  return { wrapper, router }
}
