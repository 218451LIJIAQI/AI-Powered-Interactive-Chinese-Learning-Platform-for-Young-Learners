import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { clearClassNotice, queueClassNotice } from '@/composables/useClassNotice'
import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { ClassCard, MyClassCard, MyClasses } from '@/types/classes'

const api = vi.hoisted(() => ({ myClasses: vi.fn(), leave: vi.fn() }))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))

import StudentClassesPage from './StudentClassesPage.vue'

const campus: ClassCard = {
  id: 7,
  title: 'Mandarin for Campus Life',
  description: null,
  topicName: 'Study or exchange',
  bestAfterUnit: 3,
  lessonCount: 5,
}

function entry(overrides: Partial<MyClassCard> = {}): MyClassCard {
  return {
    summary: campus,
    completedLessons: 2,
    progressPercent: 40,
    lessons: [
      {
        id: 11,
        title: 'Meeting Classmates',
        status: 'IN_PROGRESS',
        available: true,
      },
    ],
    nextStep: {
      lessonId: 11,
      lessonTitle: 'Meeting Classmates',
      lessonNumber: 1,
      stepsDone: 3,
      stepsTotal: 4,
    },
    archived: false,
    archivedAt: null,
    ...overrides,
  }
}

const archived = entry({
  summary: { ...campus, id: 3, title: 'Mandarin Foundations' },
  completedLessons: 5,
  progressPercent: 100,
  nextStep: null,
  archived: true,
  archivedAt: '2026-05-02T10:00:00',
  lessons: [],
})

function overview(classes: MyClassCard[], moreClassCount = 7): MyClasses {
  return { classes, moreClassCount }
}

function mountPage() {
  return mountAtRoute(StudentClassesPage, '/student/classes')
}

