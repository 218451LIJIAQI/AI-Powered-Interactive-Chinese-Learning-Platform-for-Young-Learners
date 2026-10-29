import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { TeachingDashboard } from '@/types/classes'

const api = vi.hoisted(() => ({ dashboard: vi.fn() }))

vi.mock('@/api/classes', () => ({ teacherClassesApi: api }))

import TeacherTeachingDashboard from './TeacherTeachingDashboard.vue'

const dashboard: TeachingDashboard = {
  todo: [
    {
      kind: 'REVISION_CHANGES_REQUESTED',
      needsYou: true,
      classId: 7,
      lessonId: 13,
      sessionId: null,
      title: 'University Life',
      note: 'Add pinyin under the new words.',
      noteBy: 'Olivia Tan',
      at: '2026-05-15T09:00:00',
      detail: null,
    },
    {
      kind: 'CLASS_IN_REVIEW',
      needsYou: false,
      classId: 9,
      lessonId: null,
      sessionId: null,
      title: 'Chinese Culture and Festivals',
      note: null,
      noteBy: null,
      at: '2026-05-18T10:00:00',
      detail: 'Lantern Festival',
    },
  ],
  studentCount: 3,
  needsSupportCount: 1,
  publishedClasses: 2,
  publishedLessons: 21,
  inReviewCount: 2,
  upcomingSessionCount: 4,
  upcomingBookings: 8,
  quizAverage: 65,
  studentsNeedingSupport: [
    {
      studentId: 5,
      name: 'Zhang Yi',
      xpLevel: 1,
      progressPercent: 20,
      quizAverage: 50,
      needsSupport: true,
      lastActiveAt: '2026-05-09T12:00:00',
    },
  ],
  upcomingSessions: [],
}

describe('TeacherTeachingDashboard', () => {
  beforeEach(() => api.dashboard.mockReset())

  it('lists what needs the teacher first, the numbers and the students needing support', async () => {
    api.dashboard.mockResolvedValue(dashboard)
    const router = await classesTestRouter('/teacher/classes')
    const wrapper = mount(TeacherTeachingDashboard, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('1 needs you · 1 to keep an eye on')
    expect(wrapper.text()).toContain('Changes requested: University Life (revision)')
    const note = wrapper.get('blockquote')
    expect(note.text()).toContain('Add pinyin under the new words.')
    expect(note.text()).toContain('Olivia Tan, May 15')
    expect(wrapper.text()).toContain('1 item needs you')
    expect(wrapper.text()).toContain('with its first lesson, "Lantern Festival"')
    const edit = wrapper.findAll('a').find((link) => link.text().includes('Edit lesson'))
    expect(edit?.attributes('href')).toBe('/teacher/classes/7/lessons/13')

    expect(wrapper.text()).toContain('2 classes and 21 lessons · 2 in review')
    expect(wrapper.text()).toContain('8 bookings')
    expect(wrapper.text()).toContain('65%')
    expect(wrapper.text()).toContain('Zhang Yi')
    expect(wrapper.text()).toContain('20% class progress · quiz average 50%')
    expect(wrapper.text()).toContain('No live sessions are coming up.')
  })
})
