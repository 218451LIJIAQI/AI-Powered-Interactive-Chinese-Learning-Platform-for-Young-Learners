import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import SafetyPage from './SafetyPage.vue'

const HELP_FORM_LINK = '[data-testid="safety-help-form"]'
/** The Help page's message form, opened on the topic "Safety concern". */
const SAFETY_FORM = '/help?topic=SAFETY#contact'

function mountPage(): VueWrapper {
  return mount(SafetyPage, { global: { stubs: { IconGlyph: true } } })
}

function reportBullets(wrapper: VueWrapper): string[] {
  return wrapper.findAll('#report li').map((bullet) => bullet.text().replace(/\s+/g, ' '))
}

describe('SafetyPage', () => {
  it('has four sections in the rail, one h1 and no "Last updated" line', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.findAll('.public-rail-link').map((link) => link.text())).toEqual([
      '1.How we keep you safe',
      '2.Your personal details',
      '3.Who MandarinSpark is for',
      '4.Report a concern',
    ])
    expect(wrapper.find('.page-title-copy h1 + p').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Last updated')
  })

  it('says the MandarinSpark team makes the classes, and nothing about teachers or live sessions', () => {
    const wrapper = mountPage()

    expect(wrapper.get('#keep-safe').text()).toContain(
      'Every class and lesson is made and checked by the MandarinSpark team.',
    )
    expect(wrapper.text()).not.toMatch(/teacher|live session|Help & support/i)
  })

  it('links the words that name the Contact us form to the form on the Help page', () => {
    const wrapper = mountPage()

    expect(reportBullets(wrapper)).toEqual([
      'Use the Contact us form on the Help page and choose the topic “Safety concern”.',
      'If someone is in danger right now, call 999.',
    ])
    const link = wrapper.get(HELP_FORM_LINK)
    expect(link.element.tagName).toBe('A')
    expect(link.text()).toBe('Contact us form')
    expect(link.attributes('href')).toBe(SAFETY_FORM)
  })

  it.each([
    [
      'ms',
      'Gunakan borang Hubungi kami di halaman Bantuan dan pilih topik “Kebimbangan keselamatan”.',
      'borang Hubungi kami',
    ],
    ['zh-CN', '请使用帮助页面上的“联系我们”表单，并选择主题“安全问题”。', '“联系我们”表单'],
  ] as const)('keeps the sentence and its link whole in %s', async (locale, sentence, linkText) => {
    const wrapper = mountPage()

    i18n.global.locale.value = locale
    await flushPromises()

    expect(reportBullets(wrapper)[0]).toBe(sentence)
    expect(wrapper.get(HELP_FORM_LINK).text()).toBe(linkText)
    expect(wrapper.get(HELP_FORM_LINK).attributes('href')).toBe(SAFETY_FORM)
  })

  it('sends "Report a concern" to the Help page form with the topic "Safety concern" chosen', () => {
    expect(mountPage().get('.page-header-meta a').attributes('href')).toBe(SAFETY_FORM)
  })
})
