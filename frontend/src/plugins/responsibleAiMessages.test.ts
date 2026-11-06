import { describe, expect, it } from 'vitest'

import { responsibleAiMessages } from './responsibleAiMessages'

const LOCALES = ['en', 'ms', 'zh-CN'] as const

// Key parity and non-empty translations are checked for every bundle in messageBundles.test.ts.
describe('responsible AI guidance translations', () => {
  it('states the provider safeguards and the teacher disclaimer', () => {
    expect(responsibleAiMessages.en.dataCopy).toContain('store=false')
    expect(responsibleAiMessages.en.disclaimer).toContain('do not replace your teacher')
  })

  it('takes the AI model from the server setting and has a wording that names no model', () => {
    for (const locale of LOCALES) {
      const messages = responsibleAiMessages[locale]
      expect(messages.dataCopy).toContain('{model}')
      expect(messages.dataCopyNoModel).not.toContain('{model}')
      for (const copy of [messages.dataCopy, messages.dataCopyNoModel]) {
        expect(copy).not.toMatch(/GPT|Luna/i)
        expect(copy).toContain('store=false')
      }
    }
  })
})
