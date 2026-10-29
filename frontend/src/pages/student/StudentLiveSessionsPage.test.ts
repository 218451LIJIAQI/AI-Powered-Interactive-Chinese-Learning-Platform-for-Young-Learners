import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { StudentSession, StudentSessions } from '@/types/classes'

const api = vi.hoisted(() => ({
  liveSessions: vi.fn(),
  myClasses: vi.fn(),
  book: vi.fn(),
  cancelBooking: vi.fn(),
  joinSession: vi.fn(),
}))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))

import StudentLiveSessionsPage from './StudentLiveSessionsPage.vue'

const inTwoDays = new Date(Date.now() + 2 * 24 * 3_600_000)

function session(overrides: Partial<StudentSession>): StudentSession {
  const startsAt = inTwoDays.toISOString()
  return {
    id: 1,
    title: 'Greetings 问候',
    classId: 7,
    classTitle: 'Mandarin for Beginners',
    lessonId: 11,
    lessonTitle: 'Greetings',
    teacherName: 'Li Wei',
    startsAt,
    endsAt: new Date(inTwoDays.getTime() + 45 * 60_000).toISOString(),
    joinOpensAt: new Date(inTwoDays.getTime() - 15 * 60_000).toISOString(),
    platform: 'ZOOM',
    status: 'SCHEDULED',
    rescheduledFrom: null,
    cancelledAt: null,
    capacity: 25,
    bookedCount: 3,
    seatsLeft: 22,
    booked: false,
    bookingCancelled: false,
    attended: false,
    canBook: true,
    canJoin: false,
    recordingUrl: null,
    ...overrides,
  }
}

function sessions(upcoming: StudentSession[], past: StudentSession[] = []): StudentSessions {
  return { upcoming, past, bookedCount: upcoming.filter((item) => item.booked).length }
}

async function mountPage() {
  const router = await classesTestRouter('/student/classes/live')
  const wrapper = mount(StudentLiveSessionsPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentLiveSessionsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.myClasses.mockResolvedValue({
      classes: [],
      liveToday: null,
      moreClassCount: 0,
      moreTeachers: [],
      upcomingLiveSessionCount: 1,
    })
  })

  it('books a seat and confirms it', async () => {
    api.liveSessions
      .mockResolvedValueOnce(sessions([session({})]))
      .mockResolvedValue(sessions([session({ booked: true, canBook: false, bookedCount: 4, seatsLeft: 21 })]))
    api.book.mockResolvedValue(session({ booked: true }))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('22 seats left')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Book')!
      .trigger('click')
    await flushPromises()

    expect(api.book).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('You booked Greetings 问候.')
    expect(wrapper.text()).toContain('Booked')
    expect(wrapper.text()).toContain('15 minutes before the start')
  })

  it('groups the upcoming sessions by day, a booked one marked as booked', async () => {
    const morning = new Date(inTwoDays)
    morning.setHours(10, 0, 0, 0)
    const later = new Date(morning.getTime() + 5 * 3_600_000)
    const nextWeek = new Date(morning.getTime() + 7 * 24 * 3_600_000)
    api.liveSessions.mockResolvedValue(
      sessions([
        session({ id: 1, booked: true, canBook: false, startsAt: morning.toISOString() }),
        session({ id: 2, title: 'Tones Clinic', startsAt: later.toISOString(), endsAt: later.toISOString() }),
        session({ id: 3, title: 'Numbers', startsAt: nextWeek.toISOString(), endsAt: nextWeek.toISOString() }),
      ]),
    )
    const wrapper = await mountPage()

    const days = wrapper.findAll('[role="group"]')
    expect(days).toHaveLength(2)
    expect(days[0]!.findAll('li')).toHaveLength(2)
    expect(days[1]!.findAll('li')).toHaveLength(1)
    expect(wrapper.get('[data-testid="session-1"]').classes()).toContain('is-booked')
    expect(wrapper.get('[data-testid="session-1"]').text()).toContain('Booked')
    expect(wrapper.get('[data-testid="session-2"]').classes()).not.toContain('is-booked')
  })

  it('cancels a booking after the dialog says what happens', async () => {
    const booked = session({ booked: true, canBook: false })
    api.liveSessions
      .mockResolvedValueOnce(sessions([booked]))
      .mockResolvedValue(sessions([session({ bookingCancelled: true })]))
    api.cancelBooking.mockResolvedValue(session({ bookingCancelled: true }))
    const wrapper = await mountPage()

    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Details'))!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cancel booking')!
      .trigger('click')
    expect(wrapper.text()).toContain('Your seat for Greetings 问候')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.cancelBooking).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Your booking for Greetings 问候 was cancelled.')
  })

  it('shows past sessions with their recordings and attendance', async () => {
    const past = session({
      id: 2,
      title: 'Numbers 数字',
      startsAt: '2026-05-11T08:00:00Z',
      endsAt: '2026-05-11T08:45:00Z',
      status: 'COMPLETED',
      booked: true,
      attended: true,
      canBook: false,
      recordingUrl: 'https://example.com/recording',
    })
    api.liveSessions.mockResolvedValue(sessions([], [past]))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('1 recording · sessions you booked')
    expect(wrapper.text()).toContain('Attended')
    expect(wrapper.find('a[href="https://example.com/recording"]').exists()).toBe(true)
  })
})
