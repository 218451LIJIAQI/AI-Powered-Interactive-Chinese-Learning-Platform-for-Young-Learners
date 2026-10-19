import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'

import { adminActivityLibraryMessages } from './adminActivityLibraryMessages'
import { adminContentMessages } from './adminContentMessages'
import { adminModerationMessages } from './adminModerationMessages'
import { adminSettingsMessages } from './adminSettingsMessages'
import { adminContentReviewMessages } from './adminContentReviewMessages'
import { contentGovernanceMessages } from './contentGovernanceMessages'
import { teacherAccessMessages } from './teacherAccessMessages'
import { teacherActivitiesMessages } from './teacherActivitiesMessages'
import { teacherAiDraftMessages } from './teacherAiDraftMessages'
import { teacherContentMessages } from './teacherContentMessages'

type Bundle = Record<'en' | 'ms' | 'zh-CN', Record<string, string>>

const bundles: Record<string, Bundle> = {
  contentGovernance: contentGovernanceMessages,
  teacherActivities: teacherActivitiesMessages,
  teacherAiDraft: teacherAiDraftMessages,
  adminContentReview: adminContentReviewMessages,
  teacherAccess: teacherAccessMessages,
}

describe('W3 message bundles', () => {
  it.each(Object.entries(bundles))('%s keeps EN / MS / zh-CN keys in parity with no empty strings', (_name, bundle) => {
    const englishKeys = Object.keys(bundle.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(bundle[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(bundle[locale][key].trim()).not.toBe('')
      }
    }
  })

  it('compiles every W3-touched message in every locale (no stray linked-message or plural syntax)', () => {
    const compiled: Bundle[] = [
      ...Object.values(bundles),
      adminContentMessages,
      teacherContentMessages,
      adminSettingsMessages,
      adminModerationMessages,
      adminActivityLibraryMessages,
    ]
    const messages = Object.fromEntries(
      (['en', 'ms', 'zh-CN'] as const).map((locale) => [
        locale,
        Object.fromEntries(compiled.map((bundle, index) => [`b${index}`, bundle[locale]])),
      ]),
    )
    const instance = createI18n({ legacy: false, locale: 'en', messages, missingWarn: false, fallbackWarn: false })
    const params = { count: 2, title: 'T', type: 'Quiz', reason: 'R', date: 'D', page: 1, total: 2, status: 'S' }

    for (const locale of ['en', 'ms', 'zh-CN'] as const) {
      instance.global.locale.value = locale
      compiled.forEach((bundle, index) => {
        for (const key of Object.keys(bundle[locale])) {
          expect(() => instance.global.t(`b${index}.${key}`, params)).not.toThrow()
        }
      })
    }
  })

  it('no longer tells teachers or admins that content is published directly', () => {
    const copy = [...Object.values(teacherContentMessages.en), ...Object.values(adminContentMessages.en)].join(' ')
    expect(copy).not.toMatch(/publish(es)? directly|goes live immediately/i)
    expect(Object.keys(adminContentMessages.en)).not.toContain('statusApproved')
    expect(Object.keys(teacherContentMessages.en)).not.toContain('statusApproved')
  })

  it('uses the agreed stage and status terminology', () => {
    expect(contentGovernanceMessages['zh-CN'].stageGuided).toBe('引导练习')
    expect(contentGovernanceMessages.ms.stageScaffolded).toBe('Bertatah')
    expect(contentGovernanceMessages.en.stageIndependent).toBe('Independent')
    expect(adminContentReviewMessages['zh-CN'].typeModule).toBe('单元')
    expect(adminContentReviewMessages['zh-CN'].typeLesson).toBe('课时')
    expect(teacherActivitiesMessages['zh-CN'].title).toBe('练习活动')
  })
})
