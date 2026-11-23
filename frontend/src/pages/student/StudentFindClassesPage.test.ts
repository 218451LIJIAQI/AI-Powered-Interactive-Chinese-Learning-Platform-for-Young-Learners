import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { ClassCard } from '@/types/classes'

const api = vi.hoisted(() => ({ catalogue: vi.fn(), catalogueMembership: vi.fn(), topics: vi.fn() }))

vi.mock('@/api/classes', () => ({
  studentClassesApi: { catalogue: api.catalogue, catalogueMembership: api.catalogueMembership },
  publicClassesApi: { topics: api.topics },
}))

import StudentFindClassesPage from './StudentFindClassesPage.vue'

function card(id: number, title: string, topicName: string | null, bestAfterUnit: number | null = null): ClassCard {
  return { id, title, description: null, topicName, bestAfterUnit, lessonCount: 5 }
}

const catalogue = [
  card(1, 'Everyday Conversations', 'Daily life', 3),
  card(2, 'Shopping and Prices', 'Travel', 4),
  card(5, 'Travel Mandarin', 'Travel', 3),
]

async function mountPage() {
  return (await mountAtRoute(StudentFindClassesPage, '/student/classes/find')).wrapper
}

describe('StudentFindClassesPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.catalogue.mockResolvedValue(pageOf([], 0, 0, 100))
    api.topics.mockResolvedValue([])
    api.catalogueMembership.mockResolvedValue({ joinedCount: 0, plannedClassIds: [] })
  })

  it('lists the classes on the shared card, with theme chips and the plan’s classes tagged', async () => {
    api.catalogue.mockResolvedValue(pageOf(catalogue, 0, 3, 100))
    api.topics.mockResolvedValue([
      { id: 1, name: 'Travel' },
      { id: 2, name: 'Daily life' },
    ])
    api.catalogueMembership.mockResolvedValue({ joinedCount: 2, plannedClassIds: [2, 5] })
    const wrapper = await mountPage()

    // S10.04: the Find classes tab already counts the classes, so All has no count of its own.
    expect(wrapper.findAll('.filter-chip').map((chip) => chip.text().replace(/\s+/g, ' '))).toEqual([
      'All',
      'Travel · 2',
      'Daily life · 1',
    ])
    const cards = wrapper.findAll('.class-catalogue-card')
    expect(cards.map((item) => item.get('.class-avatar').text())).toEqual(['EC', 'SP', 'TM'])
    expect(cards[0]!.text()).toContain('5 lessons · best after Unit 3')
    expect(cards[0]!.find('[data-testid="in-your-plan"]').exists()).toBe(false)
    expect(cards[1]!.get('[data-testid="in-your-plan"]').text()).toBe('In your plan')
    expect(cards[2]!.get('[data-testid="in-your-plan"]').text()).toBe('In your plan')
    expect(cards[1]!.get('.btn').text()).toBe('View class')
    expect(cards[1]!.get('.btn').attributes('href')).toBe('/student/classes/2')
    expect(wrapper.findAll('.route-tab-count').map((badge) => badge.text())).toEqual(['2', '3'])
  })

  it('says the learner joined every class only when they joined some', async () => {
    api.catalogueMembership.mockResolvedValue({ joinedCount: 2, plannedClassIds: [] })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('You have joined every class')
    const tab = wrapper.findAll('.route-tab').find((link) => link.text().includes('My classes'))!
    expect(tab.get('.route-tab-count').text()).toBe('2')
    expect(api.catalogueMembership).toHaveBeenCalledTimes(1)
  })

  it('says there are no classes yet when the catalogue is empty and nothing was joined', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No classes yet')
    expect(wrapper.text()).not.toContain('You have joined every class')
  })

  it('keeps the catalogue available if personal badges cannot load', async () => {
    api.catalogue.mockResolvedValue(pageOf(catalogue, 0, 3, 100))
    api.catalogueMembership.mockRejectedValue(new Error('temporarily unavailable'))
    const wrapper = await mountPage()
    expect(wrapper.findAll('.class-catalogue-card')).toHaveLength(3)
    expect(wrapper.find('[data-testid="in-your-plan"]').exists()).toBe(false)
    expect(wrapper.findAll('.route-tab-count').map((badge) => badge.text())).toEqual(['3'])
  })
})
