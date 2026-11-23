import { describe, expect, it } from 'vitest'

import { isBlankPassword, isNewPasswordValid, validatePasswordChange } from './password'

describe('isNewPasswordValid', () => {
  it('accepts passwords within both the character and UTF-8 byte limits', () => {
    expect(isNewPasswordValid('secure123')).toBe(true)
    expect(isNewPasswordValid('a'.repeat(72))).toBe(true)
    expect(isNewPasswordValid('  pass word  ')).toBe(true)
  })

  it('rejects passwords outside the backend bcrypt limits', () => {
    expect(isNewPasswordValid('short7')).toBe(false)
    expect(isNewPasswordValid('a'.repeat(73))).toBe(false)
    expect(isNewPasswordValid('密'.repeat(25))).toBe(false)
  })

  it('rejects a password the server counts as blank, however long', () => {
    expect(isNewPasswordValid(' '.repeat(8))).toBe(false)
    expect(isNewPasswordValid('\t\n'.repeat(5))).toBe(false)
  })
})

describe('isBlankPassword', () => {
  it('reads nothing but spaces and control characters as blank, like the server’s trim', () => {
    expect(isBlankPassword('')).toBe(true)
    expect(isBlankPassword(' \t\r\n\u0000')).toBe(true)
    expect(isBlankPassword(' a ')).toBe(false)
    // Java's trim() keeps a no-break space, so the server does not count it as blank either.
    expect(isBlankPassword('\u00a0')).toBe(false)
  })
})

describe('validatePasswordChange (FRAUTH08)', () => {
  const valid = { currentPassword: 'OldPassword1', newPassword: 'NewPassword1', confirmPassword: 'NewPassword1' }

  it('accepts a complete, matching, different new password', () => {
    expect(validatePasswordChange(valid)).toEqual({})
  })

  it('enforces 8..72 characters and the 72-byte bcrypt limit', () => {
    expect(validatePasswordChange({ ...valid, newPassword: 'short', confirmPassword: 'short' }).newPassword).toBe(
      'account.passwordLength',
    )
    const long = 'a'.repeat(73)
    expect(validatePasswordChange({ ...valid, newPassword: long, confirmPassword: long }).newPassword).toBe(
      'account.passwordLength',
    )
    const multiByte = '密'.repeat(25)
    expect(validatePasswordChange({ ...valid, newPassword: multiByte, confirmPassword: multiByte }).newPassword).toBe(
      'account.passwordLength',
    )
    expect(validatePasswordChange({ ...valid, currentPassword: multiByte }).currentPassword).toBe(
      'account.currentPasswordWrong',
    )
  })

  it('rejects a mismatched confirmation and a new password equal to the current one', () => {
    expect(validatePasswordChange({ ...valid, confirmPassword: 'NewPassword2' }).confirmPassword).toBe(
      'account.confirmMismatch',
    )
    expect(
      validatePasswordChange({
        currentPassword: 'SamePassword1',
        newPassword: 'SamePassword1',
        confirmPassword: 'SamePassword1',
      }).newPassword,
    ).toBe('account.sameAsCurrent')
  })
})
