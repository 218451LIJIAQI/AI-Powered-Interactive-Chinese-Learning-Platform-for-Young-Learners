import { describe, expect, it } from 'vitest'
import { studentPracticeMessages } from './studentPracticeMessages'

describe('student practice messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentPracticeMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(studentPracticeMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentPracticeMessages[locale][key as keyof typeof studentPracticeMessages.en].trim()).not.toBe('')
    }
  })
})
