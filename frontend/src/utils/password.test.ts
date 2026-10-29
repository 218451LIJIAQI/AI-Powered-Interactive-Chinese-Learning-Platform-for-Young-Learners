import { describe, expect, it } from 'vitest'

import { hasPasswordChangeErrors, isPasswordLengthValid, validatePasswordChange } from './password'

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

describe('validatePasswordChange (FRAUTH08)', () => {
  const valid = { currentPassword: 'OldPassword1', newPassword: 'NewPassword1', confirmPassword: 'NewPassword1' }

  it('accepts a complete, matching, different new password', () => {
    expect(validatePasswordChange(valid)).toEqual({})
    expect(hasPasswordChangeErrors(validatePasswordChange(valid))).toBe(false)
  })

  it('requires every field', () => {
    expect(validatePasswordChange({ currentPassword: '', newPassword: '', confirmPassword: '' })).toEqual({
      currentPassword: 'account.currentRequired',
      newPassword: 'account.newRequired',
      confirmPassword: 'account.confirmRequired',
    })
  })

  it('enforces 8..72 characters and the 72-byte bcrypt limit', () => {
    expect(validatePasswordChange({ ...valid, newPassword: 'short', confirmPassword: 'short' }).newPassword).toBe(
      'account.passwordLength',
    )
    const long = 'a'.repeat(73)
    expect(validatePasswordChange({ ...valid, newPassword: long, confirmPassword: long }).newPassword).toBe(
      'account.passwordLength',
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
