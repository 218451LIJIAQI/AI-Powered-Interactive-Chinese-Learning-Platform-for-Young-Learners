import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { storageKeys } from '@/utils/storage'

import { useUiStore } from './ui'

describe('ui store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('persists locale changes and updates the i18n plugin', () => {
    const ui = useUiStore()

    ui.setLocale('zh-CN')

    expect(ui.locale).toBe('zh-CN')
    expect(i18n.global.locale.value).toBe('zh-CN')
    expect(document.documentElement.lang).toBe('zh-CN')
    expect(window.localStorage.getItem(storageKeys.language)).toBe('zh-CN')
  })

  it('applies an account preferred language, mapping the legacy Chinese code', () => {
    const ui = useUiStore()

    ui.applyPreferredLanguage('zh')
    expect(ui.locale).toBe('zh-CN')

    ui.applyPreferredLanguage('ms')
    expect(ui.locale).toBe('ms')

    ui.applyPreferredLanguage('fr')
    ui.applyPreferredLanguage(null)
    expect(ui.locale).toBe('ms')
  })
})
