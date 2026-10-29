import { describe, expect, it } from 'vitest'

import { statusLabelKey, statusLabelMessages } from '@/plugins/statusLabelMessages'
import type { LessonMastery } from '@/types/api'

import { MASTERY_ICONS, MASTERY_TONES, normalizeMastery } from './mastery'

/** Every mastery level, from not started to mastered. */
const LEVELS = Object.keys(MASTERY_TONES) as LessonMastery[]

describe('lesson mastery helpers (FRPROG02)', () => {
  it('normalises API values and treats unknown values as not started', () => {
    expect(normalizeMastery('mastered')).toBe('MASTERED')
    expect(normalizeMastery(' IN_PROGRESS ')).toBe('IN_PROGRESS')
    expect(normalizeMastery('SOMETHING_ELSE')).toBe('NOT_STARTED')
    expect(normalizeMastery(null)).toBe('NOT_STARTED')
  })

  it('maps every mastery level to a distinct tone and icon', () => {
    expect(LEVELS.map((level) => MASTERY_TONES[level])).toEqual(['neutral', 'info', 'success', 'mastered'])
    expect(new Set(LEVELS.map((level) => MASTERY_ICONS[level])).size).toBe(4)
    expect(LEVELS.map(normalizeMastery)).toEqual(LEVELS)
  })

  it('has a translated label for every mastery level in every locale', () => {
    // Key parity and non-empty text in every locale are checked for every bundle in messageBundles.test.ts.
    for (const mastery of LEVELS) expect(statusLabelKey('mastery', mastery), mastery).not.toBeNull()
    expect(statusLabelMessages['zh-CN'].mastery_MASTERED).toBe('已掌握')
  })
})
