import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import IconGlyph from './IconGlyph.vue'

describe('IconGlyph', () => {
  it('is hidden from assistive technology, so a control is named by its text or label alone', () => {
    const wrapper = mount(IconGlyph, { props: { name: 'bell', size: 16 } })
    const svg = wrapper.get('svg')

    expect(svg.attributes('aria-hidden')).toBe('true')
    expect(svg.attributes('focusable')).toBe('false')
    expect(svg.attributes('width')).toBe('16')
  })

  it.each(['not-an-icon', 'constructor'])('falls back to the book icon for %s, still hidden', (name) => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const wrapper = mount(IconGlyph, { props: { name } })

    expect(wrapper.get('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.get('svg').classes()).toContain('lucide-book-open')
    expect(warn).toHaveBeenCalledTimes(1)
  })
})
