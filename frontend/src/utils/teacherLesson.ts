import type { ClassActivityType, LessonEditor } from '@/types/classes'

/** Every lesson has exactly these three activities, in this order. */
export const LESSON_ACTIVITY_TYPES: readonly ClassActivityType[] = ['FLASHCARDS', 'TRACING', 'MATCHING']

/** Full message keys of the activity names in the `lessonEditor` bundle. */
export const LESSON_ACTIVITY_KEYS: Record<ClassActivityType, string> = {
  FLASHCARDS: 'lessonEditor.activityType.FLASHCARDS',
  TRACING: 'lessonEditor.activityType.TRACING',
  MATCHING: 'lessonEditor.activityType.MATCHING',
}

/** The quiz of a lesson: a lesson has one, the first if older data holds more. */
export function lessonQuiz(editor: LessonEditor): LessonEditor['quizzes'][number] | null {
  return editor.quizzes[0] ?? null
}

/** True when all three activities exist. */
export function hasAllActivities(editor: LessonEditor): boolean {
  return LESSON_ACTIVITY_TYPES.every((type) => editor.activities.some((activity) => activity.type === type))
}

/** "Unit 2" for a lesson's unit, counted from the class's units in order. */
export function unitNumber(editor: LessonEditor, unitId: number | null): number {
  return editor.units.findIndex((unit) => unit.id === unitId) + 1
}
