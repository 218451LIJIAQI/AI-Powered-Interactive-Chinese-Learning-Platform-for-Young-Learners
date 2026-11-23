import { describe, expect, it } from 'vitest'

import { i18n } from '@/plugins/i18n'

import { hskLevelName } from './hsk'

/** Levels 1 to 3 have a name; a higher level is named by its number, the same on every page. */
describe('hskLevelName', () => {
  it.each([
    ['en', 1, 'HSK 1 (Basic)'],
    ['en', 2, 'HSK 2 (Survival)'],
    ['en', 3, 'HSK 3 (Conversational)'],
    ['en', 4, 'HSK 4'],
    ['en', 6, 'HSK 6'],
    ['ms', 1, 'HSK 1 (Asas)'],
    ['zh-CN', 3, 'HSK 3（会话）'],
  ] as const)('%s: level %i reads "%s"', (locale, level, name) => {
    i18n.global.locale.value = locale
    expect(hskLevelName(i18n.global.t, level)).toBe(name)
  })
})
