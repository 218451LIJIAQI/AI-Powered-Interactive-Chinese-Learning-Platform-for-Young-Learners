import { describe, expect, it } from 'vitest'

import { teacherEarningsMessages } from './teacherEarningsMessages'

describe('teacher earnings messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherEarningsMessages.en).sort()

    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(teacherEarningsMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(teacherEarningsMessages[locale][key as keyof typeof teacherEarningsMessages.en].trim()).not.toBe('')
      }
    }
  })
})
