import { onBeforeUnmount, ref } from 'vue'

/** How long a toast stays on screen. */
const TOAST_DURATION_MS = 4000

/**
 * A page's toast (render it with `<AppToast :message="toast" />`): `showToast` replaces the current message, which
 * disappears by itself after a few seconds.
 */
export function useToast() {
  const toast = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined

  function showToast(message: string): void {
    clearTimeout(timer)
    toast.value = message
    timer = setTimeout(() => {
      toast.value = null
    }, TOAST_DURATION_MS)
  }

  onBeforeUnmount(() => clearTimeout(timer))

  return { toast, showToast }
}
