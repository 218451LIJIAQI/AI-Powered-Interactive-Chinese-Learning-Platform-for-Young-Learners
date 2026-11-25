import { flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { deferred, withSetup } from '@/test/helpers'
import { testRouter } from '@/test/routes'
import type { PageResponse } from '@/types/api'

import { usePageFilters, useRecordById, useResource, useServerPage } from './useResource'

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

  it('drops a pending response and ignores a late reload after leaving the page', async () => {
    const pending = deferred<string>()
    const load = vi.fn(() => pending.promise)
    const { result, wrapper } = withSetup(() => useResource(load))
    wrapper.unmount()
    await result.reload()
    pending.resolve('previous page')
    await flushPromises()
    expect(load).toHaveBeenCalledOnce()
    expect(result.data.value).toBeNull()
    expect(result.loading.value).toBe(false)
  })

  it('keeps showing the last data while a reload runs and ignores an older answer that arrives late', async () => {
    const first = deferred<string>()
    const loader = vi
      .fn()
      .mockResolvedValueOnce('initial')
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce('latest')
    const resource = mountResource(loader)
    await flushPromises()
    expect(resource.data.value).toBe('initial')

    const slow = resource.reload()
    expect(resource.loading.value).toBe(false)
    await resource.reload()
    first.resolve('stale')
    await slow

    expect(resource.data.value).toBe('latest')
  })

  it('replaces data atomically, cancels in-flight reloads and ignores updates after unmount', async () => {
    const pending = deferred<string>()
    const failing = deferred<string>()
    const loader = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockReturnValueOnce(pending.promise)
      .mockReturnValueOnce(failing.promise)
    const { result, wrapper } = withSetup(() => useResource(loader))
    await flushPromises()
    expect(result.error.value).toBe(SERVER_TROUBLE)

    const reload1 = result.reload()
    expect(result.loading.value).toBe(true)
    result.replace('saved-value')
    expect(result.data.value).toBe('saved-value')
    expect(result.error.value).toBeNull()
    expect(result.loading.value).toBe(false)

    pending.resolve('stale-reload')
    await reload1
    expect(result.data.value).toBe('saved-value')

    const reload2 = result.reload()
    result.replace('newer-value')
    failing.reject({ response: { status: 500, data: {} } })
    await reload2
    expect(result.data.value).toBe('newer-value')
    expect(result.error.value).toBeNull()

    wrapper.unmount()
    result.replace('after-unmount')
    expect(result.data.value).toBe('newer-value')
  })

  it('sets loading to true when retrying after a reload fails with existing cached data', async () => {
    const retry = deferred<string>()
    const loader = vi
      .fn<() => Promise<string>>()
      .mockResolvedValueOnce('initial')
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockReturnValueOnce(retry.promise)
    const resource = mountResource(loader)
    await flushPromises()
    expect(resource.data.value).toBe('initial')

    await resource.reload()
    expect(resource.error.value).toBe(SERVER_TROUBLE)

    const retryPromise = resource.reload()
    expect(resource.loading.value).toBe(true)
    expect(resource.error.value).toBeNull()

    retry.resolve('recovered')
    await retryPromise
    expect(resource.loading.value).toBe(false)
    expect(resource.data.value).toBe('recovered')
  })
})

describe('useRecordById', () => {
  it('empties at once when the id changes, so the last record is not shown while the next one loads', async () => {
    const id = ref<number | null>(7)
    const next = deferred<string>()
    const load = vi
      .fn<(id: number) => Promise<string>>()
      .mockResolvedValueOnce('record 7')
      .mockReturnValueOnce(next.promise)
    const record = withSetup(() => useRecordById(id, load)).result
    await flushPromises()
    expect(record.data.value).toBe('record 7')

    id.value = 8
    await nextTick()
    expect(load).toHaveBeenLastCalledWith(8)
    expect(record.data.value).toBeNull()
    expect(record.loading.value).toBe(true)

    next.resolve('record 8')
    await flushPromises()
    expect(record.data.value).toBe('record 8')
  })

  it('clears data, cancels in-flight responses and wipes stale errors when id becomes null', async () => {
    const id = ref<number | null>(7)
    const pending = deferred<string>()
    const load = vi
      .fn<(id: number) => Promise<string>>()
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockReturnValueOnce(pending.promise)
    const record = withSetup(() => useRecordById(id, load)).result
    await flushPromises()
    expect(record.error.value).toBe(SERVER_TROUBLE)

    id.value = 8
    await nextTick()
    expect(record.loading.value).toBe(true)

    id.value = null
    await nextTick()
    pending.resolve('stale record 8')
    await flushPromises()

    expect(load).toHaveBeenCalledTimes(2)
    expect(record.data.value).toBeNull()
    expect(record.error.value).toBeNull()
    expect(record.loading.value).toBe(false)
  })
})

