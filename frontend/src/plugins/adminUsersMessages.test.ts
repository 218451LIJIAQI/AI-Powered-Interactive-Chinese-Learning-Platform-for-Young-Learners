import { describe, expect, it } from 'vitest'
import { adminUsersMessages } from './adminUsersMessages'

describe('admin users messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminUsersMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminUsersMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(adminUsersMessages[locale][key as keyof typeof adminUsersMessages.en].trim()).not.toBe('')
    }
  })
})
