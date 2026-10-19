import { describe, expect, it } from 'vitest'

import { adminContentMessages } from './adminContentMessages'

describe('admin content messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminContentMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(adminContentMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(adminContentMessages[locale][key as keyof typeof adminContentMessages.en].trim()).not.toBe('')
      }
    }
  })
})
