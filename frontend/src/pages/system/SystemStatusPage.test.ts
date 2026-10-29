import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn(),
  auth: { isAuthenticated: false, activeRole: null as string | null },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkHrefStub,
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ ...mocks.auth, logout: mocks.logout }),
}))

import SystemStatusPage from './SystemStatusPage.vue'

function mountPage(kind: 'not-found' | 'unauthorized'): VueWrapper {
  return mount(SystemStatusPage, {
    props: { kind },
    global: { stubs: { AppBrand: true, IconGlyph: true, LanguageSwitcher: true } },
  })
}

function hrefs(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.system-link').map((link) => link.attributes('href') ?? '')
}

beforeEach(() => {
  mocks.push.mockReset()
  mocks.logout.mockReset()
  mocks.auth.isAuthenticated = false
  mocks.auth.activeRole = null
})

describe('SystemStatusPage 404', () => {
  it('says the page was not found, with 迷 mí · lost, home, classes, sign in and Help', () => {
    const wrapper = mountPage('not-found')

    expect(wrapper.get('h1').text()).toBe('This page could not be found')
    expect(wrapper.get('.system-visual').attributes('aria-label')).toBe('404, 迷 mí, lost')
    expect(wrapper.get('.system-tile').text()).toBe('迷')
    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/')
    expect(wrapper.find('a[href="/classes"]').exists()).toBe(true)
    expect(hrefs(wrapper)).toEqual(['/auth', '/help'])
  })

  it('sends a signed-in user to their own home', () => {
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'ADMIN'

    const wrapper = mountPage('not-found')

    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/admin/dashboard')
    expect(hrefs(wrapper)).toEqual(['/admin/dashboard', '/help'])
  })
})

describe('SystemStatusPage 403', () => {
  it('signs out before opening sign in for another account', async () => {
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'STUDENT'
    mocks.logout.mockResolvedValue(undefined)
    const wrapper = mountPage('unauthorized')

    expect(wrapper.get('h1').text()).toBe('You don’t have access to this page')
    expect(wrapper.get('.system-tile').text()).toBe('锁')
    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/student/home')
    expect(hrefs(wrapper)).toEqual(['/classes', JSON.stringify({ path: '/help', hash: '#getting-started' })])

    await wrapper.get('[data-testid="system-switch-account"]').trigger('click')
    await flushPromises()

    expect(mocks.logout).toHaveBeenCalledOnce()
    expect(mocks.push).toHaveBeenCalledWith('/auth')
    expect(mocks.logout.mock.invocationCallOrder[0]).toBeLessThan(mocks.push.mock.invocationCallOrder[0] ?? 0)
  })

  it('offers sign in and the landing page to a visitor', () => {
    const wrapper = mountPage('unauthorized')

    expect(wrapper.find('[data-testid="system-switch-account"]').exists()).toBe(false)
    expect(wrapper.find('.system-actions a[href="/auth"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/')
  })
})
