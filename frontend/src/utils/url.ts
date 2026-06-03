/**
 * Validates that a string is a safe HTTP(S) URL before the UI follows
 * it. External redirects are absolute-only by default; callers that
 * render first-party media endpoints can opt into same-origin relative
 * URLs. Rejects `javascript:`, `data:`, `file:` etc. so a malicious
 * teacher bio or AI-generated link cannot be turned into XSS via
 * `<a href>` or `window.open`.
 *
 * {@link openSafeHttpUrl} additionally hardens the popup with
 * `noopener,noreferrer` so the new tab cannot reverse-tabnab the
 * opener via `window.opener`.
 */
interface SafeHttpUrlOptions {
  allowRelative?: boolean
  baseOrigin?: string
  sameOriginOnly?: boolean
}

const HTTP_PROTOCOLS = new Set(['http:', 'https:'])
const UNSAFE_URL_CODE_POINT_PATTERN = /[\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff]/

function currentOrigin() {
  return typeof window === 'undefined' ? null : window.location.origin
}

export function safeHttpUrl(value: string | null | undefined, options: SafeHttpUrlOptions = {}): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null
  if (trimmed.startsWith('//') || trimmed.includes('\\') || UNSAFE_URL_CODE_POINT_PATTERN.test(trimmed)) return null

  try {
    const baseOrigin = options.baseOrigin ?? currentOrigin()
    if (options.allowRelative && !baseOrigin) return null

    const url = options.allowRelative ? new URL(trimmed, baseOrigin ?? undefined) : new URL(trimmed)
    if (!HTTP_PROTOCOLS.has(url.protocol)) return null
    if (url.username || url.password) return null
    if (options.sameOriginOnly) {
      if (!baseOrigin) return null
      if (url.origin !== new URL(baseOrigin).origin) return null
    }
    if (!options.allowRelative && url.origin === 'null') return null
    return url.href
  } catch {
    return null
  }
}

export function openSafeHttpUrl(value: string | null | undefined, options: SafeHttpUrlOptions = {}): boolean {
  const url = safeHttpUrl(value, options)
  if (!url) return false
  if (typeof window === 'undefined') return false

  window.open(url, '_blank', 'noopener,noreferrer')
  return true
}
