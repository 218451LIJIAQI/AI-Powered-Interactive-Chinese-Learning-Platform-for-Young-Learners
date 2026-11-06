import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { ClassCard, FeedbackItem, StudentClass } from '@/types/classes'

const api = vi.hoisted(() => ({
  classPage: vi.fn(),
  join: vi.fn(),
  quizResults: vi.fn(),
  feedback: vi.fn(),
}))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))

import StudentClassPage from './StudentClassPage.vue'

const card: ClassCard = {
  id: 7,
  title: 'Mandarin for Beginners',
  description: 'Greetings, numbers and campus life.',
  levelName: 'Beginner 1',
  topicName: 'Daily life',
  bestAfterUnit: 3,
  learningType: 'HYBRID',
  teacher: { id: 3, name: 'Li Wei', verified: true, bio: null },
  unitCount: 1,
  lessonCount: 2,
  upcomingLiveSessionCount: 0,
}

function preview(): StudentClass {
  return {
    joined: false,
    archived: false,
    summary: card,
    teacherNote: null,
    preview: [
      {
        id: 1,
        title: 'First Conversations',
        lessons: [{ id: 11, title: 'Greetings', activityCount: 3, quizCount: 1 }],
      },
    ],
    progressPercent: 0,
    completedLessons: 0,
    lessonCount: 2,
    nextStep: null,
    units: [],
    liveSessions: [],
    quizAttemptCount: 0,
    feedbackCount: 0,
  }
}

function joined(): StudentClass {
  return {
    ...preview(),
    joined: true,
    teacherNote: 'Start with Greetings!',
    progressPercent: 50,
    completedLessons: 1,
    nextStep: {
      lessonId: 12,
      lessonTitle: 'Numbers 1–10',
      lessonNumber: 2,
      activitiesDone: 1,
      activityCount: 3,
      quizDone: false,
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
            title: 'Greetings',
            number: 1,
            status: 'MASTERED',
            percent: 100,
            bestQuizPercent: 90,
            activityCount: 3,
            available: true,
          },
          {
            id: 12,
            title: 'Numbers 1–10',
            number: 2,
            status: 'IN_PROGRESS',
            percent: 25,
            bestQuizPercent: null,
            activityCount: 3,
            available: true,
          },
        ],
      },
    ],
    quizAttemptCount: 1,
    feedbackCount: 2,
  }
}

const feedbackItems: FeedbackItem[] = [
  {
    id: 1,
    teacherName: 'Li Wei',
    type: 'ENCOURAGING',
    about: 'QUIZ_ATTEMPT',
    lessonTitle: 'Greetings',
    message: 'Great effort.',
    createdAt: '2026-05-18T12:30:00',
    editedAt: null,
  },
  {
    id: 2,
    teacherName: 'Li Wei',
    type: 'LESSON',
    about: 'LESSON',
    lessonTitle: 'Numbers 1–10',
    message: 'Well done.',
    createdAt: '2026-05-12T18:20:00',
    editedAt: null,
  },
  {
    id: 3,
    teacherName: 'Li Wei',
    type: 'PROGRESS',
    about: 'GENERAL',
    lessonTitle: null,
    message: 'Keep it up.',
    createdAt: '2026-05-10T18:20:00',
    editedAt: null,
  },
]

async function mountAt(path: string) {
  const router = await classesTestRouter(path)
  const wrapper = mount(StudentClassPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}

describe('StudentClassPage', () => {
  beforeEach(() => {
    api.classPage.mockReset()
    api.join.mockReset()
    api.quizResults.mockReset()
    api.feedback.mockReset()
  })

  it('shows the preview of a class not joined yet, and joining ends in My classes', async () => {
    api.classPage.mockResolvedValue(preview())
    api.join.mockResolvedValue(joined())
    const { wrapper, router } = await mountAt('/student/classes/7')

    expect(wrapper.text()).toContain('Free to join')
    expect(wrapper.text()).toContain('Li Wei · Daily life · 2 lessons · best after Unit 3')
    expect(wrapper.text()).toContain('Unit 1: First Conversations')
    expect(wrapper.find('[data-testid="continue-class"]').exists()).toBe(false)

    await wrapper.get('[data-testid="join-class"]').trigger('click')
    await flushPromises()

    expect(api.join).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.name).toBe('student-classes')
  })

  it('shows the error and stays when joining fails', async () => {
    api.classPage.mockResolvedValue(preview())
    api.join.mockRejectedValue(new Error('nope'))
    const { wrapper, router } = await mountAt('/student/classes/7')

    await wrapper.get('[data-testid="join-class"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You could not join this class.')
    expect(router.currentRoute.value.name).toBe('student-class')
  })

  it('shows the next step and each lesson with its progress and status', async () => {
    api.classPage.mockResolvedValue(joined())
    const { wrapper } = await mountAt('/student/classes/7')

    expect(wrapper.text()).not.toContain('Start with Greetings!')
    expect(wrapper.text()).toContain('Numbers 1–10 · Lesson 2 · 1 of 3 activities done')
    expect(wrapper.text()).toContain('50% · 1 of 2 lessons completed')
    expect(wrapper.get('[data-testid="continue-class"]').text()).toContain('Continue: Numbers 1–10')
    const rows = wrapper.findAll('a.student-class-lesson')
    expect(rows.map((row) => row.attributes('aria-label'))).toEqual(['Open Greetings', 'Open Numbers 1–10'])
    // A mastered lesson reads Completed.
    expect(rows[0]!.text()).toContain('Lesson 1 · best quiz 90%')
    expect(rows[0]!.text()).toContain('Completed')
    expect(rows[1]!.text()).toContain('25%')
    expect(rows[1]!.text()).toContain('In progress')
    expect(api.feedback).not.toHaveBeenCalled()
  })

  it('has only the Lessons and Feedback tabs, and an old results link lands on the class', async () => {
    api.classPage.mockResolvedValue(joined())
    const { wrapper, router } = await mountAt('/student/classes/7/results')
    await flushPromises()

    expect(router.currentRoute.value.params.tab).toBeFalsy()
    expect(wrapper.findAll('.route-tab').map((tab) => tab.text())).toEqual(['Lessons2', 'Feedback2'])
    expect(api.quizResults).not.toHaveBeenCalled()
  })

  it('lists the feedback, each note opening its attempt or its lesson', async () => {
    api.classPage.mockResolvedValue(joined())
    api.feedback.mockResolvedValue(feedbackItems)
    api.quizResults.mockResolvedValue({
      averagePercent: 80,
      attempts: [
        {
          attemptId: 5,
          quizId: 9,
          quizTitle: 'Greetings',
          lessonTitle: 'Greetings',
          completedAt: '2026-05-18T10:00:00',
          score: 8,
          maxScore: 10,
          percent: 80,
        },
      ],
    })
    const { wrapper } = await mountAt('/student/classes/7/feedback')
    await flushPromises()

    expect(wrapper.text()).toContain('3 messages')
    const notes = wrapper.findAll('.student-class-note')
    expect(notes[0]!.text()).toContain('Lesson quiz · Greetings')
    expect(notes[0]!.get('a').attributes('href')).toBe('/student/quiz/9/attempts/5')
    expect(notes[1]!.get('a').attributes('href')).toBe('/student/classes/7/lessons/12')
    expect(notes[2]!.find('a').exists()).toBe(false)
  })
})
