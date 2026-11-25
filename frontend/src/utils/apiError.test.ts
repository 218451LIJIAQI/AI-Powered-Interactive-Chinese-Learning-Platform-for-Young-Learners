import { describe, expect, it } from 'vitest'

import { cleanServerMessage, hasServerMessage, resolveApiError } from './apiError'

describe('cleanServerMessage', () => {
  it('keeps readable text, collapsing spaces and removing control and bidi characters', () => {
    expect(cleanServerMessage('  Lesson   not\nfound. ')).toBe('Lesson not found.')
    expect(cleanServerMessage('Bad\u202Einput\u0007')).toBe('Bad input')
    expect(cleanServerMessage('x'.repeat(300))).toBe(`${'x'.repeat(277)}...`)
    expect(cleanServerMessage('字'.repeat(276) + '𠀀后续内容')).toBe(`${'字'.repeat(276)}...`)
  })

  it('drops non-text, empty text and bare transport errors', () => {
    expect(cleanServerMessage(42)).toBeNull()
    expect(cleanServerMessage('   ')).toBeNull()
    expect(cleanServerMessage('Network Error')).toBeNull()
    expect(cleanServerMessage('Request failed')).toBeNull()
  })
})

describe('resolveApiError', () => {
  it('uses sanitized backend messages before generic fallbacks', () => {
    expect(
      resolveApiError({
        response: {
          status: 400,
          data: { message: 'Please upload a smaller file.' },
        },
      }),
    ).toBe('Please upload a smaller file.')
  })

  it('maps timeout, cancellation, and network failures to learner-friendly messages', () => {
    expect(resolveApiError({ code: 'ECONNABORTED', message: 'timeout of 20000ms exceeded' })).toContain('too long')
    expect(resolveApiError({ code: 'ETIMEDOUT', message: 'connect ETIMEDOUT' })).toContain('too long')
    expect(resolveApiError({ code: 'ERR_CANCELED', name: 'CanceledError' }, 'Fallback message.')).toBe(
      'Fallback message.',
    )
    expect(resolveApiError({ request: {} })).toContain('cannot reach')
  })

  it('does not expose transport internals as user-facing messages', () => {
    expect(
      resolveApiError({
        response: {
          status: 500,
          data: { message: 'Network Error' },
        },
      }),
    ).toBe('The learning service is having trouble right now. Please try again shortly.')
  })

  it('cleans unsafe and oversized backend messages before display', () => {
    const message = `  Please retry\u202e\n${'x'.repeat(400)}  `

    const resolved = resolveApiError({
      response: {
        status: 400,
        data: { message },
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

    document.documentElement.lang = 'ms'
    expect(resolveApiError({ response: { status: 403, data: {} } })).toContain('tiada kebenaran')
    expect(resolveApiError({ code: 'ECONNABORTED' })).toContain('terlalu lama')
  })
})

describe('hasServerMessage', () => {
  const TAKEN = 'Email is already registered.'
  const refusal = (message: unknown) => ({ response: { status: 400, data: { message } } })

  it('recognises the refusal whatever its case, spacing or final full stop, as translation does', () => {
    expect(hasServerMessage(refusal(TAKEN), TAKEN)).toBe(true)
    expect(hasServerMessage(refusal('  email is\nalready   registered '), TAKEN)).toBe(true)
  })

  it('does not take another message, no message or a failure without an answer for it', () => {
    expect(hasServerMessage(refusal('Invalid email or password.'), TAKEN)).toBe(false)
    expect(hasServerMessage(refusal(undefined), TAKEN)).toBe(false)
    expect(hasServerMessage(new Error(TAKEN), TAKEN)).toBe(false)
    expect(hasServerMessage(null, TAKEN)).toBe(false)
  })
})
