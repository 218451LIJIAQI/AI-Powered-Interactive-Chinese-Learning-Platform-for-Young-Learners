import { describe, expect, it } from 'vitest'

import { PRACTICE_TYPES } from '@/config/practiceActivities'
import { adminLearningPathMessages } from '@/plugins/adminLearningPathMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { learnBadgeMessages } from '@/plugins/learnBadgeMessages'
import { practiceTypeMessages } from '@/plugins/practiceTypeMessages'
import type { BadgeKey } from '@/types/learn'

import { BADGES } from './learnBadges'
import { DIFFICULTY_KEYS, setStatusKey, SKILL_KEYS } from './learnLabels'

/** True when a full `namespace.key` (or `namespace.group.key`) exists in every locale of the bundle. */
function hasKey(bundle: Record<string, object>, namespace: string, fullKey: string): boolean {
  const [prefix, ...path] = fullKey.split('.')
  const message = (messages: object) =>
    path.reduce<unknown>((node, key) => (node as Record<string, unknown> | undefined)?.[key], messages)
  return prefix === namespace && Object.values(bundle).every((messages) => typeof message(messages) === 'string')
}

describe('Learn labels and badges', () => {
  it('maps every practice type, skill, difficulty and status to an existing message', () => {
    for (const type of PRACTICE_TYPES) {
      const key = `practiceType.${type}`
      expect(hasKey(practiceTypeMessages, 'practiceType', key), key).toBe(true)
    }
    for (const key of [...Object.values(SKILL_KEYS), ...Object.values(DIFFICULTY_KEYS)]) {
      expect(hasKey(adminLearningPathMessages, 'adminLearning', key), key).toBe(true)
    }
    for (const key of [setStatusKey('PUBLISHED'), setStatusKey('DRAFT'), setStatusKey('ARCHIVED')]) {
      expect(hasKey(adminLearningSharedMessages, 'adminLearning', key), key).toBe(true)
    }
  })

  it('names every badge and its rule in every language', () => {
    const badges = Object.keys(BADGES) as BadgeKey[]
    expect(badges).toHaveLength(8)
    for (const badge of badges) {
      expect(hasKey(learnBadgeMessages, 'learn', BADGES[badge].nameKey), badge).toBe(true)
      expect(hasKey(learnBadgeMessages, 'learn', BADGES[badge].ruleKey), badge).toBe(true)
    }
    expect(new Set(badges.map((badge) => BADGES[badge].icon)).size).toBe(badges.length)
  })
})
