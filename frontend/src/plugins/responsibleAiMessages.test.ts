import { describe, expect, it } from 'vitest'

import { responsibleAiMessages } from './responsibleAiMessages'

describe('responsible AI guidance translations', () => {
  it('has a nonempty translation for every key in each supported language', () => {
    const englishKeys = Object.keys(responsibleAiMessages.en).sort()

    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(responsibleAiMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(responsibleAiMessages[locale][key as keyof typeof responsibleAiMessages.en].trim()).not.toBe('')
      }
    }
  })

  it('states the official title, provider safeguards and the teacher disclaimer', () => {
    expect(responsibleAiMessages.en.officialTitle).toBe(
      'AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia',
    )
    expect(responsibleAiMessages.en.dataCopy).toContain('store=false')
    expect(responsibleAiMessages.en.limitsCopy).toContain('do not replace your teacher')
  })
})
