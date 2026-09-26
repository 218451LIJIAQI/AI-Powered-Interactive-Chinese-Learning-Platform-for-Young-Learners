import { describe, expect, it } from 'vitest'
import { adminTeacherVerificationMessages } from './adminTeacherVerificationMessages'

describe('admin teacher verification messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(adminTeacherVerificationMessages.en).sort()
    for (const locale of ['ms', 'zh'] as const) {
      expect(Object.keys(adminTeacherVerificationMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(
          adminTeacherVerificationMessages[locale][key as keyof typeof adminTeacherVerificationMessages.en].trim(),
        ).not.toBe('')
    }
  })
})
