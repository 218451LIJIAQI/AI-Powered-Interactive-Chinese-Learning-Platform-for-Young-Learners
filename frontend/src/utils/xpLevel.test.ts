import { describe, expect, it } from 'vitest'

import { xpLevelFor, xpLevelTitleKey } from './xpLevel'

describe('xpLevelFor', () => {
  it('starts at level 1 and moves up every 100 XP', () => {
    expect(xpLevelFor(0)).toEqual({ level: 1, titleKey: 'xpLevel.starter', xpIntoLevel: 0, nextLevelXp: 100 })
    expect(xpLevelFor(250)).toEqual({ level: 3, titleKey: 'xpLevel.regular', xpIntoLevel: 50, nextLevelXp: 300 })
  })

  it('treats negative or invalid XP as zero and keeps the top title for high levels', () => {
    expect(xpLevelFor(-5).level).toBe(1)
    expect(xpLevelFor(Number.NaN).level).toBe(1)
    expect(xpLevelTitleKey(12)).toBe('xpLevel.champion')
  })
})
