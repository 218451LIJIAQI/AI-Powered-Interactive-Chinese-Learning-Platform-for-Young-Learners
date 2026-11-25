import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PaginationBar from './PaginationBar.vue'

describe('PaginationBar', () => {
  it('renders a labelled nav with the default texts and emits the page to show', async () => {
    const wrapper = mount(PaginationBar, {
      props: { label: 'Result pages', page: 1, totalPages: 3 },
    })

    const nav = wrapper.get('nav.pagination')
    expect(nav.attributes('aria-label')).toBe('Result pages')
    expect(wrapper.get('.pagination-info').text()).toBe('Page 2 of 3')

    const [previous, next] = wrapper.findAll('button')
    expect(previous.text()).toBe('Previous')
    expect(next.text()).toBe('Next')

    await previous.trigger('click')
    await next.trigger('click')
    expect(wrapper.emitted('update:page')).toEqual([[0], [2]])
  })

  it('shows nothing for a single page, and renders a non-nav summary when one page carries a count', async () => {
    const wrapper = mount(PaginationBar, { props: { label: 'Result pages', page: 0, totalPages: 1 } })
    expect(wrapper.find('.pagination').exists()).toBe(false)

    await wrapper.setProps({ summary: '4 events' })
    expect(wrapper.find('nav.pagination').exists()).toBe(false)
    expect(wrapper.get('div.pagination .pagination-info').text()).toBe('4 events')
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('marks buttons aria-disabled on the first and last page without ejecting focus or emitting invalid pages', async () => {
    const wrapper = mount(PaginationBar, {
      props: { label: 'Result pages', page: 0, totalPages: 2 },
    })
    const [previous, next] = wrapper.findAll('button')
    expect(previous.attributes('aria-disabled')).toBe('true')
    expect(previous.attributes('disabled')).toBeUndefined()
    expect(next.attributes('aria-disabled')).toBeUndefined()

    await previous.trigger('click')
    expect(wrapper.emitted('update:page')).toBeUndefined()

    await wrapper.setProps({ page: 1 })
    expect(previous.attributes('aria-disabled')).toBeUndefined()
    expect(next.attributes('aria-disabled')).toBe('true')

    await next.trigger('click')
    expect(wrapper.emitted('update:page')).toBeUndefined()
  })

  it('shows a caller-provided summary alongside the page position', async () => {
    const wrapper = mount(PaginationBar, {
      props: { label: 'Audit pages', page: 0, totalPages: 250, summary: '2,500 events' },
    })
    expect(wrapper.get('.pagination-info').text()).toBe('2,500 events')
    expect(wrapper.get('.pagination-position').text()).toBe('Page 1 of 250')

    await wrapper.setProps({ summary: '1 event' })
    expect(wrapper.get('.pagination-info').text()).toBe('1 event')
  })

  it('uses the labels as accessible names in icon-only mode', async () => {
    const wrapper = mount(PaginationBar, {
      props: {
        label: 'Users pages',
        page: 0,
        totalPages: 2,
        summary: '12 events',
        iconOnly: true,
      },
    })

    expect(wrapper.get('.pagination-info').text()).toBe('12 events')

    const next = wrapper.findAll('button')[1]
    expect(next.attributes('aria-label')).toBe('Next')
    expect(next.attributes('title')).toBe('Next')
    expect(next.classes()).toContain('btn-icon')
    expect(next.text()).toBe('')

    await next.trigger('click')
    expect(wrapper.emitted('update:page')).toEqual([[1]])
  })
})
