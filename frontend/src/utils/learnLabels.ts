import type { ContentStatus } from '@/types/api'
import type { Difficulty, LevelCheckLevel, LevelCheckSkill } from '@/types/learn'

/** Full message keys (of the `adminLearning` bundle) for the Learn module's admin labels. */

export const SKILL_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'adminLearning.skillVocabulary',
  LISTENING: 'adminLearning.skillListening',
  CHARACTERS: 'adminLearning.skillCharacters',
  NUMBERS: 'adminLearning.skillNumbers',
  PINYIN: 'adminLearning.skillPinyin',
  GRAMMAR: 'adminLearning.skillGrammar',
  SENTENCES: 'adminLearning.skillSentences',
  IDIOMS: 'adminLearning.skillIdioms',
}

/** HSK numbers used in the admin question list and level selector. */
export const LEVEL_HSK: Readonly<Record<LevelCheckLevel, number>> = {
  BEGINNER_1: 1,
  BEGINNER_2: 2,
}

export const DIFFICULTY_KEYS: Readonly<Record<Difficulty, string>> = {
  EASY: 'adminLearning.easy',
  MEDIUM: 'adminLearning.medium',
  HARD: 'adminLearning.hard',
}

/** Practice sets are only ever draft, published or archived; anything else reads as a draft. */
export function setStatusKey(status: ContentStatus): string {
  if (status === 'PUBLISHED') return 'adminLearning.published'
  return status === 'ARCHIVED' ? 'adminLearning.archived' : 'adminLearning.draft'
}
