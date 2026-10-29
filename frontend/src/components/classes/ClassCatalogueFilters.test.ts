import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ClassCatalogueFilters, { type CatalogueFilters } from './ClassCatalogueFilters.vue'

const DEFAULTS: CatalogueFilters = { keyword: '', topicId: null, levelId: null, learningType: null, sort: 'RECENT' }

function mountFilters(modelValue: CatalogueFilters = DEFAULTS) {
  return mount(ClassCatalogueFilters, {
    props: {
      modelValue,
      summary: {
        topics: [{ id: 4, name: 'Numbers' }],
        levels: [{ id: 1, name: 'Beginner 1' }],
      },
    },
    global: { stubs: { IconGlyph: true } },
  })
}

describe('ClassCatalogueFilters', () => {
  it('emits the whole filter set when one select changes', async () => {
    const wrapper = mountFilters()
    await wrapper.findAll('select')[1]!.setValue('1')

    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual({ ...DEFAULTS, levelId: 1 })
  })
})
