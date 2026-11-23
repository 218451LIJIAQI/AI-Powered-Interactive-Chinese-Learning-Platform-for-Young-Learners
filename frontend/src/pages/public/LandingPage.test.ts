import { mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  auth: { isAuthenticated: false, activeRole: null as string | null },
  route: { path: '/', query: {} as Record<string, string> },
  replace: vi.fn(),
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))

import { toastMessageStub } from '@/test/helpers'
import { stubSpeech } from '@/test/fakeSpeech'

import LandingPage from './LandingPage.vue'

function mountPage(): VueWrapper {
  return mount(LandingPage, {
    global: {
      stubs: {
        IconGlyph: true,
        AppToast: toastMessageStub,
      },
    },
  })
}

beforeEach(() => {
  mocks.auth.isAuthenticated = false
  mocks.auth.activeRole = null
  mocks.route.query = {}
})

describe('LandingPage (FRPUB01)', () => {
  it('has one h1 and the how-it-works anchor, and ends with the plan’s steps', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('#how-it-works').exists()).toBe(true)
    // A short page: the hero, how it works, the plan.
    expect(wrapper.findAll('section').map((section) => section.get('h1, h2').text())).toEqual([
      'Spark your progress in Mandarin.',
      'How MandarinSpark works',
      'Your plan, built in 3 minutes',
    ])
  })

  it('speaks to beginners aged 18+ and promises only what the product does', () => {
    const wrapper = mountPage()

    expect(wrapper.get('.landing-audience').text()).toBe('For beginners aged 18+')
    // Under the promise, what the product is, in one sentence.
    expect(wrapper.get('.landing-hero .landing-lead').text()).toBe(
      'Short lessons, daily practice and an AI speaking partner, with a personal plan built around your goal.',
    )
    expect(wrapper.findAll('.landing-facts li').map((fact) => fact.text())).toEqual([
      'Free to use',
      'Personal plan in 3 minutes',
    ])
    // Classes are made by the MandarinSpark team, and there are no live sessions.
    expect(wrapper.text()).not.toMatch(/teacher|verified|live/i)
  })

  it('explains how MandarinSpark works in four parts, in order, without links', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.landing-how-item h3').map((title) => title.text())).toEqual([
      'Learn',
      'Practice',
      'AI Helper',
      'Classes',
    ])
    expect(wrapper.find('#how-it-works a').exists()).toBe(false)
    // One sheet holds the four parts; the heading stands over it, where every section's heading is.
    const section = wrapper.get('#how-it-works')
    expect(section.get('h2').element.closest('.panel')).toBeNull()
    expect(section.findAll('.panel > .landing-how-item')).toHaveLength(4)
  })

  it('gives each part its own picture, which assistive technology skips', () => {
    const wrapper = mountPage()

    const pictures = wrapper.findAll('.landing-how-item .landing-art')
    expect(pictures.map((picture) => picture.classes().filter((name) => name.startsWith('is-')))).toEqual([
      ['is-path'],
      ['is-cards'],
      ['is-chat'],
      ['is-scenes'],
    ])
    expect(pictures.every((picture) => picture.attributes('aria-hidden') === 'true')).toBe(true)
  })

  it('tells the three steps of the plan setup in order, in the setup’s own numbers', () => {
    const wrapper = mountPage()

    const steps = wrapper.findAll('ol.landing-steps > li')
    expect(steps.map((step) => step.get('.page-eyebrow').text())).toEqual(['Step 1', 'Step 2', 'Step 3'])
    expect(steps.map((step) => step.get('h3').text())).toEqual([
      'Share your goal',
      'Check your level',
      'Follow your path',
    ])
    // The setup asks "4 quick questions", then "up to 8 short questions" (planSetupMessages).
    expect(steps[0]!.get('.landing-step-copy').text()).toContain('4 quick questions')
    expect(steps[1]!.get('.landing-step-copy').text()).toContain('Up to 8 short questions')
    // 一 二 三 are for the eye; the list and "Step 1" carry the order.
    const tiles = steps.map((step) => step.get('.landing-step-tile'))
    expect(tiles.map((tile) => tile.text())).toEqual(['一', '二', '三'])
    expect(tiles.every((tile) => tile.attributes('aria-hidden') === 'true')).toBe(true)
  })

  it('shows 你好 in two tiles with its pinyin and meaning', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.landing-word-tile').map((tile) => tile.text())).toEqual(['你', '好'])
    expect(wrapper.get('.landing-word-gloss').text()).toBe('nǐ hǎo · hello')
    expect(wrapper.get('.landing-word-head').text()).toBe('Your first word')
    expect(wrapper.get('.landing-tones[role="img"]').attributes('aria-label')).toContain('mǎ dips and rises')
  })

  it('invites a visitor to create an account, with one yellow action', () => {
    const wrapper = mountPage()

    const start = wrapper.get('.landing-hero .btn-accent')
    expect(wrapper.findAll('.btn-accent')).toHaveLength(1)
    expect(start.text()).toBe('Start learning')
    expect(start.attributes('href')).toBe(JSON.stringify({ path: '/auth', query: { tab: 'register' } }))
  })

  it('opens a signed-in user’s own home instead', () => {
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'STUDENT'

    const wrapper = mountPage()

    expect(wrapper.get('.landing-hero .btn-accent').text()).toBe('Open my home')
    expect(wrapper.get('.landing-hero .btn-accent').attributes('href')).toBe('/student/home')
  })

  it('confirms a deleted account with a toast, then takes the notice out of the address', async () => {
    mocks.route.query = { notice: 'account-deleted', ref: 'kept' }

    const wrapper = mountPage()
    await nextTick()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Your account was deleted.')
    expect(mocks.replace).toHaveBeenCalledExactlyOnceWith({ path: '/', query: { ref: 'kept' } })
  })

  it('shows no toast without the notice', () => {
    expect(mountPage().get('[data-testid="toast"]').text()).toBe('')
    expect(mocks.replace).not.toHaveBeenCalled()
  })

  describe('Listen, on the first word', () => {
    const listenButton = (wrapper: VueWrapper) => wrapper.find('[data-testid="landing-listen"]')

    it('is not offered where the browser cannot speak', () => {
      expect(listenButton(mountPage()).exists()).toBe(false)
    })

    it('is not offered without a Mandarin voice, and appears once the browser lists one', async () => {
      const synthesis = stubSpeech([{ lang: 'en-US' }, { lang: 'zh-HK' }])
      const wrapper = mountPage()
      await nextTick()
      expect(listenButton(wrapper).exists()).toBe(false)

      synthesis.setVoices([{ lang: 'zh-CN' }])
      await nextTick()

      expect(listenButton(wrapper).exists()).toBe(true)
    })

    it('reads 你好 aloud and lights the word’s card while it is spoken', async () => {
      const synthesis = stubSpeech([{ lang: 'zh-CN' }])
      const wrapper = mountPage()
      await nextTick()
      const listen = listenButton(wrapper)
      expect(listen.text()).toBe('Listen')
      expect(listen.attributes('aria-label')).toBe('Listen to “nǐ hǎo”')

      await listen.trigger('click')
      const utterance = synthesis.utterance()
      expect(utterance).toMatchObject({ text: '你好', lang: 'zh-CN' })

      // The card carries the state: the stylesheet lights its tiles and its speaker together.
      const card = wrapper.get('.landing-word')
      expect(card.classes()).not.toContain('is-speaking')
      utterance.onstart?.()
      await nextTick()
      expect(card.classes()).toContain('is-speaking')
      utterance.onend?.()
      await nextTick()
      expect(card.classes()).not.toContain('is-speaking')
    })

    it('keeps the card lit for the latest reading when an earlier one is cut off', async () => {
      const synthesis = stubSpeech([{ lang: 'zh-CN' }])
      const wrapper = mountPage()
      await nextTick()
      const listen = listenButton(wrapper)

      await listen.trigger('click')
      const first = synthesis.utterance()
      await listen.trigger('click')
      const second = synthesis.utterance()
      second.onstart?.()
      // The first reading ends only now, cut off by the second.
      first.onend?.()
      await nextTick()

      expect(wrapper.get('.landing-word').classes()).toContain('is-speaking')
    })

    it('stops reading, and listening for voices, when the page is left', async () => {
      const synthesis = stubSpeech([{ lang: 'zh-CN' }])
      const wrapper = mountPage()
      const [, voicesChanged] = synthesis.addEventListener.mock.calls[0]!
      await listenButton(wrapper).trigger('click')
      synthesis.cancel.mockClear()

      wrapper.unmount()

      expect(synthesis.removeEventListener).toHaveBeenCalledExactlyOnceWith('voiceschanged', voicesChanged)
      expect(synthesis.cancel).toHaveBeenCalledOnce()
    })
  })
})
