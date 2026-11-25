import type { AdminClassPage, ClassDetailsForm, ClassDetailsPayload } from '@/types/adminClasses'
import { QUIZ_QUESTION_TYPES, type QuizQuestionType } from '@/types/api'
import type { ClassActivityType } from '@/types/classes'
import { pageFromQuery, pageQuery } from '@/utils/routeQuery'

/** Only the catalogue's filters follow a class link and its breadcrumb back. */
export function catalogueQuery(query: { theme?: unknown; page?: unknown }) {
  return {
    theme: typeof query.theme === 'string' && query.theme ? query.theme : undefined,
    page: pageQuery(pageFromQuery(query.page)),
  }
}

/** Activity types travel lower-case in URLs (`/activities/flashcards/12`). */
export function activitySlug(type: ClassActivityType): string {
  return type.toLowerCase()
}

export function classLessonRoute(classId: number, lessonId: number) {
  return { name: 'student-class-lesson', params: { classId, lessonId } }
}

export function classActivityRoute(
  classId: number,
  lessonId: number,
  activity: { type: ClassActivityType; id: number },
) {
  return {
    name: 'student-class-activity',
    params: { classId, lessonId, activityType: activitySlug(activity.type), activityId: activity.id },
  }
}

export function classDetailsForm(current?: AdminClassPage): ClassDetailsForm {
  return {
    title: current?.title ?? '',
    description: current?.description ?? '',
    categoryId: current?.category?.id ?? null,
    bestAfterUnit: current?.bestAfterUnit ?? null,
  }
}

export function classDetailsPayload({
  title,
  description,
  categoryId,
  bestAfterUnit,
}: ClassDetailsForm): ClassDetailsPayload {
  return { title: title.trim(), description: description.trim() || null, categoryId, bestAfterUnit }
}

export const ACTIVITY_ICONS: Record<ClassActivityType, string> = {
  FLASHCARDS: 'flash',
  TRACING: 'brush',
  MATCHING: 'puzzle',
}

export function normalizeQuestionType(type: string | null | undefined): QuizQuestionType {
  return QUIZ_QUESTION_TYPES.find((known) => known === type) ?? 'MULTIPLE_CHOICE'
}

/** The name of a question type; one this app does not know reads as multiple choice. */
export function questionTypeKey(type: string): string {
  return `classes.questionType.${normalizeQuestionType(type)}`
}

/** Typed answers (no options to pick from). */
export function isTypedQuestion(type: string): boolean {
  return type === 'FILL_BLANK' || type === 'REARRANGE_SENTENCE'
}

/**
 * The initials a class shows in place of a portrait: the first letters of its first two words, leaving out the small
 * words a title keeps lower-case ("Mandarin for Campus Life" is MC, "Shopping and Prices" SP). A title of one word
 * shows its first letter, or the first two characters of a word without letter case (汉字 for 汉字入门).
 */
export function classInitials(title: string): string {
  const words = title.trim().split(/\s+/).filter(Boolean)
  const named = words.filter((word) => !/^\p{Ll}/u.test(word))
  const picked = (named.length ? named : words).slice(0, 2)
  if (picked.length !== 1) {
    const letters = picked.map((word) => Array.from(word)[0])
    return letters.join('').toUpperCase()
  }
  const characters = Array.from(picked[0])
  const first = characters[0]
  return first.toLowerCase() !== first.toUpperCase() ? first.toUpperCase() : characters.slice(0, 2).join('')
}

/** True for a safe web link the browser may open (http or https with a host, no embedded credentials or control chars). */
export function isWebLink(value: string): boolean {
  if (!/^https?:\/\//i.test(value) || /\s/.test(value)) return false
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(value)) return false
  try {
    const url = new URL(value)
    return (
      (url.protocol === 'https:' || url.protocol === 'http:') && Boolean(url.hostname) && !url.username && !url.password
    )
  } catch {
    return false
  }
}
