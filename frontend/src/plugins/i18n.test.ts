import { describe, expect, it } from 'vitest'

import { i18n } from './i18n'

describe('i18n navigation messages', () => {
  it('ships complete navigation translations for every supported locale', () => {
    const locales = ['en', 'ms', 'zh'] as const
    const englishKeys = Object.keys(i18n.global.messages.value.en.nav)

    locales.forEach((locale) => {
      expect(Object.keys(i18n.global.messages.value[locale].nav).sort()).toEqual([...englishKeys].sort())
    })
  })

  it('keeps Chinese navigation labels readable instead of mojibake', () => {
    expect(i18n.global.messages.value.zh.nav.home).toBe('首页')
    expect(i18n.global.messages.value.zh.nav.teacherVerification).toBe('教师审核')
    expect(i18n.global.messages.value.zh.nav.activityLibrary).toBe('活动资源库')
  })
})
