import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/** Media query of the off-canvas navigation (keep in sync with the 900px breakpoint in main.css). */
export const DRAWER_MEDIA_QUERY = '(max-width: 899.98px)'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const SCROLL_LOCK_CLASS = 'has-overlay-open'

/** Reactive `matchMedia` result; false where matchMedia is unavailable (unit tests). */
export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref<boolean>(false)
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return matches

  const list = window.matchMedia(query)
  const update = (): void => {
    matches.value = list.matches
  }
  onMounted(() => {
    update()
    list.addEventListener('change', update)
  })
  onBeforeUnmount(() => list.removeEventListener('change', update))
  return matches
}

interface OverlayFocusOptions {
  /** True while the overlay is open and behaving as an overlay (not as an inline panel). */
  readonly active: Ref<boolean>
  readonly container: Ref<HTMLElement | null>
  readonly onClose: () => void
  /** Element focused on open; defaults to the first focusable element in the container. */
  readonly initialFocus?: Ref<HTMLElement | null>
  /** Keeps Tab inside the container (modal drawers). */
  readonly trapFocus?: boolean
  /** Stops the page behind the overlay from scrolling. */
  readonly lockScroll?: boolean
}

/**
 * Shared keyboard and focus behaviour of drawers and menus: Escape closes, focus moves into the overlay on open
 * and back to the element that opened it on close, optional focus trap and body scroll lock.
 */
export function useOverlayFocus(options: OverlayFocusOptions): { focusables: () => HTMLElement[] } {
  let returnTarget: HTMLElement | null = null

  function focusables(): HTMLElement[] {
    const nodes = options.container.value?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? []
    return Array.from(nodes).filter((node) => node.getClientRects().length > 0)
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault()
      options.onClose()
      return
    }
    if (event.key !== 'Tab' || !options.trapFocus) return

    const items = focusables()
    const first = items[0]
    const last = items[items.length - 1]
    if (!first || !last) return

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  function release(restoreFocus: boolean): void {
    document.removeEventListener('keydown', handleKeydown)
    if (options.lockScroll) document.documentElement.classList.remove(SCROLL_LOCK_CLASS)
    if (restoreFocus && returnTarget?.isConnected) returnTarget.focus()
    returnTarget = null
  }

  watch(options.active, async (isActive) => {
    if (!isActive) {
      release(true)
      return
    }
    returnTarget = document.activeElement instanceof HTMLElement ? document.activeElement : null
    document.addEventListener('keydown', handleKeydown)
    if (options.lockScroll) document.documentElement.classList.add(SCROLL_LOCK_CLASS)
    await nextTick()
    const target = options.initialFocus?.value ?? focusables()[0]
    target?.focus()
  })

  onBeforeUnmount(() => release(false))

  return { focusables }
}
