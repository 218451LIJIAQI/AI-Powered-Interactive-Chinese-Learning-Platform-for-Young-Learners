import type { EditorActivity, LessonEditor } from '@/types/adminClasses'
import type { AiQuizGeneratorResponse } from '@/types/api'

/** The activity in slot `number` of a lesson, without rows unless overridden. */
export function editorActivity(
  type: EditorActivity['type'],
  number: number,
  title: string,
  overrides: Partial<EditorActivity> = {},
): EditorActivity {
  return { type, id: 40 + number, title, number, characters: [], pairs: [], cards: [], ...overrides }
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

/** An AI quiz draft as the quiz-draft endpoint returns it (`/admin/ai/quiz-draft`), for the AI-draft tests. */
export const adminAiQuizDraft: AiQuizGeneratorResponse = {
  title: 'Ordering Food at a Mamak Stall',
  questions: [
    {
      questionText: 'Which phrase means "I want one cup of tea"?',
      questionType: 'MULTIPLE_CHOICE',
      options: ['我要一杯茶。', '我喝了很多茶。', '我不要咖啡。'],
      correctAnswer: '我要一杯茶。',
      explanation: '要 means "want" and 一杯 is "one cup".',
      pinyin: 'Wǒ yào yì bēi chá.',
      translation: 'I want one cup of tea.',
    },
    {
      questionText: 'How do you ask "How much is it?"',
      questionType: 'MULTIPLE_CHOICE',
      options: ['多少钱？', '几点了？', '你好吗？'],
      correctAnswer: '多少钱？',
      explanation: '多少钱 is the usual way to ask for a price.',
      pinyin: 'Duōshao qián?',
      translation: 'How much money?',
    },
    {
      questionText: '"谢谢" means "thank you".',
      questionType: 'TRUE_FALSE',
      options: ['True', 'False'],
      correctAnswer: 'True',
      explanation: '谢谢 (xièxie) is "thank you".',
      pinyin: 'xièxie',
      translation: 'thank you',
    },
  ],
  status: 'SUCCESS',
}

for (const question of adminAiQuizDraft.questions) {
  Object.freeze(question.options)
  Object.freeze(question)
}
Object.freeze(adminAiQuizDraft.questions)
Object.freeze(adminAiQuizDraft)
