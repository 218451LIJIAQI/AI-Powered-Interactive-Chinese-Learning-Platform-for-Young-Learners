import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'

import { accountMessages } from './accountMessages'
import { apiClientMessage, apiClientMessages } from './apiClientMessages'
import { dataNoticeMessages } from './dataNoticeMessages'
import { mediaMessages } from './mediaMessages'
import { notificationsMessages } from './notificationsMessages'
import { statusLabelMessages } from './statusLabelMessages'
import { teacherActivitiesMessages } from './teacherActivitiesMessages'

type Bundle = Record<'en' | 'ms' | 'zh-CN', Record<string, string>>

const bundles: Record<string, Bundle> = {
  account: accountMessages,
  apiClient: apiClientMessages,
  dataNotice: dataNoticeMessages,
  media: mediaMessages,
  notifications: notificationsMessages,
  statusLabels: statusLabelMessages,
  teacherActivities: teacherActivitiesMessages,
}

describe('W4 shared message bundles', () => {
  it.each(Object.entries(bundles))('%s keeps EN / MS / zh-CN keys in parity with no empty strings', (_name, bundle) => {
    const englishKeys = Object.keys(bundle.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(bundle[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) {
        expect(bundle[locale][key].trim()).not.toBe('')
      }
    }
  })

  it('compiles every W4 shared message in every locale (plural forms keep the same arity)', () => {
    const list = Object.values(bundles)
    const messages = Object.fromEntries(
      (['en', 'ms', 'zh-CN'] as const).map((locale) => [
        locale,
        Object.fromEntries(list.map((bundle, index) => [`b${index}`, bundle[locale]])),
      ]),
    )
    const instance = createI18n({ legacy: false, locale: 'en', messages, missingWarn: false, fallbackWarn: false })
    for (const locale of ['en', 'ms', 'zh-CN'] as const) {
      instance.global.locale.value = locale
      list.forEach((bundle, index) => {
        for (const key of Object.keys(bundle[locale])) {
          expect(() =>
            instance.global.t(`b${index}.${key}`, { count: 2, date: 'D', page: 1, total: 2, target: 'T' }),
          ).not.toThrow()
          const forms = bundle[locale][key].split(' | ').length
          expect(forms).toBe(bundle.en[key].split(' | ').length)
        }
      })
    }
  })

  it('localises API-layer fallback errors by the document language', () => {
    document.documentElement.lang = 'ms'
    expect(apiClientMessage('unexpectedFormat')).toBe(apiClientMessages.ms.unexpectedFormat)
    document.documentElement.lang = 'zh-CN'
    expect(apiClientMessage('streamTimeout')).toBe(apiClientMessages['zh-CN'].streamTimeout)
    document.documentElement.lang = 'en'
    expect(apiClientMessage('rejected')).toBe('The request was not accepted by the server.')
  })
})
