import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import { routes } from '@/router'

import PublicLayout from './PublicLayout.vue'

const Page = { template: '<h1>Page</h1>' }

/** The app's public routes (their names and meta) under the real layout, with a blank page for each. */
function publicRoutes(): RouteRecordRaw[] {
  const shell = routes.find((record) => record.path === '/')
  const pages = (shell?.children ?? []).map((page) => ({ ...page, component: Page }) as RouteRecordRaw)
  return [
    { path: '/', component: PublicLayout, children: pages },
    // The header and the footer also link to pages outside the public shell.
    { path: '/:pathMatch(.*)*', component: Page },
  ]
}

async function mountAt(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: publicRoutes() })
  await router.push(path)
  await router.isReady()
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { router, wrapper }
}

describe('PublicLayout', () => {
  it('ends the landing page with the site footer, after the main content', async () => {
    const { wrapper } = await mountAt('/')

    const shell = wrapper.get('.public-shell')
    const footer = shell.get('footer.public-footer')
    expect(shell.element.lastElementChild).toBe(footer.element)
    // A footer inside <main> would not be the page's footer landmark.
    expect(shell.get('main').find('footer').exists()).toBe(false)
    expect(shell.find('header.public-nav').exists()).toBe(true)
  })

  it.each(['/classes', '/classes/7', '/help', '/safety', '/terms', '/privacy', '/responsible-ai', '/confirm-email'])(
    'ends %s at the main content',
    async (path) => {
      const { wrapper } = await mountAt(path)

      expect(wrapper.find('footer').exists()).toBe(false)
      expect(wrapper.get('.public-shell').element.lastElementChild?.id).toBe('main-content')
    },
  )

  it.each(['/auth', '/forgot-password', '/reset-password'])(
    'gives %s the whole window, without the header or the footer',
    async (path) => {
      const { wrapper } = await mountAt(path)

      expect(wrapper.get('.public-shell').classes()).toContain('public-shell--auth')
      expect(wrapper.find('header').exists()).toBe(false)
      expect(wrapper.find('footer').exists()).toBe(false)
    },
  )

  it('takes the footer away when the visitor leaves the landing page, and brings it back', async () => {
    const { router, wrapper } = await mountAt('/')

    await router.push('/help')
    await flushPromises()
    expect(wrapper.find('footer').exists()).toBe(false)

    await router.push('/')
    await flushPromises()
    expect(wrapper.find('footer.public-footer').exists()).toBe(true)
  })
})
