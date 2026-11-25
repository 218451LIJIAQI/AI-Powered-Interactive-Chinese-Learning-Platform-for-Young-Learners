import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { ClassCard, StudentClass } from '@/types/classes'

const api = vi.hoisted(() => ({
  classPage: vi.fn(),
  join: vi.fn(),
  leave: vi.fn(),
}))
const queueClassNotice = vi.hoisted(() => vi.fn())
const clearClassNotice = vi.hoisted(() => vi.fn())

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))
vi.mock('@/composables/useClassNotice', () => ({ queueClassNotice, clearClassNotice }))

import StudentClassPage from './StudentClassPage.vue'

const card: ClassCard = {
  id: 7,
  title: 'Mandarin for Campus Life',
  description: 'Campus scenarios for exchange students.',
  topicName: 'Study or exchange',
  bestAfterUnit: 3,
  lessonCount: 2,
}

function preview(): StudentClass {
  return {
    joined: false,
    archived: false,
    archivedAt: null,
    summary: card,
    preview: [{ id: 1, title: 'First Conversations', lessons: [{ id: 11, title: 'Meeting Classmates' }] }],
    completedLessons: 0,
    nextStep: null,
    units: [],
  }
}

function joined(): StudentClass {
  return {
    ...preview(),
    joined: true,
    completedLessons: 1,
    nextStep: {
      lessonId: 12,
      lessonTitle: 'Room Numbers',
      lessonNumber: 2,
      stepsDone: 3,
      stepsTotal: 4,
    },
    units: [
      {
        id: 1,
        title: 'First Conversations',
        lessonCount: 2,
        completedCount: 1,
        lessons: [
          {
            id: 11,
            title: 'Meeting Classmates',
            status: 'COMPLETED',
            available: true,
          },
          {
            id: 12,
            title: 'Room Numbers',
            status: 'IN_PROGRESS',
            available: true,
          },
        ],
      },
    ],
  }
}

function mountPage() {
  return mountAtRoute(StudentClassPage, '/student/classes/7')
}

