const MIN_PASSWORD_LENGTH = 8
const BCRYPT_MAX_PASSWORD_BYTES = 72

const textEncoder = new TextEncoder()

/**
 * The upper limit only: bcrypt uses at most 72 UTF-8 bytes of a password. The byte count is never below the
 * character count, so this one check also caps the length at 72 characters.
 */
export function isWithinPasswordByteLimit(password: string): boolean {
  return textEncoder.encode(password).byteLength <= BCRYPT_MAX_PASSWORD_BYTES
}

export function isPasswordLengthValid(password: string): boolean {
  return password.length >= MIN_PASSWORD_LENGTH && isWithinPasswordByteLimit(password)
}

export interface PasswordChangeForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/** Full message keys (accountMessages) for client-side checks; the server repeats every rule (FRAUTH08). */
type PasswordChangeErrorKey =
  | 'account.currentRequired'
  | 'account.newRequired'
  | 'account.confirmRequired'
  | 'account.passwordLength'
  | 'account.confirmMismatch'
  | 'account.sameAsCurrent'

export type PasswordChangeErrors = Partial<Record<keyof PasswordChangeForm, PasswordChangeErrorKey>>

/** Required fields, 8..72 characters (<= 72 UTF-8 bytes), confirmation matches, new differs from current. */
export function validatePasswordChange(form: PasswordChangeForm): PasswordChangeErrors {
  const errors: PasswordChangeErrors = {}
  if (!form.currentPassword) errors.currentPassword = 'account.currentRequired'

  if (!form.newPassword) errors.newPassword = 'account.newRequired'
  else if (!isPasswordLengthValid(form.newPassword)) errors.newPassword = 'account.passwordLength'
  else if (form.currentPassword && form.newPassword === form.currentPassword)
    errors.newPassword = 'account.sameAsCurrent'

  if (!form.confirmPassword) errors.confirmPassword = 'account.confirmRequired'
  else if (form.newPassword && form.confirmPassword !== form.newPassword)
    errors.confirmPassword = 'account.confirmMismatch'

  return errors
}

export function hasPasswordChangeErrors(errors: PasswordChangeErrors): boolean {
  return Object.keys(errors).length > 0
}
