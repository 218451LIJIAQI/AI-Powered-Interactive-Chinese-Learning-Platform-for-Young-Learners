import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { StudentLesson } from '@/types/classes'

const api = vi.hoisted(() => ({
  lesson: vi.fn(),
  classPage: vi.fn(),
  quizResults: vi.fn(),
  markVideoWatched: vi.fn(),
}))

vi.mock('@/api/classes', () => ({
  studentClassesApi: api,
  activitySlug: (type: string) => type.toLowerCase(),
  videoBlob: vi.fn(),
}))

import StudentClassLessonPage from './StudentClassLessonPage.vue'

function lesson(overrides: Partial<StudentLesson> = {}): StudentLesson {
  return {
    classId: 1,
    classTitle: 'Mandarin for Campus Life',
    teacherName: 'Li Wei',
    unitTitle: 'First Conversations',
    title: 'Meeting Classmates',
    number: 1,
    summary: 'Introduce yourself.',
    minutes: 18,
    available: true,
    video: {
      title: 'Meeting a classmate',
      externalUrl: 'https://example.com/video',
      streamPath: null,
      durationSeconds: 375,
      watched: false,
      percentWatched: 40,
    },
    keyPhrases: [{ chinese: '你好', pinyin: 'nǐ hǎo', meaning: 'Hello' }],
    activities: [
      { type: 'FLASHCARDS', id: 6, title: 'Classmate Phrases', number: 1, stage: 'GUIDED', done: true },
      { type: 'MATCHING', id: 7, title: 'Match the Answers', number: 2, stage: 'GUIDED', done: false },
    ],
    quizzes: [
      {
        id: 9,
        title: 'Meeting Classmates',
        stage: 'GUIDED',
        questionCount: 5,
        marks: 10,
        timeLimitMinutes: 10,
        hintsAvailable: true,
        bestPercent: 80,
        attemptCount: 2,
        inProgress: false,
      },
    ],
    stepsDone: 2,
    stepsTotal: 3,
    percent: 66,
    status: 'IN_PROGRESS',
    bestQuizPercent: 80,
    liveSessions: [],
    ...overrides,
  }
}

async function mountLesson() {
  const router = await classesTestRouter('/student/classes/1/lessons/1')
  const wrapper = mount(StudentClassLessonPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentClassLessonPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.lesson.mockResolvedValue(lesson())
    api.classPage.mockResolvedValue({ units: [{ id: 3, lessons: [{ id: 1 }] }] })
    api.quizResults.mockResolvedValue({ averagePercent: 80, attempts: [{ attemptId: 4, quizId: 9 }] })
  })

  it('shows the state, the next step, the content, the activities and the quiz, without a watched state', async () => {
    const wrapper = await mountLesson()

    expect(wrapper.get('h1').text()).toBe('Meeting Classmates')
    expect(wrapper.text()).toContain('In progress · 2 of 3 steps')
    expect(wrapper.get('.page-header-actions').text()).toContain('Continue: Match the Answers')
    expect(wrapper.text()).toContain('Unit 1: First Conversations')
    expect(wrapper.text()).toContain('6:15')
    expect(wrapper.text()).not.toContain('watched')
    expect(wrapper.text()).toContain('1 of 2 activities done')
    const actions = wrapper.findAll('.student-lesson-activities a')
    expect(actions.map((link) => link.text())).toEqual(['Practise again', 'Start'])
    expect(actions[1]!.attributes('aria-label')).toBe('Start Match the Answers')
    expect(wrapper.text()).toContain('Meeting Classmates quiz')
    expect(wrapper.text()).toContain('80%')
    expect(wrapper.text()).toContain('Retake quiz')
    expect(
      wrapper
        .findAll('a')
        .find((link) => link.text().includes('See latest result'))!
        .attributes('href'),
    ).toBe('/student/quiz/9/attempts/4')
  })

  it('reads a mastered lesson as Completed and offers the class as the next step', async () => {
    api.lesson.mockResolvedValue(
      lesson({
        status: 'MASTERED',
        stepsDone: 3,
        activities: [{ type: 'FLASHCARDS', id: 6, title: 'Classmate Phrases', number: 1, stage: 'GUIDED', done: true }],
      }),
    )
    const wrapper = await mountLesson()

    expect(wrapper.text()).toContain('Completed · 3 of 3 steps')
    expect(wrapper.text()).not.toContain('Mastered')
    expect(wrapper.get('.page-header-actions').text()).toContain('Open class')
  })

  it('sends a student who finished the activities to the quiz', async () => {
    api.lesson.mockResolvedValue(
      lesson({
        activities: [{ type: 'FLASHCARDS', id: 6, title: 'Classmate Phrases', number: 1, stage: 'GUIDED', done: true }],
        quizzes: [{ ...lesson().quizzes[0]!, attemptCount: 0, bestPercent: null }],
      }),
    )
    api.quizResults.mockResolvedValue({ averagePercent: null, attempts: [] })
    const wrapper = await mountLesson()

    expect(wrapper.get('.page-header-actions').text()).toContain('Take the quiz')
    expect(wrapper.text()).toContain('Start quiz')
    expect(wrapper.text()).not.toContain('See latest result')
  })
})
