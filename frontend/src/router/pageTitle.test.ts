import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('virtual:pwa-register', () => ({ registerSW: vi.fn() }))

import { i18n } from '@/plugins/i18n'
import { pageTitleMessages } from '@/plugins/pageTitleMessages'

import { router } from './index'
import { applyDocumentTitle, localizedPageTitle } from './pageTitle'

describe('localised browser tab titles (FR-I18N-05)', () => {
  afterEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('has a title for every named route in every language', () => {
    const routeNames = router
      .getRoutes()
      .map((record) => record.name)
      .filter((name): name is string => typeof name === 'string')
    expect(routeNames.length).toBeGreaterThan(30)

    const englishKeys = Object.keys(pageTitleMessages.en).sort()
    for (const locale of ['en', 'ms', 'zh-CN'] as const) {
      expect(Object.keys(pageTitleMessages[locale]).sort()).toEqual(englishKeys)
      for (const name of routeNames) {
        expect(pageTitleMessages[locale][name as keyof typeof pageTitleMessages.en], `${locale}:${name}`).toBeTruthy()
      }
    }
  })

  it('formats the title in the requested language with the brand name', () => {
    const adminUsers = { name: 'admin-users', meta: { title: 'Users' } }
    expect(localizedPageTitle(adminUsers, 'en')).toBe('Users | MandarinSpark')
    expect(localizedPageTitle(adminUsers, 'ms')).toBe('Pengguna | MandarinSpark')
    expect(localizedPageTitle(adminUsers, 'zh-CN')).toBe('用户 | MandarinSpark')
    expect(localizedPageTitle({ name: 'landing', meta: {} }, 'zh-CN')).toBe('MandarinSpark')
  })

  it('falls back to meta.title, then to the brand name', () => {
    expect(localizedPageTitle({ name: 'unknown-route', meta: { title: 'Somewhere' } }, 'ms')).toBe(
      'Somewhere | MandarinSpark',
    )
    expect(localizedPageTitle({ name: undefined, meta: {} }, 'en')).toBe('MandarinSpark')
  })

  it('uses the current i18n locale for document.title', () => {
    i18n.global.locale.value = 'zh-CN'
    applyDocumentTitle({ name: 'student-classes', meta: { title: 'Classes' } })
    expect(document.title).toBe('课堂 | MandarinSpark')

    i18n.global.locale.value = 'ms'
    applyDocumentTitle({ name: 'student-classes', meta: { title: 'Classes' } })
    expect(document.title).toBe('Kelas | MandarinSpark')
  })
})
