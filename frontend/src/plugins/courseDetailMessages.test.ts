import { describe, expect, it } from 'vitest'

import { courseDetailMessages } from './courseDetailMessages'

describe('course detail translations', () => {
  it('covers each course action and explanation in every supported language', () => {
    const englishKeys = Object.keys(courseDetailMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(courseDetailMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(courseDetailMessages[locale][key as keyof typeof courseDetailMessages.en].trim()).not.toBe('')
      }
    }
  })
})
