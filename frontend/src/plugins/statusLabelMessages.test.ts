import { describe, expect, it } from 'vitest'

import { humanizeEnumValue, statusLabelKey, statusLabelMessages } from './statusLabelMessages'

describe('status label messages', () => {
  it('keeps EN / MS / zh-CN keys in parity with no empty strings', () => {
    const englishKeys = Object.keys(statusLabelMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(statusLabelMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(statusLabelMessages[locale][key as keyof typeof statusLabelMessages.en].trim()).not.toBe('')
      }
    }
  })

  it('translates two-word enum values instead of formatting them twice (A5 #15)', () => {
    expect(statusLabelKey('feedbackType', 'WEAK_AREA')).toBe('feedbackType_WEAK_AREA')
    expect(statusLabelKey('feedbackType', 'weak_area')).toBe('feedbackType_WEAK_AREA')
    expect(statusLabelMessages.ms.feedbackType_WEAK_AREA).toBe('Bahagian lemah')
    expect(statusLabelMessages['zh-CN'].feedbackType_CORRECTIVE).toBe('纠正')
  })

  it('labels every quiz question type, points source and practice activity type the backend sends', () => {
    const groups = {
      questionType: [
        'MULTIPLE_CHOICE',
        'TRUE_FALSE',
        'FILL_BLANK',
        'MATCH_WORD_MEANING',
        'MATCH_WORD_IMAGE',
        'LISTEN_CHOOSE',
        'REARRANGE_SENTENCE',
        'CHOOSE_PINYIN',
        'CHOOSE_TONE',
        'CHARACTER_MEANING',
      ],
      pointsSource: [
        'QUIZ_COMPLETION',
        'QUIZ_BONUS',
        'LESSON_COMPLETION',
        'PLATFORM_FLASHCARD',
        'PLATFORM_MATCHING_GAME',
        'PLATFORM_LISTENING_EXERCISE',
        'PLATFORM_TRACING_EXERCISE',
        'PLATFORM_DRAG_DROP_ACTIVITY',
        'PLATFORM_STORY_ACTIVITY',
      ],
      activityType: [
        'FLASHCARD',
        'MATCHING_GAME',
        'LISTENING_EXERCISE',
        'TRACING_EXERCISE',
        'DRAG_DROP_ACTIVITY',
        'STORY_ACTIVITY',
      ],
    } as const
    for (const [group, values] of Object.entries(groups) as [keyof typeof groups, readonly string[]][]) {
      for (const value of values) expect(statusLabelKey(group, value), `${group}:${value}`).not.toBeNull()
    }
    expect(statusLabelMessages['zh-CN'].questionType_FILL_BLANK).toBe('填空题')
    expect(statusLabelMessages.ms.pointsSource_QUIZ_COMPLETION).toBe('Kuiz diselesaikan')
  })

  it('returns null for unknown values and humanises them as a fallback', () => {
    expect(statusLabelKey('enrolment', 'WAITLISTED')).toBeNull()
    expect(statusLabelKey('enrolment', '')).toBeNull()
    expect(humanizeEnumValue('WAITLISTED_LATE')).toBe('Waitlisted Late')
    expect(humanizeEnumValue(null)).toBe('')
  })
})
