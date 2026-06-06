import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  registerSW: vi.fn(),
}))

vi.mock('virtual:pwa-register', () => ({
  registerSW: mocks.registerSW,
}))

describe('pwa helpers', () => {
  const managedCacheNames = ['mandarin-spark-static-v1', 'workbox-precache-v2']
  const unmanagedCacheName = 'third-party-cache'

  beforeEach(() => {
    vi.resetModules()
    mocks.registerSW.mockReset()
  })

  it('registers the service worker when the browser supports it', async () => {
    Object.defineProperty(window.navigator, 'serviceWorker', {
      value: {},
      configurable: true,
    })

    await import('./pwa')

    expect(mocks.registerSW).toHaveBeenCalledWith(expect.objectContaining({ immediate: true }))
  })

  it('clears only MandarinSpark and Workbox caches', async () => {
    const deleted: string[] = []
    Object.defineProperty(window, 'caches', {
      value: {
        keys: vi.fn().mockResolvedValue([...managedCacheNames, unmanagedCacheName]),
        delete: vi.fn(async (name: string) => {
          deleted.push(name)
          return true
        }),
      },
      configurable: true,
    })

    const { clearOfflineCaches } = await import('./pwa')
    await clearOfflineCaches()

    expect(deleted).toEqual(managedCacheNames)
  })
})
