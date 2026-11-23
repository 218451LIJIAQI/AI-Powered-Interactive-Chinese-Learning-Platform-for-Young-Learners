import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { deferred, withSetup } from '@/test/helpers'
import { testRouter } from '@/test/routes'

import { useSelectedRecord } from './useSelectedRecord'

async function mountAt(
  path: string,
  load: (id: number) => Promise<string>,
  listQuery: () => Record<string, string> = () => ({}),
) {
  const router = await testRouter(path)
  const { result: selection } = withSetup(() => useSelectedRecord('user', load, listQuery, () => undefined), [router])
  await flushPromises()
  return { router, selection }
}

describe('useSelectedRecord', () => {
  it('loads the record the address names and the next one it is changed to', async () => {
    const load = vi.fn(async (id: number) => `user ${id}`)
    const { router, selection } = await mountAt('/admin/users?user=7', load)

    expect(selection.selectedId.value).toBe(7)
    expect(selection.detail.data.value).toBe('user 7')

    await router.replace('/admin/users?user=9')
    await flushPromises()
    expect(selection.detail.data.value).toBe('user 9')
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('stays empty when the selection is removed while its record is still on its way', async () => {
    const pending = deferred<string>()
    const { selection } = await mountAt('/admin/users?user=7', () => pending.promise)

    await selection.select(null)
    await flushPromises()
    pending.resolve('user 7')
    await flushPromises()

    expect(selection.selectedId.value).toBeNull()
    expect(selection.detail.data.value).toBeNull()
    expect(selection.detail.loading.value).toBe(false)
  })

  it('forgets the error of a record that is no longer selected', async () => {
    const { selection } = await mountAt('/admin/users?user=7', () => Promise.reject(new Error('Unavailable')))
    expect(selection.detail.error.value).not.toBeNull()

    await selection.select(null)
    await flushPromises()
    expect(selection.detail.error.value).toBeNull()
  })

  it('writes the list’s own address state with every selection', async () => {
    // A search typed on the page whose own navigation into the address has not finished yet.
    const listQuery = () => ({ q: 'new' })
    const { router, selection } = await mountAt('/admin/users?q=old&tab=2', async (id) => `user ${id}`, listQuery)

    await selection.select(9)
    expect(router.currentRoute.value.query).toEqual({ q: 'new', tab: '2', user: '9' })
    await selection.select(null)
    expect(router.currentRoute.value.query).toEqual({ q: 'new', tab: '2' })
  })
})
