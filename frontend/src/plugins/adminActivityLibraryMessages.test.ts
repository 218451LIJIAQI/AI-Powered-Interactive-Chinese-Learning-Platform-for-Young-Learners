import { describe, expect, it } from 'vitest'

import { adminActivityLibraryMessages } from './adminActivityLibraryMessages'

describe('admin activity library messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminActivityLibraryMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminActivityLibraryMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(
          adminActivityLibraryMessages[locale][key as keyof typeof adminActivityLibraryMessages.en].trim(),
        ).not.toBe('')
      }
    }
  })
})
