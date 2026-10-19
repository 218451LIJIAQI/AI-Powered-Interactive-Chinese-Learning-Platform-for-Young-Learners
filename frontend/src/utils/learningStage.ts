import type { LearningStage } from '@/types/api'

/** Scaffolding order used for student practice (D23): guided -> scaffolded -> independent -> not set. */
export const LEARNING_STAGES = ['GUIDED', 'SCAFFOLDED', 'INDEPENDENT'] as const satisfies readonly LearningStage[]

export type LearningStageMessageKey = 'stageGuided' | 'stageScaffolded' | 'stageIndependent' | 'stageNotSet'

const STAGE_MESSAGE_KEYS: Record<LearningStage, LearningStageMessageKey> = {
  GUIDED: 'stageGuided',
  SCAFFOLDED: 'stageScaffolded',
  INDEPENDENT: 'stageIndependent',
}

export function normalizeLearningStage(value: unknown): LearningStage | null {
  if (typeof value !== 'string') return null
  const upper = value.trim().toUpperCase()
  return (LEARNING_STAGES as readonly string[]).includes(upper) ? (upper as LearningStage) : null
}

/** 0 = guided, 1 = scaffolded, 2 = independent, 3 = not set (or unknown). */
export function learningStageRank(stage: unknown): number {
  const normalized = normalizeLearningStage(stage)
  return normalized ? LEARNING_STAGES.indexOf(normalized) : LEARNING_STAGES.length
}

export function learningStageMessageKey(stage: unknown): LearningStageMessageKey {
  const normalized = normalizeLearningStage(stage)
  return normalized ? STAGE_MESSAGE_KEYS[normalized] : 'stageNotSet'
}

/**
 * Stable sort by learning stage. The backend already orders student lists this way; the client sorts again
 * defensively so preview data and older responses follow the same scaffolding order.
 */
export function sortByLearningStage<T extends { learningStage?: LearningStage | string | null }>(
  items: readonly T[],
): T[] {
  return items
    .map((item, index) => ({ item, index }))
    .sort(
      (a, b) => learningStageRank(a.item.learningStage) - learningStageRank(b.item.learningStage) || a.index - b.index,
    )
    .map(({ item }) => item)
}
