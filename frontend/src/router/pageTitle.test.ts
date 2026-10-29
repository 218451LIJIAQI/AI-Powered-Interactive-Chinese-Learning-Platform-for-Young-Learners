import { describe, expect, it } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { pageTitleMessages } from '@/plugins/pageTitleMessages'

import { router } from './index'
import { applyDocumentTitle } from './pageTitle'

describe('localised browser tab titles (FRLANG02)', () => {
  it('has a title for every named route in every language', () => {
    const routeNames = router
      .getRoutes()
      .map((record) => record.name)
      .filter((name): name is string => typeof name === 'string')
    expect(routeNames.length).toBeGreaterThan(30)

    // Key parity and non-empty text in every locale are checked for every bundle in messageBundles.test.ts.
    for (const name of routeNames)
      expect(pageTitleMessages.en[name as keyof typeof pageTitleMessages.en], name).toBeTruthy()
  })

  it('writes the route title in the current i18n language with the brand name', () => {
    const titleIn = (locale: 'en' | 'ms' | 'zh-CN', name: string | undefined) => {
      i18n.global.locale.value = locale
      applyDocumentTitle({ name })
      return document.title
    }
    expect(titleIn('en', 'admin-users')).toBe('Users | MandarinSpark')
    expect(titleIn('ms', 'admin-users')).toBe('Pengguna | MandarinSpark')
    expect(titleIn('zh-CN', 'admin-users')).toBe('用户 | MandarinSpark')
    expect(titleIn('zh-CN', 'student-classes')).toBe('我的课堂 | MandarinSpark')
    expect(titleIn('zh-CN', 'landing')).toBe('MandarinSpark')
  })

  it('falls back to the brand name for a route without a title', () => {
    i18n.global.locale.value = 'ms'
    applyDocumentTitle({ name: 'unknown-route' })
    expect(document.title).toBe('MandarinSpark')
    applyDocumentTitle({ name: undefined })
    expect(document.title).toBe('MandarinSpark')
  })
})
