import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'

import { usePublicSectionNav } from './usePublicSectionNav'

describe('usePublicSectionNav', () => {
  const originalHref = window.location.href
  const originalScrollY = Object.getOwnPropertyDescriptor(window, 'scrollY')
  const originalInnerHeight = Object.getOwnPropertyDescriptor(window, 'innerHeight')
  const originalScrollHeight = Object.getOwnPropertyDescriptor(document.documentElement, 'scrollHeight')

  function restoreDescriptor(target: object, key: string, descriptor: PropertyDescriptor | undefined): void {
    if (descriptor) Object.defineProperty(target, key, descriptor)
    else delete (target as Record<string, unknown>)[key]
  }

  beforeEach(() => {
    vi.useFakeTimers()
    document.body.innerHTML = ''
    window.history.replaceState(null, '', '/')
  })

  afterEach(() => {
    restoreDescriptor(window, 'scrollY', originalScrollY)
    restoreDescriptor(window, 'innerHeight', originalInnerHeight)
    restoreDescriptor(document.documentElement, 'scrollHeight', originalScrollHeight)
    window.history.replaceState(null, '', originalHref)
    document.body.innerHTML = ''
  })

  function mountNav(initialSections = [{ id: 'a' }, { id: 'b' }, { id: 'c' }], initialHash?: string) {
    const sections = ref(initialSections)
    const hash = ref<string | undefined>(initialHash)
    let api!: ReturnType<typeof usePublicSectionNav>

    const Host = defineComponent({
      setup() {
        const rail = ref<HTMLElement | null>(null)
        api = usePublicSectionNav(
          () => sections.value,
          rail,
          () => hash.value ?? '',
        )
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

  it('keeps a pinned section through settle window and unpins on wheel or non-editable scroll keys', async () => {
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
    window.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(20)
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

    // Emptying sections clears activeId; restoring syncs the visible section without re-pinning a stale URL hash.
    window.history.replaceState(null, '', '/#b')
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

    wrapper.unmount()
  })

  it('selects the trailing section at document bottom and auto-scrolls a short rail to reveal the active link', async () => {
    const { wrapper, api } = mountNav([{ id: 'a' }, { id: 'b' }, { id: 'c' }], '')
    const nav = wrapper.get('nav').element as HTMLElement
    Object.defineProperty(nav, 'scrollHeight', { value: 300, configurable: true })
    Object.defineProperty(nav, 'clientHeight', { value: 100, configurable: true })
    nav.scrollTop = 40

    const linkRects: Record<string, { top: number; bottom: number }> = {
      '#a': { top: 0, bottom: 30 },
      '#b': { top: -20, bottom: 10 },
      '#c': { top: 130, bottom: 160 },
    }
    vi.spyOn(nav, 'getBoundingClientRect').mockReturnValue({
      top: 0,
      bottom: 100,
      left: 0,
      right: 200,
      width: 200,
      height: 100,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    })
    for (const anchor of Array.from(nav.querySelectorAll('a'))) {
      const href = anchor.getAttribute('href') ?? ''
      vi.spyOn(anchor, 'getBoundingClientRect').mockImplementation(() => ({
        top: linkRects[href]?.top ?? 0,
        bottom: linkRects[href]?.bottom ?? 30,
        left: 0,
        right: 200,
        width: 200,
        height: 30,
        x: 0,
        y: linkRects[href]?.top ?? 0,
        toJSON: () => ({}),
      }))
    }

    // Bottom-of-document fallback selects the last section even when its top is still below scrollPaddingTop.
    setSectionTops({ a: -400, b: -100, c: 260 })
    Object.defineProperty(window, 'scrollY', { value: 500, configurable: true })
    Object.defineProperty(window, 'innerHeight', { value: 600, configurable: true })
    Object.defineProperty(document.documentElement, 'scrollHeight', { value: 1100, configurable: true })

    window.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(20)
    await nextTick()
    expect(api.activeId.value).toBe('c')
    expect(nav.scrollTop).toBe(100)

    // Moving to an active link above the rail box scrolls up; returning to the first section resets scrollTop to 0.
    api.pinSection('b')
    await nextTick()
    expect(nav.scrollTop).toBe(80)

    api.pinSection('a')
    await nextTick()
    expect(nav.scrollTop).toBe(0)

    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true })
    wrapper.unmount()
  })
})
