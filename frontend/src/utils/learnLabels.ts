import type { ContentStatus } from '@/types/api'
import type { Difficulty, LevelCheckSkill, PracticeType } from '@/types/learn'

/** Full message keys (of the `adminLearning` bundle) for the Learn module's admin labels. */

export const PRACTICE_TYPES: readonly PracticeType[] = [
  'FLASHCARDS',
  'QUIZZES',
  'LISTENING',
  'TRACING',
  'DRAG_DROP',
  'MATCHING',
  'STORY',
]

const PRACTICE_TYPE_KEYS: Readonly<Record<PracticeType, string>> = {
  FLASHCARDS: 'adminLearning.typeFlashcards',
  QUIZZES: 'adminLearning.typeQuizzes',
  LISTENING: 'adminLearning.typeListening',
  TRACING: 'adminLearning.typeTracing',
  DRAG_DROP: 'adminLearning.typeDragDrop',
  MATCHING: 'adminLearning.typeMatching',
  STORY: 'adminLearning.typeStory',
}

export function practiceTypeKey(type: PracticeType): string {
  return PRACTICE_TYPE_KEYS[type]
}

const SKILL_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'adminLearning.skillVocabulary',
  LISTENING: 'adminLearning.skillListening',
  CHARACTERS: 'adminLearning.skillCharacters',
  SENTENCES: 'adminLearning.skillSentences',
  PINYIN: 'adminLearning.skillPinyin',
  NUMBERS: 'adminLearning.skillNumbers',
  GRAMMAR: 'adminLearning.skillGrammar',
  IDIOMS: 'adminLearning.skillIdioms',
}

export const SKILLS: readonly LevelCheckSkill[] = [
  'VOCABULARY',
  'LISTENING',
  'CHARACTERS',
  'NUMBERS',
  'PINYIN',
  'GRAMMAR',
  'SENTENCES',
  'IDIOMS',
]

export function skillKey(skill: LevelCheckSkill): string {
  return SKILL_KEYS[skill]
}

const DIFFICULTY_KEYS: Readonly<Record<Difficulty, string>> = {
  EASY: 'adminLearning.easy',
  MEDIUM: 'adminLearning.medium',
  HARD: 'adminLearning.hard',
}

export function difficultyKey(difficulty: Difficulty): string {
  return DIFFICULTY_KEYS[difficulty]
}

/** Practice sets are only ever draft, published or archived; anything else reads as a draft. */
export function setStatusKey(status: ContentStatus): string {
  if (status === 'PUBLISHED') return 'adminLearning.published'
  return status === 'ARCHIVED' ? 'adminLearning.archived' : 'adminLearning.draft'
}
