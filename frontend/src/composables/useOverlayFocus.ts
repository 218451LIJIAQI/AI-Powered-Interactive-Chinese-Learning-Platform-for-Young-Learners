import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const SCROLL_LOCK_CLASS = 'has-overlay-open'

/** Open overlays, innermost last: only the innermost one answers Escape and Tab. */
const openOverlays: symbol[] = []

interface OverlayFocusOptions {
  /** True while the overlay is open. */
  readonly active: Ref<boolean>
  readonly container: Ref<HTMLElement | null>
  readonly onClose: () => void
}

/**
 * Keyboard and focus behaviour of the modal dialogs and drawers (AppOverlay): Escape closes, focus moves into the
 * overlay on open (to its `[data-autofocus]` element, else the first focusable one), Tab stays inside it, the page
 * behind does not scroll, and focus returns to the element that opened it on close. Overlays opened on top of each
 * other close one at a time.
 */
export function useOverlayFocus(options: OverlayFocusOptions): void {
  const token = Symbol('overlay')
  let returnTarget: HTMLElement | null = null
  let isOpen = false

  function focusables(): HTMLElement[] {
    const nodes = options.container.value?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? []
    return Array.from(nodes).filter((node) => node.getClientRects().length > 0)
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (openOverlays[openOverlays.length - 1] !== token) return
    if (event.key === 'Escape') {
      event.preventDefault()
      options.onClose()
      return
    }
    if (event.key !== 'Tab') return

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

  async function open(): Promise<void> {
    if (isOpen) return
    isOpen = true
    returnTarget = document.activeElement instanceof HTMLElement ? document.activeElement : null
    openOverlays.push(token)
    document.addEventListener('keydown', handleKeydown)
    document.documentElement.classList.add(SCROLL_LOCK_CLASS)
    await nextTick()
    const autofocus = options.container.value?.querySelector<HTMLElement>('[data-autofocus]')
    ;(autofocus ?? focusables()[0])?.focus()
  }

  function release(): void {
    if (!isOpen) return
    isOpen = false
    document.removeEventListener('keydown', handleKeydown)
    const index = openOverlays.indexOf(token)
    if (index >= 0) openOverlays.splice(index, 1)
    // The page scrolls again once the last overlay closes.
    if (!openOverlays.length) document.documentElement.classList.remove(SCROLL_LOCK_CLASS)
    if (returnTarget?.isConnected) returnTarget.focus()
    returnTarget = null
  }

  watch(options.active, (isActive) => (isActive ? void open() : release()))
  // An overlay shown with v-if is already open when it mounts.
  onMounted(() => {
    if (options.active.value) void open()
  })
  onBeforeUnmount(release)
}
