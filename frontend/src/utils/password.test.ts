import { describe, expect, it } from 'vitest'

import { isPasswordLengthValid } from './password'

describe('isPasswordLengthValid', () => {
  it('accepts passwords within both the character and UTF-8 byte limits', () => {
    expect(isPasswordLengthValid('secure123')).toBe(true)
    expect(isPasswordLengthValid('a'.repeat(72))).toBe(true)
  })

  it('rejects passwords outside the backend bcrypt limits', () => {
    expect(isPasswordLengthValid('short7')).toBe(false)
    expect(isPasswordLengthValid('a'.repeat(73))).toBe(false)
    expect(isPasswordLengthValid('密'.repeat(25))).toBe(false)
  })
})
