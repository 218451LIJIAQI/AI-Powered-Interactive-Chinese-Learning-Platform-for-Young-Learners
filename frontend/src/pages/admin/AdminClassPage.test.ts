import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { buttonNamed, deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { AdminClassPage as ClassPage, AdminUnit } from '@/types/adminClasses'

const api = vi.hoisted(() => ({
  classPage: vi.fn(),
  saveDetails: vi.fn(),
  publish: vi.fn(),
  archive: vi.fn(),
  restore: vi.fn(),
  remove: vi.fn(),
  addUnit: vi.fn(),
  addLesson: vi.fn(),
  topics: vi.fn(),
  path: vi.fn(),
}))

vi.mock('@/api/adminClasses', () => ({ adminClassesApi: api }))
vi.mock('@/api/classes', () => ({ publicClassesApi: { topics: api.topics } }))
vi.mock('@/api/learn', () => ({ adminLearningApi: { path: api.path } }))

import AdminClassPage from './AdminClassPage.vue'

const PUBLISHED_UNITS: AdminUnit[] = [
  {
    id: 70,
    title: 'First Conversations',
    lessons: [
      { id: 11, title: 'Meeting Classmates', status: 'PUBLISHED', hasUnpublishedChanges: false },
      { id: 12, title: 'Room Numbers', status: 'PUBLISHED', hasUnpublishedChanges: false },
      { id: 13, title: 'University Life', status: 'PUBLISHED', hasUnpublishedChanges: false },
    ],
  },
  {
    id: 71,
    title: 'Campus Life',
    lessons: [
      { id: 14, title: 'Ordering Food at the Canteen', status: 'PUBLISHED', hasUnpublishedChanges: false },
      { id: 15, title: 'Finding Places', status: 'DRAFT', hasUnpublishedChanges: false },
    ],
  },
]

function classPage(id: number, overrides: Partial<ClassPage> = {}): ClassPage {
  return {
    id,
    title: id === 5 ? 'Mandarin for Campus Life' : `Class ${id}`,
    description: 'Campus scenarios for exchange students.',
    category: { id: 2, name: 'Study or exchange' },
    bestAfterUnit: 3,
    status: 'PUBLISHED',
    lessonCount: 5,
    units: PUBLISHED_UNITS,
    canPublish: false,
    ...overrides,
  }
}

function draft(overrides: Partial<ClassPage> = {}): ClassPage {
  return classPage(5, {
    title: 'Hawker Food Mandarin',
    description: null,
    category: { id: 1, name: 'Daily life' },
    bestAfterUnit: null,
    status: 'DRAFT',
    lessonCount: 0,
    units: [],
    ...overrides,
  })
}

function mountPage(path = '/admin/content/classes/5') {
  return mountAtRoute(AdminClassPage, path)
}

const confirmDialog = (wrapper: VueWrapper) => wrapper.get('[data-testid="confirm-dialog"]')

describe('AdminClassPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.classPage.mockImplementation(async (id: number) => classPage(id))
    api.topics.mockResolvedValue([
      { id: 1, name: 'Daily life' },
      { id: 2, name: 'Study or exchange' },
    ])
    api.path.mockResolvedValue({ unitCount: 0, levels: [] })
  })

  it('shows a draft class learners can’t see yet, with Publish held back until the class has its shape (A3.05)', async () => {
    api.classPage.mockResolvedValue(draft())
    const { wrapper } = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Hawker Food Mandarin')
    expect(wrapper.get('.page-header').text()).toContain('Draft')
    expect(wrapper.get('.page-header p').text()).toBe('Daily life')
    const notice = wrapper.get('[data-testid="class-notice"]')
    expect(notice.text()).toBe(
      'Learners can’t see this class yet. To publish it, add 2 units with 4–6 published lessons.',
    )
    const publish = wrapper.get<HTMLButtonElement>('[data-testid="publish-class"]')
    expect(publish.element.disabled).toBe(true)
    expect(publish.attributes('aria-describedby')).toBe(notice.attributes('id'))
    expect(wrapper.get('[data-testid="delete-class"]').classes()).toContain('btn-ghost')
    expect(wrapper.find('[data-testid="archive-class"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Add your first unit')
  })

  it('says in the header that a draft has no theme yet (A3.47)', async () => {
    api.classPage.mockResolvedValue(draft({ category: null }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('.page-header p').text()).toBe('No theme yet')
  })

  it('publishes a draft class once it has its shape, after a checklist (A3.33 format)', async () => {
    api.classPage.mockResolvedValue(draft({ units: PUBLISHED_UNITS, lessonCount: 5, canPublish: true }))
    api.publish.mockResolvedValue(classPage(5, { title: 'Hawker Food Mandarin', status: 'PUBLISHED' }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="class-notice"]').text()).toBe('Learners can’t see this class yet.')
    await wrapper.get('[data-testid="publish-class"]').trigger('click')
    const dialog = wrapper.get('[data-testid="publish-class-dialog"]')
    expect(dialog.get('h2').text()).toBe('Publish “Hawker Food Mandarin”?')
    expect(dialog.findAll('.authoring-checklist li').map((row) => row.text())).toEqual([
      '2 units',
      '4 published lessons',
      '1 draft lesson stays hidden until you publish it',
    ])
    await dialog.get('[data-testid="publish-class-confirm"]').trigger('click')
    await flushPromises()

    expect(api.publish).toHaveBeenCalledWith(5)
    expect(wrapper.find('[data-testid="publish-class-dialog"]').exists()).toBe(false)
    expect(wrapper.get('.page-header').text()).toContain('Published')
    expect(wrapper.find('[data-testid="class-notice"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe(
      '“Hawker Food Mandarin” is published. Learners can join it now.',
    )
  })

  it('keeps the publish dialog open with the reason when publishing is refused', async () => {
    api.classPage.mockResolvedValue(draft({ units: PUBLISHED_UNITS, canPublish: true }))
    api.publish.mockRejectedValue({
      response: {
        status: 400,
        data: { message: 'To publish a class, give it 2 units with 4 to 6 published lessons.' },
      },
    })
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="publish-class"]').trigger('click')
    await wrapper.get('[data-testid="publish-class-confirm"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="publish-class-dialog"] [role="alert"]').text()).toBe(
      'To publish a class, give it 2 units with 4 to 6 published lessons.',
    )
    expect(wrapper.get('.page-header').text()).toContain('Draft')
  })

  it('saves dirty details before publishing, validates empty title, and aborts publish on saveDetails failure', async () => {
    const readyDraft = draft({ units: PUBLISHED_UNITS, lessonCount: 5, canPublish: true })
    api.classPage.mockResolvedValue(readyDraft)
    const { wrapper } = await mountPage('/admin/content/classes/5/details')

    // Dirty empty title fails validation inside the publish dialog without calling either API.
    await wrapper.get('[data-testid="class-title"]').setValue('   ')
    await wrapper.get('[data-testid="publish-class"]').trigger('click')
    await wrapper.get('[data-testid="publish-class-confirm"]').trigger('click')
    await flushPromises()
    expect(api.saveDetails).not.toHaveBeenCalled()
    expect(api.publish).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="publish-class-dialog"] [role="alert"]').text()).toBeTruthy()

    // saveDetails rejection aborts publish and keeps the dialog open with the error.
    await wrapper.get('[data-testid="publish-class-dialog"] .btn-secondary').trigger('click')
    await wrapper.get('[data-testid="class-title"]').setValue('Updated Hawker Mandarin')
    api.saveDetails.mockRejectedValueOnce(new Error('save failed'))
    await wrapper.get('[data-testid="publish-class"]').trigger('click')
    await wrapper.get('[data-testid="publish-class-confirm"]').trigger('click')
    await flushPromises()
    expect(api.saveDetails).toHaveBeenCalledTimes(1)
    expect(api.publish).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="publish-class-dialog"] [role="alert"]').exists()).toBe(true)

    // Valid dirty details save first and publish with the updated title in the toast.
    const saved = { ...readyDraft, title: 'Updated Hawker Mandarin' }
    api.saveDetails.mockResolvedValueOnce(saved)
    api.publish.mockResolvedValueOnce({ ...saved, status: 'PUBLISHED' })
    await wrapper.get('[data-testid="publish-class-confirm"]').trigger('click')
    await flushPromises()
    expect(api.saveDetails).toHaveBeenCalledTimes(2)
    expect(api.publish).toHaveBeenCalledWith(5)
    expect(wrapper.get('[data-testid="toast"]').text()).toContain('Updated Hawker Mandarin')
  })

  it('deletes a draft class after asking, and goes back to the list without it (A3.09)', async () => {
    api.classPage.mockResolvedValue(draft())
    api.remove.mockResolvedValue(null)
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="delete-class"]').trigger('click')
    expect(confirmDialog(wrapper).get('h2').text()).toBe('Delete “Hawker Food Mandarin”?')
    expect(confirmDialog(wrapper).text()).toContain('The draft class is removed. You can’t undo this.')
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Keep class')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.remove).toHaveBeenCalledWith(5)
    expect(router.currentRoute.value.path).toBe('/admin/content')
    expect(router.currentRoute.value.query).toEqual({ deleted: 'Hawker Food Mandarin' })
  })

  it.each([
    [
      [{ id: 72, title: 'At the Stall', lessons: [] }],
      'The draft class and its unit are removed. You can’t undo this.',
    ],
    [
      [
        {
          id: 72,
          title: 'At the Stall',
          lessons: [
            { id: 21, title: 'Ordering', status: 'DRAFT', hasUnpublishedChanges: false },
            { id: 22, title: 'Paying', status: 'DRAFT', hasUnpublishedChanges: false },
          ],
        },
      ],
      'The draft class, its unit and 2 draft lessons are removed. You can’t undo this.',
    ],
    [PUBLISHED_UNITS, 'The draft class, its 2 units and 5 lessons are removed. You can’t undo this.'],
  ] as [AdminUnit[], string][])('counts what deleting a draft class removes (A3.09)', async (units, message) => {
    api.classPage.mockResolvedValue(draft({ units }))
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="delete-class"]').trigger('click')
    expect(confirmDialog(wrapper).text()).toContain(message)
  })

  it('archives a published class after saying what happens to its learners (A3.12), then shows it read-only (A3.13)', async () => {
    api.archive.mockResolvedValue(classPage(5, { status: 'ARCHIVED' }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('.page-header p').text()).toBe('Study or exchange · 5 lessons')
    expect(wrapper.find('[data-testid="class-notice"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="archive-class"]').classes()).toContain('btn-ghost')
    await wrapper.get('[data-testid="archive-class"]').trigger('click')
    expect(confirmDialog(wrapper).get('h2').text()).toBe('Archive “Mandarin for Campus Life”?')
    expect(confirmDialog(wrapper).text()).toContain(
      'New learners can’t join it. Learners who already joined keep its lessons and their results. You can restore it later.',
    )
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.archive).toHaveBeenCalledWith(5)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Class archived.')
    expect(wrapper.get('.page-header').text()).toContain('Archived')
    expect(wrapper.get('[data-testid="class-notice"]').text()).toBe(
      'Archived. New learners can’t join it. Learners who joined keep its lessons and results. Restore it to edit.',
    )
    expect(wrapper.find('[data-testid="restore-class"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="add-unit"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="class-lesson"] a').exists()).toBe(false)
  })

  it('restores an archived class at once, and says so (X1.14)', async () => {
    api.classPage.mockResolvedValue(classPage(5, { status: 'ARCHIVED' }))
    api.restore.mockResolvedValue(classPage(5))
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="restore-class"]').trigger('click')
    await flushPromises()

    expect(api.restore).toHaveBeenCalledWith(5)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('“Mandarin for Campus Life” is restored and published.')
    expect(wrapper.find('[data-testid="archive-class"]').exists()).toBe(true)
  })

  it('says when a class archived as a draft comes back as one', async () => {
    api.classPage.mockResolvedValue(classPage(5, { status: 'ARCHIVED' }))
    api.restore.mockResolvedValue(classPage(5, { status: 'DRAFT' }))
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="restore-class"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('“Mandarin for Campus Life” is restored as a draft.')
  })

  it('shows the details of an archived class without a way to change them', async () => {
    api.classPage.mockResolvedValue(classPage(5, { status: 'ARCHIVED' }))
    const { wrapper } = await mountPage('/admin/content/classes/5/details')

    expect(wrapper.get<HTMLInputElement>('[data-testid="class-title"]').element.disabled).toBe(true)
    expect(wrapper.find('[data-testid="save-details"]').exists()).toBe(false)
  })

  it('saves the details at once, also on a published class (X1.01)', async () => {
    api.saveDetails.mockImplementation(async (id: number) => classPage(id, { description: 'Words for campus life.' }))
    const { wrapper } = await mountPage('/admin/content/classes/5/details')
    await flushPromises()

    expect(wrapper.get('h2.card-title').text()).toBe('What learners see in the class catalogue')
    const save = () => wrapper.get<HTMLButtonElement>('[data-testid="save-details"]')
    expect(save().element.disabled).toBe(true)
    await wrapper.get('form textarea').setValue('  Words for campus life. ')
    expect(save().element.disabled).toBe(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.saveDetails).toHaveBeenCalledExactlyOnceWith(5, {
      title: 'Mandarin for Campus Life',
      description: 'Words for campus life.',
      categoryId: 2,
      bestAfterUnit: 3,
    })
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Changes saved.')
    expect(save().element.disabled).toBe(true)
  })

  it('asks for a title when it is emptied, and saves nothing until it has one', async () => {
    const { wrapper } = await mountPage('/admin/content/classes/5/details')

    await wrapper.get('[data-testid="class-title"]').setValue('  ')
    await wrapper.get('[data-testid="class-title"]').trigger('blur')
    expect(wrapper.get('form [role="alert"]').text()).toBe('Add a class title.')
    expect(wrapper.get<HTMLButtonElement>('[data-testid="save-details"]').element.disabled).toBe(true)
    await wrapper.get('form').trigger('submit')
    expect(api.saveDetails).not.toHaveBeenCalled()
  })

  it('asks before leaving the class with unsaved details, and leaves only when told (X2.09)', async () => {
    const { wrapper, router } = await mountPage('/admin/content/classes/5/details')
    await wrapper.get('form textarea').setValue('Words for campus life.')

    // The Lessons tab keeps the form: no question.
    await router.push('/admin/content/classes/5')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)

    await router.push('/admin/content')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5')
    expect(confirmDialog(wrapper).text()).toContain('Leave without saving?')
    expect(confirmDialog(wrapper).text()).toContain(
      'This class has changes you haven’t saved. Keep editing to save them, or leave and lose them.',
    )
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Keep editing')
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').text()).toBe('Leave without saving')
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5')

    await router.push('/admin/content')
    await flushPromises()
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content')
    expect(api.saveDetails).not.toHaveBeenCalled()
  })

  it('keeps the details typed on the Details tab when a unit is added on the Lessons tab', async () => {
    api.classPage.mockResolvedValue(draft())
    api.addUnit.mockResolvedValue(draft({ units: [{ id: 72, title: 'At the Hawker Stall', lessons: [] }] }))
    const { wrapper, router } = await mountPage('/admin/content/classes/5/details')
    await wrapper.get('form textarea').setValue('Order at hawker stalls.')

    await router.push('/admin/content/classes/5')
    await flushPromises()
    await wrapper.get('[data-testid="add-first-unit"]').trigger('click')
    await wrapper.get('[data-testid="unit-dialog"] input').setValue('At the Hawker Stall')
    await wrapper.get('[data-testid="unit-dialog"] form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Unit added.')
    expect(wrapper.get('[data-testid="class-unit"]').text()).toContain('At the Hawker Stall')

    await router.push('/admin/content/classes/5/details')
    await flushPromises()
    expect(wrapper.get<HTMLTextAreaElement>('form textarea').element.value).toBe('Order at hawker stalls.')
  })

  it('adds a lesson and opens it in the editor, saying it was just created', async () => {
    api.addLesson.mockResolvedValue({ lessonId: 21 })
    const { wrapper, router } = await mountPage()

    await wrapper.get('button[aria-label="Add lesson to Campus Life"]').trigger('click')
    const dialog = wrapper.get('[data-testid="unit-dialog"]')
    expect(dialog.get('h2').text()).toBe('Add a lesson')
    await dialog.get('input').setValue('Finding Places')
    await dialog.get('form').trigger('submit')
    await flushPromises()

    expect(api.addLesson).toHaveBeenCalledWith(71, 'Finding Places')
    expect(router.currentRoute.value.fullPath).toBe('/admin/content/classes/5/lessons/21?created=1')
  })

  it('shows the created lesson when unsaved class details keep the editor from opening', async () => {
    const unit: AdminUnit = { id: 72, title: 'At the Hawker Stall', lessons: [] }
    const added = { id: 90, title: 'Ordering lunch', status: 'DRAFT', hasUnpublishedChanges: false } as const
    api.classPage.mockResolvedValueOnce(draft({ units: [unit] }))
    api.classPage.mockResolvedValue(draft({ units: [{ ...unit, lessons: [added] }], lessonCount: 1 }))
    api.addLesson.mockResolvedValue({ lessonId: added.id })
    const { wrapper, router } = await mountPage('/admin/content/classes/5/details')
    await wrapper.get('form textarea').setValue('Order at hawker stalls.')
    await router.push('/admin/content/classes/5')
    await flushPromises()
    await buttonNamed(wrapper, 'Add lesson').trigger('click')
    const dialog = wrapper.get('[data-testid="unit-dialog"]')
    await dialog.get('input').setValue(added.title)
    await dialog.get('form').trigger('submit')
    await flushPromises()

    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.get('[data-testid="class-lesson"] a').attributes('href')).toBe('/admin/content/classes/5/lessons/90')
    expect(api.addLesson).toHaveBeenCalledTimes(1)
    await router.push('/admin/content/classes/5/details')
    await flushPromises()
    expect(wrapper.get<HTMLTextAreaElement>('form textarea').element.value).toBe('Order at hawker stalls.')
  })

  it('shows only the class still open when a save answers after another class was opened', async () => {
    const saved = deferred<ClassPage>()
    api.saveDetails.mockReturnValueOnce(saved.promise)
    const { wrapper, router } = await mountPage('/admin/content/classes/5/details')
    await wrapper.get('form textarea').setValue('Words for campus life.')
    await wrapper.get('form').trigger('submit')

    // The save is out, so leaving does not ask.
    await router.push('/admin/content/classes/6/details')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Class 6')
    saved.resolve(classPage(5, { description: 'Words for campus life.' }))
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Class 6')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('offers a retry and the way back to Classes when the class cannot be loaded', async () => {
    api.classPage.mockRejectedValueOnce({ response: { status: 404, data: { message: 'Class not found.' } } })
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('This class could not be loaded')
    expect(wrapper.get('a.btn-primary').attributes('href')).toBe('/admin/content')
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Mandarin for Campus Life')
  })
})
