import { describe, expect, it } from 'vitest'

import { readPersistedLocale, safeGetItem, safeRemoveItem, safeSetItem, storageKeys } from './storage'

describe('safe storage helpers', () => {
  it('no-ops safely when browser storage is unavailable', () => {
    expect(safeGetItem('missing', null)).toBeNull()
    expect(() => safeSetItem('missing', 'value', null)).not.toThrow()
    expect(() => safeRemoveItem('missing', null)).not.toThrow()
  })

  it('swallows storage implementations that throw', () => {
    const throwingStorage = {
      getItem() {
        throw new Error('blocked')
      },
      setItem() {
        throw new Error('blocked')
      },
      removeItem() {
        throw new Error('blocked')
      },
    } as unknown as Storage

    expect(safeGetItem('key', throwingStorage)).toBeNull()
    expect(() => safeSetItem('key', 'value', throwingStorage)).not.toThrow()
    expect(() => safeRemoveItem('key', throwingStorage)).not.toThrow()
  })

  it('reads only supported persisted locales and falls back to English otherwise', () => {
    window.localStorage.clear()
    expect(readPersistedLocale()).toBe('en')

    window.localStorage.setItem(storageKeys.language, 'zh')
    expect(readPersistedLocale()).toBe('zh')

    window.localStorage.setItem(storageKeys.language, 'fr')
    expect(readPersistedLocale()).toBe('en')
  })
})
