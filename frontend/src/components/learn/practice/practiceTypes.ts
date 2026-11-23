import type { RouteLocationRaw } from 'vue-router'

import { PRACTICE_ACTIVITIES, PRACTICE_TYPES } from '@/config/practiceActivities'
import type { PracticeType } from '@/types/learn'

/**
 * What a player of answered items (Listening, Drag & Drop, Typing) saw of its finished run beyond the server's result:
 * the done card says from it how many items were answered and whether anything went to Mistakes.
 */
export interface RunTally {
  /** Items whose first answer was given, not skipped. */
  readonly answered: number
  /**
   * Checks of the run that put an item on the Mistakes list: wrong answers (once more ones too) and skips, but not a
   * skip whose audio did not play.
   */
  readonly mistakes: number
}

/** The Practice page opened on one set (`/student/practice/listening?set=12`). */
export function practiceSetRoute(type: PracticeType, setId: number): RouteLocationRaw {
  return { name: 'student-practice', params: { type: PRACTICE_ACTIVITIES[type].slug }, query: { set: String(setId) } }
}

/** The server's limit of one Mistakes review (MistakeService): a longer list reaches the review over several rounds. */
export const MISTAKES_REVIEW_SIZE = 20
/** XP for a review that clears at least one mistake, once a day (RewardRules.MISTAKES_REVIEW_XP). */
const MISTAKES_REVIEW_XP = 10
/** About half a minute a mistake, as the weekly mistakes reminder counts it (LearnerReminderJobs). */
const MISTAKES_PER_MINUTE = 2

/**
 * What the next Mistakes review takes, for "Review all 6 · about 3 min · +10 XP" (Practice) and "Fix 6 mistakes ·
 * Practice · 3 min · +10 XP" (Home): the mistakes one review works through and the minutes they take.
 */
export function mistakesReview(waiting: number): { count: number; minutes: number; xp: number } {
  const count = Math.min(waiting, MISTAKES_REVIEW_SIZE)
  return { count, minutes: Math.ceil(count / MISTAKES_PER_MINUTE), xp: MISTAKES_REVIEW_XP }
}

/** The activity after this one (wrapping round), for "Try …" once a type offers no next set. */
export function nextPracticeType(type: PracticeType): PracticeType {
  const index = PRACTICE_TYPES.indexOf(type)
  return PRACTICE_TYPES[(index + 1) % PRACTICE_TYPES.length]!
}
