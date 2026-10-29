import { describe, expect, it } from 'vitest'

import { bookedPercent, groupByDay, relativeDay } from './classes'

describe('live session timetables', () => {
  it('groups sessions in time order by the day they start on', () => {
    const days = groupByDay([
      { id: 1, startsAt: '2026-05-18T09:00:00' },
      { id: 2, startsAt: '2026-05-18T16:00:00' },
      { id: 3, startsAt: '2026-05-20T10:00:00' },
    ])

    expect(days.map((day) => day.items.map((item) => item.id))).toEqual([[1, 2], [3]])
    expect(days[0]!.at).toBe('2026-05-18T09:00:00')
  })

  it('names today, tomorrow and yesterday', () => {
    const now = new Date(2026, 4, 18, 12, 0)
    expect(relativeDay('2026-05-18T20:00:00', now)).toBe('today')
    expect(relativeDay('2026-05-19T08:00:00', now)).toBe('tomorrow')
    expect(relativeDay('2026-05-17T08:00:00', now)).toBe('yesterday')
    expect(relativeDay('2026-05-25T08:00:00', now)).toBeNull()
  })

  it('gives the booked share of the seats', () => {
    expect(bookedPercent(3, 25)).toBe(12)
    expect(bookedPercent(2, 2)).toBe(100)
    expect(bookedPercent(4, null)).toBe(0)
  })
})
