import { describe, expect, it } from 'vitest'

import { systemMessages } from './systemMessages'

describe('system messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(systemMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(systemMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(systemMessages[locale][key as keyof typeof systemMessages.en].trim()).not.toBe('')
      }
    }
  })
})
