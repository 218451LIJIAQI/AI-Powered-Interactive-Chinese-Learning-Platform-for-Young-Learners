import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import FilterChips from './FilterChips.vue'

function mountFilters(total?: number) {
  return mount(FilterChips, {
    props: {
      modelValue: null,
      options: [
        { value: null, label: 'All', count: total },
        { value: 'Travel', label: 'Travel', count: 2 },
        { value: 'Daily life', label: 'Daily life', count: 1 },
      ],
    },
  })
}

describe('FilterChips', () => {
  it('shows All and each theme with its count', () => {
    const chips = mountFilters(3).findAll('button')

    expect(chips.map((chip) => chip.text().replace(/\s+/g, ' '))).toEqual(['All · 3', 'Travel · 2', 'Daily life · 1'])
    expect(chips[0]!.attributes('aria-pressed')).toBe('true')
  })

  it('leaves All without a count where none is given', () => {
    const chips = mountFilters().findAll('button')
    expect(chips.map((chip) => chip.text().replace(/\s+/g, ' '))).toEqual(['All', 'Travel · 2', 'Daily life · 1'])
  })

  it('emits the chosen theme', async () => {
    const wrapper = mountFilters()
    await wrapper.findAll('button')[1]!.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Travel'])
  })
})
