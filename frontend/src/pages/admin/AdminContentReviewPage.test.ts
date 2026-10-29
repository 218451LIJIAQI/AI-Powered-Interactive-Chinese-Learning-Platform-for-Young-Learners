import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { ReviewDetail, ReviewQueue, ReviewQueueItem } from '@/types/classes'

const api = vi.hoisted(() => ({
  queue: vi.fn(),
  classReview: vi.fn(),
  lessonReview: vi.fn(),
  approve: vi.fn(),
  requestChanges: vi.fn(),
}))

vi.mock('@/api/classes', () => ({ adminClassesApi: api }))
const navCounts = vi.hoisted(() => vi.fn())

vi.mock('@/api/shell', () => ({ shellApi: { navCounts } }))

import AdminContentReviewPage from './AdminContentReviewPage.vue'

const newClass: ReviewQueueItem = {
  kind: 'NEW_CLASS',
  type: 'CLASS',
  classId: 4,
  lessonId: null,
  title: 'Chinese Culture and Festivals',
  classTitle: 'Chinese Culture and Festivals',
  teacherName: 'Li Wei',
  submittedAt: '2026-05-18T10:00:00',
  lessonTitles: ['Lantern Festival'],
  changesQuiz: false,
}

const revision: ReviewQueueItem = {
  kind: 'LESSON_REVISION',
  type: 'LESSON',
  classId: 7,
  lessonId: 21,
  title: 'Ordering Food at the Canteen',
  classTitle: 'Mandarin for Beginners',
  teacherName: 'Li Wei',
  submittedAt: '2026-05-18T11:00:00',
  lessonTitles: [],
  changesQuiz: true,
}

const queue: ReviewQueue = { items: [newClass, revision], total: 2 }

const classDetail: ReviewDetail = {
  item: newClass,
  classPreview: {
    title: 'Chinese Culture and Festivals',
    description: 'Festivals through the year.',
    levelName: 'Beginner 2',
    topicName: 'Culture',
    learningType: 'RECORDED',
    teacherName: 'Li Wei',
    unitCount: 1,
    lessonCount: 1,
    lessons: [{ id: 30, title: 'Lantern Festival', unitTitle: 'Festival Stories' }],
  },
  lessons: [],
  changes: [],
  notInReview: ['Festival Greetings'],
  history: [],
}

async function mountPage() {
  const router = await classesTestRouter('/admin/content')
  const wrapper = mount(AdminContentReviewPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('AdminContentReviewPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    navCounts.mockReset().mockResolvedValue({ reviewQueue: 9 })
    api.queue.mockResolvedValue(queue)
    api.classReview.mockResolvedValue(classDetail)
  })

  it('opens the oldest item with the class as students will see it', async () => {
    const wrapper = await mountPage()

    expect(api.queue).toHaveBeenCalledWith('ALL')
    expect(api.classReview).toHaveBeenCalledWith(4)
    expect(wrapper.text()).toContain('Preview as a student')
    expect(wrapper.text()).toContain('Lantern Festival')
    expect(wrapper.text()).toContain('Not in this review: Festival Greetings.')
    // The review tab counts the queue on the page, not the sidebar's nav count.
    expect(wrapper.get('[data-testid="content-tab-review"] .route-tab-count').text()).toBe('2')
    expect(navCounts).not.toHaveBeenCalled()
  })

  it('needs a reason to request changes and sends it to the teacher', async () => {
    api.requestChanges.mockResolvedValue({ items: [revision], total: 1 })
    api.queue.mockResolvedValueOnce(queue).mockResolvedValue({ items: [revision], total: 1 })
    api.lessonReview.mockResolvedValue({ ...classDetail, item: revision, classPreview: null, notInReview: [] })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="request-changes"]').trigger('click')
    const dialog = wrapper.get('[data-testid="changes-dialog"]')
    expect(dialog.get('[data-testid="changes-dialog-confirm"]').attributes('disabled')).toBeDefined()

    await dialog.get('[data-testid="review-reason"]').setValue('Add the meaning of 灯笼.')
    await wrapper.get('[data-testid="changes-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.requestChanges).toHaveBeenCalledWith('CLASS', 4, 'Add the meaning of 灯笼.')
    expect(wrapper.text()).toContain('Your reason was sent to Li Wei.')
    // The next waiting item opens.
    expect(api.lessonReview).toHaveBeenCalledWith(21)
  })

  it('approves after confirming what goes live', async () => {
    api.approve.mockResolvedValue({ items: [], total: 0 })
    api.queue.mockResolvedValueOnce(queue).mockResolvedValue({ items: [], total: 0 })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="approve"]').trigger('click')
    expect(wrapper.get('[data-testid="approve-dialog"]').text()).toContain('go live in the catalogue now')
    await wrapper.get('[data-testid="approve-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.approve).toHaveBeenCalledWith('CLASS', 4)
    expect(wrapper.text()).toContain('All caught up')
    expect(wrapper.find('[data-testid="content-tab-review"] .route-tab-count').exists()).toBe(false)
  })
})
