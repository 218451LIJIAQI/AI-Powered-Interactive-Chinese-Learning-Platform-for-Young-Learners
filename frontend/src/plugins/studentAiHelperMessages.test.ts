import { describe, expect, it } from 'vitest'
import { studentAiHelperMessages } from './studentAiHelperMessages'

describe('student AI helper messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentAiHelperMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentAiHelperMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(studentAiHelperMessages[locale][key as keyof typeof studentAiHelperMessages.en].trim()).not.toBe('')
    }
  })
})
