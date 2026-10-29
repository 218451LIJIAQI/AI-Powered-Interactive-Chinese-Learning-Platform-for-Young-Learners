import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { ClassCard, StudentClass } from '@/types/classes'

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
  topicName: 'Sentences',
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
    feedbackCount: 0,
  }
}

async function mountAt(path: string) {
  const router = await classesTestRouter(path)
  const wrapper = mount(StudentClassPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentClassPage', () => {
  beforeEach(() => {
    api.classPage.mockReset()
    api.join.mockReset()
    api.quizResults.mockReset()
  })

  it('shows the preview of a class not joined yet and joins it', async () => {
    api.classPage.mockResolvedValue(preview())
    api.join.mockResolvedValue(joined())
    const wrapper = await mountAt('/student/classes/7')

    expect(wrapper.text()).toContain('Free to join')
    expect(wrapper.text()).toContain('Lessons open when you join.')
    expect(wrapper.find('[data-testid="continue-class"]').exists()).toBe(false)

    await wrapper.get('[data-testid="join-class"]').trigger('click')
    await flushPromises()

    expect(api.join).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('You joined Mandarin for Beginners.')
    expect(wrapper.get('[data-testid="continue-class"]').text()).toContain('Continue: Numbers 1–10')
  })

  it('shows the note, the next step and each lesson with its status', async () => {
    api.classPage.mockResolvedValue(joined())
    const wrapper = await mountAt('/student/classes/7')

    expect(wrapper.text()).toContain('Note from Li Wei')
    expect(wrapper.text()).toContain('Start with Greetings!')
    expect(wrapper.text()).toContain('Numbers 1–10 · Lesson 2 · 1 of 3 activities done')
    expect(wrapper.text()).toContain('50% · 1 of 2 lessons completed')
    expect(wrapper.findAll('.student-class-lesson-state').map((state) => state.text())).toEqual([
      'Mastered',
      'In progress · 25%',
    ])
    expect(wrapper.get('a.student-class-lesson.is-current').attributes('aria-label')).toBe('Open Numbers 1–10')
    expect(api.quizResults).not.toHaveBeenCalled()
  })

  it('loads the quiz results on the results tab', async () => {
    api.classPage.mockResolvedValue(joined())
    api.quizResults.mockResolvedValue({
      averagePercent: 80,
      attempts: [
        {
          attemptId: 5,
          quizId: 9,
          quizTitle: 'Lesson quiz: Greetings',
          lessonTitle: 'Greetings',
          completedAt: '2026-05-18T10:00:00',
          score: 8,
          maxScore: 10,
          percent: 80,
        },
      ],
    })
    const wrapper = await mountAt('/student/classes/7/results')

    expect(api.quizResults).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('Average 80%')
    expect(wrapper.text()).toContain('8 / 10')
  })
})
