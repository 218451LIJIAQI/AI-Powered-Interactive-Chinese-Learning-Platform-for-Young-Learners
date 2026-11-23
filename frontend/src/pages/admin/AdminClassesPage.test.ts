import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as contentCounts from '@/components/admin/adminContentCounts'
import { deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { PageResponse } from '@/types/api'
import type { AdminClassPage, AdminContentRow } from '@/types/adminClasses'

const api = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), topics: vi.fn(), path: vi.fn() }))

vi.mock('@/api/adminClasses', () => ({ adminClassesApi: { list: api.list, create: api.create } }))
vi.mock('@/api/classes', () => ({ publicClassesApi: { topics: api.topics } }))
vi.mock('@/api/learn', () => ({ adminLearningApi: { path: api.path } }))

import AdminClassesPage from './AdminClassesPage.vue'

const ROWS: AdminContentRow[] = [
  {
    type: 'CLASS',
    id: 5,
    title: 'Mandarin for Campus Life',
    classId: 5,
    classTitle: null,
    status: 'PUBLISHED',
    updatedAt: '2026-05-18T10:00:00',
  },
  {
    type: 'LESSON',
    id: 31,
    title: 'Lantern Festival',
    classId: 6,
    classTitle: 'Chinese Culture and Festivals',
    status: 'DRAFT',
    updatedAt: '2026-05-17T09:00:00',
  },
]

function created(id: number, title: string): AdminClassPage {
  return {
    id,
    title,
    description: null,
    category: null,
    bestAfterUnit: null,
    status: 'DRAFT',
    lessonCount: 0,
    units: [],
    canPublish: false,
  }
}

function mountPage(path = '/admin/content') {
  return mountAtRoute(AdminClassesPage, path)
}

type Page = Awaited<ReturnType<typeof mountPage>>['wrapper']

const dialog = (wrapper: Page) => wrapper.get('[data-testid="new-class-dialog"]')

