import { describe, expect, it } from 'vitest'

import { adminDashboardMessages } from './adminDashboardMessages'

describe('admin dashboard translations', () => {
  it('has a nonempty translation for every key in each supported language', () => {
    const englishKeys = Object.keys(adminDashboardMessages.en).sort()

    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminDashboardMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(adminDashboardMessages[locale][key as keyof typeof adminDashboardMessages.en].trim()).not.toBe('')
      }
    }
  })
})
