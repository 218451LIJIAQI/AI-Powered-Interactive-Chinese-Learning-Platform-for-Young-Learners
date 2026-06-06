import { registerSW } from 'virtual:pwa-register'

if (typeof window !== 'undefined' && 'serviceWorker' in navigator) {
  registerSW({
    immediate: true,
    onRegisterError(error) {
      console.warn('Service worker registration failed.', error)
    },
  })
}

export async function clearOfflineCaches(): Promise<void> {
  if (typeof window === 'undefined' || !('caches' in window)) {
    return
  }

  const cacheNames = await window.caches.keys()
  await Promise.all(
    cacheNames.filter(isManagedOfflineCache).map((cacheName) => window.caches.delete(cacheName)),
  )
}

function isManagedOfflineCache(cacheName: string): boolean {
  return cacheName.startsWith('mandarin-spark-') || cacheName.includes('workbox')
}
