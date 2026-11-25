import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as contentCounts from '@/components/admin/adminContentCounts'
import { deferred, pageOf } from '@/test/helpers'
import { adminPracticeItem } from '@/test/learnFixtures'
import { mountAtRoute } from '@/test/routes'
import type { AdminPracticeSetDetail, AdminPracticeSetRow } from '@/types/learn'

const api = vi.hoisted(() => ({
  practiceSets: vi.fn(),
  practiceSet: vi.fn(),
  savePracticeSet: vi.fn(),
  changePracticeSet: vi.fn(),
}))

vi.mock('@/api/learn', () => ({ adminLearningApi: api }))

import AdminPracticeLibraryPage from './AdminPracticeLibraryPage.vue'

const publishedSet = {
  id: 4,
  type: 'LISTENING',
  title: 'Ordering food',
  hskLevel: 1,
  status: 'PUBLISHED',
} satisfies Omit<AdminPracticeSetRow, 'updatedAt'>
const row: AdminPracticeSetRow = { ...publishedSet, updatedAt: new Date(2026, 4, 10, 12).toISOString() }
const archivedRow: AdminPracticeSetRow = { ...row, status: 'ARCHIVED' }
const otherRow: AdminPracticeSetRow = { ...row, id: 5, title: 'Numbers 1 to 10', status: 'DRAFT' }

function detail(overrides: Partial<AdminPracticeSetDetail> = {}): AdminPracticeSetDetail {
  return {
    ...publishedSet,
    createdBy: null,
    stage: 'SCAFFOLDED',
    xpReward: 15,
    playedBy: 12,
    items: [
      adminPracticeItem({
        prompt: 'Which one is rice?',
        audioText: '米饭',
        options: [
          { text: '米饭', pinyin: 'mǐfàn' },
          { text: '面条', pinyin: 'miàntiáo' },
        ],
        correctIndex: 0,
      }),
    ],
    strokeCount: null,
    characterStrokes: null,
    history: [
      { action: 'CREATED', actor: null, at: '2026-05-08T03:00:00Z' },
      { action: 'PUBLISHED', actor: null, at: '2026-05-10T03:00:00Z' },
    ],
    ...overrides,
  }
}

/** A set nobody can play yet (A4.17): its history is its creation. */
function draft(overrides: Partial<AdminPracticeSetDetail> = {}): AdminPracticeSetDetail {
  return detail({
    status: 'DRAFT',
    playedBy: 0,
    history: [{ action: 'CREATED', actor: null, at: '2026-05-08T03:00:00Z' }],
    ...overrides,
  })
}

async function mountPage() {
  return (await mountAtRoute(AdminPracticeLibraryPage, '/admin/content/practice')).wrapper
}

/** Mounts the page and opens the first row's set in its drawer. */
async function openFirstSet() {
  const wrapper = await mountPage()
  await wrapper.get('[data-testid="library-row"]').trigger('click')
  await flushPromises()
  return wrapper
}

/** How often the list was asked for. */
function listLoads(): number {
  return api.practiceSets.mock.calls.length
}

function toast(wrapper: VueWrapper) {
  return wrapper.get('[data-testid="toast"]')
}

function footerButtons(wrapper: VueWrapper): string[] {
  return wrapper.findAll('[data-testid="set-details"] .overlay-footer button').map((button) => button.text())
}

async function nameNewActivity(wrapper: VueWrapper, title: string) {
  await wrapper.get('[data-testid="new-activity"]').trigger('click')
  await wrapper.get('[data-testid="editor-title"]').setValue(title)
}

/** Restores archived set 4 from its row and opens set `id` in the drawer while the restore is on its way. */
async function restoreThenOpen(id: number) {
  const restoration = deferred<AdminPracticeSetDetail>()
  api.changePracticeSet.mockReturnValue(restoration.promise)
  api.practiceSets.mockResolvedValue(pageOf([archivedRow, otherRow]))
  const wrapper = await mountPage()
  await wrapper.get('[data-testid="library-restore"]').trigger('click')
  api.practiceSet.mockResolvedValue(
    id === 4 ? detail({ status: 'ARCHIVED' }) : draft({ id: 5, title: 'Numbers 1 to 10' }),
  )
  await wrapper.findAll('[data-testid="library-row"]')[id === 4 ? 0 : 1]!.trigger('click')
  await flushPromises()
  return { wrapper, restoration }
}

