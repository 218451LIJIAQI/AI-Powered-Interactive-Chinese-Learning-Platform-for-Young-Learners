import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'

import { extractApiError } from './apiError'

/**
 * `extractApiError` is used by ~30 pages and the auth store to turn an unknown
 * error (axios failure, validation response, thrown Error, or raw value) into
 * a user-safe message. It has a strict preference order:
 *
 *   1. First non-empty string in `response.data.errors`
 *   2. `response.data.message`
 *   3. `error.message` from axios
 *   4. `error.message` from a generic Error
 *   5. Caller-supplied fallback
 *
 * These tests pin that ordering so a future tweak never silently flips the
 * precedence and starts leaking generic "Network Error" strings to the UI.
 */

function makeAxiosError(data: unknown, message = 'Request failed'): AxiosError {
  const error = new AxiosError(message)
  error.response = {
    data,
    status: 400,
    statusText: 'Bad Request',
    headers: {},
    config: {} as never,
  }
  return error
}

describe('extractApiError', () => {
  it('returns the first non-empty string from the errors array', () => {
    const error = makeAxiosError({
      success: false,
      message: 'Validation failed',
      errors: ['email: must be a valid email'],
    })
    expect(extractApiError(error, 'fallback')).toBe('email: must be a valid email')
  })

  it('skips empty strings in the errors array before falling through', () => {
    const error = makeAxiosError({
      success: false,
      message: 'Validation failed',
      errors: ['', '   ', 'password: must not be blank'],
    })
    expect(extractApiError(error, 'fallback')).toBe('password: must not be blank')
  })

  it('falls back to `message` when errors is empty or missing', () => {
    const errorWithEmptyArray = makeAxiosError({ errors: [], message: 'Invalid credentials' })
    expect(extractApiError(errorWithEmptyArray, 'fallback')).toBe('Invalid credentials')

    const errorWithoutArray = makeAxiosError({ message: 'Plan not found' })
    expect(extractApiError(errorWithoutArray, 'fallback')).toBe('Plan not found')
  })

  it('falls back to axios `error.message` when the response body is unusable', () => {
    const error = makeAxiosError(undefined, 'Network Error')
    expect(extractApiError(error, 'fallback')).toBe('Network Error')
  })

  it('uses a generic Error message when the thrown value is not an AxiosError', () => {
    expect(extractApiError(new Error('Boom'), 'fallback')).toBe('Boom')
  })

  it('returns the caller-supplied fallback when nothing usable is available', () => {
    expect(extractApiError({}, 'Unable to load data.')).toBe('Unable to load data.')
    expect(extractApiError(null, 'Unable to load data.')).toBe('Unable to load data.')
    expect(extractApiError(undefined, 'Unable to load data.')).toBe('Unable to load data.')
    expect(extractApiError(new Error('   '), 'Unable to load data.')).toBe('Unable to load data.')
  })

  it('ignores non-string entries in the errors array', () => {
    const error = makeAxiosError({
      errors: [null, 42, { a: 1 }, 'readable error'] as unknown as string[],
      message: 'Validation failed',
    })
    expect(extractApiError(error, 'fallback')).toBe('readable error')
  })
})
