import { describe, expect, it } from 'vitest'

import {
  inferPracticeActivityFromText,
  normalizePracticeActivity,
  practiceActivityQueryValue,
  practiceRoute,
} from './practiceRoute'

describe('practiceRoute helpers', () => {
  it('normalizes activity query aliases from route params and human labels', () => {
    expect(normalizePracticeActivity('listening')).toBe('LISTENING_EXERCISE')
    expect(normalizePracticeActivity('drag-and-drop')).toBe('DRAG_DROP_ACTIVITY')
    expect(normalizePracticeActivity('story activity')).toBe('STORY_ACTIVITY')
    expect(normalizePracticeActivity(['TRACING_EXERCISE'])).toBe('TRACING_EXERCISE')
    expect(normalizePracticeActivity('unknown')).toBeNull()
  })

  it('builds stable student practice route targets', () => {
    expect(practiceActivityQueryValue('MATCHING_GAME')).toBe('matching-game')
    expect(practiceRoute('MATCHING_GAME')).toEqual({
      name: 'student-practice',
      query: { activity: 'matching-game' },
    })
    expect(practiceRoute('not-a-real-activity')).toEqual({ name: 'student-practice' })
  })

  it('infers useful practice modes from weak areas and recommendation copy', () => {
    expect(inferPracticeActivityFromText('Tone distinction and listening speed')).toBe('LISTENING_EXERCISE')
    expect(inferPracticeActivityFromText('Character tracing and stroke order')).toBe('TRACING_EXERCISE')
    expect(inferPracticeActivityFromText('Match words with meanings')).toBe('MATCHING_GAME')
    expect(inferPracticeActivityFromText('Build a story comprehension habit')).toBe('STORY_ACTIVITY')
    expect(inferPracticeActivityFromText('Keep reviewing starter vocabulary')).toBe('FLASHCARD')
  })
})
