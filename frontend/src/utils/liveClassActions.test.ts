import { describe, expect, it } from 'vitest'

import { studentLiveClasses } from '@/demo/data'
import type { LiveClassResponse } from '@/types/api'

import {
  effectiveLiveClassStatus,
  formatLiveClassSchedule,
  liveClassActionState,
  liveClassSeatState,
  replaceLiveClass,
  sortLiveClassesByStart,
} from './liveClassActions'

const NOW = Date.parse('2026-05-17T00:00:00Z')

function liveClass(overrides: Partial<LiveClassResponse> = {}): LiveClassResponse {
  return {
    ...studentLiveClasses[1],
    id: 99,
    startTime: '2026-05-20T09:00:00Z',
    endTime: '2026-05-20T09:45:00Z',
    liveClassStatus: 'SCHEDULED',
    maxParticipants: 20,
    enrolledCount: 5,
    remainingSeats: 15,
    full: false,
    enrolled: false,
    canEnrol: true,
    canJoin: false,
    ...overrides,
  }
}

describe('liveClassSeatState', () => {
  it('reports remaining seats, full classes and classes without a limit', () => {
    expect(liveClassSeatState(liveClass())).toEqual({ kind: 'available', remaining: 15, total: 20 })
    expect(liveClassSeatState(liveClass({ remainingSeats: 0, enrolledCount: 20, full: true }))).toEqual({
      kind: 'full',
      total: 20,
    })
    expect(liveClassSeatState(liveClass({ maxParticipants: null, remainingSeats: null }))).toEqual({
      kind: 'unlimited',
    })
  })

  it('derives the remaining seats when the backend omits them', () => {
    expect(liveClassSeatState(liveClass({ remainingSeats: undefined, full: undefined, enrolledCount: 18 }))).toEqual({
      kind: 'available',
      remaining: 2,
      total: 20,
    })
    expect(liveClassSeatState(liveClass({ remainingSeats: undefined, full: undefined, enrolledCount: 20 })).kind).toBe(
      'full',
    )
  })
})

describe('liveClassActionState', () => {
  it('offers enrol, join, class full and waiting states per class', () => {
    expect(liveClassActionState(liveClass(), NOW)).toEqual({ primary: 'enrol', canCancel: false })
    expect(liveClassActionState(liveClass({ enrolled: true, canEnrol: false, canJoin: true }), NOW)).toEqual({
      primary: 'join',
      canCancel: true,
    })
    expect(
      liveClassActionState(liveClass({ canEnrol: false, remainingSeats: 0, enrolledCount: 20, full: true }), NOW),
    ).toEqual({ primary: 'full', canCancel: false })
    expect(liveClassActionState(liveClass({ enrolled: true, canEnrol: false }), NOW)).toEqual({
      primary: 'waiting',
      canCancel: true,
    })
  })

  it('only allows cancelling an enrolment before the class starts and while it is not terminal', () => {
    const enrolled = { enrolled: true, canEnrol: false }
    expect(liveClassActionState(liveClass({ ...enrolled, startTime: '2026-05-16T09:00:00Z' }), NOW).canCancel).toBe(
      false,
    )
    expect(liveClassActionState(liveClass({ ...enrolled, liveClassStatus: 'CANCELLED' }), NOW)).toEqual({
      primary: 'closed',
      canCancel: false,
    })
    expect(liveClassActionState(liveClass({ ...enrolled, liveClassStatus: 'RESCHEDULED' }), NOW).canCancel).toBe(true)
  })

  it('keeps an enrolled learner in a full class on their own action, not "class full"', () => {
    const state = liveClassActionState(
      liveClass({ enrolled: true, canEnrol: false, remainingSeats: 0, enrolledCount: 20, full: true }),
      NOW,
    )
    expect(state.primary).toBe('waiting')
  })
})

describe('effectiveLiveClassStatus (D21)', () => {
  const classWindow = { startTime: '2026-05-20T09:00:00Z', endTime: '2026-05-20T09:45:00Z' }
  const during = Date.parse('2026-05-20T09:10:00Z')

  it('keeps an in-progress status only while now is inside the class window', () => {
    for (const status of ['ONGOING', 'IN_PROGRESS', 'LIVE']) {
      const item = { ...classWindow, liveClassStatus: status }
      expect(effectiveLiveClassStatus(item, during)).toBe(status)
      expect(effectiveLiveClassStatus(item, Date.parse('2026-05-20T08:59:00Z'))).toBe('SCHEDULED')
      expect(effectiveLiveClassStatus(item, Date.parse('2026-05-20T09:46:00Z'))).toBe('COMPLETED')
    }
    expect(effectiveLiveClassStatus({ ...classWindow, liveClassStatus: 'ongoing' }, during)).toBe('ongoing')
  })

  it('treats unreadable times as not started and leaves other statuses unchanged', () => {
    expect(effectiveLiveClassStatus({ startTime: 'soon', endTime: '', liveClassStatus: 'ONGOING' }, during)).toBe(
      'SCHEDULED',
    )
    for (const status of ['SCHEDULED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED']) {
      expect(effectiveLiveClassStatus({ ...classWindow, liveClassStatus: status }, during)).toBe(status)
    }
    expect(effectiveLiveClassStatus(liveClass({ liveClassStatus: 'ONGOING', ...classWindow }), during)).toBe('ONGOING')
  })
})

describe('list helpers', () => {
  it('sorts by start time and replaces a class with the updated response', () => {
    const later = liveClass({ id: 1, startTime: '2026-05-25T09:00:00Z' })
    const sooner = liveClass({ id: 2, startTime: '2026-05-19T09:00:00Z' })
    expect(sortLiveClassesByStart([later, sooner]).map((item) => item.id)).toEqual([2, 1])

    const updated = { ...sooner, enrolled: true, canEnrol: false, enrolledCount: 6, remainingSeats: 14 }
    const result = replaceLiveClass([later, sooner], updated)
    expect(result[1]).toBe(updated)
    expect(result[0]).toBe(later)
  })

  it('formats the schedule in the class time zone', () => {
    const labels = formatLiveClassSchedule(
      { startTime: '2026-05-20T09:00:00Z', endTime: '2026-05-20T09:45:00Z', timeZone: 'Asia/Kuala_Lumpur' },
      'en-GB',
    )
    expect(labels?.timeZone).toBe('Asia/Kuala_Lumpur')
    // 09:00 UTC is 17:00 in Kuala Lumpur (UTC+8), whatever the test machine's zone is.
    expect(labels?.time).toMatch(/17:00.*17:45/)
    expect(labels?.date).toMatch(/20/)
  })

  it('falls back to the browser zone for an unknown time zone', () => {
    const labels = formatLiveClassSchedule(
      { startTime: '2026-05-20T09:00:00Z', endTime: '2026-05-20T09:45:00Z', timeZone: 'Not/AZone' },
      'en',
    )
    expect(labels).not.toBeNull()
    expect(labels?.timeZone).toBe('Not/AZone')
  })
})

describe('preview fixtures', () => {
  it('cover enrolled, full, open-seat and no-limit classes', () => {
    const kinds = studentLiveClasses.map((item) => ({
      enrolled: item.enrolled,
      seats: liveClassSeatState(item).kind,
      primary: liveClassActionState(item, NOW).primary,
    }))
    expect(kinds.some((item) => item.enrolled)).toBe(true)
    expect(kinds.some((item) => item.primary === 'full')).toBe(true)
    expect(kinds.some((item) => item.seats === 'available' && item.primary === 'enrol')).toBe(true)
    expect(kinds.some((item) => item.seats === 'unlimited')).toBe(true)
  })
})
