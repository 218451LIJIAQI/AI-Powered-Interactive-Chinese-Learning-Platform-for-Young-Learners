import { describe, expect, it } from 'vitest'

import { apiFieldError, cleanServerMessage, resolveApiError } from './apiError'

describe('cleanServerMessage', () => {
  it('keeps readable text, collapsing spaces and removing control and bidi characters', () => {
    expect(cleanServerMessage('  Lesson   not\nfound. ')).toBe('Lesson not found.')
    expect(cleanServerMessage('Bad\u202Einput\u0007')).toBe('Bad input')
    expect(cleanServerMessage('x'.repeat(300))).toBe(`${'x'.repeat(277)}...`)
  })

  it('drops non-text, empty text and bare transport errors', () => {
    expect(cleanServerMessage(42)).toBeNull()
    expect(cleanServerMessage('   ')).toBeNull()
    expect(cleanServerMessage('Network Error')).toBeNull()
    expect(cleanServerMessage('Request failed')).toBeNull()
  })
})

describe('apiFieldError', () => {
  const bioRejected = {
    response: {
      status: 400,
      data: {
        message: 'The information you entered is incomplete or invalid. Please check it and try again.',
        fieldErrors: { bio: ['Teacher bio must be between 20 and 1000 characters', 'second'] },
      },
    },
  }

  it('returns the first server message for the field, in the interface language', () => {
    expect(apiFieldError(bioRejected, 'bio')).toBe('Teacher bio must be between 20 and 1000 characters')
    document.documentElement.lang = 'ms'
    expect(apiFieldError(bioRejected, 'bio')).toBe('Biografi guru mestilah antara 20 hingga 1000 aksara.')
  })

  it('returns null when the server sent nothing usable for the field', () => {
    expect(apiFieldError(bioRejected, 'fullName')).toBeNull()
    expect(apiFieldError({ response: { status: 400, data: { fieldErrors: { bio: [' '] } } } }, 'bio')).toBeNull()
    expect(apiFieldError({ response: { status: 400, data: { fieldErrors: { bio: 'text' } } } }, 'bio')).toBeNull()
    expect(apiFieldError({ request: {} }, 'bio')).toBeNull()
    expect(apiFieldError(null, 'bio')).toBeNull()
  })
})

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

  it('uses the first validation error when no backend message is present', () => {
    expect(
      resolveApiError({
        response: {
          status: 400,
          data: { errors: ['Email is required.'] },
        },
      }),
    ).toBe('Email is required.')
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

  it('explains a missing item and a rate limit when the server sends no readable message (SDD Table 6.3)', () => {
    expect(resolveApiError({ response: { status: 404, data: {} } })).toBe(
      'This item is not available any more. Please go back and choose another one.',
    )
    expect(resolveApiError({ response: { status: 429, data: {} } }, 'Could not load lessons.')).toBe(
      'You are doing that too often. Please wait a moment and try again.',
    )
    expect(resolveApiError({ response: { status: 418, data: {} } })).toBe(
      'We could not complete that request. Please try again.',
    )
  })

  it('localizes generic transport and authorization errors from the active document language', () => {
    document.documentElement.lang = 'zh-CN'
    expect(resolveApiError({ response: { status: 401, data: {} } })).toBe('请重新登录后继续。')
    expect(resolveApiError({ request: {} })).toContain('无法连接学习服务')

    document.documentElement.lang = 'ms-MY'
    expect(resolveApiError({ response: { status: 403, data: {} } })).toContain('tiada kebenaran')
    expect(resolveApiError({ code: 'ECONNABORTED' })).toContain('terlalu lama')
  })
})
