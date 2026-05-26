import { describe, expect, it, vi } from 'vitest'

import { openSafeHttpUrl, safeHttpUrl } from './url'

describe('safeHttpUrl', () => {
  it('accepts absolute HTTP and HTTPS URLs', () => {
    expect(safeHttpUrl('https://example.com/checkout?session=123')).toBe('https://example.com/checkout?session=123')
    expect(safeHttpUrl('http://localhost:8080/files/video.mp4')).toBe('http://localhost:8080/files/video.mp4')
  })

  it('rejects dangerous and ambiguous protocols', () => {
    expect(safeHttpUrl('javascript:alert(1)')).toBeNull()
    expect(safeHttpUrl('data:text/html,<script>alert(1)</script>')).toBeNull()
    expect(safeHttpUrl('file:///etc/passwd')).toBeNull()
    expect(safeHttpUrl('//evil.example/redirect')).toBeNull()
  })

  it('rejects credentialed or visually ambiguous URLs', () => {
    expect(safeHttpUrl('https://trusted.example@evil.example/checkout')).toBeNull()
    expect(safeHttpUrl('https://example.com/\u202echeckout')).toBeNull()
    expect(safeHttpUrl('https://example.com/\u0000checkout')).toBeNull()
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

  it('rejects backslash-prefixed browser URL quirks', () => {
    expect(safeHttpUrl('\\evil.example\\payload', { allowRelative: true })).toBeNull()
    expect(safeHttpUrl('https:\\evil.example\\payload')).toBeNull()
  })
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
