import { describe, expect, it } from 'vitest'

import { normalizeApiBaseUrl } from './env'

describe('normalizeApiBaseUrl', () => {
  it('keeps the same-origin default and trims trailing slashes', () => {
    expect(normalizeApiBaseUrl(undefined)).toBe('/api/v1')
    expect(normalizeApiBaseUrl('  /api/v1///  ')).toBe('/api/v1')
  })

  it('accepts absolute HTTP and HTTPS API roots without credentials', () => {
    expect(normalizeApiBaseUrl('https://api.example.com/api/v1/')).toBe('https://api.example.com/api/v1')
    expect(normalizeApiBaseUrl('http://127.0.0.1:8080/api/v1')).toBe('http://127.0.0.1:8080/api/v1')
  })

  it('rejects ambiguous or unsafe API roots', () => {
    expect(() => normalizeApiBaseUrl('//evil.example/api')).toThrow(/protocol-relative/)
    expect(() => normalizeApiBaseUrl('javascript:alert(1)')).toThrow(/http or https/)
    expect(() => normalizeApiBaseUrl('https://user:secret@example.com/api')).toThrow(/credentials/)
    expect(() => normalizeApiBaseUrl('https://example.com/api?token=secret')).toThrow(/query strings/)
    expect(() => normalizeApiBaseUrl('https:\\\\example.com\\api')).toThrow(/unsafe URL characters/)
  })
})
