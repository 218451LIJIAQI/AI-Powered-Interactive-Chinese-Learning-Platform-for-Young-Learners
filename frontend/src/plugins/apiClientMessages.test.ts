import { describe, expect, it } from 'vitest'

import { apiClientMessage, apiClientMessages } from './apiClientMessages'

// Key parity and compilation are checked for every bundle in messageBundles.test.ts.
describe('API-layer fallback messages', () => {
  it('localises API-layer fallback errors by the document language', () => {
    document.documentElement.lang = 'ms'
    expect(apiClientMessage('unexpectedFormat')).toBe(apiClientMessages.ms.unexpectedFormat)
    document.documentElement.lang = 'zh-CN'
    expect(apiClientMessage('streamTimeout')).toBe(apiClientMessages['zh-CN'].streamTimeout)
    document.documentElement.lang = 'en'
    expect(apiClientMessage('rejected')).toBe('The request was not accepted by the server.')
  })
})
