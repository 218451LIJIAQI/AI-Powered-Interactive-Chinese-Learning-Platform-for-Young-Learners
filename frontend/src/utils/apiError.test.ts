import { describe, expect, it } from 'vitest'

import { isUnauthorizedApiError, resolveApiError } from './apiError'

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

  it('does not expose transport internals as user-facing messages', () => {
    expect(
      resolveApiError({
        response: {
          status: 500,
          data: { message: 'Network Error', errors: ['Request failed'] },
        },
      }),
    ).toBe('The learning service is having trouble right now. Please try again shortly.')
  })

  it('cleans unsafe and oversized backend messages before display', () => {
    const message = `  Please retry\u202e\n${'x'.repeat(400)}  `

    const resolved = resolveApiError({
      response: {
        status: 400,
        data: { message, errors: [] },
      },
    })

    expect(resolved).toMatch(/^Please retry x+/)
    expect(resolved).not.toContain('\u202e')
    expect(resolved).not.toContain('\n')
    expect(resolved).toHaveLength(280)
    expect(resolved.endsWith('...')).toBe(true)
  })

  it('uses status-aware fallbacks for common authorization and server failures', () => {
    expect(resolveApiError({ response: { status: 401, data: {} } })).toBe('Please sign in again to continue.')
    expect(resolveApiError({ response: { status: 403, data: {} } })).toBe('You do not have permission to do that.')
    expect(resolveApiError({ response: { status: 404, data: {} } }, 'Missing course.')).toBe('Missing course.')
    expect(resolveApiError({ response: { status: 503, data: {} } })).toContain('having trouble')
  })

  it('detects unauthorized responses', () => {
    expect(isUnauthorizedApiError({ response: { status: 401 } })).toBe(true)
    expect(isUnauthorizedApiError({ response: { status: 403 } })).toBe(false)
  })
})
