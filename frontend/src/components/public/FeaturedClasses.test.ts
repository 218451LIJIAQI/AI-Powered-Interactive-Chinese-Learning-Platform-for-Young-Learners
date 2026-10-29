import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ClassCard } from '@/types/classes'

const mocks = vi.hoisted(() => ({ featured: vi.fn() }))

vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkHrefStub }))

vi.mock('@/api/classes', () => ({ publicClassesApi: { featured: mocks.featured } }))

import FeaturedClasses from './FeaturedClasses.vue'

function card(overrides: Partial<ClassCard> = {}): ClassCard {
  return {
    id: 1,
    title: 'Mandarin for Beginners',
    description: null,
    levelName: 'Beginner 1',
    topicName: 'Sentences',
    learningType: 'HYBRID',
    teacher: { id: 1, name: 'Li Wei', verified: true, bio: null },
    unitCount: 2,
    lessonCount: 5,
    upcomingLiveSessionCount: 4,
    ...overrides,
  }
}

function mountList() {
  return mount(FeaturedClasses, { global: { stubs: { IconGlyph: true, PortraitAvatar: true } } })
}

beforeEach(() => {
  mocks.featured.mockReset()
})

describe('FeaturedClasses (FRPUB01)', () => {
  it('lists each class teacher first, linking to its class page', async () => {
    mocks.featured.mockResolvedValue([
      card(),
      card({
        id: 10,
        title: 'Numbers, Money and Time',
        teacher: { id: 4, name: 'Tan Mei Fong', verified: false, bio: null },
        upcomingLiveSessionCount: 0,
      }),
    ])
    const wrapper = mountList()
    await flushPromises()

    const rows = wrapper.findAll('.featured-class')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.get('.featured-class-teacher').text()).toBe('Li Wei Verified')
    expect(rows[0]!.get('.featured-class-link').attributes('href')).toBe(
      JSON.stringify({ name: 'class-detail', params: { classId: 1 } }),
    )
    expect(rows[0]!.text()).toContain('Beginner 1 · Sentences · 2 units · 5 lessons · 4 live sessions coming up')
    expect(rows[0]!.find('.verified-mark').exists()).toBe(true)
    expect(rows[1]!.find('.verified-mark').exists()).toBe(false)
    expect(rows[1]!.text()).not.toContain('live session')
  })

  it('offers a retry when the classes cannot be loaded', async () => {
    mocks.featured.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([card()])
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.text()).toContain('Featured classes could not be loaded.')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.featured-class')).toHaveLength(1)
  })

  it('says so when no class is published yet', async () => {
    mocks.featured.mockResolvedValue([])
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.text()).toContain('Classes will appear here once teachers publish them.')
  })
})
