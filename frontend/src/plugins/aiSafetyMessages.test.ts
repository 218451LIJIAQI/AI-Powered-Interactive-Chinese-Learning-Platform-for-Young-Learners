import { describe, expect, it } from 'vitest'

import { AI_REPORT_REASON_KEYS, aiSafetyMessages } from './aiSafetyMessages'

describe('shared AI safety messages', () => {
  it('has a nonempty translation for every key in each supported language', () => {
    const englishKeys = Object.keys(aiSafetyMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(aiSafetyMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(aiSafetyMessages[locale][key as keyof typeof aiSafetyMessages.en].trim()).not.toBe('')
      }
    }
  })

  it('labels exactly the five report reasons accepted by the backend', () => {
    expect(Object.keys(AI_REPORT_REASON_KEYS).sort()).toEqual(
      ['HALLUCINATION', 'INAPPROPRIATE_CONTENT', 'INCORRECT_CONTENT', 'OTHER', 'UNSAFE_CONTENT'].sort(),
    )
    for (const key of Object.values(AI_REPORT_REASON_KEYS)) {
      expect(aiSafetyMessages.en[key]).toBeTruthy()
    }
  })

  it('uses the agreed default disclaimer', () => {
    expect(aiSafetyMessages.en.disclaimer).toBe('AI responses may contain errors and do not replace your teacher.')
  })
})
