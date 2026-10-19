import { afterEach, describe, expect, it } from 'vitest'

import { resolveApiError } from './apiError'
import { localizeServerMessage, serverMessageCatalogue } from './serverMessages'

function apiError(status: number, message: string) {
  return { response: { status, data: { message, errors: [message] } } }
}

describe('localised backend error messages (FR-I18N-05)', () => {
  afterEach(() => {
    document.documentElement.lang = 'en'
  })

  it('translates every mapped backend message in Malay and Simplified Chinese', () => {
    const keys = new Set(Object.values(serverMessageCatalogue.BACKEND_MESSAGES))
    for (const locale of ['ms', 'zh-CN'] as const) {
      const translations = serverMessageCatalogue.TRANSLATIONS[locale]
      expect(Object.keys(translations).sort()).toEqual([...keys].sort())
      for (const key of keys) expect(translations[key].trim()).not.toBe('')
    }
  })

  it('keeps English and unknown messages unchanged (backend text is the fallback)', () => {
    expect(localizeServerMessage('Invalid email or password.', 'en')).toBe('Invalid email or password.')
    expect(localizeServerMessage('Lesson 12 has no quiz yet.', 'ms')).toBe('Lesson 12 has no quiz yet.')
  })

  it('keeps the daily AI limit distinct from the generic rate-limit message', () => {
    const daily = 'Daily AI request limit reached. Please try again tomorrow.'
    expect(localizeServerMessage(daily, 'en')).toBe(daily)
    expect(localizeServerMessage(daily, 'zh-CN')).toBe('今天的 AI 使用次数已达上限，请明天再试。')
    expect(localizeServerMessage(daily, 'ms')).toBe('Had permintaan AI harian telah dicapai. Sila cuba lagi esok.')
  })

  it('matches messages regardless of case and spacing', () => {
    expect(localizeServerMessage('  invalid EMAIL or   password. ', 'zh-CN')).toBe('邮箱或密码不正确。')
  })

  it('resolveApiError shows the login, registration, reset and validation errors in the interface language', () => {
    document.documentElement.lang = 'ms'
    expect(resolveApiError(apiError(401, 'Invalid email or password.'))).toBe('E-mel atau kata laluan tidak sah.')
    expect(resolveApiError(apiError(400, 'Email is already registered.'))).toBe('E-mel ini sudah didaftarkan.')

    document.documentElement.lang = 'zh-CN'
    expect(resolveApiError(apiError(400, 'Reset token has expired. Please request a new one.'))).toBe(
      '重置链接已过期，请重新申请。',
    )
    expect(
      resolveApiError(
        apiError(400, 'The information you entered is incomplete or invalid. Please check it and try again.'),
      ),
    ).toBe('输入的信息不完整或无效，请检查后重试。')
    expect(resolveApiError(apiError(400, 'Something only the server knows.'))).toBe('Something only the server knows.')
  })
})
