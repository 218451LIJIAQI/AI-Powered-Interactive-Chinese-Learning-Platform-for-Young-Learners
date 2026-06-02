import type { AdminActivityType } from '@/types/api'

export type StudentPracticeActivityType = AdminActivityType

const PRACTICE_ACTIVITY_TYPES = [
  'FLASHCARD',
  'MATCHING_GAME',
  'LISTENING_EXERCISE',
  'TRACING_EXERCISE',
  'DRAG_DROP_ACTIVITY',
  'STORY_ACTIVITY',
] as const satisfies readonly StudentPracticeActivityType[]

const PRACTICE_ACTIVITY_ALIASES: Record<string, StudentPracticeActivityType> = {
  card: 'FLASHCARD',
  cards: 'FLASHCARD',
  flashcard: 'FLASHCARD',
  flashcards: 'FLASHCARD',
  match: 'MATCHING_GAME',
  matching: 'MATCHING_GAME',
  'matching-game': 'MATCHING_GAME',
  listening: 'LISTENING_EXERCISE',
  listen: 'LISTENING_EXERCISE',
  audio: 'LISTENING_EXERCISE',
  tone: 'LISTENING_EXERCISE',
  tones: 'LISTENING_EXERCISE',
  writing: 'TRACING_EXERCISE',
  character: 'TRACING_EXERCISE',
  characters: 'TRACING_EXERCISE',
  tracing: 'TRACING_EXERCISE',
  'tracing-exercise': 'TRACING_EXERCISE',
  drag: 'DRAG_DROP_ACTIVITY',
  drop: 'DRAG_DROP_ACTIVITY',
  'drag-drop': 'DRAG_DROP_ACTIVITY',
  'drag-and-drop': 'DRAG_DROP_ACTIVITY',
  'drag-drop-activity': 'DRAG_DROP_ACTIVITY',
  story: 'STORY_ACTIVITY',
  stories: 'STORY_ACTIVITY',
  'story-activity': 'STORY_ACTIVITY',
}

function normalizeAliasKey(value: string) {
  return value.trim().toLowerCase().replaceAll('_', '-').replace(/\s+/g, '-')
}

function practiceActivityQueryValue(type: StudentPracticeActivityType) {
  return type.toLowerCase().replaceAll('_', '-')
}

export function normalizePracticeActivity(value: unknown): StudentPracticeActivityType | null {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    return null
  }

  const normalized = rawValue.trim().toUpperCase().replaceAll('-', '_').replaceAll(' ', '_')
  if (PRACTICE_ACTIVITY_TYPES.includes(normalized as StudentPracticeActivityType)) {
    return normalized as StudentPracticeActivityType
  }

  return PRACTICE_ACTIVITY_ALIASES[normalizeAliasKey(rawValue)] ?? null
}

export function practiceRoute(activity?: unknown) {
  const normalized = normalizePracticeActivity(activity)
  return {
    name: 'student-practice',
    ...(normalized ? { query: { activity: practiceActivityQueryValue(normalized) } } : {}),
  }
}

export function inferPracticeActivityFromText(value: string | null | undefined): StudentPracticeActivityType {
  const text = (value ?? '').toLowerCase()
  if (/(listen|audio|tone|pronunciation|hearing)/u.test(text)) return 'LISTENING_EXERCISE'
  if (/(write|writing|trace|tracing|stroke|character|hanzi)/u.test(text)) return 'TRACING_EXERCISE'
  if (/(match|pair|meaning)/u.test(text)) return 'MATCHING_GAME'
  if (/(drag|drop|sort|arrange)/u.test(text)) return 'DRAG_DROP_ACTIVITY'
  if (/(story|read|reading|comprehension|culture)/u.test(text)) return 'STORY_ACTIVITY'
  return 'FLASHCARD'
}
