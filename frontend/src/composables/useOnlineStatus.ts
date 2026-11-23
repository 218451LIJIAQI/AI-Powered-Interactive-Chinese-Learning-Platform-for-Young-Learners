import { onBeforeUnmount, onMounted, readonly, ref, type Ref } from 'vue'

/**
 * Whether the browser has a network connection (`navigator.onLine`, kept up to date by the window's `online` and
 * `offline` events). `onReconnect` runs each time the connection comes back, so a page can read its data again.
 */
export function useOnlineStatus(onReconnect?: () => void): { online: Readonly<Ref<boolean>> } {
  const online = ref(navigator.onLine)

  function handleOnline(): void {
    const wasOffline = !online.value
    online.value = true
    if (wasOffline) onReconnect?.()
  }

  function handleOffline(): void {
    online.value = false
  }

  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return { online: readonly(online) }
}
