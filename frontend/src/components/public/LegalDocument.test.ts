import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkHrefStub }))

import LegalDocument from './LegalDocument.vue'

const SECTIONS = [
  { id: 'first', title: 'First', paragraphs: ['One paragraph.'] },
  {
    id: 'second',
    title: 'Second',
    note: { label: 'Shown with every answer:', text: 'A boxed statement.' },
    bullets: ['A point.', 'Another point.'],
  },
]

function mountDocument() {
  return mount(LegalDocument, {
    props: { title: 'Terms of Service', sections: SECTIONS },
    global: { stubs: { IconGlyph: true } },
  })
}

describe('LegalDocument', () => {
  it('has one h1 under the breadcrumb and no page lede', () => {
    const wrapper = mountDocument()

    expect(wrapper.findAll('h1').map((heading) => heading.text())).toEqual(['Terms of Service'])
    expect(wrapper.find('.page-title p').exists()).toBe(false)
  })

  it('numbers the rail from the sections and links each row to its section', () => {
    const wrapper = mountDocument()

    const rows = wrapper.findAll('.public-rail-link')
    expect(rows.map((row) => row.text())).toEqual(['1.First', '2.Second'])
    expect(rows.map((row) => row.attributes('href'))).toEqual(['#first', '#second'])
    expect(wrapper.find('#second h2').text()).toBe('Second')
  })

  it('marks the first section, then the row that was clicked', async () => {
    const wrapper = mountDocument()
    const rows = wrapper.findAll('.public-rail-link')
    expect(rows[0]!.attributes('aria-current')).toBe('location')

    await rows[1]!.trigger('click')

    expect(rows[0]!.attributes('aria-current')).toBeUndefined()
    expect(rows[1]!.classes()).toContain('is-active')
  })

  it('shows a section note above its text, and bullets as a list', () => {
    const wrapper = mountDocument()

    expect(wrapper.get('#second .legal-note').text()).toBe('Shown with every answer: A boxed statement.')
    expect(wrapper.findAll('#second .legal-bullets li')).toHaveLength(2)
  })
})
