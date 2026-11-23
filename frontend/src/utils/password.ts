const MIN_PASSWORD_LENGTH = 8
const BCRYPT_MAX_PASSWORD_BYTES = 72

const textEncoder = new TextEncoder()

/**
 * True for a password the server counts as missing (@NotBlank): empty, or nothing but spaces and control characters,
 * which Java's `String.trim()` removes (every character up to U+0020).
 */
export function isBlankPassword(password: string): boolean {
  // eslint-disable-next-line no-control-regex
  return !/[^\u0000-\u0020]/.test(password)
}

/**
 * The upper limit only: bcrypt uses at most 72 UTF-8 bytes of a password. The byte count is never below the
 * character count, so this one check also caps the length at 72 characters.
 */
export function isWithinPasswordByteLimit(password: string): boolean {
  return textEncoder.encode(password).byteLength <= BCRYPT_MAX_PASSWORD_BYTES
}

export function meetsPasswordMinimum(password: string): boolean {
  return password.length >= MIN_PASSWORD_LENGTH && !isBlankPassword(password)
}

/** The server's rule for a new password: 8 to 72 characters (at most 72 UTF-8 bytes), and not blank. */
export function isNewPasswordValid(password: string): boolean {
  return meetsPasswordMinimum(password) && isWithinPasswordByteLimit(password)
}

export interface PasswordChangeForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/** Account field errors from local validation or a refused current password (FRAUTH08). */
type PasswordChangeErrorKey =
  | 'account.currentPasswordWrong'
  | 'account.passwordLength'
  | 'account.confirmMismatch'
  | 'account.sameAsCurrent'

export type PasswordChangeErrors = Partial<Record<keyof PasswordChangeForm, PasswordChangeErrorKey>>

/**
 * A valid new password, a matching confirmation, and a new password unlike the current. The form is only sent with
 * all three fields filled (not blank), so blank fields are not checked here.
 */
export function validatePasswordChange(form: PasswordChangeForm): PasswordChangeErrors {
  const errors: PasswordChangeErrors = {}
  if (!isWithinPasswordByteLimit(form.currentPassword)) errors.currentPassword = 'account.currentPasswordWrong'
  if (!isNewPasswordValid(form.newPassword)) errors.newPassword = 'account.passwordLength'
  else if (form.newPassword === form.currentPassword) errors.newPassword = 'account.sameAsCurrent'
  if (form.confirmPassword !== form.newPassword) errors.confirmPassword = 'account.confirmMismatch'
  return errors
}
