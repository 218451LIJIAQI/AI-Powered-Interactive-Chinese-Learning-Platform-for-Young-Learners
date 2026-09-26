import { describe, expect, it } from 'vitest'

import { paymentMessages } from './paymentMessages'

describe('payment result translations', () => {
  it('covers each payment state in every supported language', () => {
    const englishKeys = Object.keys(paymentMessages.en).sort()

    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(paymentMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(paymentMessages[locale][key as keyof typeof paymentMessages.en].trim()).not.toBe('')
      }
    }
  })
})
