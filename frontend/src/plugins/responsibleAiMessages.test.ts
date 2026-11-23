import { describe, expect, it } from 'vitest'

import { legalMessages } from './legalMessages'
import { responsibleAiMessages } from './responsibleAiMessages'

const LOCALES = ['en', 'ms', 'zh-CN'] as const
/** What only the OpenAI Responses client does. */
const OPENAI_ONLY = /OpenAI|store=false/

// Key parity and non-empty translations are checked for every bundle in messageBundles.test.ts.
describe('responsible AI guidance translations', () => {
  it('says the safety checks are on by default (PlatformSettingsService seeds both switches on)', () => {
    expect(responsibleAiMessages.en.safetyCopy).toContain('on unless an admin turns them off')
    expect(responsibleAiMessages.en.safetyCopy).not.toMatch(/when an admin turns it on/i)
  })

  it('says the level and the lesson topic are sent too, as the Help and Privacy pages do', () => {
    expect(responsibleAiMessages.en.dataWhat).toContain('relevant context such as your level and the lesson topic')
    expect(responsibleAiMessages.en.dataWhatText).toContain('relevant context such as your level and the lesson topic')
    const { aiOpenAi, aiOther, aiUnknown } = legalMessages.en.privacy
    for (const copy of [aiOpenAi, aiOther, aiUnknown]) {
      expect(copy).toContain('plus your level and the lesson topic')
    }
  })

  it('names no teacher in any language: classes are made by the MandarinSpark team', () => {
    for (const locale of LOCALES) {
      for (const copy of Object.values(responsibleAiMessages[locale])) {
        expect(copy, locale).not.toMatch(/teacher|guru|老师/i)
      }
    }
  })

  it('takes the AI model from the server setting and has a wording that names no model', () => {
    for (const locale of LOCALES) {
      const messages = responsibleAiMessages[locale]
      expect(messages.targetModel).toContain('{model}')
      expect(messages.targetOpenAi).not.toContain('{model}')
      expect(messages.targetModel).toContain('{target}')
      expect(messages.targetOther).not.toContain('{model}')
      for (const copy of Object.values(messages)) expect(copy).not.toMatch(/GPT|Luna/i)
    }
  })

  it('keeps the OpenAI-specific handling out of the wording for other providers, AI off and an unknown provider', () => {
    for (const locale of LOCALES) {
      const ai = responsibleAiMessages[locale]
      const privacy = legalMessages[locale].privacy
      expect(ai.dataOpenAi, locale).toContain('store=false')
      expect(privacy.aiOpenAi, locale).toContain('store=false')
      for (const copy of [
        ai.dataWhat,
        ai.dataWhatText,
        ai.dataOther,
        ai.dataOff,
        ai.dataUnknown,
        ai.dataMinimise,
        ai.targetOther,
      ]) {
        expect(copy, locale).not.toMatch(OPENAI_ONLY)
      }
      for (const copy of [privacy.aiOther, privacy.aiOff, privacy.aiUnknown]) {
        expect(copy, locale).not.toMatch(OPENAI_ONLY)
      }
    }
  })
})
