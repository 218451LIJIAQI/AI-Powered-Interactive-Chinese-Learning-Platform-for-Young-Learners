import { describe, expect, it } from 'vitest'

import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { AI_FEATURES } from '@/types/api'

import { accountTone, adminReportTone, adminSupportTone, aiFeatureLabel, contentStatusTone } from './adminDisplay'

describe('aiFeatureLabel', () => {
  it('names a feature through the page translator', () => {
    const translate = (key: string) => `[${key}]`

    expect(aiFeatureLabel('CHAT_TUTOR', translate)).toBe('[opsLabels.aiFeatures.CHAT_TUTOR]')
    expect(aiFeatureLabel('QUIZ_DRAFT', translate)).toBe('[opsLabels.aiFeatures.QUIZ_DRAFT]')
  })

  it('resolves a non-empty label for every logged AI feature against opsLabelMessages', () => {
    const translate = (key: string) => {
      const feature = key.replace(/^opsLabels\.aiFeatures\./, '') as keyof typeof opsLabelMessages.en.aiFeatures
      return opsLabelMessages.en.aiFeatures[feature] ?? ''
    }

    for (const feature of AI_FEATURES) {
      expect(aiFeatureLabel(feature, translate)).toBeTruthy()
    }
  })
})

describe('admin status tones', () => {
  it('marks waiting items strong, intermediate items info or neutral, and closed or inactive items appropriately', () => {
    expect(adminSupportTone('OPEN')).toBe('strong')
    expect(adminSupportTone('ANSWERED')).toBe('info')
    expect(adminSupportTone('SOLVED')).toBe('success')

    expect(adminReportTone('PENDING_REVIEW')).toBe('strong')
    expect(adminReportTone('RESOLVED')).toBe('success')
    expect(adminReportTone('DISMISSED')).toBe('neutral')

    expect(accountTone(true)).toBe('success')
    expect(accountTone(false)).toBe('outline')
    expect(contentStatusTone('PUBLISHED')).toBe('success')
    expect(contentStatusTone('DRAFT')).toBe('outline')
    expect(contentStatusTone('ARCHIVED')).toBe('outline')
  })
})
