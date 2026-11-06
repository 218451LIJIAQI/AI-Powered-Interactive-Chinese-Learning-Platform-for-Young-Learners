import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { StudentSession, StudentSessions } from '@/types/classes'

const api = vi.hoisted(() => ({
  liveSessions: vi.fn(),
  myClasses: vi.fn(),
  book: vi.fn(),
  joinSession: vi.fn(),
}))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))

import StudentLiveSessionsPage from './StudentLiveSessionsPage.vue'

const MINUTE = 60_000

function atOffset(minutes: number): string {
  return new Date(Date.now() + minutes * MINUTE).toISOString()
}

function session(overrides: Partial<StudentSession>): StudentSession {
  return {
    id: 1,
    title: 'Greetings 问候',
    classId: 7,
    classTitle: 'Mandarin for Beginners',
    lessonId: 11,
    lessonTitle: 'Greetings',
    teacherName: 'Li Wei',
    startsAt: atOffset(2 * 24 * 60),
    endsAt: atOffset(2 * 24 * 60 + 45),
    joinOpensAt: atOffset(2 * 24 * 60 - 15),
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

/** A session in its join window: started five minutes ago. */
function openNow(overrides: Partial<StudentSession> = {}): StudentSession {
  return session({ startsAt: atOffset(-5), endsAt: atOffset(40), joinOpensAt: atOffset(-20), ...overrides })
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

function joinButton(wrapper: Awaited<ReturnType<typeof mountPage>>, id = 1) {
  return wrapper.get(`[data-testid="session-${id}"]`).get('button')
}

describe('StudentLiveSessionsPage', () => {
  let tab: { opener: unknown; location: { href: string }; close: ReturnType<typeof vi.fn> }

  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.myClasses.mockResolvedValue({
      classes: [],
      liveToday: null,
      moreClassCount: 0,
      moreTeachers: [],
      upcomingLiveSessionCount: 1,
    })
    api.joinSession.mockResolvedValue({ meetingLink: 'https://example.com/meet' })
    tab = { opener: {}, location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('joins in one click, booking the seat first when she has none', async () => {
    api.liveSessions.mockResolvedValue(sessions([openNow()]))
    api.book.mockResolvedValue(openNow({ booked: true }))
    const wrapper = await mountPage()

    expect(wrapper.text()).not.toContain('Book')
    await joinButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.book).toHaveBeenCalledWith(1)
    expect(api.joinSession).toHaveBeenCalledWith(1)
    expect(tab.location.href).toBe('https://example.com/meet')
    expect(api.book.mock.invocationCallOrder[0]).toBeLessThan(api.joinSession.mock.invocationCallOrder[0]!)
  })

  it('does not book again when she already has a seat', async () => {
    api.liveSessions.mockResolvedValue(sessions([openNow({ booked: true, canBook: false, canJoin: true })]))
    const wrapper = await mountPage()

    await joinButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.book).not.toHaveBeenCalled()
    expect(api.joinSession).toHaveBeenCalledWith(1)
  })

  it('closes the tab and says why when joining fails', async () => {
    api.liveSessions.mockResolvedValue(sessions([openNow()]))
    api.book.mockRejectedValue(new Error('nope'))
    const wrapper = await mountPage()

    await joinButton(wrapper).trigger('click')
    await flushPromises()

    expect(tab.close).toHaveBeenCalled()
    expect(api.joinSession).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('You could not join this session.')
  })

  it('shows Join disabled on the day before its window opens, and says when it opens', async () => {
    const start = new Date()
    start.setHours(23, 50, 0, 0)
    // Late in the evening "today" has no room for a window that has not opened, so this case needs a clock.
    vi.useFakeTimers({ now: new Date(start.getFullYear(), start.getMonth(), start.getDate(), 9, 0, 0) })
    const startsAt = new Date(start.getFullYear(), start.getMonth(), start.getDate(), 16, 0, 0)
    api.liveSessions.mockResolvedValue(
      sessions([
        session({
          startsAt: startsAt.toISOString(),
          endsAt: new Date(startsAt.getTime() + 45 * MINUTE).toISOString(),
          joinOpensAt: new Date(startsAt.getTime() - 15 * MINUTE).toISOString(),
        }),
      ]),
    )
    const wrapper = await mountPage()
    vi.useRealTimers()

    expect(joinButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Join opens at')
  })

  it('gives later days no action, a cancelled session its status and a full one without her seat its status', async () => {
    api.liveSessions.mockResolvedValue(
      sessions([
        session({ id: 1 }),
        session({ id: 2, status: 'CANCELLED', canBook: false }),
        session({ id: 3, status: 'FULL', canBook: false }),
      ]),
    )
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="session-1"]').find('button').exists()).toBe(false)
    expect(wrapper.get('[data-testid="session-2"]').text()).toContain('Cancelled')
    expect(wrapper.get('[data-testid="session-3"]').text()).toContain('Full')
    // Cancelled sessions do not count.
    expect(wrapper.text()).toContain('2 sessions')
  })

  it('shows past sessions with their recordings and no attendance', async () => {
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

    expect(wrapper.text()).toContain('1 recording')
    expect(wrapper.text()).not.toContain('Attended')
    const link = wrapper.get('a[href="https://example.com/recording"]')
    expect(link.text()).toContain('Watch recording')
    expect(link.attributes('aria-label')).toBe('Watch the recording of Numbers 数字 (opens in a new tab)')
  })

  it('offers Find classes when there are no sessions at all', async () => {
    api.liveSessions.mockResolvedValue(sessions([]))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No live sessions yet')
    expect(wrapper.text()).toContain('Find classes')
  })
})
