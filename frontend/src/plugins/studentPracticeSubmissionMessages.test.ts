import { describe, expect, it } from 'vitest'
import { studentPracticeSubmissionMessages } from './studentPracticeSubmissionMessages'

describe('student practice submission messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentPracticeSubmissionMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentPracticeSubmissionMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(
          studentPracticeSubmissionMessages[locale][key as keyof typeof studentPracticeSubmissionMessages.en].trim(),
        ).not.toBe('')
      }
    }
  })
})
