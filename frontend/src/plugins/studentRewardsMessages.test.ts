import { describe, expect, it } from 'vitest'
import { studentRewardsMessages } from './studentRewardsMessages'

describe('student rewards messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentRewardsMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(studentRewardsMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentRewardsMessages[locale][key as keyof typeof studentRewardsMessages.en].trim()).not.toBe('')
    }
  })
})
