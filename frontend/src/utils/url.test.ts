import { describe, expect, it, vi } from 'vitest'

import { openSafeHttpUrl, safeHttpUrl } from './url'

describe('safeHttpUrl', () => {
  it.each(['https://example.com/checkout?session=123', 'http://localhost:8080/files/video.mp4'])(
    'accepts %s',
    (url) => {
      expect(safeHttpUrl(url)).toBe(url)
    },
  )

  it.each([
    'javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'file:///etc/passwd',
    '//evil.example/redirect',
  ])('rejects dangerous or ambiguous URL %s', (url) => {
    expect(safeHttpUrl(url)).toBeNull()
  })

  it.each([
    'https://trusted.example@evil.example/checkout',
    'https://example.com/\u202echeckout',
    'https://example.com/\u0000checkout',
  ])('rejects credentialed or visually ambiguous URL %s', (url) => {
    expect(safeHttpUrl(url)).toBeNull()
  })

  it('rejects relative URLs unless explicitly allowed for first-party resources', () => {
    expect(safeHttpUrl('/api/files/1')).toBeNull()
    expect(safeHttpUrl('/api/files/1', { allowRelative: true })).toBe('http://localhost:3000/api/files/1')
    expect(safeHttpUrl('/api/files/1', { allowRelative: true, baseOrigin: 'https://app.example' })).toBe(
      'https://app.example/api/files/1',
    )
  })

  it('can restrict relative resource mode to the same origin', () => {
    expect(
      safeHttpUrl('/api/files/1', {
        allowRelative: true,
        sameOriginOnly: true,
        baseOrigin: 'https://app.example',
      }),
    ).toBe('https://app.example/api/files/1')
    expect(
      safeHttpUrl('https://cdn.example/files/1', {
        allowRelative: true,
        sameOriginOnly: true,
        baseOrigin: 'https://app.example',
      }),
    ).toBeNull()
  })

  it.each(['\\evil.example\\payload', 'https:\\evil.example\\payload'])(
    'rejects backslash-prefixed browser URL quirk %s',
    (url) => {
      expect(safeHttpUrl(url, { allowRelative: true })).toBeNull()
    },
  )
})

describe('openSafeHttpUrl', () => {
  it('opens only validated URLs with opener isolation', () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)

    expect(openSafeHttpUrl('https://example.com/classroom')).toBe(true)
    expect(open).toHaveBeenCalledWith('https://example.com/classroom', '_blank', 'noopener,noreferrer')

    expect(openSafeHttpUrl('/relative')).toBe(false)
    expect(open).toHaveBeenCalledTimes(1)

    open.mockRestore()
  })
})
