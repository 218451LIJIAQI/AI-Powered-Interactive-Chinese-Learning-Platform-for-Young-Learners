import type { PracticeResult } from '@/types/learn'

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

/** What a practice player sends when a run finishes. */
export interface PracticeCompletion {
  readonly result: PracticeResult
  readonly recap?: string
  readonly tally?: RunTally | null
}
