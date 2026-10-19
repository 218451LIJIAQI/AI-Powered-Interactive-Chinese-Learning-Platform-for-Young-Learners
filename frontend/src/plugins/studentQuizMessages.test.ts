import { describe, expect, it } from 'vitest'
import { studentQuizMessages } from './studentQuizMessages'

describe('student quiz messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentQuizMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentQuizMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentQuizMessages[locale][key as keyof typeof studentQuizMessages.en].trim()).not.toBe('')
    }
  })
})
