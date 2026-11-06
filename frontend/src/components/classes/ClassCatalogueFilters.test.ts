import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ClassCatalogueFilters from './ClassCatalogueFilters.vue'

function mountFilters() {
  return mount(ClassCatalogueFilters, {
    props: {
      modelValue: null,
      total: 3,
      themes: [
        { name: 'Travel', count: 2 },
        { name: 'Daily life', count: 1 },
      ],
    },
  })
}

describe('ClassCatalogueFilters', () => {
  it('shows All and each theme with its count', () => {
    const chips = mountFilters().findAll('button')

    expect(chips.map((chip) => chip.text().replace(/\s+/g, ' '))).toEqual(['All · 3', 'Travel · 2', 'Daily life · 1'])
    expect(chips[0]!.attributes('aria-pressed')).toBe('true')
  })

  it('emits the chosen theme', async () => {
    const wrapper = mountFilters()
    await wrapper.findAll('button')[1]!.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Travel'])
  })
})
