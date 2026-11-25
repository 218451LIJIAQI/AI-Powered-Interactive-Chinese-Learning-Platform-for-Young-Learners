import { mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/plugins/i18n'

import PublicFooter from './PublicFooter.vue'

const Empty = { template: '<div />' }

async function mountFooter(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: Empty }],
  })
  await router.push('/')
  await router.isReady()
  return mount(PublicFooter, { global: { plugins: [router] } })
}

/** The links as "label -> href", in the order they are shown. */
function links(wrapper: VueWrapper): string[] {
  return wrapper.findAll('nav a').map((link) => `${link.text()} -> ${link.attributes('href')}`)
}

describe('PublicFooter', () => {
  it('leads to the pages the header has no room for, the policies included', async () => {
    const wrapper = await mountFooter()

    expect(wrapper.element.tagName).toBe('FOOTER')
    expect(wrapper.get('nav').attributes('aria-label')).toBe('Footer')
    expect(links(wrapper)).toEqual([
      'Contact us -> /help#contact',
      'Safety centre -> /safety',
      'Terms of Service -> /terms',
      'Privacy Notice -> /privacy',
      'Responsible AI guidance -> /responsible-ai',
    ])
    // The header stays in view with the classes, Help, the account and the language switch.
    expect(wrapper.text()).not.toMatch(/Classes|Sign in|Create account/)
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('says the year, and goodbye in Chinese', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2031-03-04T05:06:07Z') })
    const wrapper = await mountFooter()

    expect(wrapper.get('.public-footer-note').text()).toContain('© 2031 MandarinSpark')
    const bye = wrapper.get('.public-footer-bye')
    expect(bye.get('[lang="zh-CN"]').text()).toBe('再见')
    expect(bye.get('[lang="zh-Latn-pinyin"]').text()).toBe('zàijiàn')
    expect(bye.text()).toContain('see you again')
  })

  it('follows the interface language, naming each page as its own title does', async () => {
    const wrapper = await mountFooter()

    i18n.global.locale.value = 'zh-CN'
    await nextTick()
    expect(wrapper.get('nav').attributes('aria-label')).toBe('页脚导航')
    expect(links(wrapper)).toEqual([
      '联系我们 -> /help#contact',
      '安全中心 -> /safety',
      '服务条款 -> /terms',
      '隐私声明 -> /privacy',
      '负责任 AI 指南 -> /responsible-ai',
    ])

    i18n.global.locale.value = 'ms'
    await nextTick()
    expect(links(wrapper)).toEqual([
      'Hubungi kami -> /help#contact',
      'Pusat keselamatan -> /safety',
      'Terma Perkhidmatan -> /terms',
      'Notis Privasi -> /privacy',
      'Panduan AI Bertanggungjawab -> /responsible-ai',
    ])
    expect(wrapper.get('.public-footer-bye').text()).toContain('jumpa lagi')
  })
})
