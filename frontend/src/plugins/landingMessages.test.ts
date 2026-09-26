import { describe, expect, it } from 'vitest'

import { landingMessages } from './landingMessages'

describe('landing page translations', () => {
  it('has a nonempty translation for every key in each supported language', () => {
    const englishKeys = Object.keys(landingMessages.en).sort()

    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(landingMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(landingMessages[locale][key as keyof typeof landingMessages.en].trim()).not.toBe('')
      }
    }
  })
})
