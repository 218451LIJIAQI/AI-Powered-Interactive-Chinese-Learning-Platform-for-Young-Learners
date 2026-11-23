import type { AdminClassPage, ClassDetailsForm, ClassDetailsPayload } from '@/types/adminClasses'
import { QUIZ_QUESTION_TYPES, type QuizQuestionType } from '@/types/api'
import type { ClassActivityType } from '@/types/classes'
import { pageFromQuery, pageQuery } from '@/utils/pageQuery'

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

export function isQuestionType(type: string): type is QuizQuestionType {
  return QUIZ_QUESTION_TYPES.some((known) => known === type)
}

/** The name of a question type; one this app does not know reads as multiple choice. */
export function questionTypeKey(type: string): string {
  return `classes.questionType.${isQuestionType(type) ? type : 'MULTIPLE_CHOICE'}`
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

/** True for a link the browser may open (http or https only). */
export function isWebLink(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:'
  } catch {
    return false
  }
}