describe('StudentClassesPage', () => {
  beforeEach(() => {
    api.myClasses.mockReset()
    api.leave.mockReset()
    setAccessToken(null)
    clearClassNotice()
  })

  it('counts every joined class, the archived one too, and the classes left to find', async () => {
    api.myClasses.mockResolvedValue(overview([entry(), archived]))
    const { wrapper } = await mountPage()

    const tabs = wrapper.findAll('.route-tab')
    expect(tabs.map((tab) => tab.text())).toEqual(['My classes2', 'Find classes7'])
    expect(wrapper.text()).not.toMatch(/live session/i)
  })

  it('shows each class with its initials, progress and next lesson; an archived one only opens', async () => {
    api.myClasses.mockResolvedValue(overview([entry(), archived]))
    const { wrapper } = await mountPage()

    const [campusCard, archivedCard] = wrapper.findAll('.student-classes-card')
    expect(campusCard!.get('.class-avatar').text()).toBe('MC')
    expect(campusCard!.text()).toContain('2 of 5 lessons done')
    const campusLinks = campusCard!.findAll('a')
    expect(campusLinks.map((link) => link.text())).toEqual(['Open class', 'Continue lesson'])
    expect(campusLinks[1]!.attributes('href')).toBe('/student/classes/7/lessons/11')
    // The row names the lesson its main button opens.
    expect(campusCard!.get('[data-testid="my-class-next"]').text()).toBe('Next: Meeting Classmates')

    expect(archivedCard!.get('.class-avatar').text()).toBe('MF')
    expect(archivedCard!.text()).toContain('Archived 2 May · 5 of 5 lessons done')
    expect(archivedCard!.findAll('a').map((link) => link.text())).toEqual(['Open class'])
    expect(archivedCard!.find('[data-testid="my-class-next"]').exists()).toBe(false)
  })

  it('offers lesson 1 of a class not started yet', async () => {
    const joined = entry()
    api.myClasses.mockResolvedValue(
      overview([
        {
          ...joined,
          completedLessons: 0,
          progressPercent: 0,
          lessons: [{ ...joined.lessons[0]!, status: 'NOT_STARTED' }],
          nextStep: { ...joined.nextStep!, stepsDone: 0 },
        },
      ]),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('Not started')
    expect(wrapper.findAll('.student-classes-card a').map((link) => link.text())).toEqual([
      'Open class',
      'Start lesson 1',
    ])
  })

  it('says why My classes is empty and leads to Find classes', async () => {
    api.myClasses.mockResolvedValue(overview([], 8))
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('No classes yet')
    expect(wrapper.text()).toContain('Classes are free and made by the MandarinSpark team.')
    expect(wrapper.get('.student-classes-empty a').attributes('href')).toBe('/student/classes/find')
  })

  it('shows skeleton cards while loading', async () => {
    api.myClasses.mockReturnValue(deferred<MyClasses>().promise)
    const { wrapper } = await mountPage()

    expect(wrapper.findAll('.student-classes-card.is-skeleton')).toHaveLength(2)
    expect(wrapper.text()).toContain('Loading…')
  })

  it('shows the toast a join left, and its Undo leaves the class and goes back to Find classes', async () => {
    api.myClasses.mockResolvedValue(overview([entry()]))
    api.leave.mockResolvedValue(undefined)
    queueClassNotice({ message: 'Mandarin for Campus Life is in My classes.', joinedClassId: 7 })
    const { wrapper, router } = await mountPage()

    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.text()).toContain('Mandarin for Campus Life is in My classes.')
    await toast.get('button').trigger('click')
    await flushPromises()

    expect(api.leave).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.name).toBe('student-classes-find')
  })

  it('says so and keeps the class when Undo fails', async () => {
    api.myClasses.mockResolvedValue(overview([entry()]))
    api.leave.mockRejectedValue(new Error('offline'))
    queueClassNotice({ message: 'Mandarin for Campus Life is in My classes.', joinedClassId: 7 })
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="toast"] button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('you are still in Mandarin for Campus Life')
    expect(router.currentRoute.value.name).toBe('student-classes')
  })

  it('stays where the learner went while undoing a join', async () => {
    api.myClasses.mockResolvedValue(overview([entry()]))
    const leaving = deferred<void>()
    api.leave.mockReturnValueOnce(leaving.promise)
    queueClassNotice({ message: 'Class joined.', joinedClassId: 7 })
    const { wrapper, router } = await mountPage()
    await wrapper.get('[data-testid="toast"] button').trigger('click')
    wrapper.unmount()
    await router.push('/student/home')
    leaving.resolve()
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/student/home')
  })

  it('keeps an Undo error with the account that requested it', async () => {
    api.myClasses.mockResolvedValue(overview([entry()]))
    const leaving = deferred<void>()
    api.leave.mockReturnValueOnce(leaving.promise)
    queueClassNotice({ message: 'Class joined.', joinedClassId: 7 })
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="toast"] button').trigger('click')
    setAccessToken('different-learner-after-undo')
    leaving.reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.text()).not.toContain('you are still in Mandarin for Campus Life')
  })

  it.each(['cancelled', 'failed'])('refreshes My classes after Undo when navigation is %s', async (outcome) => {
    api.myClasses.mockResolvedValueOnce(overview([entry()])).mockResolvedValueOnce(overview([], 8))
    api.leave.mockResolvedValue(undefined)
    queueClassNotice({ message: 'Class joined.', joinedClassId: 7 })
    const { wrapper, router } = await mountPage()
    if (outcome === 'cancelled') router.beforeEach(() => false)
    else vi.spyOn(router, 'push').mockRejectedValueOnce(new Error('Page unavailable'))

    await wrapper.get('[data-testid="toast"] button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('student-classes')
    expect(wrapper.text()).toContain('No classes yet')
    expect(wrapper.text()).not.toContain('you are still in Mandarin for Campus Life')
  })

  it('shows the confirmation of a left class without Undo', async () => {
    api.myClasses.mockResolvedValue(overview([archived], 8))
    queueClassNotice({ message: 'You left Mandarin for Campus Life.' })
    const { wrapper } = await mountPage()

    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.text()).toContain('You left Mandarin for Campus Life.')
    expect(toast.find('button').exists()).toBe(false)
  })

  it.each(['before display', 'before Undo'])(
    'keeps a join notice with its session when it changes %s',
    async (when) => {
      api.myClasses.mockResolvedValue(overview([entry()]))
      setAccessToken('original-class-session')
      queueClassNotice({ message: 'Class joined.', joinedClassId: 7 })
      if (when === 'before display') setAccessToken('new-class-session')
      const { wrapper } = await mountPage()
      if (when === 'before display') {
        expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
      } else {
        setAccessToken('new-class-session')
        await wrapper.get('[data-testid="toast"] button').trigger('click')
      }
      expect(api.leave).not.toHaveBeenCalled()
    },
  )
})
