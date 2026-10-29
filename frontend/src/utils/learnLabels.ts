import type { ContentStatus, LearningStage } from '@/types/api'
import type { Difficulty, LevelCheckSkill, PracticeType } from '@/types/learn'
import { STAGE_KEYS } from '@/utils/classes'

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

export const STAGES: readonly LearningStage[] = ['GUIDED', 'SCAFFOLDED', 'INDEPENDENT']

/** The stage names classes use too (`classes.stage.*`: the page mounts the classes bundle), or "No stage". */
export function stageKey(stage: LearningStage | null): string {
  return stage ? STAGE_KEYS[stage] : 'adminLearning.stageNone'
}

const SKILL_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'adminLearning.skillVocabulary',
  LISTENING: 'adminLearning.skillListening',
  CHARACTERS: 'adminLearning.skillCharacters',
  SENTENCES: 'adminLearning.skillSentences',
  PINYIN: 'adminLearning.skillPinyin',
}

export const SKILLS: readonly LevelCheckSkill[] = ['VOCABULARY', 'LISTENING', 'CHARACTERS', 'SENTENCES', 'PINYIN']

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

export function setStatusTone(status: ContentStatus): 'success' | 'neutral' | 'warning' {
  if (status === 'PUBLISHED') return 'success'
  return status === 'ARCHIVED' ? 'warning' : 'neutral'
}
