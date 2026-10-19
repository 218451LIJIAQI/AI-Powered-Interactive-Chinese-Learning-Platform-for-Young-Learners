import { describe, expect, it } from 'vitest'

import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'

import {
  buildFollowUpSuggestion,
  followUpFeedbackType,
  lessonMasteryRows,
  shouldApplySuggestion,
  studentNeedsSupport,
  studentSupportReasons,
  weakAreaLessons,
  type Translate,
} from './teacherRoster'

const english = teacherStudentsMessages.en as Record<string, string>
const translate: Translate = (key, params = {}) =>
  (english[key] ?? key).replace(/\{(\w+)\}/g, (_match, name: string) => String(params[name] ?? ''))

describe('teacher roster: needs support', () => {
  it('uses only needsSupport / supportReasons, never received feedback', () => {
    expect(
      studentNeedsSupport({
        needsSupport: false,
        supportReasons: [],
        // A learner who just received corrective feedback is NOT flagged.
        ...{ latestFeedbackType: 'CORRECTIVE', latestFeedbackAt: '2026-05-18T04:30:00Z' },
      }),
    ).toBe(false)
    expect(studentNeedsSupport({ needsSupport: true, supportReasons: [] })).toBe(true)
    expect(studentNeedsSupport({ needsSupport: false, supportReasons: ['INACTIVE'] })).toBe(true)
    expect(studentNeedsSupport(null)).toBe(false)
  })

  it('keeps known reasons in canonical order without duplicates', () => {
    expect(
      studentSupportReasons({
        needsSupport: true,
        supportReasons: ['INACTIVE', 'LOW_QUIZ_SCORE', 'INACTIVE', 'SOMETHING_ELSE' as never],
      }),
    ).toEqual(['LOW_QUIZ_SCORE', 'INACTIVE'])
  })
})

describe('teacher roster: lesson mastery', () => {
  it('normalises and sorts lessons weakest first', () => {
    const rows = lessonMasteryRows([
      {
        lessonId: 12,
        lessonTitle: 'Numbers 1-10',
        completionPercent: 100,
        mastery: 'MASTERED',
        bestQuizPercentage: 90,
      },
      {
        lessonId: 10,
        lessonTitle: 'Greetings',
        completionPercent: 130,
        mastery: 'IN_PROGRESS',
        bestQuizPercentage: null,
      },
      { lessonId: 13, lessonTitle: ' ', completionPercent: 0, mastery: 'BOGUS' as never, bestQuizPercentage: 40 },
    ])

    expect(rows.map((row) => [row.lessonTitle, row.mastery, row.completionPercent, row.bestQuizPercentage])).toEqual([
      ['#13', 'NOT_STARTED', 0, 40],
      ['Greetings', 'IN_PROGRESS', 100, null],
      ['Numbers 1-10', 'MASTERED', 100, 90],
    ])
  })

  it('returns weak-area lesson titles trimmed and de-duplicated', () => {
    expect(weakAreaLessons([' Greetings ', 'Greetings', '', 'Numbers 1-10'], 4)).toEqual(['Greetings', 'Numbers 1-10'])
    expect(weakAreaLessons(undefined)).toEqual([])
  })
})

describe('teacher roster: follow-up message suggestion', () => {
  it('maps tone and focus to a feedback type', () => {
    expect(followUpFeedbackType('CORRECTIVE', 'WEAK_AREAS')).toBe('CORRECTIVE')
    expect(followUpFeedbackType('ENCOURAGING', 'QUIZ_ACCURACY')).toBe('QUIZ')
    expect(followUpFeedbackType('SPECIFIC', 'WEAK_AREAS')).toBe('WEAK_AREA')
    expect(followUpFeedbackType('ENCOURAGING', 'CONSISTENCY')).toBe('GENERAL')
  })

  it('builds the suggestion from the weak lesson title (not lower-cased) or the quiz average', () => {
    expect(
      buildFollowUpSuggestion(
        { name: 'Zhang', tone: 'SPECIFIC', focus: 'WEAK_AREAS', weakLesson: 'Numbers 1-10' },
        translate,
      ),
    ).toBe(
      'For the next step, focus on the lesson "Numbers 1-10". Zhang can improve with short, repeatable practice and one teacher check-in.',
    )
    expect(
      buildFollowUpSuggestion(
        { name: 'Zhang', tone: 'ENCOURAGING', focus: 'QUIZ_ACCURACY', quizAverageLabel: '54%' },
        translate,
      ),
    ).toContain('their 54% quiz average')
    expect(buildFollowUpSuggestion({ name: 'Zhang', tone: 'CORRECTIVE', focus: 'QUIZ_ACCURACY' }, translate)).toContain(
      'their recent activities',
    )
  })

  it('never overwrites text the teacher typed (A5 #11)', () => {
    expect(shouldApplySuggestion('', null)).toBe(true)
    expect(shouldApplySuggestion('   ', 'Old suggestion')).toBe(true)
    expect(shouldApplySuggestion('Old suggestion', 'Old suggestion')).toBe(true)
    expect(shouldApplySuggestion('Old suggestion, plus my own note', 'Old suggestion')).toBe(false)
    expect(shouldApplySuggestion('My own note', null)).toBe(false)
  })
})
