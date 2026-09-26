import { describe, expect, it } from 'vitest'
import { teacherStudentsMessages } from './teacherStudentsMessages'

describe('teacher students messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherStudentsMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(teacherStudentsMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(teacherStudentsMessages[locale][key as keyof typeof teacherStudentsMessages.en].trim()).not.toBe('')
    }
  })
})
