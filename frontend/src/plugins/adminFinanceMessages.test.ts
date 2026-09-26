import { describe, expect, it } from 'vitest'
import { adminFinanceMessages } from './adminFinanceMessages'

describe('admin finance messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminFinanceMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminFinanceMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(adminFinanceMessages[locale][key as keyof typeof adminFinanceMessages.en].trim()).not.toBe('')
    }
  })
})
