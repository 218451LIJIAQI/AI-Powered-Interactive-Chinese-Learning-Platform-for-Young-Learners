import { describe, expect, it } from 'vitest'

import { relativeDay } from './classes'

describe('live session timetables', () => {
  it('names today, tomorrow and yesterday', () => {
    const now = new Date(2026, 4, 18, 12, 0)
    expect(relativeDay('2026-05-18T20:00:00', now)).toBe('today')
    expect(relativeDay('2026-05-19T08:00:00', now)).toBe('tomorrow')
    expect(relativeDay('2026-05-17T08:00:00', now)).toBe('yesterday')
    expect(relativeDay('2026-05-25T08:00:00', now)).toBeNull()
  })
})
