import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { hintSettings } from '@/test/learnFixtures'
import type { HintSettings } from '@/types/learn'

const session = vi.hoisted(() => ({ version: 1, token: 'token' as string | null }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))

vi.mock('@/api/http', () => ({
  getAccessToken: () => session.token,
  getAuthSessionVersion: () => session.version,
}))
vi.mock('@/api/learn', () => ({ hintApi }))

import { useHintSettings } from './useHintSettings'

describe('useHintSettings', () => {
  // Every test is a new sign-in, which is what makes the composable forget what it loaded.
  beforeEach(() => {
    hintApi.settings.mockReset()
    session.version += 1
    session.token = 'token'
  })

  it('loads the setting once per sign-in and shares it', async () => {
    hintApi.settings.mockResolvedValueOnce(hintSettings())
    const first = useHintSettings()
    const second = useHintSettings()
    await flushPromises()
    expect(hintApi.settings).toHaveBeenCalledTimes(1)
    expect(first.settings.value?.mode).toBe('AUTOMATIC')
    expect(second.settings.value?.mode).toBe('AUTOMATIC')

    second.remember(hintSettings({ mode: 'OFF' }))
    expect(first.settings.value?.mode).toBe('OFF')
  })

  it('keeps a saved setting when the initial read answers later', async () => {
    const initial = deferred<HintSettings>()
    hintApi.settings.mockReturnValueOnce(initial.promise)
    const hints = useHintSettings()
    hints.remember(hintSettings({ mode: 'OFF' }))
    initial.resolve(hintSettings({ mode: 'AUTOMATIC' }))
    await flushPromises()
    expect(hints.settings.value?.mode).toBe('OFF')
    expect(hints.failed.value).toBe(false)
  })

  it('loads again for another sign-in and drops an answer meant for the earlier one', async () => {
    const first = deferred<HintSettings>()
    hintApi.settings.mockReturnValueOnce(first.promise)
    useHintSettings()

    session.version += 1
    hintApi.settings.mockResolvedValueOnce(hintSettings({ tier: 'HSK2', delaySeconds: 30 }))
    const next = useHintSettings()
    await flushPromises()
    expect(next.settings.value?.tier).toBe('HSK2')

    first.resolve(hintSettings({ tier: 'HSK1' }))
    await flushPromises()
    expect(next.settings.value?.tier).toBe('HSK2')
    expect(hintApi.settings).toHaveBeenCalledTimes(2)
  })

  it('leaves hints off when the setting cannot be loaded, says so, and asks again next time', async () => {
    hintApi.settings.mockRejectedValueOnce(new Error('offline'))
    const failed = useHintSettings()
    expect(failed.failed.value).toBe(false)
    await flushPromises()
    expect(failed.settings.value).toBeNull()
    expect(failed.failed.value).toBe(true)

    const answer = deferred<HintSettings>()
    hintApi.settings.mockReturnValueOnce(answer.promise)
    const retried = useHintSettings()
    expect(retried.failed.value).toBe(false)
    answer.resolve(hintSettings())
    await flushPromises()
    expect(retried.settings.value?.mode).toBe('AUTOMATIC')
    expect(retried.failed.value).toBe(false)
  })

  it('forgets a failed load of an earlier sign-in', async () => {
    hintApi.settings.mockRejectedValueOnce(new Error('offline'))
    useHintSettings()
    await flushPromises()

    session.version += 1
    hintApi.settings.mockReturnValueOnce(new Promise(() => {}))
    expect(useHintSettings().failed.value).toBe(false)
  })

  it('remembers a setting saved under a rotated token when the caller passes its current session version', async () => {
    hintApi.settings.mockResolvedValueOnce(hintSettings({ mode: 'AUTOMATIC' }))
    const hints = useHintSettings()
    await flushPromises()

    session.version += 1
    const rotatedVersion = session.version
    expect(hints.remember(hintSettings({ mode: 'OFF' }), rotatedVersion)).toBe(true)
    expect(hints.settings.value?.mode).toBe('OFF')
    expect(hints.remember(hintSettings({ mode: 'ON_REQUEST' }), rotatedVersion - 1)).toBe(false)
    expect(hints.settings.value?.mode).toBe('OFF')
  })

  it('recovers automatically when a token rotates while the initial read is still in flight, and supports reload()', async () => {
    const first = deferred<HintSettings>()
    hintApi.settings.mockReturnValueOnce(first.promise).mockResolvedValueOnce(hintSettings({ mode: 'ON_REQUEST' }))
    const hints = useHintSettings()

    session.version += 1
    first.resolve(hintSettings({ mode: 'AUTOMATIC' }))
    await flushPromises()
    expect(hintApi.settings).toHaveBeenCalledTimes(2)
    expect(hints.settings.value?.mode).toBe('ON_REQUEST')

    hintApi.settings.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(hintSettings({ mode: 'OFF' }))
    session.version += 1
    await hints.reload()
    expect(hints.failed.value).toBe(true)
    await hints.reload()
    expect(hints.failed.value).toBe(false)
    expect(hints.settings.value?.mode).toBe('OFF')
  })

  it('does not retry when signed out or invalidated while the initial read is in flight', async () => {
    const initial = deferred<HintSettings>()
    hintApi.settings.mockReturnValueOnce(initial.promise)
    const hints = useHintSettings()

    session.token = null
    session.version += 1
    initial.reject(new Error('401'))
    await flushPromises()

    expect(hintApi.settings).toHaveBeenCalledTimes(1)
    expect(hints.settings.value).toBeNull()
  })

  it('bounds automatic rotation retries to a single attempt', async () => {
    const first = deferred<HintSettings>()
    const second = deferred<HintSettings>()
    hintApi.settings.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    useHintSettings()

    session.version += 1
    first.resolve(hintSettings({ mode: 'AUTOMATIC' }))
    await flushPromises()
    expect(hintApi.settings).toHaveBeenCalledTimes(2)

    session.version += 1
    second.resolve(hintSettings({ mode: 'ON_REQUEST' }))
    await flushPromises()
    expect(hintApi.settings).toHaveBeenCalledTimes(2)
  })
})
