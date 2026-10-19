import { describe, expect, it } from 'vitest'
import { teacherProfileMessages } from './teacherProfileMessages'

describe('teacher profile messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherProfileMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(teacherProfileMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(teacherProfileMessages[locale][key as keyof typeof teacherProfileMessages.en].trim()).not.toBe('')
      }
    }
  })
})
