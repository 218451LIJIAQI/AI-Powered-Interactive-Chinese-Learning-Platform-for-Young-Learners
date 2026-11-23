import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'

import AdminContentTabs from './AdminContentTabs.vue'
import { contentCounts, reloadContentCounts, syncContentCount } from './adminContentCounts'

const api = vi.hoisted(() => ({
  classes: vi.fn(),
  path: vi.fn(),
  practice: vi.fn(),
}))
vi.mock('@/api/adminClasses', () => ({ adminClassesApi: { list: api.classes } }))
vi.mock('@/api/learn', () => ({ adminLearningApi: { path: api.path, practiceSets: api.practice } }))

beforeEach(async () => {
  vi.resetAllMocks()
  setAccessToken(null)
  api.classes.mockResolvedValue({ totalItems: 6 })
  api.path.mockResolvedValue({ unitCount: 5 })
  api.practice.mockResolvedValue({ totalItems: 4 })
  await reloadContentCounts()
})

describe('shared admin content counts', () => {
  it('counts classes and lessons, Learn path units and practice activities', async () => {
    expect(api.classes).toHaveBeenLastCalledWith({ page: 0, size: 1 })
    expect(contentCounts.value).toEqual({ classes: 6, units: 5, practice: 4 })
  })

  it('keeps the latest refresh when overlapping reloads finish out of order', async () => {
    const first = deferred<{ totalItems: number }>()
    api.classes.mockReturnValueOnce(first.promise)
    const old = reloadContentCounts()
    api.classes.mockResolvedValueOnce({ totalItems: 1 })
    await reloadContentCounts()
    first.resolve({ totalItems: 6 })
    await old
    expect(contentCounts.value.classes).toBe(1)
  })

  it('hides only a failed current count and preserves the other current results', async () => {
    api.classes.mockRejectedValueOnce(new Error('Unavailable'))
    await reloadContentCounts()
    expect(contentCounts.value).toEqual({ classes: null, units: 5, practice: 4 })
  })

  it('loads all three counts on first mount in a fresh session and shows no review queue', async () => {
    setAccessToken('next-session')
    api.classes.mockResolvedValue({ totalItems: 9 })
    api.path.mockResolvedValue({ unitCount: 7 })
    api.practice.mockResolvedValue({ totalItems: 3 })
    vi.clearAllMocks()

    const wrapper = mount(AdminContentTabs, {
      global: { stubs: { RouteTabs: { name: 'RouteTabs', props: ['tabs', 'label'], template: '<div />' } } },
    })
    await flushPromises()

    expect(api.classes).toHaveBeenCalledTimes(1)
    expect(api.path).toHaveBeenCalledTimes(1)
    expect(api.practice).toHaveBeenCalledTimes(1)
    const tabs = wrapper.getComponent({ name: 'RouteTabs' }).props('tabs') as { label: string; count: number }[]
    expect(tabs.map((tab) => [tab.label, tab.count])).toEqual([
      ['Classes', 9],
      ['Learning path', 7],
      ['Practice library', 3],
    ])
  })

  it('reuses the session unit count on subsequent tab mounts instead of re-fetching the full learning path', async () => {
    api.classes.mockResolvedValue({ totalItems: 8 })
    api.practice.mockResolvedValue({ totalItems: 2 })
    vi.clearAllMocks()

    mount(AdminContentTabs, {
      global: { stubs: { RouteTabs: { name: 'RouteTabs', props: ['tabs', 'label'], template: '<div />' } } },
    })
    await flushPromises()

    expect(api.classes).toHaveBeenCalledTimes(1)
    expect(api.path).not.toHaveBeenCalled()
    expect(api.practice).toHaveBeenCalledTimes(1)
    expect(contentCounts.value).toEqual({ classes: 8, units: 5, practice: 2 })
  })

  it('skips fetching the learning path when skipUnits is set and preserves synced unit counts', async () => {
    setAccessToken('fresh-session')
    const classesPending = deferred<{ totalItems: number }>()
    api.classes.mockReturnValueOnce(classesPending.promise)
    api.practice.mockResolvedValue({ totalItems: 4 })
    vi.clearAllMocks()

    mount(AdminContentTabs, {
      props: { skipUnits: true },
      global: { stubs: { RouteTabs: { name: 'RouteTabs', props: ['tabs', 'label'], template: '<div />' } } },
    })
    syncContentCount('units', 12)
    classesPending.resolve({ totalItems: 10 })
    await flushPromises()

    expect(api.path).not.toHaveBeenCalled()
    expect(contentCounts.value).toEqual({ classes: 10, units: 12, practice: 4 })
  })

  it('keeps a count synced while a full reload (including units) is in flight', async () => {
    const pathPending = deferred<{ unitCount: number }>()
    api.path.mockReturnValueOnce(pathPending.promise)
    const reloading = reloadContentCounts()

    syncContentCount('units', 11)
    pathPending.resolve({ unitCount: 5 })
    await reloading

    expect(contentCounts.value.units).toBe(11)
  })

  it('clears previous-session counts synchronously on mount before the new session reload finishes', async () => {
    setAccessToken('session-b')
    const classesPending = deferred<{ totalItems: number }>()
    const pathPending = deferred<{ unitCount: number }>()
    const practicePending = deferred<{ totalItems: number }>()
    api.classes.mockReturnValueOnce(classesPending.promise)
    api.path.mockReturnValueOnce(pathPending.promise)
    api.practice.mockReturnValueOnce(practicePending.promise)

    const wrapper = mount(AdminContentTabs, {
      global: { stubs: { RouteTabs: { name: 'RouteTabs', props: ['tabs', 'label'], template: '<div />' } } },
    })

    const initialTabs = wrapper.getComponent({ name: 'RouteTabs' }).props('tabs') as {
      label: string
      count: number | null
    }[]
    expect(initialTabs.map((tab) => tab.count)).toEqual([null, null, null])

    classesPending.resolve({ totalItems: 2 })
    pathPending.resolve({ unitCount: 3 })
    practicePending.resolve({ totalItems: 4 })
    await flushPromises()
    expect(contentCounts.value).toEqual({ classes: 2, units: 3, practice: 4 })
  })
})
