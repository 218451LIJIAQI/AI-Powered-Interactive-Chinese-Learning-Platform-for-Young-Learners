import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PaginationBar from './PaginationBar.vue'

describe('PaginationBar', () => {
  it('renders a labelled nav with previous / next buttons and emits their events', async () => {
    const wrapper = mount(PaginationBar, {
      props: {
        label: 'Result pages',
        info: 'Page 2 of 3',
        previousLabel: 'Previous',
        nextLabel: 'Next',
        nextDisabled: true,
      },
    })

    const nav = wrapper.get('nav.pagination')
    expect(nav.attributes('aria-label')).toBe('Result pages')
    expect(wrapper.get('.pagination-info').text()).toBe('Page 2 of 3')

    const [previous, next] = wrapper.findAll('button')
    expect(previous.text()).toBe('Previous')
    expect(next.text()).toBe('Next')
    expect(next.attributes('disabled')).toBeDefined()

    await previous.trigger('click')
    expect(wrapper.emitted('previous')).toHaveLength(1)
  })

  it('uses the labels as accessible names in icon-only mode and adds test ids from the prefix', async () => {
    const wrapper = mount(PaginationBar, {
      props: {
        info: 'Page 1 of 2',
        previousLabel: 'Previous page',
        nextLabel: 'Next page',
        status: '1 / 2',
        iconOnly: true,
        testIdPrefix: 'users',
      },
    })

    expect(wrapper.element.tagName).toBe('DIV')
    expect(wrapper.attributes('data-testid')).toBe('users-pagination')
    expect(wrapper.get('[data-testid="users-page-label"]').text()).toBe('Page 1 of 2')
    expect(wrapper.get('.pagination-status').text()).toBe('1 / 2')

    const next = wrapper.get('[data-testid="users-next-page"]')
    expect(next.attributes('aria-label')).toBe('Next page')
    expect(next.classes()).toContain('btn-icon')
    expect(next.text()).toBe('')

    await next.trigger('click')
    expect(wrapper.emitted('next')).toHaveLength(1)
  })

  it('renders numbered pages and emits the selected page', async () => {
    const wrapper = mount(PaginationBar, {
      props: {
        info: 'Showing 1-5 of 12',
        pageCount: 3,
        currentPage: 2,
        pageLabel: (page: number) => `Go to page ${page}`,
      },
    })

    const buttons = wrapper.findAll('button')
    expect(buttons.map((button) => button.text())).toEqual(['1', '2', '3'])
    expect(buttons[1].attributes('aria-current')).toBe('page')
    expect(buttons[1].classes()).toContain('btn-primary')
    expect(buttons[2].attributes('aria-label')).toBe('Go to page 3')

    await buttons[2].trigger('click')
    expect(wrapper.emitted('select')).toEqual([[3]])
  })
})
