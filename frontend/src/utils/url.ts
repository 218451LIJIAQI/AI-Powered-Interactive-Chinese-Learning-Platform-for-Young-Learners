/**
 * Validates that a string is a safe HTTP(S) URL before the UI follows
 * it. Rejects `javascript:`, `data:`, `file:` etc. so a malicious
 * teacher bio or AI-generated link cannot be turned into XSS via
 * `<a href>` or `window.open`.
 *
 * {@link openSafeHttpUrl} additionally hardens the popup with
 * `noopener,noreferrer` so the new tab cannot reverse-tabnab the
 * opener via `window.opener`.
 */
export function safeHttpUrl(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null

  try {
    const url = new URL(trimmed, window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

export function openSafeHttpUrl(value: string | null | undefined): boolean {
  const url = safeHttpUrl(value)
  if (!url) return false
  window.open(url, '_blank', 'noopener,noreferrer')
  return true
}
