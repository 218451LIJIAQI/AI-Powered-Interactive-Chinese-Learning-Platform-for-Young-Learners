import { describe, expect, it } from 'vitest'

import { teacherAiQuizDraft } from '@/test/aiDraftFixtures'

import { quizDraftToQuestionDrafts } from './aiDraft'

describe('teacher AI drafts (FRAI05)', () => {
  it('turns AI quiz questions into editable question-builder rows with the correct option marked', () => {
    const drafts = quizDraftToQuestionDrafts(teacherAiQuizDraft)

    expect(drafts).toHaveLength(3)
    expect(drafts[0]).toMatchObject({
      questionType: 'MULTIPLE_CHOICE',
      questionText: 'Which phrase means "I want one cup of tea"?',
      correctAnswerText: '我要一杯茶。',
    })
    expect(drafts[0].options.filter((option) => option.correctOption).map((option) => option.optionText)).toEqual([
      '我要一杯茶。',
    ])
    expect(drafts[2].questionType).toBe('TRUE_FALSE')
  })

  it('falls back to multiple choice and drops empty AI questions', () => {
    const [fillBlank, essay] = quizDraftToQuestionDrafts({
      ...teacherAiQuizDraft,
      questions: [
        { questionText: 'Fill in the blank', questionType: 'fill blank', options: [] },
        { questionText: 'Write an essay', questionType: 'essay', options: [] },
      ],
    })
    expect(fillBlank.questionType).toBe('FILL_BLANK')
    expect(essay.questionType).toBe('MULTIPLE_CHOICE')
    expect(
      quizDraftToQuestionDrafts({ ...teacherAiQuizDraft, questions: [{ questionText: '  ', options: [] }] }),
    ).toEqual([])
  })

  it('marks the correct option for letter answer keys', () => {
    const [draft] = quizDraftToQuestionDrafts({
      ...teacherAiQuizDraft,
      questions: [{ questionText: 'What does 食堂 mean?', options: ['Library', 'Canteen'], correctAnswer: 'B' }],
    })
    expect(draft.correctAnswerText).toBe('Canteen')
    expect(draft.options.map((option) => option.correctOption)).toEqual([false, true])
  })
})
