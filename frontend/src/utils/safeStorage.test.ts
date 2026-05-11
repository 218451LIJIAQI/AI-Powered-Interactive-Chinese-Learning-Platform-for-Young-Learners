import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { safeGetItem, safeRemoveItem, safeSetItem } from './safeStorage'

/**
 * `safeStorage` is the only module in the app allowed to touch `localStorage`
 * directly. Every other service/store/component calls these helpers so that
 * quota-exceeded, Safari "block all cookies", and iOS Lockdown Mode scenarios
 * never crash app bootstrap (SecurityError / QuotaExceededError on access).
 *
 * These tests pin down:
 *   1. Happy-path pass-through to the underlying Storage API.
 *   2. Swallow behaviour when the underlying API throws.
 */

describe('safeStorage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('safeGetItem returns the stored value when present', () => {
    localStorage.setItem('foo', 'bar')
    expect(safeGetItem('foo')).toBe('bar')
  })

  it('safeGetItem returns null for missing keys', () => {
    expect(safeGetItem('not-present')).toBeNull()
  })

  it('safeGetItem returns null when the underlying API throws', () => {
    // jsdom's storage shim uses a custom class, not Storage.prototype, so we
    // spy on the instance method directly.
    vi.spyOn(window.localStorage, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError: storage disabled')
    })
    expect(safeGetItem('foo')).toBeNull()
  })

  it('safeSetItem writes the value through to localStorage', () => {
    safeSetItem('k', 'v')
    expect(localStorage.getItem('k')).toBe('v')
  })

  it('safeSetItem silently swallows quota-exceeded errors', () => {
    const spy = vi.spyOn(window.localStorage, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })
    // Must not throw — a disabled/full storage is not a crash condition for
    // the caller; the value just won't persist across reloads.
    expect(() => safeSetItem('k', 'v')).not.toThrow()
    expect(spy).toHaveBeenCalledWith('k', 'v')
  })

  it('safeRemoveItem deletes an existing value', () => {
    localStorage.setItem('k', 'v')
    safeRemoveItem('k')
    expect(localStorage.getItem('k')).toBeNull()
  })

  it('safeRemoveItem silently swallows errors', () => {
    vi.spyOn(window.localStorage, 'removeItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    expect(() => safeRemoveItem('k')).not.toThrow()
  })
})
