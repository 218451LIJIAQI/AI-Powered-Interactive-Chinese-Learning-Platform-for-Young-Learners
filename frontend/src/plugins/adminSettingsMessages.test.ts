import { describe, expect, it } from 'vitest'
import { adminSettingsMessages } from './adminSettingsMessages'

describe('admin settings messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminSettingsMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminSettingsMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(adminSettingsMessages[locale][key as keyof typeof adminSettingsMessages.en].trim()).not.toBe('')
    }
  })
})
