import { describe, expect, it } from 'vitest'

import { i18n } from './i18n'
import { dashboardMessages } from './dashboardMessages'

const supportedLocales = ['en', 'ms', 'zh'] as const

function sortedNavigationKeys(locale: (typeof supportedLocales)[number]): string[] {
  return Object.keys(i18n.global.messages.value[locale].nav).sort()
}

describe('i18n navigation messages', () => {
  it('ships complete navigation translations for every supported locale', () => {
    const englishKeys = sortedNavigationKeys('en')

    supportedLocales.forEach((locale) => {
      expect(sortedNavigationKeys(locale)).toEqual(englishKeys)
    })
  })

  it('keeps Chinese navigation labels readable instead of mojibake', () => {
    expect(i18n.global.messages.value.zh.nav.home).toBe('首页')
    expect(i18n.global.messages.value.zh.nav.teacherVerification).toBe('教师审核')
    expect(i18n.global.messages.value.zh.nav.activityLibrary).toBe('活动资源库')
  })

  it('ships complete shared interface text for every supported locale', () => {
    const englishKeys = Object.keys(i18n.global.messages.value.en.chrome).sort()
    for (const locale of supportedLocales) {
      expect(Object.keys(i18n.global.messages.value[locale].chrome).sort()).toEqual(englishKeys)
    }
  })

  it('ships complete dashboard shell text for every supported locale', () => {
    const englishKeys = Object.keys(dashboardMessages.en).sort()
    for (const locale of supportedLocales) {
      expect(Object.keys(dashboardMessages[locale]).sort()).toEqual(englishKeys)
    }
  })
})
