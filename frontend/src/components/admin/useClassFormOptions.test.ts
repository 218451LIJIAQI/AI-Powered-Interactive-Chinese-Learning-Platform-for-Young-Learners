import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { withSetup } from '@/test/helpers'

const api = vi.hoisted(() => ({ topics: vi.fn(), path: vi.fn() }))

vi.mock('@/api/classes', () => ({ publicClassesApi: { topics: api.topics } }))
vi.mock('@/api/learn', () => ({ adminLearningApi: { path: api.path } }))

import { useClassFormOptions } from './useClassFormOptions'

describe('useClassFormOptions', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.topics.mockResolvedValue([{ id: 1, name: 'Daily life' }])
    api.path.mockResolvedValue({
      unitCount: 2,
      levels: [
        { level: 1, hskLevel: 1, units: [{ number: 1, title: 'Pinyin and tones' }] },
        { level: 2, hskLevel: 2, units: [{ number: 3, title: 'First sentences' }] },
      ],
    })
  })

  it('loads the themes and the Learn path once, when a form first needs them', async () => {
    const { result } = withSetup(useClassFormOptions)
    expect(api.topics).not.toHaveBeenCalled()

    result.load()
    result.load()
    await flushPromises()

    expect(api.topics).toHaveBeenCalledOnce()
    expect(api.path).toHaveBeenCalledOnce()
    expect(result.themes.value).toEqual([{ id: 1, name: 'Daily life' }])
  })

  it('offers the units 1 to 8, named after the Learn path where it knows them', async () => {
    const { result } = withSetup(useClassFormOptions)
    expect(result.learnUnits.value.map((unit) => unit.number)).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
    expect(result.learnUnits.value.every((unit) => unit.title === null)).toBe(true)

    result.load()
    await flushPromises()

    expect(result.learnUnits.value.slice(0, 4)).toEqual([
      { number: 1, title: 'Pinyin and tones' },
      { number: 2, title: null },
      { number: 3, title: 'First sentences' },
      { number: 4, title: null },
    ])
  })

  it('still offers the unit numbers when the Learn path cannot be loaded', async () => {
    api.path.mockRejectedValue(new Error('offline'))
    const { result } = withSetup(useClassFormOptions)

    result.load()
    await flushPromises()

    expect(result.learnUnits.value).toHaveLength(8)

    api.path.mockResolvedValue({ levels: [{ units: [{ number: 1, title: 'Pinyin' }] }] })
    result.load()
    await flushPromises()
    expect(result.learnUnits.value[0]?.title).toBe('Pinyin')
    expect(api.path).toHaveBeenCalledTimes(2)
    expect(api.topics).toHaveBeenCalledOnce()
  })
})
