import { describe, expect, it } from 'vitest'

import { teacherAccessMessages } from './teacherAccessMessages'
import { teacherDashboardMessages } from './teacherDashboardMessages'

function expectCompleteTranslations(messages: Record<string, Record<string, string>>) {
  const englishKeys = Object.keys(messages.en ?? {}).sort()
  for (const locale of ['ms', 'zh-CN']) {
    expect(Object.keys(messages[locale] ?? {}).sort()).toEqual(englishKeys)
    for (const key of englishKeys) {
      expect(messages[locale]?.[key]?.trim()).not.toBe('')
    }
  }
}

describe('teacher workspace translations', () => {
  it('keeps dashboard keys complete', () => {
    expectCompleteTranslations(teacherDashboardMessages)
  })

  it('keeps teacher approval keys complete', () => {
    expectCompleteTranslations(teacherAccessMessages)
  })
})
