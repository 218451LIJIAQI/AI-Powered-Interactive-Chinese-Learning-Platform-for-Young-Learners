import { describe, expect, it } from 'vitest'

import { studentHomeMessages } from './studentHomeMessages'

describe('student home translations', () => {
  it('has a nonempty translation for every key in each supported language', () => {
    const englishKeys = Object.keys(studentHomeMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentHomeMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(studentHomeMessages[locale][key as keyof typeof studentHomeMessages.en].trim()).not.toBe('')
      }
    }
  })
})
