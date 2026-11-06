import { PRACTICE_TYPE_SLUGS } from '@/api/learn'
import type { PracticeType } from '@/types/learn'

/** One Practice activity as its tab and set meta line name it. Keys are full message keys. */
export interface PracticeTypeInfo {
  readonly type: PracticeType
  readonly slug: string
  readonly icon: string
  readonly labelKey: string
  /** The activity in a set's meta line ("Quiz · 6 questions"). */
  readonly metaKey: string
  readonly unitKey: string
}

/** The six activities, in tab order. Story is not one of them: AI Helper keeps its own Story. */
export const PRACTICE_TYPES: readonly PracticeTypeInfo[] = [
  {
    type: 'FLASHCARDS',
    slug: PRACTICE_TYPE_SLUGS.FLASHCARDS,
    icon: 'copy',
    labelKey: 'practice.typeFlashcards',
    metaKey: 'practice.typeFlashcards',
    unitKey: 'practice.unitCards',
  },
  {
    type: 'QUIZZES',
    slug: PRACTICE_TYPE_SLUGS.QUIZZES,
    icon: 'checklist',
    labelKey: 'practice.typeQuizzes',
    metaKey: 'practice.typeQuiz',
    unitKey: 'practice.unitQuestions',
  },
  {
    type: 'LISTENING',
    slug: PRACTICE_TYPE_SLUGS.LISTENING,
    icon: 'listening',
    labelKey: 'practice.typeListening',
    metaKey: 'practice.typeListening',
    unitKey: 'practice.unitQuestions',
  },
  {
    type: 'TRACING',
    slug: PRACTICE_TYPE_SLUGS.TRACING,
    icon: 'pen',
    labelKey: 'practice.typeTracing',
    metaKey: 'practice.typeTracing',
    unitKey: 'practice.unitStrokes',
  },
  {
    type: 'DRAG_DROP',
    slug: PRACTICE_TYPE_SLUGS.DRAG_DROP,
    icon: 'puzzle',
    labelKey: 'practice.typeDragDrop',
    metaKey: 'practice.typeDragDrop',
    unitKey: 'practice.unitSentences',
  },
  {
    type: 'MATCHING',
    slug: PRACTICE_TYPE_SLUGS.MATCHING,
    icon: 'link',
    labelKey: 'practice.typeMatching',
    metaKey: 'practice.typeMatching',
    unitKey: 'practice.unitPairs',
  },
]

export function practiceTypeInfo(type: PracticeType): PracticeTypeInfo {
  return PRACTICE_TYPES.find((info) => info.type === type) ?? PRACTICE_TYPES[0]!
}

/** The activity after this one (wrapping round), for "Try …" once a type has no set left. */
export function nextPracticeType(type: PracticeType): PracticeTypeInfo {
  const index = PRACTICE_TYPES.findIndex((info) => info.type === type)
  return PRACTICE_TYPES[(index + 1) % PRACTICE_TYPES.length]!
}
