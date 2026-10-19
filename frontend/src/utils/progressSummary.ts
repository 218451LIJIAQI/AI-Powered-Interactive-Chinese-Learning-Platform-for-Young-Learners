import type { LessonMastery, StudentProgressLessonResponse, StudentProgressSummaryResponse } from '@/types/api'
import { clampPercent, nonNegativeInteger } from '@/utils/format'
import { masteryRank, normalizeMastery } from '@/utils/mastery'

/** Student progress page helpers (D25 mastery, weak areas = lesson titles of recent wrong answers). */

export type MasteryCounts = Record<LessonMastery, number>
export type MasteryFilter = 'ALL' | LessonMastery
export type LessonSort = 'recent' | 'needsAttention' | 'mastery' | 'title'

export interface ProgressOverview {
  overallPercent: number
  completedLessons: number
  startedLessons: number
  quizAverage: number
}

export interface WeakAreaLink {
  title: string
  lessonId: number | null
}

function emptyMasteryCounts(): MasteryCounts {
  return { NOT_STARTED: 0, IN_PROGRESS: 0, COMPLETED: 0, MASTERED: 0 }
}

/** Number of lessons per mastery level (unknown values count as NOT_STARTED). */
export function countMastery(lessons: readonly Pick<StudentProgressLessonResponse, 'mastery'>[]): MasteryCounts {
  const counts = emptyMasteryCounts()
  for (const lesson of lessons) counts[normalizeMastery(lesson.mastery)] += 1
  return counts
}

/** Clamped headline numbers; completed never exceeds started. */
export function progressOverview(
  summary: Partial<StudentProgressSummaryResponse> | null | undefined,
): ProgressOverview {
  const started = nonNegativeInteger(summary?.totalLessonsStarted)
  const completed = nonNegativeInteger(summary?.completedLessons)
  return {
    overallPercent: clampPercent(summary?.overallCompletionPercent),
    completedLessons: started > 0 ? Math.min(completed, started) : completed,
    startedLessons: Math.max(started, completed),
    quizAverage: clampPercent(summary?.quizAverage),
  }
}

/** Best quiz percentage rounded to 0..100, or null when the learner has not attempted a quiz of the lesson. */
export function bestQuizPercent(lesson: Pick<StudentProgressLessonResponse, 'bestQuizPercentage'>): number | null {
  const value = lesson.bestQuizPercentage
  if (value === null || value === undefined || !Number.isFinite(value)) return null
  return clampPercent(value)
}

export function filterLessonsByMastery<T extends Pick<StudentProgressLessonResponse, 'mastery'>>(
  lessons: readonly T[],
  filter: MasteryFilter,
): T[] {
  if (filter === 'ALL') return [...lessons]
  return lessons.filter((lesson) => normalizeMastery(lesson.mastery) === filter)
}

function compareTitles(a: StudentProgressLessonResponse, b: StudentProgressLessonResponse): number {
  return (a.lessonTitle ?? '').localeCompare(b.lessonTitle ?? '', undefined, { sensitivity: 'base' })
}

/**
 * Sorts a copy of the lessons. `recent` keeps the server order (newest activity first);
 * `needsAttention` puts the lowest mastery / completion first; `mastery` the highest first.
 */
export function sortLessons<T extends StudentProgressLessonResponse>(lessons: readonly T[], sort: LessonSort): T[] {
  const copy = [...lessons]
  switch (sort) {
    case 'needsAttention':
      return copy.sort(
        (a, b) =>
          masteryRank(a.mastery) - masteryRank(b.mastery) ||
          clampPercent(a.completionPercent) - clampPercent(b.completionPercent) ||
          compareTitles(a, b),
      )
    case 'mastery':
      return copy.sort(
        (a, b) =>
          masteryRank(b.mastery) - masteryRank(a.mastery) ||
          (bestQuizPercent(b) ?? -1) - (bestQuizPercent(a) ?? -1) ||
          compareTitles(a, b),
      )
    case 'title':
      return copy.sort(compareTitles)
    default:
      return copy
  }
}

function normalizeLessonTitle(value: string | null | undefined): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLocaleLowerCase()
}

/**
 * Weak areas are lesson titles; link each one to its lesson when the title matches a lesson in `lessons`
 * (case / whitespace insensitive). Blank and duplicate titles are dropped.
 */
export function resolveWeakAreas(
  weakAreas: readonly (string | null | undefined)[] | null | undefined,
  lessons: readonly Pick<StudentProgressLessonResponse, 'lessonId' | 'lessonTitle'>[],
): WeakAreaLink[] {
  const index = new Map<string, number>()
  for (const lesson of lessons) {
    const key = normalizeLessonTitle(lesson.lessonTitle)
    if (key && lesson.lessonId > 0 && !index.has(key)) index.set(key, lesson.lessonId)
  }

  const seen = new Set<string>()
  const result: WeakAreaLink[] = []
  for (const raw of weakAreas ?? []) {
    const title = (raw ?? '').trim()
    const key = normalizeLessonTitle(title)
    if (!key || seen.has(key)) continue
    seen.add(key)
    result.push({ title, lessonId: index.get(key) ?? null })
  }
  return result
}

/** Route to the lesson player for a lesson. */
export function lessonRoute(lessonId: number): { path: string; query: { lessonId: string } } {
  return { path: '/student/learn', query: { lessonId: String(lessonId) } }
}
