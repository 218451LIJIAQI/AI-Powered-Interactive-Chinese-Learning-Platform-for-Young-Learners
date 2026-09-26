import { describe, expect, it } from 'vitest'

import { recoveryMessages } from './recoveryMessages'

describe('account recovery translations', () => {
  it('covers every recovery message in each supported language', () => {
    for (const section of ['forgot', 'reset'] as const) {
      const englishKeys = Object.keys(recoveryMessages.en[section]).sort()

      for (const locale of ['ms', 'zh'] as const) {
        expect(Object.keys(recoveryMessages[locale][section]).sort()).toEqual(englishKeys)
        for (const key of englishKeys) {
          expect(
            recoveryMessages[locale][section][key as keyof (typeof recoveryMessages.en)[typeof section]].trim(),
          ).not.toBe('')
        }
      }
    }
  })
})
