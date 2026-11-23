import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'

const FOCUSABLE_SELECTOR =
  'a[href],button,input,select,textarea,audio[controls],video[controls],details>summary:first-of-type,[contenteditable]:not([contenteditable="false"]),[tabindex]'

const SCROLL_LOCK_CLASS = 'has-overlay-open'

/** Open overlays, innermost last: only the innermost one answers Escape and Tab. */
const openOverlays: OverlayFocusOptions[] = []
let lockedApp: HTMLElement | null = null

interface OverlayFocusOptions {
  /** True while the overlay is open. */
  readonly active: Ref<boolean>
  readonly container: Ref<HTMLElement | null>
  readonly onClose: () => void
}

/** The app and any underlying teleported dialog leave the tab order and accessibility tree together. */
function updateModalLayers(): void {
  const top = openOverlays.at(-1)
  for (const overlay of openOverlays) overlay.container.value?.toggleAttribute('inert', overlay !== top)
  document.documentElement.classList.toggle(SCROLL_LOCK_CLASS, Boolean(top))
  if (!top) {
    lockedApp?.removeAttribute('inert')
    lockedApp = null
    return
  }
  const app = document.getElementById('app')
  if (app && !app.contains(top.container.value)) {
    if (!app.hasAttribute('inert')) {
      app.setAttribute('inert', '')
      lockedApp = app
    }
  } else if (lockedApp) {
    // A nested non-teleported overlay sits inside #app; release inert on #app so it stays interactive.
    lockedApp.removeAttribute('inert')
    lockedApp = null
  }
}

function isHiddenByStyle(node: HTMLElement): boolean {
  if (node.closest('[hidden]')) return true
  if (getComputedStyle(node).visibility === 'hidden') return true
  for (let current: HTMLElement | null = node; current; current = current.parentElement) {
    if (getComputedStyle(current).display === 'none') return true
  }
  return false
}

/**
 * Keyboard and focus behaviour of the modal dialogs and drawers (AppOverlay): Escape closes, focus moves into the
 * overlay on open (to its `[data-autofocus]` element, else the first focusable one), Tab stays inside it, the page
 * behind does not scroll, and focus returns to the element that opened it on close. Overlays opened on top of each
 * other close one at a time.
 */
export function useOverlayFocus(options: OverlayFocusOptions): void {
  let returnTarget: HTMLElement | null = null
  let openVersion = 0

  function focusables(): HTMLElement[] {
    const nodes = options.container.value?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? []
    return Array.from(nodes).filter(
      (node) =>
        node.tabIndex >= 0 &&
        !node.matches(':disabled') &&
        !node.closest('[inert]') &&
        !isHiddenByStyle(node) &&
        node.getClientRects().length > 0,
    )
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (openOverlays.at(-1) !== options) return
    if (event.key === 'Escape') {
      if (event.isComposing) return
      event.preventDefault()
      options.onClose()
      return
    }
    if (event.key !== 'Tab') return

    const items = focusables()
    const first = items[0]
    const last = items[items.length - 1]
    const active = document.activeElement as HTMLElement
    const boundary = event.shiftKey ? first : last
    if (items.includes(active) && active !== boundary) return

    event.preventDefault()
    const target = event.shiftKey ? last : first
    ;(target ?? options.container.value)?.focus()
  }

  async function open(): Promise<void> {
    if (openOverlays.includes(options)) return
    const version = ++openVersion
    returnTarget = document.activeElement instanceof HTMLElement ? document.activeElement : null
    openOverlays.push(options)
    document.addEventListener('keydown', handleKeydown)
    await nextTick()
    if (version !== openVersion || openOverlays.at(-1) !== options) return
    updateModalLayers()
    const items = focusables()
    const autofocus = items.find((node) => node.hasAttribute('data-autofocus'))
    ;(autofocus ?? items[0] ?? options.container.value)?.focus()
  }

  function release(): void {
    openVersion += 1
    options.container.value?.removeAttribute('inert')
    const index = openOverlays.indexOf(options)
    if (index === -1) return
    document.removeEventListener('keydown', handleKeydown)
    const wasTop = index === openOverlays.length - 1
    openOverlays.splice(index, 1)
    updateModalLayers()
    if (wasTop) {
      const parent = openOverlays.at(-1)?.container.value
      const canReturn =
        Boolean(returnTarget) &&
        returnTarget!.isConnected &&
        !returnTarget!.closest('[inert], :disabled') &&
        !isHiddenByStyle(returnTarget!) &&
        returnTarget !== document.body
      const target =
        canReturn && (!parent || parent.contains(returnTarget))
          ? returnTarget
          : (parent ?? document.getElementById('main-content'))
      target?.focus({ preventScroll: true })
    }
    returnTarget = null
  }

  watch(options.active, (isActive) => (isActive ? void open() : release()))
  // An overlay shown with v-if is already open when it mounts.
  onMounted(() => {
    if (options.active.value) void open()
  })
  onBeforeUnmount(release)
}
