import { describe, expect, it } from 'vitest'

import type { StudentProgressLessonResponse } from '@/types/api'

import {
  bestQuizPercent,
  countMastery,
  filterLessonsByMastery,
  lessonRoute,
  progressOverview,
  resolveWeakAreas,
  sortLessons,
} from './progressSummary'

function lesson(overrides: Partial<StudentProgressLessonResponse>): StudentProgressLessonResponse {
  return {
    lessonId: 1,
    lessonTitle: 'Lesson',
    completionPercent: 0,
    completed: false,
    lastAccessedAt: null,
    mastery: 'NOT_STARTED',
    bestQuizPercentage: null,
    ...overrides,
  }
}

const lessons = [
  lesson({
    lessonId: 10,
    lessonTitle: 'Greetings',
    completionPercent: 60,
    mastery: 'IN_PROGRESS',
    bestQuizPercentage: 67,
  }),
  lesson({
    lessonId: 12,
    lessonTitle: 'Numbers 1-10',
    completionPercent: 100,
    mastery: 'MASTERED',
    bestQuizPercentage: 90,
  }),
  lesson({
    lessonId: 13,
    lessonTitle: 'Ordering Food at the Canteen',
    completionPercent: 100,
    mastery: 'COMPLETED',
    bestQuizPercentage: 70,
  }),
  lesson({ lessonId: 11, lessonTitle: 'University Life', completionPercent: 0, mastery: 'NOT_STARTED' }),
]

describe('countMastery', () => {
  it('counts every mastery level and treats unknown values as not started', () => {
    const counts = countMastery([...lessons, lesson({ mastery: 'SOMETHING' as never })])
    expect(counts).toEqual({ NOT_STARTED: 2, IN_PROGRESS: 1, COMPLETED: 1, MASTERED: 1 })
  })
})

describe('progressOverview', () => {
  it('clamps percentages and keeps completed <= started', () => {
    expect(
      progressOverview({
        totalLessonsStarted: 3,
        completedLessons: 5,
        quizAverage: 120.4,
        overallCompletionPercent: -3,
      }),
    ).toEqual({ overallPercent: 0, completedLessons: 3, startedLessons: 5, quizAverage: 100 })
  })

  it('handles a missing summary', () => {
    expect(progressOverview(null)).toEqual({
      overallPercent: 0,
      completedLessons: 0,
      startedLessons: 0,
      quizAverage: 0,
    })
  })
})

describe('bestQuizPercent', () => {
  it('returns null when no quiz was attempted', () => {
    expect(bestQuizPercent({ bestQuizPercentage: null })).toBeNull()
    expect(bestQuizPercent({})).toBeNull()
    expect(bestQuizPercent({ bestQuizPercentage: 79.6 })).toBe(80)
  })
})

describe('filterLessonsByMastery / sortLessons', () => {
  it('filters by mastery level', () => {
    expect(filterLessonsByMastery(lessons, 'ALL')).toHaveLength(4)
    expect(filterLessonsByMastery(lessons, 'MASTERED').map((item) => item.lessonId)).toEqual([12])
  })

  it('keeps the server order for recent and sorts the others', () => {
    expect(sortLessons(lessons, 'recent').map((item) => item.lessonId)).toEqual([10, 12, 13, 11])
    expect(sortLessons(lessons, 'needsAttention').map((item) => item.lessonId)).toEqual([11, 10, 13, 12])
    expect(sortLessons(lessons, 'mastery').map((item) => item.lessonId)).toEqual([12, 13, 10, 11])
    expect(sortLessons(lessons, 'title').map((item) => item.lessonTitle)).toEqual([
      'Greetings',
      'Numbers 1-10',
      'Ordering Food at the Canteen',
      'University Life',
    ])
  })

  it('does not mutate the input', () => {
    const copy = [...lessons]
    sortLessons(lessons, 'title')
    expect(lessons).toEqual(copy)
  })
})

describe('resolveWeakAreas', () => {
  it('links weak-area lesson titles to matching lessons and drops blanks/duplicates', () => {
    expect(
      resolveWeakAreas(['Greetings', '  ordering food at the  canteen ', 'Tones', '', 'greetings', null], lessons),
    ).toEqual([
      { title: 'Greetings', lessonId: 10 },
      { title: 'ordering food at the  canteen', lessonId: 13 },
      { title: 'Tones', lessonId: null },
    ])
  })

  it('handles missing weak areas', () => {
    expect(resolveWeakAreas(undefined, lessons)).toEqual([])
  })
})

describe('lessonRoute', () => {
  it('opens the lesson player with the lesson id query', () => {
    expect(lessonRoute(12)).toEqual({ path: '/student/learn', query: { lessonId: '12' } })
  })
})
