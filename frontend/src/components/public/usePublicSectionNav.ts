import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

const SCROLL_KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '])

function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && Boolean(target.closest('input, textarea, select, [contenteditable]'))
}

/** Keep the Help and policy sidebars in step with scrolling, local links and browser history. */
export function usePublicSectionNav(
  sections: () => readonly { id: string }[],
  rail: Readonly<Ref<HTMLElement | null>>,
  hash: () => string,
) {
  const activeId = ref<string | undefined>(sections()[0]?.id)
  // A linked short section may never reach the top. Keep it selected until scrolling resumes.
  let pinned = false
  let settleTimer: ReturnType<typeof setTimeout> | null = null
  let scrollFrame: number | null = null

  function currentHash(): string {
    return window.location.hash || hash()
  }

  function clearSettleTimer(): void {
    if (settleTimer !== null) {
      window.clearTimeout(settleTimer)
      settleTimer = null
    }
  }

  function armSettleTimer(): void {
    clearSettleTimer()
    settleTimer = window.setTimeout(() => {
      settleTimer = null
    }, 150)
  }

  function pinSection(id: string): void {
    activeId.value = id
    pinned = true
    armSettleTimer()
  }

  function unpin(): void {
    pinned = false
    clearSettleTimer()
  }

  function unpinOnKeydown(event: KeyboardEvent): void {
    if (!SCROLL_KEYS.has(event.key) || isEditableTarget(event.target)) return
    unpin()
  }

  function markSectionInView(): void {
    if (pinned) return
    const entries = sections()
    const page = document.documentElement
    if (window.scrollY > 0 && window.innerHeight + window.scrollY >= page.scrollHeight - 1) {
      activeId.value = entries.at(-1)?.id
      return
    }
    // Use the same line as fragment navigation under the sticky header.
    const scrollPadding = parseFloat(getComputedStyle(page).scrollPaddingTop) || 0
    let current = entries[0]?.id
    for (const { id } of entries) {
      const section = document.getElementById(id)
      if (!section) continue
      if (section.getBoundingClientRect().top > scrollPadding + 1) break
      current = id
    }
    activeId.value = current
  }

  function onScroll(): void {
    if (pinned) {
      if (settleTimer !== null) {
        armSettleTimer()
        return
      }
      unpin()
    }
    if (scrollFrame !== null) return
    scrollFrame = window.requestAnimationFrame(() => {
      scrollFrame = null
      markSectionInView()
    })
  }

  function followHash(rawHash: string): void {
    const linked = rawHash.slice(1)
    if (sections().some(({ id }) => id === linked)) pinSection(linked)
    else sync()
  }

  function onBrowserHashChange(): void {
    followHash(window.location.hash)
  }

  function sync(): void {
    unpin()
    markSectionInView()
  }

  function followCurrentHash(): void {
    const raw = currentHash()
    if (raw) followHash(raw)
    else markSectionInView()
  }

  // Re-evaluate when the section list itself changes (e.g. conditional sections mount/unmount).
  watch(
    () => sections().map((entry) => entry.id),
    (ids) => {
      if (ids.length === 0) {
        unpin()
        activeId.value = undefined
        return
      }
      if (!activeId.value || !ids.includes(activeId.value)) sync()
    },
    { flush: 'post' },
  )

  watch(
    hash,
    (next) => {
      followHash(next)
    },
    { flush: 'post' },
  )

  // A short desktop window can scroll the rail itself. Reveal the current link without moving the article.
  watch(
    activeId,
    (id) => {
      const sidebar = rail.value
      if (!sidebar || sidebar.scrollHeight <= sidebar.clientHeight) return
      if (id === sections()[0]?.id) {
        sidebar.scrollTop = 0
        return
      }
      const link = sidebar.querySelector('[aria-current="location"]')?.getBoundingClientRect()
      if (!link) return
      const box = sidebar.getBoundingClientRect()
      if (link.top < box.top) sidebar.scrollTop += link.top - box.top
      else if (link.bottom > box.bottom) sidebar.scrollTop += link.bottom - box.bottom
    },
    { flush: 'post' },
  )

  onMounted(() => {
    followCurrentHash()
    window.addEventListener('hashchange', onBrowserHashChange)
    window.addEventListener('popstate', onBrowserHashChange)
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('scrollend', clearSettleTimer)
    window.addEventListener('wheel', unpin, { passive: true })
    window.addEventListener('keydown', unpinOnKeydown)
  })

  onBeforeUnmount(() => {
    clearSettleTimer()
    if (scrollFrame !== null) {
      window.cancelAnimationFrame(scrollFrame)
      scrollFrame = null
    }
    window.removeEventListener('hashchange', onBrowserHashChange)
    window.removeEventListener('popstate', onBrowserHashChange)
    window.removeEventListener('scroll', onScroll)
    window.removeEventListener('scrollend', clearSettleTimer)
    window.removeEventListener('wheel', unpin)
    window.removeEventListener('keydown', unpinOnKeydown)
  })

  return { activeId, pinSection }
}
