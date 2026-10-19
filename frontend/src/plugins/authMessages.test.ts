import { describe, expect, it } from 'vitest'

import { authMessages } from './authMessages'

describe('authentication translations', () => {
  it('covers every authentication message in each supported language', () => {
    const englishKeys = Object.keys(authMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(authMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(authMessages[locale][key as keyof typeof authMessages.en].trim()).not.toBe('')
      }
    }
  })
})
