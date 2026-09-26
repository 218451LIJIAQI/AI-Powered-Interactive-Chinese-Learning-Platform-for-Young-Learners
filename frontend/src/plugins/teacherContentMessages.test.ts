import { describe, expect, it } from 'vitest'
import { teacherContentMessages } from './teacherContentMessages'

describe('teacher content messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherContentMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(teacherContentMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(teacherContentMessages[locale][key as keyof typeof teacherContentMessages.en].trim()).not.toBe('')
      }
    }
  })
})
