import type { RouteLocationRaw } from 'vue-router'

import type { IconName } from '@/components/common/IconGlyph.vue'

interface PracticeActivityMeta {
  readonly slug: string
  readonly icon: IconName
  readonly unitKey: string
}

/** Practice activities in tab and library order. The same slugs identify them in the API and the router. */
export const PRACTICE_ACTIVITIES = {
  FLASHCARDS: { slug: 'flashcards', icon: 'copy', unitKey: 'practice.unitCards' },
  LISTENING: { slug: 'listening', icon: 'listening', unitKey: 'practice.unitQuestions' },
  TRACING: { slug: 'tracing', icon: 'pen', unitKey: 'practice.unitStrokes' },
  DRAG_DROP: { slug: 'drag-drop', icon: 'puzzle', unitKey: 'practice.unitSentences' },
  MATCHING: { slug: 'matching', icon: 'link', unitKey: 'practice.unitPairs' },
  TYPING: { slug: 'typing', icon: 'language', unitKey: 'practice.unitWords' },
} as const satisfies Record<string, PracticeActivityMeta>

export type PracticeType = keyof typeof PRACTICE_ACTIVITIES
export const PRACTICE_TYPES = Object.keys(PRACTICE_ACTIVITIES) as readonly PracticeType[]

/** The Practice page opened on one set (`/student/practice/listening?set=12`). */
export function practiceSetRoute(type: PracticeType, setId: number): RouteLocationRaw {
  return { name: 'student-practice', params: { type: PRACTICE_ACTIVITIES[type].slug }, query: { set: String(setId) } }
}

/** The activity after this one (wrapping round), for "Try …" once a type offers no next set. */
export function nextPracticeType(type: PracticeType): PracticeType {
  const index = PRACTICE_TYPES.indexOf(type)
  return PRACTICE_TYPES[(index + 1) % PRACTICE_TYPES.length]!
}

/** The server's limit of one Mistakes review (MistakeService): a longer list reaches the review over several rounds. */
export const MISTAKES_REVIEW_SIZE = 20
/** XP for a review that clears at least one mistake, once a day (RewardRules.MISTAKES_REVIEW_XP). */
const MISTAKES_REVIEW_XP = 10
/** About half a minute a mistake, as the weekly mistakes reminder counts it (LearnerNotifications.mistakesToReviewDraft). */
const MISTAKES_PER_MINUTE = 2

/**
 * What the next Mistakes review takes, for "Review all 6 · about 3 min · +10 XP" (Practice) and "Fix 6 mistakes ·
 * Practice · 3 min · +10 XP" (Home): the mistakes one review works through and the minutes they take.
 */
export function mistakesReview(waiting: number): { count: number; minutes: number; xp: number } {
  const count = Math.min(waiting, MISTAKES_REVIEW_SIZE)
  return { count, minutes: Math.ceil(count / MISTAKES_PER_MINUTE), xp: MISTAKES_REVIEW_XP }
}
