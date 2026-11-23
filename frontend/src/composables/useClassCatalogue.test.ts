import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { publicClassesApi } from '@/api/classes'
import { pageOf, withSetup } from '@/test/helpers'
import type { CatalogueQuery, ClassCard } from '@/types/classes'

import { useClassCatalogue } from './useClassCatalogue'

vi.mock('@/api/classes', () => ({ publicClassesApi: { topics: vi.fn() } }))

function card(id: number, topicName = 'Daily life'): ClassCard {
  return {
    id,
    title: `Class ${id}`,
    description: null,
    topicName,
    lessonCount: 1,
  }
}

beforeEach(() => {
  vi.mocked(publicClassesApi.topics).mockResolvedValue([
    { id: 1, name: 'Daily life' },
    { id: 2, name: 'Travel' },
  ])
})

describe('useClassCatalogue', () => {
  it('loads every server page so counts, filters and the final display page include classes after 100', async () => {
    const cards = [...Array.from({ length: 100 }, (_, index) => card(index + 1)), card(101, 'Travel')]
    const loader = vi.fn(async ({ page, size }: CatalogueQuery) =>
      pageOf(cards.slice(page * size, (page + 1) * size), page, cards.length, size),
    )
    const { result } = withSetup(() => useClassCatalogue(loader))
    await flushPromises()

    expect(loader.mock.calls).toEqual([[{ page: 0, size: 100 }], [{ page: 1, size: 100 }]])
    expect(result.totalItems.value).toBe(101)
    expect(result.totalPages.value).toBe(9)
    expect(result.themes.value).toEqual([
      { name: 'Daily life', count: 100 },
      { name: 'Travel', count: 1 },
    ])
    result.page.value = 8
    expect(result.items.value.map(({ id }) => id)).toEqual([97, 98, 99, 100, 101])
    result.selectTheme('Travel')
    expect(result.page.value).toBe(0)
    expect(result.items.value).toEqual([cards[100]])
  })

  it('uses one request for an empty or small catalogue', async () => {
    const loader = vi
      .fn()
      .mockResolvedValueOnce(pageOf([], 0, 0, 100))
      .mockResolvedValueOnce(pageOf([card(1)], 0, 1, 100))
    const { result } = withSetup(() => useClassCatalogue(loader))
    await flushPromises()
    expect(loader).toHaveBeenCalledTimes(1)
    expect(result.items.value).toEqual([])

    await result.resource.reload()
    expect(loader).toHaveBeenCalledTimes(2)
    expect(result.items.value).toEqual([card(1)])
  })

  it('counts a class once when publication shifts it across a page boundary during loading', async () => {
    const first = Array.from({ length: 100 }, (_, index) => card(101 - index))
    const loader = vi
      .fn()
      .mockResolvedValueOnce(pageOf(first, 0, 101, 100))
      .mockResolvedValueOnce(pageOf([card(2), card(1, 'Travel')], 1, 102, 100))
    const { result } = withSetup(() => useClassCatalogue(loader))
    await flushPromises()

    expect(result.totalItems.value).toBe(101)
    expect(result.themes.value).toContainEqual({ name: 'Daily life', count: 100 })
    result.page.value = 8
    expect(result.items.value.map(({ id }) => id)).toEqual([5, 4, 3, 2, 1])
  })

  it('reports a later page failure without presenting an incomplete catalogue, and can retry', async () => {
    const firstPage = pageOf(
      Array.from({ length: 100 }, (_, index) => card(index + 1)),
      0,
      101,
      100,
    )
    const loader = vi
      .fn()
      .mockResolvedValueOnce(firstPage)
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce(pageOf([card(101)], 1, 101, 100))
    const { result } = withSetup(() => useClassCatalogue(loader))
    await flushPromises()

    expect(result.resource.error.value).toBeTruthy()
    expect(result.allCards.value).toEqual([])
    expect(result.resource.loading.value).toBe(false)
    await result.resource.reload()
    expect(result.resource.error.value).toBeNull()
    expect(result.allCards.value).toHaveLength(101)
  })
})
