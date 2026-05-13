import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { i18n } from '../i18n'
import { STORAGE_KEYS } from '../constants/storageKeys'
import { useLanguageStore } from './languageStore'

const LOCALE_KEY = STORAGE_KEYS.LOCALE

/**
 * The language store is the single source of truth for the active UI locale.
 * Three behaviours absolutely must hold:
 *
 *   1. The initial locale defaults to `en` when nothing is stored, so a brand
 *      new visitor (or a Safari user with localStorage disabled) does not get
 *      a blank UI.
 *   2. A previously stored locale is restored on app boot, including `ms` and
 *      `zh` — the two non-default locales the platform actually ships.
 *   3. Setting the locale persists to localStorage AND updates the i18n
 *      composer so every translated string switches in lockstep.
 *
 * These tests pin those guarantees so a future refactor of the persistence
 * layer never silently regresses to the wrong default or stops syncing i18n.
 */
describe('languageStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    // Reset i18n to a known state between tests so assertions about the
    // composer reflect this store's writes, not leftovers from earlier specs.
    i18n.global.locale.value = 'en'
  })

  afterEach(() => {
    localStorage.clear()
    i18n.global.locale.value = 'en'
  })

  it('defaults to English when no preference is stored', () => {
    const store = useLanguageStore()
    expect(store.locale).toBe('en')
    expect(i18n.global.locale.value).toBe('en')
  })

  it('hydrates the stored locale on creation', () => {
    localStorage.setItem(LOCALE_KEY, 'zh')
    const store = useLanguageStore()
    expect(store.locale).toBe('zh')
    expect(i18n.global.locale.value).toBe('zh')
  })

  it('ignores an unsupported stored locale and falls back to English', () => {
    localStorage.setItem(LOCALE_KEY, 'fr')
    const store = useLanguageStore()
    expect(store.locale).toBe('en')
  })

  it('setLocale persists the new value and updates the i18n composer', async () => {
    const store = useLanguageStore()
    store.setLocale('ms')
    // The watcher that pushes to i18n + localStorage runs asynchronously, so
    // we have to flush a tick before asserting the side effects.
    await nextTick()
    expect(store.locale).toBe('ms')
    expect(localStorage.getItem(LOCALE_KEY)).toBe('ms')
    expect(i18n.global.locale.value).toBe('ms')

    store.setLocale('zh')
    await nextTick()
    expect(store.locale).toBe('zh')
    expect(localStorage.getItem(LOCALE_KEY)).toBe('zh')
    expect(i18n.global.locale.value).toBe('zh')
  })

  it('setLocale rejects unsupported values without mutating state', async () => {
    const store = useLanguageStore()
    store.setLocale('ja' as never)
    await nextTick()
    expect(store.locale).toBe('en')
    // The watcher initialised with `immediate: true` writes the current
    // locale to storage on first run, so the persisted value matches the
    // default rather than being null/absent.
    expect(localStorage.getItem(LOCALE_KEY)).toBe('en')
    expect(i18n.global.locale.value).toBe('en')
  })
})