/** Saves a new activity, closes the editor while the save is on its way and starts another one. */
async function saveThenReopenEditor() {
  const wrapper = await mountPage()
  await nameNewActivity(wrapper, 'Older activity')
  await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
  await wrapper.get('[data-testid="set-editor"] button[aria-label="Close"]').trigger('click')
  await nameNewActivity(wrapper, 'Current activity')
  return wrapper
}

describe('AdminPracticeLibraryPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    vi.spyOn(contentCounts, 'reloadContentCounts').mockResolvedValue()
    api.practiceSets.mockResolvedValue(pageOf([row]))
    api.practiceSet.mockResolvedValue(detail())
  })

  it('lists the sets by title, type, level, status and date, and filters them by type on the server', async () => {
    const wrapper = await mountPage()

    expect(api.practiceSets).toHaveBeenCalledWith({ q: '', type: null, status: null, page: 0, size: 10 })
    expect(wrapper.findAll('.admin-library-table th').map((cell) => cell.text())).toEqual([
      'Title',
      'Type',
      'Level',
      'Status',
      'Updated',
      'Actions',
    ])
    const rows = wrapper.findAll('[data-testid="library-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0]!.findAll('td').map((cell) => cell.text())).toEqual([
      'Ordering food',
      'Listening',
      'HSK 1',
      'Published',
      '10 May',
      'Open',
    ])
    expect(api.practiceSet).not.toHaveBeenCalled()
    expect(wrapper.findAll('#library-type option').map((option) => option.text())).toEqual([
      'All types',
      'Flashcards',
      'Listening',
      'Tracing',
      'Drag & Drop',
      'Matching',
      'Typing',
    ])

    await wrapper.get('#library-type').setValue('TYPING')
    await flushPromises()
    expect(api.practiceSets).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'TYPING', page: 0 }))
  })

  it('counts pages, not activities: the Practice tab already says how many there are', async () => {
    api.practiceSets.mockResolvedValue(pageOf([row], 0, 81))
    const wrapper = await mountPage()

    const pages = wrapper.get('nav[aria-label="Practice library pages"]')
    expect(pages.get('.pagination-info').text()).toBe('Page 1 of 9')
    await pages.get('button[aria-label="Next"]').trigger('click')
    await flushPromises()
    expect(api.practiceSets).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 10 }))
  })

  it('names the search that matched nothing; the button says what it clears and is not the primary action', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const wrapper = await mountPage()
    api.practiceSets.mockResolvedValue(pageOf([]))

    await wrapper.get('[data-testid="library-search"]').setValue('  Calligraphy ')
    vi.advanceTimersByTime(300)
    await flushPromises()

    const empty = () => wrapper.get('.state-panel.is-empty')
    const clear = () => wrapper.get('[data-testid="library-clear"]')
    expect(empty().get('h3').text()).toBe('No activities match “Calligraphy”')
    expect(empty().text()).toContain('Check the spelling, or clear the search to see all activities.')
    expect(clear().text()).toBe('Clear search')
    // New activity is this page's primary action; the filters above stay the way forward.
    expect(clear().classes()).toContain('btn-secondary')
    expect(wrapper.get('[data-testid="new-activity"]').classes()).toContain('btn-primary')

    // With a filter as well, the button clears, and says it clears, the filters too.
    await wrapper.get('#library-type').setValue('TRACING')
    await flushPromises()
    expect(empty().get('h3').text()).toBe('No activities match “Calligraphy”')
    expect(empty().text()).toContain('Try another search or clear the filters.')
    expect(clear().text()).toBe('Clear filters')

    api.practiceSets.mockResolvedValue(pageOf([row]))
    await clear().trigger('click')
    await flushPromises()
    expect(api.practiceSets).toHaveBeenLastCalledWith({ q: '', type: null, status: null, page: 0, size: 10 })
    expect(wrapper.findAll('[data-testid="library-row"]')).toHaveLength(1)
  })

  it('returns to a populated page after restoring the last archived set on the current page', async () => {
    let restored = false
    api.practiceSets.mockImplementation(async ({ page }: { page: number }) => {
      const lastPage = restored ? [] : [archivedRow]
      return pageOf(page === 0 ? [{ ...archivedRow, id: 2 }] : lastPage, page, restored ? 10 : 11)
    })
    api.changePracticeSet.mockImplementation(async () => {
      restored = true
      return draft()
    })
    const wrapper = await mountPage()
    await wrapper.get('#library-status').setValue('ARCHIVED')
    await flushPromises()
    await wrapper.get('nav.pagination button[aria-label="Next"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="library-restore"]').trigger('click')
    await flushPromises()

    expect(api.changePracticeSet).toHaveBeenCalledWith('LISTENING', 4, 'restore')
    expect(api.practiceSets).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, status: 'ARCHIVED' }))
    expect(wrapper.find('[data-testid="library-row"]').exists()).toBe(true)
  })

  it('says no activity matched the filters when there is no search', async () => {
    const wrapper = await mountPage()
    api.practiceSets.mockResolvedValue(pageOf([]))

    await wrapper.get('#library-status').setValue('ARCHIVED')
    await flushPromises()

    expect(wrapper.get('.state-panel.is-empty h3').text()).toBe('No activities match')
    expect(wrapper.get('[data-testid="library-clear"]').text()).toBe('Clear filters')
  })

  it('shows who played a published set, its history, then Archive, Unpublish and Edit (A4.10)', async () => {
    const wrapper = await openFirstSet()

    expect(api.practiceSet).toHaveBeenCalledWith('LISTENING', 4)
    const drawer = wrapper.get('[data-testid="set-details"]')
    expect(drawer.text()).toContain('Practice activity · Listening')
    expect(drawer.text()).toContain('Played by')
    expect(drawer.text()).toContain('12 learners')
    expect(drawer.text()).toContain('Created as a draft')
    expect(drawer.text()).toContain('MandarinSpark team')
    expect(footerButtons(wrapper)).toEqual(['Archive', 'Unpublish', 'Edit'])
    expect(wrapper.get('[data-testid="set-edit"]').classes()).toContain('btn-primary')
    expect(wrapper.get('[data-testid="set-archive"]').classes()).toContain('btn-ghost')
  })

  it('shows a draft with its history only, and publishes it at once; Undo unpublishes it (A4.17, A4.18)', async () => {
    api.practiceSet.mockResolvedValue(draft())
    api.changePracticeSet.mockResolvedValue(detail())
    const wrapper = await openFirstSet()

    const drawer = wrapper.get('[data-testid="set-details"]')
    // Nobody has played a draft: it has no "Played by".
    expect(drawer.text()).not.toContain('Played by')
    expect(drawer.text()).toContain('Created as a draft')
    expect(footerButtons(wrapper)).toEqual(['Edit', 'Publish'])
    expect(wrapper.get('[data-testid="set-edit"]').classes()).toContain('btn-secondary')
    expect(wrapper.get('[data-testid="set-publish"]').classes()).toContain('btn-primary')

    const loads = listLoads()
    await wrapper.get('[data-testid="set-publish"]').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledWith('LISTENING', 4, 'publish')
    // Back on the list, which shows the set's new status.
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
    expect(listLoads()).toBe(loads + 1)
    expect(toast(wrapper).text()).toContain('Ordering food is published. Learners can find it in Practice.')

    api.changePracticeSet.mockResolvedValue(draft())
    await toast(wrapper).get('button').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenLastCalledWith('LISTENING', 4, 'unpublish')
    expect(listLoads()).toBe(loads + 2)
    // The Undo's own toast offers no Undo.
    expect(toast(wrapper).text()).toBe('Unpublished: “Ordering food”.')
  })

  it('archives a published set at once, without a question; Undo puts it back published (A4.14)', async () => {
    api.changePracticeSet.mockResolvedValue(detail({ status: 'ARCHIVED' }))
    const wrapper = await openFirstSet()

    await wrapper.get('[data-testid="set-archive"]').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledWith('LISTENING', 4, 'archive')
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
    expect(toast(wrapper).text()).toContain('Archived: “Ordering food”.')
    expect(toast(wrapper).get('button').text()).toBe('Undo')

    // Not Restore, which would make it a draft: the server puts it back as it was.
    api.changePracticeSet.mockResolvedValue(detail())
    await toast(wrapper).get('button').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenLastCalledWith('LISTENING', 4, 'unarchive')
    expect(toast(wrapper).text()).toBe('Restored: “Ordering food”.')
  })

  it('unpublishes a published set at once; Undo publishes it again (A4.13)', async () => {
    api.changePracticeSet.mockResolvedValue(draft())
    const wrapper = await openFirstSet()

    const loads = listLoads()
    await wrapper.get('[data-testid="set-unpublish"]').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledExactlyOnceWith('LISTENING', 4, 'unpublish')
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
    expect(listLoads()).toBe(loads + 1)
    expect(toast(wrapper).text()).toContain('Unpublished: “Ordering food”.')
    expect(toast(wrapper).get('button').text()).toBe('Undo')

    api.changePracticeSet.mockResolvedValue(detail())
    await toast(wrapper).get('button').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenLastCalledWith('LISTENING', 4, 'publish')
    // The Undo's own toast offers none.
    expect(toast(wrapper).text()).toBe('Ordering food is published. Learners can find it in Practice.')
  })

  it.each([
    ['DRAFT', 'Ordering food is restored as a draft.'],
    ['PUBLISHED', 'Restored: “Ordering food”.'],
  ] as const)(
    'restores an archived set from its row, as the server puts it back (%s); Undo archives it (A4.14, A4.19)',
    async (status, message) => {
      api.practiceSets.mockResolvedValue(pageOf([archivedRow]))
      api.changePracticeSet.mockResolvedValue(detail({ status }))
      const wrapper = await mountPage()

      // An archived row offers Restore in place of Open.
      const restore = wrapper.get('[data-testid="library-row"] button')
      expect(restore.text()).toBe('Restore')
      expect(restore.attributes('aria-label')).toBe('Restore Ordering food')
      const loads = listLoads()
      await restore.trigger('click')
      await flushPromises()
      expect(api.changePracticeSet).toHaveBeenCalledWith('LISTENING', 4, 'restore')
      expect(api.practiceSet).not.toHaveBeenCalled()
      expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
      expect(listLoads()).toBe(loads + 1)
      expect(toast(wrapper).text()).toContain(message)

      api.changePracticeSet.mockResolvedValue(detail({ status: 'ARCHIVED' }))
      await toast(wrapper).get('button').trigger('click')
      await flushPromises()
      expect(api.changePracticeSet).toHaveBeenLastCalledWith('LISTENING', 4, 'archive')
      expect(toast(wrapper).text()).toBe('Archived: “Ordering food”.')
    },
  )

  it('runs one change at a time: until it is done the drawer stays open and its buttons wait', async () => {
    const publication = deferred<AdminPracticeSetDetail>()
    api.practiceSet.mockResolvedValue(draft())
    api.changePracticeSet.mockReturnValue(publication.promise)
    const wrapper = await openFirstSet()

    await wrapper.get('[data-testid="set-publish"]').trigger('click')
    expect(wrapper.get('[data-testid="set-publish"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="set-edit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="set-details"] button[aria-label="Close"]').attributes('disabled')).toBeDefined()

    publication.resolve(detail())
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
  })

  it('says in the open drawer, not under its scrim, when publishing a set fails, and Publish tries again', async () => {
    api.practiceSet.mockResolvedValue(draft())
    api.changePracticeSet.mockRejectedValue(new Error('offline'))
    const wrapper = await openFirstSet()

    await wrapper.get('[data-testid="set-publish"]').trigger('click')
    await flushPromises()
    // In the footer, by the button pressed: always in view, however long the history above it.
    expect(wrapper.get('[data-testid="set-details"] .overlay-footer [role="alert"]').text()).toBe(
      'Your changes could not be saved. Please try again.',
    )
    expect(wrapper.find('.data-notice').exists()).toBe(false)
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)

    api.changePracticeSet.mockResolvedValue(detail())
    await wrapper.get('[data-testid="set-publish"]').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
    expect(toast(wrapper).text()).toContain('Ordering food is published. Learners can find it in Practice.')
  })

  it.each(['archive', 'unpublish'])('says in the open drawer when %s fails', async (action) => {
    api.changePracticeSet.mockRejectedValue(new Error('offline'))
    const wrapper = await openFirstSet()

    await wrapper.get(`[data-testid="set-${action}"]`).trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledWith('LISTENING', 4, action)
    expect(wrapper.get('[data-testid="set-details"] .overlay-footer [role="alert"]').text()).toBe(
      'Your changes could not be saved. Please try again.',
    )
    expect(wrapper.find('.data-notice').exists()).toBe(false)
  })

  it('keeps a failed Restore from the list on the page, where no drawer covers it', async () => {
    api.practiceSets.mockResolvedValue(pageOf([archivedRow]))
    api.changePracticeSet.mockRejectedValue(new Error('offline'))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="library-restore"]').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledWith('LISTENING', 4, 'restore')
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
    expect(wrapper.get('.data-notice').text()).toContain('Your changes could not be saved. Please try again.')
  })

  it('says on the page when an Undo fails', async () => {
    api.changePracticeSet.mockResolvedValue(detail({ status: 'ARCHIVED' }))
    const wrapper = await openFirstSet()
    await wrapper.get('[data-testid="set-archive"]').trigger('click')
    await flushPromises()

    api.changePracticeSet.mockRejectedValue(new Error('offline'))
    await toast(wrapper).get('button').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenLastCalledWith('LISTENING', 4, 'unarchive')
    expect(wrapper.get('.data-notice').text()).toContain('Your changes could not be saved. Please try again.')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it.each([
    [4, 'the drawer'],
    [5, 'the page'],
  ])('shows a restore that fails once set %i was opened meanwhile in %s', async (id, where) => {
    const { wrapper, restoration } = await restoreThenOpen(id)

    restoration.reject(new Error('offline'))
    await flushPromises()
    // The restored set's own drawer shows its failure; another set's drawer does not take it, so the page keeps it.
    const inDrawer = wrapper.find('[data-testid="set-details"] .overlay-footer [role="alert"]')
    const onPage = wrapper.find('.data-notice')
    expect(inDrawer.exists()).toBe(where === 'the drawer')
    expect(onPage.exists()).toBe(where === 'the page')
    expect((where === 'the drawer' ? inDrawer : onPage).text()).toContain(
      'Your changes could not be saved. Please try again.',
    )
  })

  it.each([4, 5])('keeps a drawer opened during a restore open when the restore is done: set %i', async (id) => {
    const { wrapper, restoration } = await restoreThenOpen(id)

    restoration.resolve(detail())
    await flushPromises()
    // The restored set's drawer shows its new status without a redundant refetch; another set's drawer is left as it was.
    expect(wrapper.get('[data-testid="set-details"] h2').text()).toBe(id === 4 ? 'Ordering food' : 'Numbers 1 to 10')
    expect(wrapper.get('[data-testid="set-details"] .status-pill').text()).toBe(id === 4 ? 'Published' : 'Draft')
    expect(api.practiceSet).toHaveBeenCalledTimes(1)
    expect(toast(wrapper).text()).toContain('Restored: “Ordering food”.')
  })

  it('edits a published set from its drawer, says the activity is saved and goes back to its details', async () => {
    const saved = detail({
      title: 'Ordering food at a stall',
      history: [{ action: 'UPDATED', actor: 'Olivia Tan', at: '2026-05-12T03:00:00Z' }, ...detail().history],
    })
    api.savePracticeSet.mockResolvedValue(saved)
    const wrapper = await openFirstSet()
    vi.mocked(contentCounts.reloadContentCounts).mockClear()

    await wrapper.get('[data-testid="set-edit"]').trigger('click')
    expect(wrapper.find('[data-testid="set-details"]').exists()).toBe(false)
    const editor = wrapper.get('[data-testid="set-editor"]')
    expect(editor.text()).toContain('Published · changes go live when you save')
    expect(editor.text()).toContain('Created by MandarinSpark team · played by 12 learners')
    expect(wrapper.get<HTMLInputElement>('[data-testid="editor-title"]').element.value).toBe('Ordering food')

    await wrapper.get('[data-testid="editor-title"]').setValue('Ordering food at a stall')
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    await flushPromises()
    expect(api.savePracticeSet).toHaveBeenCalledWith(
      'LISTENING',
      4,
      expect.objectContaining({ title: 'Ordering food at a stall', stage: 'SCAFFOLDED', characterStrokes: [] }),
    )
    expect(api.savePracticeSet.mock.calls[0]![2]).not.toHaveProperty('xpReward')
    expect(contentCounts.reloadContentCounts).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="set-editor"]').exists()).toBe(false)
    expect(toast(wrapper).text()).toBe('Activity saved.')
    // Back on the set's details, as the save answered: its History has the edit, with nothing loaded again.
    const drawer = wrapper.get('[data-testid="set-details"]')
    expect(drawer.get('h2').text()).toBe('Ordering food at a stall')
    expect(drawer.text()).toContain('Edited')
    expect(api.practiceSet).toHaveBeenCalledTimes(1)
  })

  it('says what saving a draft does, and goes back to the draft’s details after the save', async () => {
    api.practiceSet.mockResolvedValue(draft())
    api.savePracticeSet.mockResolvedValue(draft({ title: 'Ordering food 2' }))
    const wrapper = await openFirstSet()

    await wrapper.get('[data-testid="set-edit"]').trigger('click')
    expect(wrapper.get('[data-testid="set-editor"]').text()).toContain(
      'Draft · learners can’t see it until you publish',
    )
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    await flushPromises()

    expect(toast(wrapper).text()).toBe('Activity saved.')
    expect(wrapper.get('[data-testid="set-details"] h2').text()).toBe('Ordering food 2')
    expect(wrapper.get('[data-testid="set-details"]').text()).toContain('Created as a draft')
  })

  it('creates a flashcard set from the editor as a draft', async () => {
    api.savePracticeSet.mockResolvedValue(detail({ type: 'FLASHCARDS', title: 'Colours', status: 'DRAFT' }))
    const wrapper = await mountPage()
    vi.mocked(contentCounts.reloadContentCounts).mockClear()

    await wrapper.get('[data-testid="new-activity"]').trigger('click')
    await wrapper.get('[data-testid="editor-type"]').setValue('FLASHCARDS')
    await wrapper.get('[data-testid="set-editor"] input.input').setValue('Colours')
    // The stubbed Teleport re-creates its content on every update, so each field is looked up again.
    const itemInputs = () => wrapper.get('[data-testid="editor-item"]').findAll('input')
    await itemInputs()[0]!.setValue('红色')
    await itemInputs()[2]!.setValue('red')
    // A card has an example sentence with its meaning, which the back of the card shows.
    expect(itemInputs()[3]!.attributes('aria-label')).toBe('Example sentence, card 1')
    await itemInputs()[3]!.setValue('我喜欢红色。')
    await itemInputs()[4]!.setValue('I like red.')
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    await flushPromises()

    expect(api.savePracticeSet).toHaveBeenCalledWith(
      'FLASHCARDS',
      null,
      expect.objectContaining({
        title: 'Colours',
        stage: 'GUIDED',
        items: [
          expect.objectContaining({
            hanzi: '红色',
            meaning: 'red',
            example: '我喜欢红色。',
            exampleMeaning: 'I like red.',
          }),
        ],
      }),
    )
    expect(wrapper.text()).toContain('Activity saved.')
    expect(contentCounts.reloadContentCounts).toHaveBeenCalledOnce()
  })

  it.each(['success', 'failure'])('keeps a reopened editor independent of an older save %s', async (outcome) => {
    const save = deferred<AdminPracticeSetDetail>()
    api.savePracticeSet.mockReturnValue(save.promise)
    const wrapper = await saveThenReopenEditor()

    const before = listLoads()
    api.practiceSets.mockResolvedValue(pageOf([{ ...row, title: 'Older activity' }]))
    if (outcome === 'success') save.resolve(detail({ title: 'Older activity' }))
    else save.reject(new Error('The older save failed'))
    await flushPromises()

    expect(listLoads()).toBe(before + (outcome === 'success' ? 1 : 0))
    expect(wrapper.get('[data-testid="library-row"]').text()).toContain(
      outcome === 'success' ? 'Older activity' : row.title,
    )
    expect(wrapper.find('[data-testid="set-editor"]').exists()).toBe(true)
    expect(wrapper.get<HTMLInputElement>('[data-testid="editor-title"]').element.value).toBe('Current activity')
    expect(wrapper.find('[data-testid="set-editor"] [role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="editor-save"]').attributes('disabled')).toBeUndefined()
  })

  it('does not release a new save when a closed editor finishes saving', async () => {
    const oldSave = deferred<AdminPracticeSetDetail>()
    const newSave = deferred<AdminPracticeSetDetail>()
    api.savePracticeSet.mockReturnValueOnce(oldSave.promise).mockReturnValueOnce(newSave.promise)
    const wrapper = await saveThenReopenEditor()
    expect(wrapper.get('[data-testid="editor-save"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    oldSave.resolve(detail({ title: 'Older activity' }))
    await flushPromises()

    expect(wrapper.get('[data-testid="editor-save"]').attributes('disabled')).toBeDefined()
    newSave.resolve(detail({ title: 'Current activity' }))
    await flushPromises()
    expect(wrapper.find('[data-testid="set-editor"]').exists()).toBe(false)
  })

  it.each(['success', 'failure'])(
    'reports a closed editor save %s on the page when no new editor is open',
    async (outcome) => {
      const save = deferred<AdminPracticeSetDetail>()
      api.savePracticeSet.mockReturnValue(save.promise)
      const wrapper = await mountPage()
      await nameNewActivity(wrapper, 'Closed mid-save')
      await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
      await wrapper.get('[data-testid="set-editor"] button[aria-label="Close"]').trigger('click')
      expect(wrapper.find('[data-testid="set-editor"]').exists()).toBe(false)

      if (outcome === 'success') save.resolve(detail({ title: 'Closed mid-save' }))
      else save.reject(new Error('offline'))
      await flushPromises()

      if (outcome === 'success') {
        expect(toast(wrapper).text()).toBe('Activity saved.')
        expect(wrapper.find('.data-notice').exists()).toBe(false)
      } else {
        expect(wrapper.get('.data-notice').text()).toContain('Your changes could not be saved. Please try again.')
      }
    },
  )

  it('submits a pending editor form only once', async () => {
    const save = deferred<AdminPracticeSetDetail>()
    api.savePracticeSet.mockReturnValue(save.promise)
    const wrapper = await mountPage()
    await nameNewActivity(wrapper, 'One activity')
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    expect(api.savePracticeSet).toHaveBeenCalledTimes(1)
    save.resolve(detail({ title: 'One activity' }))
    await flushPromises()
  })

  it('shows a dedicated empty-library state without asking to clear filters when the library has no rows', async () => {
    api.practiceSets.mockResolvedValueOnce(pageOf([], 0, 0, 10))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No practice activities yet')
    expect(wrapper.text()).toContain('Create an activity to start building the practice library.')
    expect(wrapper.find('[data-testid="library-clear"]').exists()).toBe(false)
  })
})
