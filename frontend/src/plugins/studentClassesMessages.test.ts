import { describe, expect, it } from 'vitest'
import { studentClassesMessages } from './studentClassesMessages'

describe('student classes messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentClassesMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(studentClassesMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentClassesMessages[locale][key as keyof typeof studentClassesMessages.en].trim()).not.toBe('')
    }
  })
})
