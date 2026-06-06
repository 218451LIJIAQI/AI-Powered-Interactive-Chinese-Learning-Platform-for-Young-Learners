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
