import { describe, expect, it } from 'vitest'

import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { BadgeKey, Difficulty } from '@/types/learn'

import { BADGES } from './learnBadges'
import {
  difficultyKey,
  PRACTICE_TYPES,
  practiceTypeKey,
  setStatusKey,
  setStatusTone,
  SKILLS,
  skillKey,
  STAGES,
  stageKey,
} from './learnLabels'

/** True when a full `namespace.key` (or `namespace.group.key`) exists in every locale of the bundle. */
function hasKey(bundle: Record<string, object>, namespace: string, fullKey: string): boolean {
  const [prefix, ...path] = fullKey.split('.')
  const message = (messages: object) =>
    path.reduce<unknown>((node, key) => (node as Record<string, unknown> | undefined)?.[key], messages)
  return prefix === namespace && Object.values(bundle).every((messages) => typeof message(messages) === 'string')
}

describe('Learn labels and badges', () => {
  it('maps every practice type, stage, skill, difficulty and status to an existing message', () => {
    const keys = [
      ...PRACTICE_TYPES.map(practiceTypeKey),
      stageKey(null),
      ...SKILLS.map(skillKey),
      ...(['EASY', 'MEDIUM', 'HARD'] as Difficulty[]).map(difficultyKey),
      setStatusKey('PUBLISHED'),
      setStatusKey('DRAFT'),
      setStatusKey('ARCHIVED'),
    ]
    for (const key of keys) expect(hasKey(adminLearningMessages, 'adminLearning', key), key).toBe(true)
    // The stages are named as in classes.
    for (const key of STAGES.map(stageKey)) expect(hasKey(classesMessages, 'classes', key), key).toBe(true)
  })

  it('names every badge and its rule in every language', () => {
    const badges = Object.keys(BADGES) as BadgeKey[]
    expect(badges).toHaveLength(8)
    for (const badge of badges) {
      expect(hasKey(learnMessages, 'learn', BADGES[badge].nameKey), badge).toBe(true)
      expect(hasKey(learnMessages, 'learn', BADGES[badge].ruleKey), badge).toBe(true)
    }
    expect(new Set(badges.map((badge) => BADGES[badge].icon)).size).toBe(badges.length)
  })

  it('colours an archived set as a warning and a published one as a success', () => {
    expect(setStatusTone('PUBLISHED')).toBe('success')
    expect(setStatusTone('ARCHIVED')).toBe('warning')
    expect(setStatusTone('DRAFT')).toBe('neutral')
  })
})
