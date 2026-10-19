import { describe, expect, it } from 'vitest'

import { resourceSectionMessages } from './resourceSectionMessages'

describe('resource section messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(resourceSectionMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(resourceSectionMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(resourceSectionMessages[locale][key as keyof typeof resourceSectionMessages.en].trim()).not.toBe('')
      }
    }
  })
})
