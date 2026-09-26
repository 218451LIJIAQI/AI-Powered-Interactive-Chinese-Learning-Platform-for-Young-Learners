import { describe, expect, it } from 'vitest'
import { adminModerationMessages } from './adminModerationMessages'

describe('admin moderation messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminModerationMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminModerationMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(adminModerationMessages[locale][key as keyof typeof adminModerationMessages.en].trim()).not.toBe('')
    }
  })
})
