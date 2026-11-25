/**
 * Shared helpers of the AI Helper tabs: blocked-state type, practice-check display helpers, report target descriptor,
 * and scrolling a freshly generated result into view.
 */
import type { AiReportPayload, PracticeCheck, PracticeCheckType } from '@/types/ai'

/** Why the AI Helper cannot send a request right now, as the page shows it. */
export type AiBlockedReason = 'unavailable' | 'limit' | null

export function practiceCheckKindKey(kind: PracticeCheckType): 'aiHelper.recording' | 'aiHelper.handwriting' {
  return kind === 'RECORDING' ? 'aiHelper.recording' : 'aiHelper.handwriting'
}

export function formatPracticeCheckMeta(check: PracticeCheck, when: string, fileName?: string | null): string {
  return [check.lessonTitle, fileName, when].filter(Boolean).join(' · ')
}

/** What a report is about and how the dialog words it; the student adds the reason and a note. */
export interface AiReportTarget {
  payload: Omit<AiReportPayload, 'reasonCategory' | 'description'>
  titleKey: string
  attachedKey: string
}

/**
 * Brings a new result (a score, a quiz, a story, the conversation summary) into view when it starts above the screen
 * or low on it. A result already in view is left where it is.
 */
export function revealResult(element: Element | null | undefined): void {
  if (!element || !element.isConnected) return
  const top = element.getBoundingClientRect().top
  const minTop = Number.parseFloat(getComputedStyle(element).scrollMarginTop) || 0
  if (top >= minTop && top <= window.innerHeight * 0.6) return
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  element.scrollIntoView({ block: 'start', behavior: reduceMotion ? 'auto' : 'smooth' })
}
