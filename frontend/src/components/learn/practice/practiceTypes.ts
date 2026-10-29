import { PRACTICE_TYPE_SLUGS } from '@/api/learn'
import type { PracticeType } from '@/types/learn'

/** The pigments of `tokens.css` (`--pigment-*`) a Practice type can wear. */
export type PracticePigment = 'cobalt' | 'cinnabar' | 'teal' | 'ink' | 'plum' | 'jade' | 'ochre'

/**
 * One Practice type as the room shows it: its own pigment and a character (set in a 田字格 tile) that tell the types
 * apart at a glance, on the hub, in the player header and on the done card. Keys are full message keys.
 */
export interface PracticeTypeInfo {
  readonly type: PracticeType
  readonly slug: string
  readonly glyph: string
  readonly pigment: PracticePigment
  readonly labelKey: string
  readonly trainsKey: string
  readonly unitKey: string
  /** A typical set takes about this many minutes. */
  readonly minutes: number
}

/** The seven types, in the order the room lists them. */
export const PRACTICE_TYPES: readonly PracticeTypeInfo[] = [
  {
    type: 'FLASHCARDS',
    slug: PRACTICE_TYPE_SLUGS.FLASHCARDS,
    glyph: '卡',
    pigment: 'cobalt',
    labelKey: 'practice.typeFlashcards',
    trainsKey: 'practice.trainsFlashcards',
    unitKey: 'practice.unitCards',
    minutes: 2,
  },
  {
    type: 'QUIZZES',
    slug: PRACTICE_TYPE_SLUGS.QUIZZES,
    glyph: '测',
    pigment: 'cinnabar',
    labelKey: 'practice.typeQuizzes',
    trainsKey: 'practice.trainsQuizzes',
    unitKey: 'practice.unitQuestions',
    minutes: 3,
  },
  {
    type: 'LISTENING',
    slug: PRACTICE_TYPE_SLUGS.LISTENING,
    glyph: '听',
    pigment: 'teal',
    labelKey: 'practice.typeListening',
    trainsKey: 'practice.trainsListening',
    unitKey: 'practice.unitQuestions',
    minutes: 3,
  },
  {
    type: 'TRACING',
    slug: PRACTICE_TYPE_SLUGS.TRACING,
    glyph: '写',
    pigment: 'ink',
    labelKey: 'practice.typeTracing',
    trainsKey: 'practice.trainsTracing',
    unitKey: 'practice.unitWords',
    minutes: 1,
  },
  {
    type: 'DRAG_DROP',
    slug: PRACTICE_TYPE_SLUGS.DRAG_DROP,
    glyph: '句',
    pigment: 'plum',
    labelKey: 'practice.typeDragDrop',
    trainsKey: 'practice.trainsDragDrop',
    unitKey: 'practice.unitSentences',
    minutes: 3,
  },
  {
    type: 'MATCHING',
    slug: PRACTICE_TYPE_SLUGS.MATCHING,
    glyph: '配',
    pigment: 'jade',
    labelKey: 'practice.typeMatching',
    trainsKey: 'practice.trainsMatching',
    unitKey: 'practice.unitPairs',
    minutes: 2,
  },
  {
    type: 'STORY',
    slug: PRACTICE_TYPE_SLUGS.STORY,
    glyph: '读',
    pigment: 'ochre',
    labelKey: 'practice.typeStory',
    trainsKey: 'practice.trainsStory',
    unitKey: 'practice.unitQuestions',
    minutes: 4,
  },
]

export function practiceTypeInfo(type: PracticeType): PracticeTypeInfo {
  return PRACTICE_TYPES.find((info) => info.type === type) ?? PRACTICE_TYPES[0]!
}

/** The type after this one in the room (wrapping round), for "Try …" once every set of a type is done. */
export function nextPracticeType(type: PracticeType): PracticeTypeInfo {
  const index = PRACTICE_TYPES.findIndex((info) => info.type === type)
  return PRACTICE_TYPES[(index + 1) % PRACTICE_TYPES.length]!
}
