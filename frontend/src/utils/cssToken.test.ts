import { afterEach, describe, expect, it } from 'vitest'

import { readCssToken } from './cssToken'

describe('readCssToken', () => {
  afterEach(() => {
    document.documentElement.style.removeProperty('--test-ink')
  })

  it('returns the resolved custom property value', () => {
    document.documentElement.style.setProperty('--test-ink', ' #3a1f9c ')
    expect(readCssToken(document.documentElement, '--test-ink', '#000000')).toBe('#3a1f9c')
  })

  it('falls back when the property is not defined or there is no element', () => {
    expect(readCssToken(document.documentElement, '--test-missing', '#123456')).toBe('#123456')
    expect(readCssToken(null, '--test-ink', '#654321')).toBe('#654321')
  })
})
