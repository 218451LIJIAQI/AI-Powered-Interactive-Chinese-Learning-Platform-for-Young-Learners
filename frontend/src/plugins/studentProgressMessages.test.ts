import { describe, expect, it } from 'vitest'
import { studentProgressMessages } from './studentProgressMessages'

describe('student progress messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentProgressMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentProgressMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentProgressMessages[locale][key as keyof typeof studentProgressMessages.en].trim()).not.toBe('')
    }
  })
})
