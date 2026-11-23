import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'

import { usePublicSectionNav } from './usePublicSectionNav'

describe('usePublicSectionNav', () => {
  const originalHref = window.location.href

  beforeEach(() => {
    vi.useFakeTimers()
    document.body.innerHTML = ''
    window.history.replaceState(null, '', '/')
  })

  afterEach(() => {
    vi.useRealTimers()
    window.history.replaceState(null, '', originalHref)
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  function mountNav(initialSections = [{ id: 'a' }, { id: 'b' }, { id: 'c' }], initialHash?: string) {
    const sections = ref(initialSections)
    const hash = ref<string | undefined>(initialHash)
    let api!: ReturnType<typeof usePublicSectionNav>

    const Host = defineComponent({
      setup() {
        const rail = ref<HTMLElement | null>(null)
        api = usePublicSectionNav(() => sections.value, rail, initialHash !== undefined ? () => hash.value : undefined)
        return () =>
          h('div', [
            h(
              'nav',
              { ref: rail },
              sections.value.map((s) =>
                h('a', {
                  href: `#${s.id}`,
                  'aria-current': api.activeId.value === s.id ? 'location' : undefined,
                }),
              ),
            ),
            ...sections.value.map((s) => h('section', { id: s.id })),
          ])
      },
    })

    const wrapper = mount(Host, { attachTo: document.body })
    return { wrapper, sections, hash, api }
  }

  function setSectionTops(tops: Record<string, number>) {
    for (const [id, top] of Object.entries(tops)) {
      const el = document.getElementById(id)
      if (el) {
        vi.spyOn(el, 'getBoundingClientRect').mockReturnValue({
          top,
          bottom: top + 200,
          left: 0,
          right: 100,
          width: 100,
          height: 200,
          x: 0,
          y: top,
          toJSON: () => ({}),
        })
      }
    }
  }

  it('tracks sections via scroll-spy with scrollPaddingTop and rAF throttling', async () => {
    const rafCallbacks: FrameRequestCallback[] = []
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      rafCallbacks.push(cb)
      return rafCallbacks.length
    })
    const cancelSpy = vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})
    vi.spyOn(window, 'getComputedStyle').mockReturnValue({ scrollPaddingTop: '64px' } as CSSStyleDeclaration)

    const { wrapper, api } = mountNav()
    setSectionTops({ a: -120, b: 50, c: 400 })

    // Multiple scroll events coalesce into a single rAF callback.
    window.dispatchEvent(new Event('scroll'))
    window.dispatchEvent(new Event('scroll'))
    expect(rafCallbacks).toHaveLength(1)

    rafCallbacks.shift()!(0)
    expect(api.activeId.value).toBe('b')

    // Pending rAF is cancelled on unmount.
    window.dispatchEvent(new Event('scroll'))
    wrapper.unmount()
    expect(cancelSpy).toHaveBeenCalledTimes(1)
  })

  it('keeps a pinned section through settle window and unpins on wheel, touchmove, or non-editable scroll keys', async () => {
    const rafCallbacks: FrameRequestCallback[] = []
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      rafCallbacks.push(cb)
      return rafCallbacks.length
    })
    const flushRaf = () => {
      while (rafCallbacks.length) rafCallbacks.shift()!(0)
    }

    const { wrapper, api } = mountNav()
    setSectionTops({ a: 0, b: 300, c: 600 })

    api.pinSection('c')
    expect(api.activeId.value).toBe('c')

    // Scrolling during settle keeps the pin alive.
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('c')

    // Keydown inside an input does NOT unpin.
    const input = document.createElement('input')
    document.body.appendChild(input)
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('c')

    // Pressing ArrowDown outside editable elements unpins and lets scroll update activeId.
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }))
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('a')

    // Pin again, settle finishes after scrollend, next scroll unpins.
    api.pinSection('c')
    window.dispatchEvent(new Event('scrollend'))
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('a')

    // Pin again, wheel unpins immediately.
    api.pinSection('b')
    window.dispatchEvent(new Event('wheel'))
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('a')

    // Pin again, touchmove unpins immediately.
    api.pinSection('b')
    window.dispatchEvent(new Event('touchmove'))
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('a')

    // Pin again; after the 150ms settle timer elapses without scrollend, the next scroll unpins.
    api.pinSection('c')
    vi.advanceTimersByTime(150)
    window.dispatchEvent(new Event('scroll'))
    flushRaf()
    expect(api.activeId.value).toBe('a')

    wrapper.unmount()
  })

  it('follows reactive hash updates, browser hashchange, dynamic section removal/restoration, and unknown hashes', async () => {
    const { wrapper, sections, hash, api } = mountNav([{ id: 'a' }, { id: 'b' }], '')
    setSectionTops({ a: 0, b: 300 })
    api.sync()
    expect(api.activeId.value).toBe('a')

    hash.value = '#b'
    await nextTick()
    expect(api.activeId.value).toBe('b')

    // Native hashchange follows window.location.hash even before a reactive route hash updates.
    window.history.replaceState(null, '', '/#a')
    window.dispatchEvent(new Event('hashchange'))
    expect(api.activeId.value).toBe('a')

    // Unknown hash unpins and falls back to visible section.
    hash.value = '#missing'
    await nextTick()
    expect(api.activeId.value).toBe('a')

    // Emptying sections clears activeId; restoring re-evaluates automatically via the section watcher.
    sections.value = []
    await nextTick()
    expect(api.activeId.value).toBeUndefined()

    const tops: Record<string, number> = { a: 0, b: 300 }
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const top = tops[this.id] ?? 0
      return { top, bottom: top + 200, left: 0, right: 100, width: 100, height: 200, x: 0, y: top, toJSON: () => ({}) }
    })
    sections.value = [{ id: 'a' }, { id: 'b' }]
    await nextTick()
    expect(api.activeId.value).toBe('a')

    // Explicit sync() unpins and computes activeId from current viewport.
    tops.a = -300
    tops.b = 0
    api.pinSection('a')
    api.sync()
    expect(api.activeId.value).toBe('b')

    wrapper.unmount()
  })
})
