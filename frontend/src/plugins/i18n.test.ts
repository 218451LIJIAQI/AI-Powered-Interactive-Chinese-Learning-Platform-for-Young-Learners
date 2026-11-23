import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { storageKeys } from '@/utils/storage'

import { appLocale, applyPreferredLanguage, i18n, setLocale } from './i18n'

// Key parity and compilation are checked for every bundle, these global messages included, in messageBundles.test.ts.
describe('i18n navigation messages', () => {
  it('keeps Chinese navigation labels readable instead of mojibake', () => {
    expect(i18n.global.messages.value['zh-CN'].nav.home).toBe('首页')
    expect(i18n.global.messages.value['zh-CN'].nav.classes).toBe('课堂')
    expect(i18n.global.messages.value['zh-CN'].nav.aiHelper).toBe('AI 助手')
  })
})

describe('interface language', () => {
  beforeEach(() => {
    window.localStorage.clear()
    setLocale('en')
  })

  afterEach(() => {
    window.localStorage.clear()
    setLocale('en')
  })

  it('persists locale changes and updates the i18n plugin and document lang', () => {
    expect(appLocale.value).toBe('en')
    setLocale('zh-CN')

    expect(i18n.global.locale.value).toBe('zh-CN')
    expect(document.documentElement.lang).toBe('zh-CN')
    expect(window.localStorage.getItem(storageKeys.language)).toBe('zh-CN')
  })

  it('applies an account preferred language and ignores unknown or empty values', () => {
    expect(appLocale.value).toBe('en')
    applyPreferredLanguage('zh-CN')
    expect(appLocale.value).toBe('zh-CN')

    applyPreferredLanguage('ms')
    expect(appLocale.value).toBe('ms')

    applyPreferredLanguage('fr')
    applyPreferredLanguage(null)
    applyPreferredLanguage(undefined)
    applyPreferredLanguage('')
    expect(appLocale.value).toBe('ms')
  })
})
