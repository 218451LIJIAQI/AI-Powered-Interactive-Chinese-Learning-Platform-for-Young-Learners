import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { ClassCard } from '@/types/classes'

const api = vi.hoisted(() => ({ search: vi.fn(), topics: vi.fn() }))
vi.mock('@/api/classes', () => ({ publicClassesApi: api }))

import PublicClassesPage from './PublicClassesPage.vue'

const SEE_ALL = '[data-testid="catalogue-see-all"]'

function card(id: number, title: string, topicName: string, bestAfterUnit: number | null = null): ClassCard {
  return { id, title, description: null, topicName, bestAfterUnit, lessonCount: 5 }
}

async function mountPage(path = '/classes'): Promise<VueWrapper> {
  return (await mountAtRoute(PublicClassesPage, path, { attachTo: document.body })).wrapper
}

function chip(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('.filter-chip').find((button) => button.text().startsWith(label))!
}

function cardTitles(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.class-catalogue-card-title').map((title) => title.text())
}

beforeEach(() => {
  api.search.mockReset()
  api.topics.mockReset()
  api.search.mockResolvedValue(
    pageOf(
      [
        card(1, 'Shopping and Prices', 'Travel', 4),
        card(2, 'Everyday Conversations', 'Daily life', 3),
        card(3, 'Travel Mandarin', 'Travel', 3),
      ],
      0,
      3,
      100,
    ),
  )
  api.topics.mockResolvedValue([
    { id: 1, name: 'Travel' },
    { id: 2, name: 'Daily life' },
  ])
})

describe('PublicClassesPage', () => {
  it('lists every class on the shared card: initials, theme, title, lessons and where it fits in Learn', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Explore classes')
    expect(wrapper.findAll('.filter-chip').map((button) => button.text().replace(/\s+/g, ' '))).toEqual([
      'All · 3',
      'Travel · 2',
      'Daily life · 1',
    ])
    const first = wrapper.get('.class-catalogue-card')
    expect(first.get('.class-avatar').text()).toBe('SP')
    expect(first.get('.tag').text()).toBe('Travel')
    expect(first.get('.class-catalogue-card-meta').text()).toBe('5 lessons · best after Unit 4')
    expect(first.get('a').attributes('href')).toBe('/classes/1')
    expect(wrapper.text()).not.toMatch(/teacher|live session/i)
  })

  it('offers "See all classes" under the results only while a theme is selected', async () => {
    const wrapper = await mountPage()
    expect(cardTitles(wrapper)).toEqual(['Shopping and Prices', 'Everyday Conversations', 'Travel Mandarin'])
    expect(wrapper.find(SEE_ALL).exists()).toBe(false)

    await chip(wrapper, 'Daily life').trigger('click')

    expect(cardTitles(wrapper)).toEqual(['Everyday Conversations'])
    const seeAll = wrapper.get(SEE_ALL)
    expect(seeAll.text()).toBe('See all classes')
    expect(seeAll.classes()).toContain('btn-secondary')
    // The chips carry the counts; the row under the results does not repeat them.
    const row = wrapper.get('.class-catalogue-more')
    expect(row.get('p').text()).toBe('Looking for something else?')
    expect(row.text()).not.toMatch(/\d/)
  })

  it('clears the filter and moves the focus to the All chip', async () => {
    const wrapper = await mountPage()
    await chip(wrapper, 'Travel').trigger('click')
    expect(cardTitles(wrapper)).toEqual(['Shopping and Prices', 'Travel Mandarin'])

    await wrapper.get(SEE_ALL).trigger('click')
    await flushPromises()

    expect(cardTitles(wrapper)).toHaveLength(3)
    expect(wrapper.find(SEE_ALL).exists()).toBe(false)
    const all = chip(wrapper, 'All')
    expect(all.attributes('aria-pressed')).toBe('true')
    expect(document.activeElement).toBe(all.element)
  })

  it('shows placeholder cards while loading and says why the catalogue is empty', async () => {
    const pending = deferred<never>()
    api.search.mockReturnValueOnce(pending.promise)
    const loading = await mountPage()
    expect(loading.findAll('.class-catalogue-card.is-skeleton')).toHaveLength(4)
    loading.unmount()

    api.search.mockResolvedValueOnce(pageOf([], 0, 0, 100))
    const empty = await mountPage()
    expect(empty.text()).toContain('No classes yet')
    expect(empty.text()).toContain('New classes appear here once the MandarinSpark team publishes them.')
  })

  it('restores a saved theme and corrects pages or themes missing from the current catalogue', async () => {
    const wrapper = await mountPage('/classes?theme=Travel&page=99')
    expect(cardTitles(wrapper)).toEqual(['Shopping and Prices', 'Travel Mandarin'])
    expect(wrapper.vm.$route.fullPath).toBe('/classes?theme=Travel')
    await wrapper.vm.$router.push('/classes?theme=Retired&page=99&ref=home')
    await flushPromises()
    expect(cardTitles(wrapper)).toHaveLength(3)
    expect(wrapper.vm.$route.fullPath).toBe('/classes?ref=home')
  })

  it('lets the visitor leave a stale catalogue link while the classes are loading', async () => {
    const catalogue = deferred<ReturnType<typeof pageOf<ClassCard>>>()
    const destination = deferred<boolean>()
    api.search.mockReturnValueOnce(catalogue.promise)
    const { router } = await mountAtRoute(PublicClassesPage, '/classes?theme=Retired')
    router.beforeResolve((to) => (to.path === '/help' ? destination.promise : true))

    const leaving = router.push('/help')
    await flushPromises()
    catalogue.resolve(pageOf([card(1, 'Shopping', 'Travel')]))
    await flushPromises()
    destination.resolve(true)
    await leaving

    expect(router.currentRoute.value.path).toBe('/help')
  })
})
