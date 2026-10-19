import { describe, expect, it } from 'vitest'
import { teacherClassesMessages } from './teacherClassesMessages'

describe('teacher classes messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherClassesMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(teacherClassesMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(teacherClassesMessages[locale][key as keyof typeof teacherClassesMessages.en].trim()).not.toBe('')
      }
    }
  })
})
