import { mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  auth: { isAuthenticated: false, activeRole: null as string | null },
  route: { path: '/', query: {} as Record<string, string> },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkHrefStub,
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))

import LandingPage from './LandingPage.vue'

function mountPage(): VueWrapper {
  return mount(LandingPage, { global: { stubs: { IconGlyph: true, FeaturedClasses: true } } })
}

beforeEach(() => {
  mocks.auth.isAuthenticated = false
  mocks.auth.activeRole = null
  mocks.route.query = {}
})

describe('LandingPage (FRPUB01)', () => {
  it('has one h1, the how-it-works anchor and the featured classes', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('#how-it-works').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'FeaturedClasses' }).exists()).toBe(true)
  })

  it('shows how MandarinSpark works in four parts, in order', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.landing-how-item h3').map((title) => title.text())).toEqual([
      'Learn',
      'Practice',
      'AI Helper',
      'Classes',
    ])
  })

  it('shows 你好 in two tiles with its pinyin and meaning', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.landing-word-tile').map((tile) => tile.text())).toEqual(['你', '好'])
    expect(wrapper.get('.landing-word-gloss').text()).toBe('nǐ hǎo · hello')
  })

  it('invites a visitor to create a student account, with one yellow action', () => {
    const wrapper = mountPage()

    const start = wrapper.get('.landing-hero .btn-accent')
    expect(wrapper.findAll('.btn-accent')).toHaveLength(1)
    expect(start.text()).toBe('Start learning')
    expect(start.attributes('href')).toBe(
      JSON.stringify({ path: '/auth', query: { tab: 'register', role: 'student' } }),
    )
  })

  it('opens a signed-in user’s own home instead', () => {
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'STUDENT'

    const wrapper = mountPage()

    expect(wrapper.get('.landing-hero .btn-accent').attributes('href')).toBe('/student/home')
  })

  it('shows the account-deleted notice after an account is deleted', () => {
    mocks.route.query = { notice: 'account-deleted' }

    const notice = mountPage().get('[data-testid="account-deleted-notice"]')

    expect(notice.text()).toBe('Your account was deleted.')
    expect(notice.attributes('role')).toBe('status')
  })

  it('has no notice without the query', () => {
    expect(mountPage().find('[data-testid="account-deleted-notice"]').exists()).toBe(false)
  })
})
