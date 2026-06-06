import { describe, expect, it } from 'vitest'

import { inferPracticeActivityFromText, normalizePracticeActivity, practiceRoute } from './practiceRoute'

describe('practiceRoute helpers', () => {
  it('normalizes activity query aliases from route params and human labels', () => {
    expect(normalizePracticeActivity('listening')).toBe('LISTENING_EXERCISE')
    expect(normalizePracticeActivity('drag-and-drop')).toBe('DRAG_DROP_ACTIVITY')
    expect(normalizePracticeActivity('story activity')).toBe('STORY_ACTIVITY')
    expect(normalizePracticeActivity(['TRACING_EXERCISE'])).toBe('TRACING_EXERCISE')
    expect(normalizePracticeActivity('unknown')).toBeNull()
  })

  it('builds stable student practice route targets', () => {
    expect(practiceRoute('MATCHING_GAME')).toEqual({
      name: 'student-practice',
      query: { activity: 'matching-game' },
    })
    expect(practiceRoute('not-a-real-activity')).toEqual({ name: 'student-practice' })
  })

  it.each([
    ['Tone distinction and listening speed', 'LISTENING_EXERCISE'],
    ['Character tracing and stroke order', 'TRACING_EXERCISE'],
    ['Match words with meanings', 'MATCHING_GAME'],
    ['Build a story comprehension habit', 'STORY_ACTIVITY'],
    ['Keep reviewing starter vocabulary', 'FLASHCARD'],
  ])('infers %s as %s', (text, activity) => {
    expect(inferPracticeActivityFromText(text)).toBe(activity)
  })
})
