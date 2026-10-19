const MIN_PASSWORD_LENGTH = 8
const BCRYPT_MAX_PASSWORD_BYTES = 72

const textEncoder = new TextEncoder()

export function isPasswordLengthValid(password: string): boolean {
  return (
    password.length >= MIN_PASSWORD_LENGTH &&
    password.length <= BCRYPT_MAX_PASSWORD_BYTES &&
    textEncoder.encode(password).byteLength <= BCRYPT_MAX_PASSWORD_BYTES
  )
}

export interface PasswordChangeForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/** Message keys (accountMessages) for client-side checks; the server repeats every rule (D12). */
type PasswordChangeErrorKey =
  | 'currentRequired'
  | 'newRequired'
  | 'confirmRequired'
  | 'passwordLength'
  | 'confirmMismatch'
  | 'sameAsCurrent'

export type PasswordChangeErrors = Partial<Record<keyof PasswordChangeForm, PasswordChangeErrorKey>>

/** Required fields, 8..72 characters (<= 72 UTF-8 bytes), confirmation matches, new differs from current. */
export function validatePasswordChange(form: PasswordChangeForm): PasswordChangeErrors {
  const errors: PasswordChangeErrors = {}
  if (!form.currentPassword) errors.currentPassword = 'currentRequired'

  if (!form.newPassword) errors.newPassword = 'newRequired'
  else if (!isPasswordLengthValid(form.newPassword)) errors.newPassword = 'passwordLength'
  else if (form.currentPassword && form.newPassword === form.currentPassword) errors.newPassword = 'sameAsCurrent'

  if (!form.confirmPassword) errors.confirmPassword = 'confirmRequired'
  else if (form.newPassword && form.confirmPassword !== form.newPassword) errors.confirmPassword = 'confirmMismatch'

  return errors
}

export function hasPasswordChangeErrors(errors: PasswordChangeErrors): boolean {
  return Object.keys(errors).length > 0
}
