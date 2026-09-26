import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { storageKeys } from '@/utils/storage'

import { useUiStore } from './ui'

describe('ui store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    i18n.global.locale.value = 'en'
  })

  it('persists locale changes and updates the i18n plugin', () => {
    const ui = useUiStore()

    ui.setLocale('zh')

    expect(ui.locale).toBe('zh')
    expect(i18n.global.locale.value).toBe('zh')
    expect(document.documentElement.lang).toBe('zh')
    expect(window.localStorage.getItem(storageKeys.language)).toBe('zh')
  })
})
