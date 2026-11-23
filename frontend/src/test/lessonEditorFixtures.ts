import type { EditorActivity, LessonEditor } from '@/types/adminClasses'

/** The activity in slot `number` of a lesson, without rows. */
export function editorActivity(type: EditorActivity['type'], number: number, title: string): EditorActivity {
  return { type, id: 40 + number, title, number, characters: [], pairs: [], cards: [] }
}

export function draftLesson(overrides: Partial<LessonEditor> = {}): LessonEditor {
  const activityTypes = new Set(overrides.activities?.map((activity) => activity.type))
  const activitiesComplete = activityTypes.size === 3
  return {
    lessonId: 21,
    classId: 5,
    classTitle: 'Hawker Food Mandarin',
    classStatus: 'DRAFT',
    unitId: 70,
    unitTitle: 'Unit 1: At the Hawker Stall',
    units: [{ id: 70, title: 'Unit 1: At the Hawker Stall' }],
    status: 'DRAFT',
    hasUnpublishedChanges: false,
    editable: true,
    title: 'At the Hawker Stall',
    minutes: null,
    summary: null,
    keyPhrases: [],
    video: null,
    activities: [],
    quizzes: [],
    checklist: {
      videoAdded: false,
      keyPhrasesAdded: false,
      activitiesComplete,
      quizzesReady: false,
      pinyinComplete: true,
    },
    ...overrides,
  }
}
