import { describe, expect, it } from 'vitest'

import { extractFileName } from './fileDownload'

/**
 * Regression tests for the filename parser used when downloading protected
 * verification documents. The server sets `Content-Disposition` and the
 * frontend must fall back gracefully to a known filename when the header is
 * missing or malformed — otherwise the downloaded file is named `blob`.
 */
describe('extractFileName', () => {
  it('returns null for missing or empty headers', () => {
    expect(extractFileName(undefined)).toBeNull()
    expect(extractFileName('')).toBeNull()
  })

  it('parses the quoted filename form', () => {
    expect(extractFileName('attachment; filename="document.pdf"')).toBe('document.pdf')
  })

  it('prefers the RFC 5987 UTF-8 form when both are present', () => {
    const header = `attachment; filename="fallback.txt"; filename*=UTF-8''document.pdf`
    expect(extractFileName(header)).toBe('document.pdf')
  })

  it('decodes percent-encoded UTF-8 filenames (e.g. Chinese characters)', () => {
    const encoded = encodeURIComponent('教师证明.pdf')
    const header = `attachment; filename*=UTF-8''${encoded}`
    expect(extractFileName(header)).toBe('教师证明.pdf')
  })

  it('falls back to the quoted form when UTF-8 percent-encoding is malformed', () => {
    // `%E0%A4` is an incomplete UTF-8 sequence — decodeURIComponent throws.
    const header = `attachment; filename="safe.pdf"; filename*=UTF-8''%E0%A4`
    expect(extractFileName(header)).toBe('safe.pdf')
  })

  it('returns null when no filename attribute is present', () => {
    expect(extractFileName('attachment')).toBeNull()
    expect(extractFileName('inline; boundary=something')).toBeNull()
  })
})
