import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PaginationBar from './PaginationBar.vue'

describe('PaginationBar', () => {
  it('renders a labelled nav with the default texts and emits the page to show', async () => {
    const wrapper = mount(PaginationBar, {
      props: { label: 'Result pages', currentPage: 2, totalPages: 3 },
    })

    const nav = wrapper.get('nav.pagination')
    expect(nav.attributes('aria-label')).toBe('Result pages')
    expect(wrapper.get('.pagination-info').text()).toBe('Page 2 of 3')

    const [previous, next] = wrapper.findAll('button')
    expect(previous.text()).toBe('Previous')
    expect(next.text()).toBe('Next')

    await previous.trigger('click')
    await next.trigger('click')
    expect(wrapper.emitted('update:currentPage')).toEqual([[1], [3]])
  })

  it('disables the buttons on the first and last page', async () => {
    const wrapper = mount(PaginationBar, {
      props: { label: 'Result pages', currentPage: 1, totalPages: 2 },
    })
    const [previous, next] = wrapper.findAll('button')
    expect(previous.attributes('disabled')).toBeDefined()
    expect(next.attributes('disabled')).toBeUndefined()

    await wrapper.setProps({ currentPage: 2 })
    expect(previous.attributes('disabled')).toBeUndefined()
    expect(next.attributes('disabled')).toBeDefined()
  })

  it('uses the labels as accessible names in icon-only mode and adds test ids from the prefix', async () => {
    const wrapper = mount(PaginationBar, {
      props: {
        label: 'Users pages',
        currentPage: 1,
        totalPages: 2,
        info: 'Rows 1-10',
        iconOnly: true,
        testIdPrefix: 'users',
      },
    })

    expect(wrapper.attributes('data-testid')).toBe('users-pagination')
    expect(wrapper.get('[data-testid="users-page-label"]').text()).toBe('Rows 1-10')

    const next = wrapper.get('[data-testid="users-next-page"]')
    expect(next.attributes('aria-label')).toBe('Next')
    expect(next.classes()).toContain('btn-icon')
    expect(next.text()).toBe('')

    await next.trigger('click')
    expect(wrapper.emitted('update:currentPage')).toEqual([[2]])
  })
})
