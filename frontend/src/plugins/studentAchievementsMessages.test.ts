import { describe, expect, it } from 'vitest'
import { studentAchievementsMessages } from './studentAchievementsMessages'

describe('student achievements messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentAchievementsMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentAchievementsMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(studentAchievementsMessages[locale][key as keyof typeof studentAchievementsMessages.en].trim()).not.toBe(
          '',
        )
      }
    }
  })
})
