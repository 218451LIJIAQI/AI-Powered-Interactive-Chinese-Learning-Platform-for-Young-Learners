import { describe, expect, it } from 'vitest'

import { aiFeatureMessages } from '@/plugins/aiFeatureMessages'

import { AI_FEATURE_MESSAGE_KEYS, aiFeatureLabel } from './adminDisplay'

describe('admin display helpers', () => {
  it('names AI features by their message keys, ignoring case, and humanises unknown values', () => {
    const translate = (key: string) => `t:${key}`
    expect(aiFeatureLabel('CHAT_TUTOR', translate)).toBe('t:aiFeatures.chatTutor')
    expect(aiFeatureLabel(' learning_plan ', translate)).toBe('t:aiFeatures.learningPlan')
    expect(aiFeatureLabel('SOMETHING_NEW', translate)).toBe('Something New')
  })

  it('only points at full keys the aiFeatures bundle defines', () => {
    // Key parity and non-empty text in every locale are checked for every bundle in messageBundles.test.ts.
    for (const key of Object.values(AI_FEATURE_MESSAGE_KEYS)) {
      expect(key).toMatch(/^aiFeatures\./)
      expect(Object.keys(aiFeatureMessages.en)).toContain(key.replace('aiFeatures.', ''))
    }
  })
})
