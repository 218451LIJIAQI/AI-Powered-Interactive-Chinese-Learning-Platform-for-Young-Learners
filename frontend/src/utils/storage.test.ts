import { describe, expect, it } from 'vitest'

import { safeGetItem, safeRemoveItem, safeSetItem, sessionOwner } from './storage'

describe('safe storage helpers', () => {
  it('parses sessionOwner only for positive safe integer ids or explicit null sign-out', () => {
    expect(sessionOwner(JSON.stringify({ userId: 42 }))).toBe(42)
    expect(sessionOwner(JSON.stringify({ userId: null }))).toBeNull()
    expect(sessionOwner(JSON.stringify({ userId: 0 }))).toBeUndefined()
    expect(sessionOwner(JSON.stringify({ userId: -5 }))).toBeUndefined()
    expect(sessionOwner(JSON.stringify({ userId: 1.5 }))).toBeUndefined()
    expect(sessionOwner(JSON.stringify({ userId: '42' }))).toBeUndefined()
    expect(sessionOwner('not-json')).toBeUndefined()
    expect(sessionOwner(null)).toBeUndefined()
  })

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
})
