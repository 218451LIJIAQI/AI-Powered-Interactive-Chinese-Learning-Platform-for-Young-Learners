import { describe, expect, it } from 'vitest'

import { legalMessages } from './legalMessages'

describe('legal page translations', () => {
  it('covers the same sections in all supported languages', () => {
    const englishSections = Object.keys(legalMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(legalMessages[locale]).sort()).toEqual(englishSections)
      expect(legalMessages[locale].back.trim()).not.toBe('')

      for (const section of ['terms', 'privacy'] as const) {
        const englishKeys = Object.keys(legalMessages.en[section]).sort()
        expect(Object.keys(legalMessages[locale][section]).sort()).toEqual(englishKeys)
        for (const key of englishKeys) {
          expect(
            legalMessages[locale][section][key as keyof (typeof legalMessages.en)[typeof section]].trim(),
          ).not.toBe('')
        }
      }
    }
  })
})
