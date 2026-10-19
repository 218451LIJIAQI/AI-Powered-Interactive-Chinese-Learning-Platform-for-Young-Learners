import { describe, expect, it } from 'vitest'
import { teacherFeedbackMessages } from './teacherFeedbackMessages'

describe('teacher feedback messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherFeedbackMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(teacherFeedbackMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(teacherFeedbackMessages[locale][key as keyof typeof teacherFeedbackMessages.en].trim()).not.toBe('')
      }
    }
  })
})
