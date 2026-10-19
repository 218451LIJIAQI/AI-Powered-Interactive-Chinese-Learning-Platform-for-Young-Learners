import { describe, expect, it } from 'vitest'

import { catalogMessages } from './catalogMessages'

describe('catalog translations', () => {
  it('covers each catalog action and state in every supported language', () => {
    const englishKeys = Object.keys(catalogMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(catalogMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(catalogMessages[locale][key as keyof typeof catalogMessages.en].trim()).not.toBe('')
      }
    }
  })
})