describe('StudentClassPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    queueClassNotice.mockReset()
    clearClassNotice.mockReset()
    api.classPage.mockResolvedValue(preview())
  })

  it('previews a class not joined yet with its lessons locked until joining', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Mandarin for Campus Life')
    // The way back is to Find classes, where the class was found.
    const back = wrapper.get('.breadcrumb a')
    expect(back.text()).toBe('Find classes')
    expect(back.attributes('href')).toBe('/student/classes/find')
    expect(wrapper.get('.class-avatar').text()).toBe('MC')
    expect(wrapper.get('.class-page-header .page-title-copy p').text()).toBe('Study or exchange')
    expect(wrapper.text()).toContain('Campus scenarios for exchange students.')
    expect(wrapper.get('[data-testid="class-preview-facts"]').text()).toBe(
      'Free · best after Unit 3 of Learn · join to open the lessons',
    )
    expect(wrapper.get('.student-class-locked').text()).toBe('Meeting Classmates')
    expect(wrapper.find('.student-class-locked a').exists()).toBe(false)
    expect(wrapper.find('[data-testid="leave-class"]').exists()).toBe(false)
  })

  it('joins, then lands in My classes with a notice whose Undo can leave the class again', async () => {
    api.join.mockResolvedValue(undefined)
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="join-class"]').trigger('click')
    await flushPromises()

    expect(api.join).toHaveBeenCalledWith(7)
    expect(queueClassNotice).toHaveBeenCalledWith({
      message: 'Mandarin for Campus Life is in My classes.',
      targetRoute: 'student-classes',
      joinedClassId: 7,
      joinedClassTitle: 'Mandarin for Campus Life',
    })
    expect(router.currentRoute.value.name).toBe('student-classes')
  })

  it('shows the error and stays when joining fails', async () => {
    api.join.mockRejectedValue(new Error('nope'))
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="join-class"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You could not join this class.')
    expect(router.currentRoute.value.name).toBe('student-class')
  })

  it.each(['success', 'failure'] as const)('ignores an old join %s while joining another class', async (outcome) => {
    const first = deferred<void>()
    const second = deferred<void>()
    api.classPage.mockImplementation(async (id: number) => ({
      ...preview(),
      summary: { ...card, id, title: `Class ${id}` },
    }))
    api.join.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { wrapper, router } = await mountPage()
    await wrapper.get('[data-testid="join-class"]').trigger('click')
    await router.push('/student/classes/8')
    await flushPromises()
    const button = wrapper.get<HTMLButtonElement>('[data-testid="join-class"]')
    expect(button.element.disabled).toBe(false)
    await button.trigger('click')
    expect(api.join.mock.calls).toEqual([[7], [8]])

    if (outcome === 'success') first.resolve()
    else first.reject(new Error('Old class join failed'))
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/student/classes/8')
    expect(wrapper.text()).not.toContain('You could not join this class.')
    expect(button.element.disabled).toBe(true)
    expect(queueClassNotice).not.toHaveBeenCalled()

    second.resolve()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-classes')
    expect(queueClassNotice).toHaveBeenCalledWith(expect.objectContaining({ joinedClassId: 8 }))
    wrapper.unmount()
  })

  it('does not navigate or leave a notice when a join finishes after unmount', async () => {
    const request = deferred<void>()
    api.join.mockReturnValue(request.promise)
    const { wrapper, router } = await mountPage()
    await wrapper.get('[data-testid="join-class"]').trigger('click')
    wrapper.unmount()
    await router.push('/student/classes/find')
    request.resolve()
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/student/classes/find')
    expect(queueClassNotice).not.toHaveBeenCalled()
  })

  it('sends only one join when clicks arrive before the button is disabled', async () => {
    const request = deferred<void>()
    api.join.mockReturnValue(request.promise)
    const { wrapper } = await mountPage()
    const button = wrapper.get<HTMLButtonElement>('[data-testid="join-class"]').element
    button.click()
    button.click()
    expect(api.join).toHaveBeenCalledTimes(1)
    request.resolve()
    await flushPromises()
    wrapper.unmount()
  })

  it('shows the next step and each lesson with its status, made by the team (no teacher anywhere)', async () => {
    api.classPage.mockResolvedValue(joined())
    const { wrapper } = await mountPage()

    expect(wrapper.get('.class-page-header .page-title-copy p').text()).toBe('1 of 2 lessons done')
    expect(wrapper.get('[data-testid="class-next"]').text()).toContain('Room Numbers · 3 of 4 steps done')
    expect(wrapper.get('[data-testid="continue-class"]').text()).toBe('Continue: Room Numbers')
    expect(wrapper.get('[data-testid="continue-class"]').attributes('href')).toBe('/student/classes/7/lessons/12')
    expect(wrapper.text()).toContain('1 of 2 done')
    const rows = wrapper.findAll('a.student-class-lesson')
    expect(rows.map((row) => row.attributes('title'))).toEqual(['Open Meeting Classmates', 'Open Room Numbers'])
    expect(rows[0]!.text()).toContain('Completed')
    expect(rows[1]!.text()).toContain('In progress')
    expect(wrapper.text()).not.toMatch(/teacher|Li Wei|live session/i)
  })

  it('offers to start the next lesson when it is not begun yet, from the real progress', async () => {
    const current = joined()
    current.units[0]!.lessons[1] = { ...current.units[0]!.lessons[1]!, status: 'NOT_STARTED' }
    current.nextStep = { ...current.nextStep!, stepsDone: 0 }
    api.classPage.mockResolvedValue(current)
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="class-next"]').text()).toContain('Room Numbers · not started yet')
    expect(wrapper.get('[data-testid="continue-class"]').text()).toBe('Start: Room Numbers')
  })

  it('asks before leaving, and leaving ends in My classes with a notice without Undo', async () => {
    api.classPage.mockResolvedValue(joined())
    api.leave.mockResolvedValue(undefined)
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="leave-class"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave Mandarin for Campus Life?')
    expect(dialog.text()).toContain('Your XP, rank and streak stay')
    expect(dialog.text()).toContain('If you join again, your finished lessons and quiz scores come back.')

    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(api.leave).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="leave-class"]').trigger('click')
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Stay in class')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.leave).toHaveBeenCalledWith(7)
    expect(queueClassNotice).toHaveBeenCalledWith({
      message: 'You left Mandarin for Campus Life.',
      targetRoute: 'student-classes',
    })
    expect(router.currentRoute.value.name).toBe('student-classes')
  })

  it('keeps the dialog open with the reason when leaving fails', async () => {
    api.classPage.mockResolvedValue(joined())
    api.leave.mockRejectedValue(new Error('offline'))
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="leave-class"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('You could not leave this class. Try again.')
    expect(router.currentRoute.value.name).toBe('student-class')
    expect(queueClassNotice).not.toHaveBeenCalled()
  })

  it('shows an archived class read-only: the banner with its date, no next step and no Leave', async () => {
    api.classPage.mockResolvedValue({
      ...joined(),
      archived: true,
      archivedAt: '2026-05-02T09:00:00',
      completedLessons: 2,
      nextStep: null,
    })
    const { wrapper } = await mountPage()

    expect(wrapper.get('.class-page-header .page-title-copy p').text()).toBe('Archived · 2 of 2 lessons done')
    expect(wrapper.get('[data-testid="class-archived"]').text()).toBe(
      'This class was archived on 2 May. You can still open its lessons and your results.',
    )
    expect(wrapper.text()).not.toContain('spring term')
    expect(wrapper.find('[data-testid="class-next"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="leave-class"]').exists()).toBe(false)
    expect(wrapper.findAll('a.student-class-lesson')).toHaveLength(2)
  })

  it('does not claim all lessons are finished when a joined class has zero lessons', async () => {
    api.classPage.mockResolvedValue({
      ...joined(),
      summary: { ...card, lessonCount: 0 },
      completedLessons: 0,
      nextStep: null,
      units: [],
    })
    const { wrapper } = await mountPage()

    expect(wrapper.find('[data-testid="class-next"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('You finished every lesson of this class.')
  })
})
