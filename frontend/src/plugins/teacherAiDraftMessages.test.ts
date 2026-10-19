import { describe, expect, it } from 'vitest'

import { teacherAiDraftMessages } from './teacherAiDraftMessages'

describe('teacher AI draft messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(teacherAiDraftMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(teacherAiDraftMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys)
        expect(teacherAiDraftMessages[locale][key as keyof typeof teacherAiDraftMessages.en].trim()).not.toBe('')
    }
  })
})
