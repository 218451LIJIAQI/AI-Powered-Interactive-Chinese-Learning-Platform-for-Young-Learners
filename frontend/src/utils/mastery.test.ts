import { describe, expect, it } from 'vitest'

import { statusLabelKey, statusLabelMessages } from '@/plugins/statusLabelMessages'

import { LESSON_MASTERY_ORDER, masteryIcon, masteryRank, masteryTone, normalizeMastery } from './mastery'

describe('lesson mastery helpers (D25)', () => {
  it('normalises API values and treats unknown values as not started', () => {
    expect(normalizeMastery('mastered')).toBe('MASTERED')
    expect(normalizeMastery(' IN_PROGRESS ')).toBe('IN_PROGRESS')
    expect(normalizeMastery('SOMETHING_ELSE')).toBe('NOT_STARTED')
    expect(normalizeMastery(null)).toBe('NOT_STARTED')
  })

  it('maps every mastery level to a distinct tone, icon and rank', () => {
    expect(LESSON_MASTERY_ORDER.map(masteryTone)).toEqual(['neutral', 'info', 'success', 'mastered'])
    expect(new Set(LESSON_MASTERY_ORDER.map(masteryIcon)).size).toBe(4)
    expect(LESSON_MASTERY_ORDER.map(masteryRank)).toEqual([0, 1, 2, 3])
  })

  it('has a translated label for every mastery level in every locale', () => {
    for (const mastery of LESSON_MASTERY_ORDER) {
      const key = statusLabelKey('mastery', mastery)
      expect(key).not.toBeNull()
      for (const locale of ['en', 'ms', 'zh-CN'] as const) {
        expect(statusLabelMessages[locale][key!].trim()).not.toBe('')
      }
    }
    expect(statusLabelMessages['zh-CN'].mastery_MASTERED).toBe('已掌握')
  })
})