describe('AdminClassesPage', () => {
  beforeEach(() => {
    vi.spyOn(contentCounts, 'reloadContentCounts').mockResolvedValue()
    Object.values(api).forEach((fn) => fn.mockReset())
    api.list.mockResolvedValue(pageOf(ROWS, 0, 81))
    api.topics.mockResolvedValue([
      { id: 1, name: 'Daily life' },
      { id: 2, name: 'Study or exchange' },
    ])
    api.path.mockResolvedValue({
      unitCount: 2,
      levels: [{ level: 1, hskLevel: 1, units: [{ number: 3, title: 'First sentences' }] }],
    })
  })

  it('lists classes and lessons with their type, class, status and date, each opening its page (A3.01)', async () => {
    const { wrapper, router } = await mountPage()

    expect(api.list).toHaveBeenCalledWith({ keyword: undefined, type: undefined, status: undefined, page: 0, size: 10 })
    const rows = wrapper.findAll('[data-testid="admin-classes-row"]')
    expect(rows[0]!.findAll('td').map((cell) => cell.text())).toEqual([
      'Mandarin for Campus Life',
      'Class',
      '—',
      'Published',
      '18 May',
      'Open',
    ])
    expect(rows[1]!.text()).toContain('Chinese Culture and Festivals')
    expect(rows[1]!.text()).toContain('Draft')
    // A class opens its class page; a lesson its editor, drafts included.
    expect(rows[0]!.get('a').attributes('href')).toBe('/admin/content/classes/5')
    expect(rows[1]!.get('a').attributes('href')).toBe('/admin/content/classes/6/lessons/31')
    expect(rows[1]!.get('a').attributes('aria-label')).toBe('Open Lantern Festival')

    await rows[1]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/admin/content/classes/6/lessons/31')
  })

  it('puts New class at the right end of the tab row, and pages without a second count', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.get('.route-tabs-meta [data-testid="admin-classes-new"]').text()).toBe('New class')
    expect(wrapper.get('.admin-filters').find('button').exists()).toBe(false)
    // The Classes tab's Count badge says how many there are: the pager says only where the list is.
    expect(wrapper.get('.pagination-info').text()).toBe('Page 1 of 9')
  })

  it.each([true, false])(
    'applies a pending search only if the editor navigation is cancelled (opens: %s)',
    async (opens) => {
      const { wrapper, router } = await mountPage()
      const editorReady = deferred<boolean>()
      router.beforeResolve((to) => (to.name === 'admin-class' ? editorReady.promise : true))

      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
      try {
        await wrapper.get('input[type="search"]').setValue('Campus')
        await wrapper.get('[data-testid="admin-classes-row"] a').trigger('click')
        await flushPromises()
        await vi.runOnlyPendingTimersAsync()
        editorReady.resolve(opens)
        await flushPromises()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()
        expect(router.currentRoute.value.fullPath).toBe(opens ? '/admin/content/classes/5' : '/admin/content?q=Campus')
      } finally {
        editorReady.resolve(opens)
        vi.useRealTimers()
      }
    },
  )

  it('does not replace a dashboard navigation when the class list is about to step back from an empty page', async () => {
    const response = deferred<PageResponse<AdminContentRow>>()
    api.list.mockReturnValueOnce(response.promise)
    const { router } = await mountPage('/admin/content?page=2')
    const destination = deferred<void>()
    router.beforeResolve((to) => (to.name === 'admin-dashboard' ? destination.promise : true))
    const navigation = router.push('/admin/dashboard')
    await flushPromises()
    response.resolve(pageOf([], 1, 10))
    await flushPromises()
    destination.resolve()
    await navigation

    expect(router.currentRoute.value.fullPath).toBe('/admin/dashboard')
    expect(api.list).toHaveBeenCalledOnce()
  })

  it('filters by every status, archived classes included', async () => {
    const { wrapper } = await mountPage()

    const options = wrapper.get('[data-testid="admin-classes-status"]').findAll('option')
    expect(options.map((option) => option.text())).toEqual(['All statuses', 'Published', 'Draft', 'Archived'])
  })

  it('keeps the search being typed when a status filter updates the address', async () => {
    const { wrapper, router } = await mountPage()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      const search = wrapper.get<HTMLInputElement>('input[type="search"]')
      await search.setValue('Campus')
      await wrapper.get('[data-testid="admin-classes-status"]').setValue('DRAFT')
      await flushPromises()
      expect(search.element.value).toBe('Campus')

      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()
      expect(router.currentRoute.value.query).toEqual({ q: 'Campus', status: 'DRAFT' })
      expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'Campus', status: 'DRAFT' }))
    } finally {
      vi.useRealTimers()
    }
  })

  it('restores a saved search and its page when Back is used during typing', async () => {
    const { wrapper, router } = await mountPage('/admin/content?q=Campus&page=2')
    await router.push('/admin/content?q=Food')
    await flushPromises()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      const search = wrapper.get<HTMLInputElement>('input[type="search"]')
      await search.setValue('Unfinished')
      router.back()
      await flushPromises()
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(search.element.value).toBe('Campus')
      expect(router.currentRoute.value.query).toEqual({ q: 'Campus', page: '2' })
      expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'Campus', page: 1 }))
    } finally {
      vi.useRealTimers()
    }
  })

  it('keeps its filters in the address and starts over from the first page when one changes', async () => {
    const { wrapper, router } = await mountPage('/admin/content?type=LESSON&status=DRAFT&page=2')

    expect(api.list).toHaveBeenLastCalledWith({
      keyword: undefined,
      type: 'LESSON',
      status: 'DRAFT',
      page: 1,
      size: 10,
    })
    expect(wrapper.get<HTMLSelectElement>('[data-testid="admin-classes-type"]').element.value).toBe('LESSON')

    await wrapper.get('[data-testid="admin-classes-status"]').setValue('PUBLISHED')
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith({
      keyword: undefined,
      type: 'LESSON',
      status: 'PUBLISHED',
      page: 0,
      size: 10,
    })
    expect(router.currentRoute.value.query).toEqual({ type: 'LESSON', status: 'PUBLISHED' })
  })

  it('follows the address when the sidebar opens the page again without filters', async () => {
    const { wrapper, router } = await mountPage('/admin/content?status=ARCHIVED')

    await router.push('/admin/content')
    await flushPromises()

    expect(api.list).toHaveBeenLastCalledWith({
      keyword: undefined,
      type: undefined,
      status: undefined,
      page: 0,
      size: 10,
    })
    expect(wrapper.get<HTMLSelectElement>('[data-testid="admin-classes-status"]').element.selectedIndex).toBe(0)
  })

  it('says when a search finds nothing and offers Clear search (A3.02)', async () => {
    api.list.mockResolvedValueOnce(pageOf([], 0, 0))
    const { wrapper, router } = await mountPage('/admin/content?q=Calligraphy')

    expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'Calligraphy' }))
    expect(wrapper.text()).toContain('No classes or lessons match “Calligraphy”')
    expect(wrapper.text()).toContain('Check the spelling, or clear the search to see all content.')
    const clear = wrapper.get('[data-testid="admin-classes-clear"]')
    expect(clear.text()).toBe('Clear search')
    expect(clear.classes()).toContain('btn-secondary')

    await clear.trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: undefined }))
    expect(router.currentRoute.value.query).toEqual({})
    expect(wrapper.findAll('[data-testid="admin-classes-row"]')).toHaveLength(2)
  })

  it('offers Clear filters when the type or status leaves nothing', async () => {
    api.list.mockResolvedValue(pageOf([], 0, 0))
    const { wrapper } = await mountPage('/admin/content?status=ARCHIVED')

    expect(wrapper.text()).toContain('No classes or lessons match')
    expect(wrapper.get('[data-testid="admin-classes-clear"]').text()).toBe('Clear filters')
  })

  it('starts the platform’s first class from the empty list', async () => {
    api.list.mockResolvedValue(pageOf([], 0, 0))
    const { wrapper } = await mountPage()

    const empty = wrapper.get('.state-panel')
    expect(empty.text()).toContain('No classes yet')
    const start = empty.get('button')
    expect(start.text()).toBe('New class')
    expect(start.classes()).toContain('btn-primary')
    await start.trigger('click')
    expect(dialog(wrapper).get('h2').text()).toBe('New class')
  })

  it('creates a draft class from New class and opens it (A3.03)', async () => {
    api.create.mockResolvedValue(created(9, 'Hawker Food Mandarin'))
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="admin-classes-new"]').trigger('click')
    await flushPromises()
    expect(dialog(wrapper).text()).toContain('It starts as a draft.')
    // The dialog renders again on every change, so each field is looked up when it is used.
    const select = (index: number) => dialog(wrapper).findAll('select')[index]!
    expect(
      select(0)
        .findAll('option')
        .map((option) => option.text()),
    ).toEqual(['Not set', 'Daily life', 'Study or exchange'])
    const bestAfterOptions = select(1)
      .findAll('option')
      .map((option) => option.text())
    expect(bestAfterOptions).toContain('Unit 3 · First sentences')
    expect(bestAfterOptions).toContain('Unit 4')

    await dialog(wrapper).get('[data-testid="class-title"]').setValue('  Hawker Food Mandarin ')
    await dialog(wrapper).get('textarea').setValue('Order at hawker stalls.')
    await select(0).setValue(1)
    await select(1).setValue(3)
    await dialog(wrapper).get('form').trigger('submit')
    await flushPromises()

    expect(api.create).toHaveBeenCalledWith({
      title: 'Hawker Food Mandarin',
      description: 'Order at hawker stalls.',
      categoryId: 1,
      bestAfterUnit: 3,
    })
    expect(router.currentRoute.value.fullPath).toBe('/admin/content/classes/9')
  })

  it('asks for a title before it creates the class, and holds Create class back until there is one (A3.04)', async () => {
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="admin-classes-new"]').trigger('click')
    await dialog(wrapper).get('form').trigger('submit')

    expect(api.create).not.toHaveBeenCalled()
    expect(dialog(wrapper).get('[role="alert"]').text()).toBe('Add a class title.')
    expect(wrapper.get<HTMLButtonElement>('[data-testid="new-class-create"]').element.disabled).toBe(true)

    await dialog(wrapper).get('[data-testid="class-title"]').setValue('Hawker Food Mandarin')
    expect(dialog(wrapper).find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="new-class-create"]').element.disabled).toBe(false)
  })

  it('keeps the dialog open with the reason when the class cannot be created', async () => {
    api.create.mockRejectedValue({
      response: { status: 400, data: { message: 'Selected learning category does not exist.' } },
    })
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="admin-classes-new"]').trigger('click')
    await dialog(wrapper).get('[data-testid="class-title"]').setValue('Hawker Food Mandarin')
    await dialog(wrapper).get('form').trigger('submit')
    await flushPromises()

    expect(dialog(wrapper).get('[role="alert"]').text()).toBe('Selected learning category does not exist.')
    expect(router.currentRoute.value.path).toBe('/admin/content')
  })

  it('says once that a class was deleted, and takes that out of the address', async () => {
    const { wrapper, router } = await mountPage('/admin/content?deleted=Hawker%20Food%20Mandarin')
    await flushPromises()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('“Hawker Food Mandarin” deleted.')
    expect(router.currentRoute.value.query.deleted).toBeUndefined()
  })
})
