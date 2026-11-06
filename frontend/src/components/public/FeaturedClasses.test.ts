import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ClassCard } from '@/types/classes'

const mocks = vi.hoisted(() => ({ featured: vi.fn() }))

vi.mock('@/api/classes', () => ({ publicClassesApi: { featured: mocks.featured } }))

import FeaturedClasses from './FeaturedClasses.vue'

function card(overrides: Partial<ClassCard> = {}): ClassCard {
  return {
    id: 1,
    title: 'Mandarin for Beginners',
    description: null,
    levelName: 'Beginner 1',
    topicName: 'Daily life',
    learningType: 'HYBRID',
    teacher: { id: 1, name: 'Li Wei', verified: true, bio: null },
    unitCount: 2,
    lessonCount: 5,
    upcomingLiveSessionCount: 4,
    ...overrides,
  }
}

/** The card itself belongs to the catalogue; here it only has to get the class and the route to its page. */
const ClassCatalogueCard = {
  props: ['card', 'to'],
  template: '<article class="card-stub" :data-to="JSON.stringify(to)">{{ card.title }}</article>',
}

function mountList() {
  return mount(FeaturedClasses, { global: { stubs: { IconGlyph: true, ClassCatalogueCard } } })
}

beforeEach(() => {
  mocks.featured.mockReset()
})

describe('FeaturedClasses (FRPUB01)', () => {
  it('shows each class on the catalogue card, linking to its class page', async () => {
    mocks.featured.mockResolvedValue([card(), card({ id: 10, title: 'Numbers, Money and Time' })])
    const wrapper = mountList()
    await flushPromises()

    const cards = wrapper.findAll('.card-stub')
    expect(cards.map((item) => item.text())).toEqual(['Mandarin for Beginners', 'Numbers, Money and Time'])
    expect(cards[1]!.attributes('data-to')).toBe(JSON.stringify({ name: 'class-detail', params: { classId: 10 } }))
  })

  it('offers a retry when the classes cannot be loaded', async () => {
    mocks.featured.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([card()])
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.text()).toContain('Featured classes could not be loaded.')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.card-stub')).toHaveLength(1)
  })

  it('says why the list is empty when no class is published yet', async () => {
    mocks.featured.mockResolvedValue([])
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.text()).toContain('No classes yet')
    expect(wrapper.text()).toContain('Classes will appear here once teachers publish them.')
  })
})
