import { describe, expect, it } from 'vitest'

import { APP_LANGUAGES } from '@/types/api'

import { aiDisclosureMessages } from './aiDisclosureMessages'
import { legalMessages } from './legalMessages'
import { responsibleAiMessages } from './responsibleAiMessages'
import { safetyMessages } from './safetyMessages'

/** What only the OpenAI Responses client does. */
const OPENAI_ONLY = /OpenAI|store=false/

// Key parity and non-empty translations are checked for every bundle in messageBundles.test.ts.
describe('responsible AI guidance translations', () => {
  it('says the safety checks are on by default in every locale (PlatformSettingsService seeds both switches on)', () => {
    expect(responsibleAiMessages.en.safetyCopy).toContain('on unless an admin turns them off')
    expect(responsibleAiMessages.en.safetyCopy).not.toMatch(/when an admin turns it on/i)
    expect(responsibleAiMessages.ms.safetyCopy).toContain('dihidupkan melainkan pentadbir mematikannya')
    expect(responsibleAiMessages['zh-CN'].safetyCopy).toContain('默认开启')
  })

  it('says the level and the lesson topic are sent too, in every locale, as the Help and Privacy pages do', () => {
    expect(responsibleAiMessages.en.dataWhat).toContain('relevant context such as your level and the lesson topic')
    expect(responsibleAiMessages.en.dataWhatText).toContain('relevant context such as your level and the lesson topic')
    expect(responsibleAiMessages.ms.dataWhat).toContain('tahap anda dan topik pelajaran')
    expect(responsibleAiMessages.ms.dataWhatText).toContain('tahap anda dan topik pelajaran')
    expect(responsibleAiMessages['zh-CN'].dataWhat).toContain('你的水平和课时主题')
    expect(responsibleAiMessages['zh-CN'].dataWhatText).toContain('你的水平和课时主题')
    const { aiOpenAi, aiOther, aiUnknown } = legalMessages.en.privacy
    for (const copy of [aiOpenAi, aiOther, aiUnknown]) {
      expect(copy).toContain('plus your level and the lesson topic')
    }
  })

  it('names no teacher in any language across responsible AI, legal, and safety guidance', () => {
    for (const locale of APP_LANGUAGES) {
      for (const copy of [
        ...Object.values(responsibleAiMessages[locale]),
        ...Object.values(safetyMessages[locale]),
        ...Object.values(legalMessages[locale].terms),
        ...Object.values(legalMessages[locale].privacy),
      ]) {
        expect(copy, locale).not.toMatch(/\bteachers?\b|\bguru\b|老师/i)
      }
    }
  })

  it('takes the AI model from the server setting and has a wording that names no model', () => {
    for (const locale of APP_LANGUAGES) {
      const messages = responsibleAiMessages[locale]
      expect(messages.targetModel).toContain('{model}')
      expect(messages.targetOpenAi).not.toContain('{model}')
      expect(messages.targetModel).toContain('{target}')
      expect(messages.targetOther).not.toContain('{model}')
      for (const copy of Object.values(messages)) expect(copy).not.toMatch(/GPT|Luna/i)
    }
  })

  it('keeps the OpenAI-specific handling out of the wording for other providers, AI off and an unknown provider', () => {
    for (const locale of APP_LANGUAGES) {
      const ai = responsibleAiMessages[locale]
      const disclosure = aiDisclosureMessages[locale]
      const privacy = legalMessages[locale].privacy
      expect(disclosure.openAi, locale).toContain('store=false')
      expect(privacy.aiOpenAi, locale).toContain('store=false')
      for (const copy of [
        ai.dataWhat,
        ai.dataWhatText,
        disclosure.other,
        disclosure.off,
        ai.dataUnknown,
        ai.dataMinimise,
        ai.targetOther,
      ]) {
        expect(copy, locale).not.toMatch(OPENAI_ONLY)
      }
      for (const copy of [privacy.aiOther, privacy.aiUnknown]) {
        expect(copy, locale).not.toMatch(OPENAI_ONLY)
      }
    }
  })
})
