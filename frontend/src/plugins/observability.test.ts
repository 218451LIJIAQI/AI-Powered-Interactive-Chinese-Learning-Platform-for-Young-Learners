import type { Event } from '@sentry/vue'
import { describe, expect, it } from 'vitest'

import { sanitizeSentryEvent } from './observability'

describe('sanitizeSentryEvent', () => {
  it('redacts secrets and identity from every Sentry event surface the app populates', () => {
    const event: Event = {
      message: 'Reset failed for parent@example.com token=raw-token',
      logentry: {
        message: 'Authorization: Bearer raw-bearer',
      },
      request: {
        url: 'https://learn.example/reset-password?token=raw-token&tab=account&email=parent@example.com',
        headers: {
          Authorization: 'Bearer raw-bearer',
          'X-Request-Name': 'reset',
        },
        cookies: {
          mandarin_spark_refresh: 'raw-refresh-cookie',
        },
        query_string: [
          ['token', 'raw-token'],
          ['tab', 'account'],
          ['email', 'parent@example.com'],
        ],
        data: {
          password: 'new-password',
          note: 'Bearer raw-note-token',
        },
      },
      transaction: '/reset-password?token=raw-token',
      user: {
        id: '42',
        email: 'parent@example.com',
        username: 'parent',
        ip_address: '203.0.113.10',
        role: 'STUDENT',
        sessionId: 'raw-session',
      },
      tags: {
        token: 'raw-token',
        page: 'reset-password',
      },
      extra: {
        payload: {
          resetToken: 'raw-token',
          email: 'parent@example.com',
        },
      },
      contexts: {
        trace: {
          span_id: 'span',
          trace_id: 'trace',
          data: {
            apiKey: 'raw-api-key',
          },
        },
      },
      breadcrumbs: [
        {
          message: 'Clicked reset token=raw-token',
          data: {
            url: '/reset-password?token=raw-token',
            authorization: 'Bearer raw-bearer',
          },
        },
      ],
      exception: {
        values: [
          {
            type: 'Error',
            value: 'backend rejected password=new-password token=raw-token',
          },
        ],
      },
      spans: [
        {
          span_id: 'span',
          trace_id: 'trace',
          start_timestamp: 1,
          data: {
            Authorization: 'Bearer raw-bearer',
            component: 'ResetPasswordPage',
          },
          description: 'POST /auth/reset-password?token=raw-token',
        },
      ],
    }

    const sanitized = sanitizeSentryEvent(event)
    const serialized = JSON.stringify(sanitized)
    const sensitiveValues = [
      'raw-token',
      'raw-bearer',
      'raw-refresh-cookie',
      'parent@example.com',
      'new-password',
      'raw-session',
    ]

    sensitiveValues.forEach((sensitiveValue) => {
      expect(serialized).not.toContain(sensitiveValue)
    })
    expect(sanitized.request?.headers?.['X-Request-Name']).toBe('reset')
    expect(sanitized.request?.query_string).toContainEqual(['tab', 'account'])
    expect(sanitized.user).toEqual({ role: 'STUDENT', sessionId: '[Filtered]' })
    expect(sanitized.tags?.page).toBe('reset-password')
    expect(sanitized.spans?.[0]?.data.component).toBe('ResetPasswordPage')
  })
})
