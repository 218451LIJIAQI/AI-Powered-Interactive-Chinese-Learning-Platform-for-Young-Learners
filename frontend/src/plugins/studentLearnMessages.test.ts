import { describe, expect, it } from 'vitest'
import { studentLearnMessages } from './studentLearnMessages'

describe('student learn messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentLearnMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentLearnMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentLearnMessages[locale][key as keyof typeof studentLearnMessages.en].trim()).not.toBe('')
    }
  })
})
