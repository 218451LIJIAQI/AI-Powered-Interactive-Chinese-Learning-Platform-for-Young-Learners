import { describe, expect, it } from 'vitest'

import { isUnauthorizedApiError, resolveApiError, resolveFieldErrors } from './apiError'

describe('resolveApiError', () => {
  it('uses sanitized backend messages before generic fallbacks', () => {
    expect(
      resolveApiError({
        response: {
          status: 400,
          data: { message: 'Please upload a smaller file.', errors: [] },
        },
      }),
    ).toBe('Please upload a smaller file.')
  })

  it('maps timeout and network failures to learner-friendly messages', () => {
    expect(resolveApiError({ code: 'ECONNABORTED', message: 'timeout of 20000ms exceeded' })).toContain('too long')
    expect(resolveApiError({ request: {} })).toContain('cannot reach')
  })

  it('detects unauthorized responses', () => {
    expect(isUnauthorizedApiError({ response: { status: 401 } })).toBe(true)
    expect(isUnauthorizedApiError({ response: { status: 403 } })).toBe(false)
  })

  it('extracts clean server field errors', () => {
    expect(
      resolveFieldErrors({
        response: {
          data: {
            fieldErrors: {
              email: ['Email address is required.', '   ', null],
              password: ['Password is required.'],
            },
          },
        },
      }),
    ).toEqual({
      email: ['Email address is required.'],
      password: ['Password is required.'],
    })
  })
})
