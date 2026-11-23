import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import TermsPage from './TermsPage.vue'

function mountPage() {
  return mount(TermsPage, { global: { stubs: { IconGlyph: true } } })
}

describe('TermsPage', () => {
  it('has its five sections and says when the terms last changed', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('.public-rail-link').map((link) => link.text())).toEqual([
      '1.Accounts and roles',
      '2.Learning content and AI support',
      '3.Classes are free',
      '4.Safe use',
      '5.Support and changes',
    ])
    expect(wrapper.get('.page-title-copy h1 + p').text()).toBe('Last updated 1 May 2026')
  })

  it('asks for respect towards support and in AI chats, with nothing about classes, teachers or live sessions', () => {
    const wrapper = mountPage()

    const safeUse = wrapper.get('#terms-safety').text()
    expect(safeUse).toContain('Be respectful in messages to support and in AI chats.')
    expect(safeUse).not.toContain('in classes')
    expect(wrapper.text()).not.toMatch(/teacher|live session|Help & support/i)
  })
})
