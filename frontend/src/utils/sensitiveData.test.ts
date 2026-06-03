import { describe, expect, it } from 'vitest'

import {
  isSensitiveKey,
  scrubSensitiveRecord,
  scrubSensitiveUnknown,
  scrubSensitiveUrl,
  scrubSensitiveValue,
  firstQueryText,
  removeSensitiveRouteState,
} from './sensitiveData'

describe('sensitive data scrubbing', () => {
  it('detects common secret-bearing keys', () => {
    expect(isSensitiveKey('Authorization')).toBe(true)
    expect(isSensitiveKey('email')).toBe(true)
    expect(isSensitiveKey('reset_token')).toBe(true)
    expect(isSensitiveKey('sessionId')).toBe(true)
    expect(isSensitiveKey('stripeApiKey')).toBe(true)
    expect(isSensitiveKey('displayName')).toBe(false)
  })

  it('redacts tokens, passwords, emails, cookies, bearer credentials, and JSON secret fields', () => {
    const scrubbed = scrubSensitiveValue(
      'Authorization: Bearer abc.def token=reset-secret password:super-secret email=parent@example.com {"apiKey":"sk_live_secret"}',
    )

    expect(scrubbed).toContain('[Filtered]')
    expect(scrubbed).toContain('token=[Filtered]')
    expect(scrubbed).toContain('password=[Filtered]')
    expect(scrubbed).toContain('"apiKey":"[Filtered]"')
    expect(scrubbed).not.toContain('abc.def')
    expect(scrubbed).not.toContain('reset-secret')
    expect(scrubbed).not.toContain('super-secret')
    expect(scrubbed).not.toContain('parent@example.com')
    expect(scrubbed).not.toContain('sk_live_secret')
  })

  it('redacts sensitive absolute URL query values while preserving safe navigation context', () => {
    const scrubbed = scrubSensitiveUrl('https://learn.example/reset-password?token=raw-token&tab=account')

    expect(scrubbed).toBe('https://learn.example/reset-password?token=[Filtered]&tab=account')
    expect(scrubbed).not.toContain('raw-token')
  })

  it('redacts relative URLs without expanding them to the current origin', () => {
    expect(scrubSensitiveUrl('/reset-password?token=raw-token&tab=account')).toBe(
      '/reset-password?token=[Filtered]&tab=account',
    )
    expect(scrubSensitiveUrl('?token=raw-token&tab=account')).toBe('?token=[Filtered]&tab=account')
  })

  it('extracts the first non-empty query text from scalar or array query values', () => {
    expect(firstQueryText(' Parent@Example.com ')).toBe('Parent@Example.com')
    expect(firstQueryText(['', null, ' reset-token '])).toBe('reset-token')
    expect(firstQueryText(null)).toBe('')
  })

  it('removes sensitive router state while preserving safe query context', () => {
    expect(
      removeSensitiveRouteState(
        {
          tab: 'account',
          token: 'raw-reset-token',
          redirect: '/auth?token=nested-token',
        },
        '#email=parent@example.com',
      ),
    ).toEqual({
      query: { tab: 'account' },
      hash: '',
      changed: true,
    })
  })

  it('redacts nested objects and sensitive record keys', () => {
    expect(
      scrubSensitiveUnknown({
        safe: 'hello',
        nested: { email: 'parent@example.com', message: 'Bearer access-token', token: 'raw-token' },
      }),
    ).toEqual({
      safe: 'hello',
      nested: { email: '[Filtered]', message: '[Filtered]', token: '[Filtered]' },
    })

    expect(scrubSensitiveRecord({ Authorization: 'Bearer access-token', title: 'Lesson' })).toEqual({
      Authorization: '[Filtered]',
      title: 'Lesson',
    })
  })

  it('handles cyclic objects without throwing or leaking nested sensitive values', () => {
    const cyclic: Record<string, unknown> = {
      safe: 'hello',
      nested: { token: 'raw-token' },
    }
    cyclic.self = cyclic

    expect(scrubSensitiveUnknown(cyclic)).toEqual({
      safe: 'hello',
      nested: { token: '[Filtered]' },
      self: '[Filtered]',
    })
  })
})
