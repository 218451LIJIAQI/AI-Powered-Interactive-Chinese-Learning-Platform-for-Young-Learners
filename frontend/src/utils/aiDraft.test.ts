import { describe, expect, it } from 'vitest'

import { adminAiQuizDraft } from '@/test/aiDraftFixtures'

import { quizDraftToQuestionDrafts } from './aiDraft'

describe('AI quiz drafts (FRAI05)', () => {
  it('turns AI quiz questions into editable question-builder rows with the correct option marked', () => {
    const drafts = quizDraftToQuestionDrafts(adminAiQuizDraft)

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

  it('reads the type the AI wrote and falls back to multiple choice', () => {
    const [fillBlank, essay] = quizDraftToQuestionDrafts({
      ...adminAiQuizDraft,
      questions: [
        { questionText: 'Fill in the blank', questionType: 'fill blank', options: [], correctAnswer: '好' },
        { questionText: 'Write an essay', questionType: 'essay', options: [], correctAnswer: '好' },
      ],
    })
    expect(fillBlank.questionType).toBe('FILL_BLANK')
    expect(essay.questionType).toBe('MULTIPLE_CHOICE')
  })

  it('marks the correct option for letter answer keys', () => {
    const [draft] = quizDraftToQuestionDrafts({
      ...adminAiQuizDraft,
      questions: [
        {
          questionText: 'What does 食堂 mean?',
          questionType: 'MULTIPLE_CHOICE',
          options: ['Library', 'Canteen'],
          correctAnswer: 'B',
        },
      ],
    })
    expect(draft.correctAnswerText).toBe('Canteen')
    expect(draft.options.map((option) => option.correctOption)).toEqual([false, true])
  })

  it('marks only the literal answer when another option differs by Chinese punctuation', () => {
    const [draft] = quizDraftToQuestionDrafts({
      ...adminAiQuizDraft,
      questions: [{ ...adminAiQuizDraft.questions[0], options: ['茶。', '茶', '水'], correctAnswer: '茶' }],
    })
    expect(draft.options.map((option) => option.correctOption)).toEqual([false, true, false])
  })
})
