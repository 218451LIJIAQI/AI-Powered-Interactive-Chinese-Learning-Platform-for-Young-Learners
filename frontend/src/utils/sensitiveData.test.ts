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
    expect(isSensitiveKey('openAiApiKey')).toBe(true)
    expect(isSensitiveKey('displayName')).toBe(false)
  })

  it('redacts tokens, passwords, emails, cookies, bearer credentials, and JSON secret fields', () => {
    const scrubbed = scrubSensitiveValue(
      'Authorization: Bearer abc.def token=reset-secret password:super-secret email=learner@example.com {"apiKey":"test_secret_value"}',
    )

    expect(scrubbed).toContain('[Filtered]')
    expect(scrubbed).toContain('token=[Filtered]')
    expect(scrubbed).toContain('password=[Filtered]')
    expect(scrubbed).toContain('"apiKey":"[Filtered]"')
    expect(scrubbed).not.toContain('abc.def')
    expect(scrubbed).not.toContain('reset-secret')
    expect(scrubbed).not.toContain('super-secret')
    expect(scrubbed).not.toContain('learner@example.com')
    expect(scrubbed).not.toContain('test_secret_value')
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
    expect(firstQueryText(' Learner@Example.com ')).toBe('Learner@Example.com')
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
        '#email=learner@example.com',
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
        nested: { email: 'learner@example.com', message: 'Bearer access-token', token: 'raw-token' },
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

  it('redacts sensitive values in Error objects', () => {
    expect(scrubSensitiveUnknown(new Error('Reset failed for learner@example.com token=raw-token'))).toEqual({
      name: 'Error',
      message: 'Reset failed for [Filtered] token=[Filtered]',
    })
  })
})
