import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { hintSettings } from '@/test/learnFixtures'
import type { HintSettings } from '@/types/learn'

const session = vi.hoisted(() => ({ version: 1 }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))

vi.mock('@/api/http', () => ({ getAuthSessionVersion: () => session.version }))
vi.mock('@/api/learn', () => ({ hintApi }))

import { useHintSettings } from './useHintSettings'

describe('useHintSettings', () => {
  // Every test is a new sign-in, which is what makes the composable forget what it loaded.
  beforeEach(() => {
    hintApi.settings.mockReset()
    session.version += 1
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
})
