import { mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  auth: { isAuthenticated: false, activeRole: null as string | null },
  route: { path: '/', query: {} as Record<string, string> },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkHrefStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))

import LandingPage from './LandingPage.vue'

function mountPage(): VueWrapper {
  return mount(LandingPage, { global: { stubs: { IconGlyph: true, FeaturedClasses: true } } })
}

beforeEach(() => {
  mocks.replace.mockReset()
  mocks.auth.isAuthenticated = false
  mocks.auth.activeRole = null
  mocks.route.query = {}
})

describe('LandingPage (FRPUB01)', () => {
  it('has one h1, the how-it-works and about anchors, the featured classes and links into Help', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('#how-it-works').exists()).toBe(true)
    expect(wrapper.find('#about').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'FeaturedClasses' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('Personal plan in 3 minutes')
    for (const anchor of ['/help#getting-started', '/help#for-teachers', '/help#ai-and-safety']) {
      expect(wrapper.find(`a[href="${anchor}"]`).exists(), anchor).toBe(true)
    }
  })

  it('shows the four ways to learn in order, and only Learn and Practice earn XP', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.landing-way h3').map((title) => title.text())).toEqual([
      'Learn',
      'Practice',
      'AI Helper',
      'Classes',
    ])
    expect(wrapper.findAll('.landing-xp').map((rule) => rule.text())).toEqual([
      'Earns XP',
      'Earns XP',
      'Optional · no XP',
      'Free · no XP',
    ])
    expect(wrapper.findAll('.landing-xp.is-earning')).toHaveLength(2)
  })

  it('shows the first lesson moment: 你好 in two tiles with pinyin and tone', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.landing-moment-tile').map((tile) => tile.text())).toEqual(['你', '好'])
    expect(wrapper.findAll('.landing-moment-pinyin').map((pinyin) => pinyin.text())).toEqual(['nǐ', 'hǎo'])
    // jsdom has no speech synthesis, so the Listen button stays hidden.
    expect(wrapper.find('.landing-moment-meaning button').exists()).toBe(false)
  })

  it('invites a visitor to create a student account', () => {
    const wrapper = mountPage()

    const start = wrapper.get('.landing-actions a')
    expect(start.text()).toBe('Start learning')
    expect(start.attributes('href')).toBe(
      JSON.stringify({ path: '/auth', query: { tab: 'register', role: 'student' } }),
    )
  })

  it('opens a signed-in user’s own home instead', () => {
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'STUDENT'

    const wrapper = mountPage()

    expect(wrapper.get('.landing-actions a').attributes('href')).toBe('/student/home')
  })

  it('shows the account-deleted notice until it is dismissed', async () => {
    mocks.route.query = { notice: 'account-deleted', tab: 'x' }
    const wrapper = mountPage()

    const notice = wrapper.get('[data-testid="account-deleted-notice"]')
    expect(notice.text()).toContain('Your account was deleted.')

    await notice.get('button').trigger('click')
    expect(mocks.replace).toHaveBeenCalledWith({ query: { tab: 'x' } })
  })

  it('has no notice without the query', () => {
    expect(mountPage().find('[data-testid="account-deleted-notice"]').exists()).toBe(false)
  })
})
