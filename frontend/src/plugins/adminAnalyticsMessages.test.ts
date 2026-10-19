import { describe, expect, it } from 'vitest'

import { adminAnalyticsMessages } from './adminAnalyticsMessages'

describe('admin analytics translations', () => {
  it('has a nonempty translation for every key in each supported language', () => {
    const englishKeys = Object.keys(adminAnalyticsMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(adminAnalyticsMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(adminAnalyticsMessages[locale][key as keyof typeof adminAnalyticsMessages.en].trim()).not.toBe('')
      }
    }
  })
})
