import { describe, expect, it } from 'vitest'

import {
  learningStageMessageKey,
  learningStageRank,
  normalizeLearningStage,
  sortByLearningStage,
} from './learningStage'

describe('learning stage helpers (D23)', () => {
  it('normalises backend values and ignores unknown ones', () => {
    expect(normalizeLearningStage('guided')).toBe('GUIDED')
    expect(normalizeLearningStage(' INDEPENDENT ')).toBe('INDEPENDENT')
    expect(normalizeLearningStage('EXPERT')).toBeNull()
    expect(normalizeLearningStage(null)).toBeNull()
  })

  it('ranks guided before scaffolded before independent before not set', () => {
    expect([null, 'INDEPENDENT', 'GUIDED', 'SCAFFOLDED'].map(learningStageRank)).toEqual([3, 2, 0, 1])
  })

  it('orders practice guided -> scaffolded -> independent -> unset and keeps the server order inside a stage', () => {
    const items = [
      { id: 1, learningStage: null },
      { id: 2, learningStage: 'INDEPENDENT' as const },
      { id: 3, learningStage: 'GUIDED' as const },
      { id: 4, learningStage: 'SCAFFOLDED' as const },
      { id: 5, learningStage: 'GUIDED' as const },
      { id: 6 },
    ]

    expect(sortByLearningStage(items).map((item) => item.id)).toEqual([3, 5, 4, 2, 1, 6])
    expect(items.map((item) => item.id)).toEqual([1, 2, 3, 4, 5, 6])
  })

  it('maps stages to shared message keys', () => {
    expect(learningStageMessageKey('SCAFFOLDED')).toBe('stageScaffolded')
    expect(learningStageMessageKey(undefined)).toBe('stageNotSet')
  })
})
