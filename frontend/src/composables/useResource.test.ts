import { flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { withSetup } from '@/test/helpers'
import type { PageResponse } from '@/types/api'

import { useResource, useServerPage } from './useResource'

const SERVER_TROUBLE = 'The learning service is having trouble right now. Please try again shortly.'

function mountResource<T>(loader: () => Promise<T>, immediate = true) {
  return withSetup(() => useResource(loader, immediate)).result
}

describe('useResource', () => {
  it('loads on mount and shows the data', async () => {
    const resource = mountResource(async () => ({ count: 1 }))
    expect(resource.loading.value).toBe(true)

    await flushPromises()

    expect(resource.loading.value).toBe(false)
    expect(resource.data.value).toEqual({ count: 1 })
  })

  it('shows a translated error when the request fails, never placeholder data', async () => {
    const resource = mountResource(() => Promise.reject({ response: { status: 500, data: {} } }))
    await flushPromises()

    expect(resource.error.value).toBe(SERVER_TROUBLE)
    expect(resource.data.value).toBeNull()
    expect(resource.loading.value).toBe(false)
  })

  it('loads on demand and clears the previous error on reload', async () => {
    const loader = vi
      .fn()
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockResolvedValueOnce({ count: 2 })
    const resource = mountResource(loader, false)

    expect(resource.loading.value).toBe(false)
    expect(loader).not.toHaveBeenCalled()

    await resource.reload()
    expect(resource.error.value).toBe(SERVER_TROUBLE)

    await resource.reload()
    expect(resource.error.value).toBeNull()
    expect(resource.data.value).toEqual({ count: 2 })
  })

  it('keeps showing the last data while a reload runs and ignores an older answer that arrives late', async () => {
    let resolveFirst: (value: string) => void = () => {}
    const loader = vi
      .fn()
      .mockResolvedValueOnce('initial')
      .mockImplementationOnce(() => new Promise<string>((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce('latest')
    const resource = mountResource(loader)
    await flushPromises()
    expect(resource.data.value).toBe('initial')

    const slow = resource.reload()
    expect(resource.loading.value).toBe(false)
    await resource.reload()
    resolveFirst('stale')
    await slow

    expect(resource.data.value).toBe('latest')
  })
})

describe('useServerPage', () => {
  it('reads the rows and totals of a server page and keeps page changes within the known pages', () => {
    const data = ref<PageResponse<string> | null>(null)
    const reload = vi.fn(async () => {})
    const page = ref(0)
    const view = useServerPage({ data, reload }, page)
    expect([view.items.value, view.totalItems.value, view.totalPages.value]).toEqual([[], 0, 1])

    data.value = { items: ['a', 'b'], page: 0, size: 2, totalItems: 5, totalPages: 3 }
    expect([view.items.value, view.totalItems.value, view.totalPages.value]).toEqual([['a', 'b'], 5, 3])
    view.goToPage(7)
    expect(page.value).toBe(2)
    view.goToPage(-1)
    expect(page.value).toBe(0)
    expect(reload).toHaveBeenCalledTimes(2)
  })

  it('shows the range of the rows on the page, and 0–0 for an empty page', () => {
    const data = ref<PageResponse<number> | null>(null)
    const view = useServerPage({ data, reload: vi.fn() }, ref(0))
    expect(view.range.value).toEqual({ from: '0', to: '0', total: '0' })

    data.value = { items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 }
    expect(view.range.value).toEqual({ from: '0', to: '0', total: '0' })
    expect(view.totalPages.value).toBe(1)

    data.value = { items: [1, 2, 3], page: 1, size: 10, totalItems: 13, totalPages: 2 }
    expect(view.range.value).toEqual({ from: '11', to: '13', total: '13' })

    data.value = {
      items: Array.from({ length: 10 }, (_, index) => index),
      page: 199,
      size: 10,
      totalItems: 2500,
      totalPages: 250,
    }
    expect(view.range.value).toEqual({ from: '1,991', to: '2,000', total: '2,500' })
  })

  it('reads a page nested in a larger answer and reloads with the page’s own reload', () => {
    const data = ref<{ counts: number; requests: PageResponse<string> } | null>(null)
    const resourceReload = vi.fn()
    const ownReload = vi.fn()
    const page = ref(0)
    const view = useServerPage({ data, reload: resourceReload }, page, {
      select: (answer) => answer.requests,
      reload: ownReload,
    })
    expect(view.items.value).toEqual([])

    data.value = { counts: 3, requests: { items: ['a'], page: 2, size: 5, totalItems: 11, totalPages: 3 } }
    expect([view.items.value, view.totalItems.value, view.totalPages.value]).toEqual([['a'], 11, 3])
    expect(view.range.value).toEqual({ from: '11', to: '11', total: '11' })

    view.goToPage(1)
    expect(page.value).toBe(1)
    expect(ownReload).toHaveBeenCalledOnce()
    expect(resourceReload).not.toHaveBeenCalled()
  })
})
