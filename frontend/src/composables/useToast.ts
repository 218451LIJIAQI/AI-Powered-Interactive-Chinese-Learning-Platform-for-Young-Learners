import { onBeforeUnmount, ref, shallowRef } from 'vue'

/** How long a toast stays on screen. */
const TOAST_DURATION_MS = 4000

/** The action button of a toast (Undo after a reversible action), as `AppToast` receives it. */
export interface ToastAction {
  readonly label: string
  /** Runs the action; the toast closes first. */
  readonly run: () => void
  /** Keeps the toast on screen while the pointer or the keyboard focus is on it, so the action stays reachable. */
  readonly hold: () => void
  /** Starts the countdown again once the pointer and the focus have left. */
  readonly release: () => void
}

/**
 * A page's toast (render it with `<AppToast :message="toast" :action="toastAction" />`): `showToast` replaces the
 * current message, which disappears by itself after a few seconds. A reversible action passes `{ label, run }` (Undo).
 */
export function useToast() {
  const toast = ref<string | null>(null)
  const toastAction = shallowRef<ToastAction | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  let disposed = false

  function hideToast(): void {
    clearTimeout(timer)
    toast.value = null
    toastAction.value = null
  }

  function startCountdown(): void {
    clearTimeout(timer)
    if (toast.value !== null) timer = setTimeout(hideToast, TOAST_DURATION_MS)
  }

  function showToast(message: string, action?: { readonly label: string; readonly run: () => void }): void {
    if (disposed) return
    toast.value = message
    toastAction.value = action
      ? {
          label: action.label,
          run: () => {
            hideToast()
            action.run()
          },
          hold: () => clearTimeout(timer),
          release: startCountdown,
        }
      : null
    startCountdown()
  }

  onBeforeUnmount(() => {
    disposed = true
    hideToast()
  })

  return { toast, toastAction, showToast, hideToast }
}
