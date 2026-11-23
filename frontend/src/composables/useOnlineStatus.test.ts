import { describe, expect, it, vi } from 'vitest'

import { withSetup } from '@/test/helpers'

import { useOnlineStatus } from './useOnlineStatus'

function setBrowserOnline(value: boolean): void {
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(value)
}

describe('useOnlineStatus', () => {
  it('starts from the browser’s own state', () => {
    setBrowserOnline(false)

    expect(withSetup(useOnlineStatus).result.online.value).toBe(false)
  })

  it('follows the offline and online events and reports the reconnect once', () => {
    setBrowserOnline(true)
    const onReconnect = vi.fn()
    const { online } = withSetup(() => useOnlineStatus(onReconnect)).result
    expect(online.value).toBe(true)

    window.dispatchEvent(new Event('offline'))
    expect(online.value).toBe(false)
    expect(onReconnect).not.toHaveBeenCalled()

    window.dispatchEvent(new Event('online'))
    expect(online.value).toBe(true)
    expect(onReconnect).toHaveBeenCalledTimes(1)

    // A second "online" without going offline is not a reconnect.
    window.dispatchEvent(new Event('online'))
    expect(onReconnect).toHaveBeenCalledTimes(1)
  })

  it('stops listening after unmount', () => {
    setBrowserOnline(true)
    const onReconnect = vi.fn()
    const { wrapper } = withSetup(() => useOnlineStatus(onReconnect))
    window.dispatchEvent(new Event('offline'))
    wrapper.unmount()

    window.dispatchEvent(new Event('online'))

    expect(onReconnect).not.toHaveBeenCalled()
  })
})