describe('usePageFilters', () => {
  function mountFilters() {
    const filter = ref('ALL')
    const page = ref(3)
    const data = ref<string | null>('loaded')
    const reload = vi.fn(async () => {})
    const onEdit = vi.fn()
    const followAddress = withSetup(() => usePageFilters([filter], page, { data, reload }, onEdit)).result
    return { filter, page, data, reload, onEdit, followAddress }
  }

  it('resets page to 0, clears data, notifies onEdit and reloads on filter changes', async () => {
    const { filter, page, data, reload, onEdit } = mountFilters()

    filter.value = 'OPEN'
    await nextTick()

    expect(page.value).toBe(0)
    expect(data.value).toBeNull()
    expect(onEdit).toHaveBeenCalledTimes(1)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('keeps the linked page without calling onEdit when followAddress updates filters, and preserves data for page-only changes', async () => {
    const { filter, page, data, reload, onEdit, followAddress } = mountFilters()

    followAddress(2, () => {
      filter.value = 'OPEN'
    })
    await nextTick()
    expect(page.value).toBe(2)
    expect(data.value).toBeNull()
    expect(onEdit).not.toHaveBeenCalled()
    expect(reload).toHaveBeenCalledTimes(1)

    data.value = 'page-2-rows'
    followAddress(4, () => {
      filter.value = 'OPEN'
    })
    await nextTick()
    expect(page.value).toBe(4)
    expect(data.value).toBe('page-2-rows')
    expect(onEdit).not.toHaveBeenCalled()
    expect(reload).toHaveBeenCalledTimes(2)
  })

  it('still updates page and reloads when followAddress reverses a same-tick filter edit back to the committed value', async () => {
    const { filter, page, reload, onEdit, followAddress } = mountFilters()

    // Edit filter then immediately revert via followAddress before Vue flushes the watcher.
    filter.value = 'CLOSED'
    followAddress(1, () => {
      filter.value = 'ALL'
    })
    await nextTick()

    expect(page.value).toBe(1)
    expect(onEdit).not.toHaveBeenCalled()
    expect(reload).toHaveBeenCalledTimes(1)
  })
})

describe('useServerPage', () => {
  it('reads the rows and totals of a server page and keeps page changes within the known pages', () => {
    const data = ref<PageResponse<string> | null>(null)
    const reload = vi.fn(async () => {})
    const page = ref(0)
    const onPageChange = vi.fn()
    const view = useServerPage({ data, reload }, page, { onPageChange })
    expect([view.items.value, view.totalItems.value, view.totalPages.value]).toEqual([[], 0, 1])

    data.value = { items: ['a', 'b'], page: 0, totalItems: 5, totalPages: 3 }
    expect([view.items.value, view.totalItems.value, view.totalPages.value]).toEqual([['a', 'b'], 5, 3])
    view.goToPage(7)
    expect(page.value).toBe(2)
    expect(view.displayedPage.value).toBe(0)
    data.value = { items: ['e'], page: 2, totalItems: 5, totalPages: 3 }
    expect(view.displayedPage.value).toBe(2)
    view.goToPage(-1)
    expect(page.value).toBe(0)
    expect(reload).toHaveBeenCalledTimes(2)
    expect(onPageChange).toHaveBeenCalledTimes(2)
  })

  it('steps back from a later page left empty when asked to, and tells the page', async () => {
    const data = ref<PageResponse<string> | null>(null)
    const reload = vi.fn()
    const onPageChange = vi.fn()
    const page = ref(4)
    const router = await testRouter('/admin/content')
    withSetup(() => useServerPage({ data, reload }, page, { stepBackFromEmpty: true, onPageChange }), [router])

    data.value = { items: [], page: 4, totalItems: 25, totalPages: 3 }
    await nextTick()
    expect(page.value).toBe(2)
    expect(onPageChange).toHaveBeenCalledOnce()
    expect(reload).toHaveBeenCalledOnce()

    // A page with rows stays where it is.
    data.value = { items: ['a'], page: 2, totalItems: 21, totalPages: 3 }
    await nextTick()
    expect(page.value).toBe(2)
    expect(reload).toHaveBeenCalledOnce()

    // The first page stays at 0 even when empty.
    page.value = 0
    data.value = { items: [], page: 0, totalItems: 0, totalPages: 0 }
    await nextTick()
    expect(page.value).toBe(0)
    expect(reload).toHaveBeenCalledOnce()
  })

  it('stays on an empty later page unless asked to step back', async () => {
    const data = ref<PageResponse<string> | null>(null)
    const reload = vi.fn()
    const page = ref(1)
    useServerPage({ data, reload }, page)

    data.value = { items: [], page: 1, totalItems: 10, totalPages: 1 }
    await nextTick()
    expect(page.value).toBe(1)
    expect(reload).not.toHaveBeenCalled()
  })

  it('keeps an empty list on its first page with a total of zero', () => {
    const data = ref<PageResponse<number> | null>(null)
    const view = useServerPage({ data, reload: vi.fn() }, ref(0))

    data.value = { items: [], page: 0, totalItems: 0, totalPages: 0 }
    expect(view.totalItems.value).toBe(0)
    expect(view.totalPages.value).toBe(1)
  })
})
