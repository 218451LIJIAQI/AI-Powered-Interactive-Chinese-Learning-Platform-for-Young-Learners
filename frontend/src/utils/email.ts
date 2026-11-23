/**
 * Same simple shape check on every form: local@domain.tld, where neither side starts or ends with a dot or has two in a
 * row (the server's @Email refuses those). The server does the authoritative validation.
 */
const EMAIL_PATTERN = /^[^\s@.]+(?:\.[^\s@.]+)*@[^\s@.]+(?:\.[^\s@.]+)+$/

export function isValidEmail(value: string): boolean {
  return EMAIL_PATTERN.test(value)
}
