import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

const SCROLL_KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '])

function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && Boolean(target.closest('input, textarea, select, [contenteditable]'))
}

/** Keep the Help and policy sidebars in step with scrolling, local links and browser history. */
export function usePublicSectionNav(
  sections: () => readonly { id: string }[],
  rail: Readonly<Ref<HTMLElement | null>>,
  hash?: () => string | undefined,
) {
  const activeId = ref<string | undefined>(sections()[0]?.id)
  // A linked short section may never reach the top. Keep it selected until scrolling resumes.
  let pinned = false
  let settling = false
  let settleTimer: ReturnType<typeof setTimeout> | null = null
  let scrollFrame: number | null = null

  function currentHash(): string {
    return hash?.() ?? window.location.hash
  }

  function clearSettleTimer(): void {
    if (settleTimer !== null) {
      window.clearTimeout(settleTimer)
      settleTimer = null
    }
  }

  function finishSettling(): void {
    settling = false
    clearSettleTimer()
  }

  function armSettleTimer(): void {
    clearSettleTimer()
    settleTimer = window.setTimeout(() => {
      settleTimer = null
      settling = false
    }, 150)
  }

  function pinSection(id: string): void {
    activeId.value = id
    pinned = true
    settling = true
    armSettleTimer()
  }

  function unpin(): void {
    pinned = false
    finishSettling()
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
      if (settling) {
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

  function followHash(rawHash = currentHash()): void {
    const linked = rawHash.slice(1)
    if (sections().some(({ id }) => id === linked)) pinSection(linked)
    else {
      unpin()
      markSectionInView()
    }
  }

  function onBrowserHashChange(): void {
    followHash(window.location.hash)
  }

  function sync(): void {
    unpin()
    markSectionInView()
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
      if (!activeId.value) {
        if (currentHash()) followHash()
        else markSectionInView()
        return
      }
      if (!ids.includes(activeId.value)) {
        unpin()
        markSectionInView()
      }
    },
    { flush: 'post' },
  )

  if (hash) {
    watch(
      hash,
      () => {
        followHash()
      },
      { flush: 'post' },
    )
  }

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
    if (currentHash()) followHash()
    else markSectionInView()
    window.addEventListener('hashchange', onBrowserHashChange)
    window.addEventListener('popstate', onBrowserHashChange)
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('scrollend', finishSettling)
    window.addEventListener('wheel', unpin, { passive: true })
    window.addEventListener('touchmove', unpin, { passive: true })
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
    window.removeEventListener('scrollend', finishSettling)
    window.removeEventListener('wheel', unpin)
    window.removeEventListener('touchmove', unpin)
    window.removeEventListener('keydown', unpinOnKeydown)
  })

  return { activeId, pinSection, sync }
}
