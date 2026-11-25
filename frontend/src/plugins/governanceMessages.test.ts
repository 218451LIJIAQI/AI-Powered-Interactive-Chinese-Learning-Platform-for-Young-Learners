import { describe, expect, it } from 'vitest'

import { APP_LANGUAGES } from '@/types/api'

import { adminClassesMessages } from './adminClassesMessages'
import { i18n } from './i18n'
import { lessonEditorMessages } from './lessonEditorMessages'

// Key parity and compilation are checked for every bundle in messageBundles.test.ts.
describe('content governance wording', () => {
  it('has no review step: admins publish classes and lessons themselves', () => {
    for (const locale of APP_LANGUAGES) {
      const copy = JSON.stringify([adminClassesMessages[locale], lessonEditorMessages[locale]])
      expect(copy, locale).not.toMatch(
        /submit(ted)? for review|changes requested|in review|review queue|hantar(?:kan)? untuk semakan|dalam semakan|perubahan diminta|baris gilir semakan|提交审核|审核中|审核队列|要求修改/i,
      )
    }
  })

  it('says that learners keep the published lesson until the changes are published in every locale', () => {
    expect(lessonEditorMessages.en.status.publishedMeta).toMatch(/published version/)
    expect(lessonEditorMessages.ms.status.publishedMeta).toMatch(/diterbitkan/)
    expect(lessonEditorMessages['zh-CN'].status.publishedMeta).toMatch(/已发布的版本/)
  })

  it('uses the agreed class, unit and lesson terminology', () => {
    expect(i18n.global.t('nav.classes', {}, { locale: 'zh-CN' })).toBe('课堂')
    expect(adminClassesMessages['zh-CN'].types.LESSON).toBe('课时')
    expect(adminClassesMessages['zh-CN'].publish.units).toContain('单元')
  })
})
