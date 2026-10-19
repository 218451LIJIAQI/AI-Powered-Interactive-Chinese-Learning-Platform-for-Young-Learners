import { describe, expect, it } from 'vitest'
import { studentProfileMessages } from './studentProfileMessages'

describe('student profile messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentProfileMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentProfileMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentProfileMessages[locale][key as keyof typeof studentProfileMessages.en].trim()).not.toBe('')
    }
  })
})
