import type { LessonResult } from '@/types/learn'

/** The path needs the completion, XP and unlock from the player once, on arrival. */
export function finishedLessonState(result: LessonResult | null) {
  if (!result) return { lessonResult: null }
  const { kind, passed, nextLesson, xp } = result
  return {
    lessonResult: {
      kind,
      passed,
      nextLesson: nextLesson ? { ...nextLesson } : null,
      xp: { xpEarned: xp.xpEarned, bonusXp: xp.bonusXp },
    },
  }
}

export function takeFinishedLesson() {
  const state = window.history.state as ReturnType<typeof finishedLessonState> | null
  if (!state?.lessonResult) return null
  window.history.replaceState({ ...state, lessonResult: undefined }, '')
  return state.lessonResult.passed ? state.lessonResult : null
}
