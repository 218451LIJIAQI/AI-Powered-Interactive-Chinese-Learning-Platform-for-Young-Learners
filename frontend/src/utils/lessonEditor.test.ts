import { describe, expect, it } from 'vitest'

import { adminAiQuizDraft } from '@/test/lessonEditorFixtures'
import type { EditorQuestion } from '@/types/adminClasses'

import { DEFAULT_QUESTION_POINTS, questionPayload, quizDraftToQuestionPayloads } from './lessonEditor'

describe('questionPayload', () => {
  it('sends a question back as it was, so Undo restores it unchanged, an unchecked AI draft as one', () => {
    const question: EditorQuestion = {
      id: 30,
      number: 5,
      type: 'LISTEN_CHOOSE',
      text: 'Listen: which word do you hear?',
      points: 3,
      correctAnswerText: null,
      explanation: '每天 (měitiān) means every day.',
      hint: 'Listen for the tone.',
      audioUrl: 'https://example.com/meitian.mp3',
      imageUrl: null,
      options: [
        { id: 1, text: '每天', correct: true, feedback: null },
        { id: 2, text: '明天', correct: false, feedback: '明天 (míngtiān) is tomorrow.' },
      ],
      aiDraft: true,
    }

    expect(questionPayload(question)).toEqual({
      questionType: 'LISTEN_CHOOSE',
      questionText: 'Listen: which word do you hear?',
      points: 3,
      correctAnswerText: null,
      explanation: '每天 (měitiān) means every day.',
      hint: 'Listen for the tone.',
      audioUrl: 'https://example.com/meitian.mp3',
      imageUrl: null,
      options: [
        { optionText: '每天', correctOption: true, feedback: null },
        { optionText: '明天', correctOption: false, feedback: '明天 (míngtiān) is tomorrow.' },
      ],
      aiDraft: true,
    })
  })
})

describe('quizDraftToQuestionPayloads (FRAI05)', () => {
  it('turns AI quiz questions into save-ready question payloads with the correct option marked', () => {
    const drafts = quizDraftToQuestionPayloads(adminAiQuizDraft)

    expect(drafts).toHaveLength(3)
    expect(drafts[0]).toMatchObject({
      questionType: 'MULTIPLE_CHOICE',
      questionText: 'Which phrase means "I want one cup of tea"?',
      points: DEFAULT_QUESTION_POINTS,
      correctAnswerText: '我要一杯茶。',
      aiDraft: true,
    })
    expect(drafts[0].options?.filter((option) => option.correctOption).map((option) => option.optionText)).toEqual([
      '我要一杯茶。',
    ])
    expect(drafts[2].questionType).toBe('TRUE_FALSE')
  })

  it('reads the type the AI wrote and falls back to multiple choice when options or media do not match', () => {
    const [fillBlank, essay, threeOptionTrueFalse, listenChoose, matchImage] = quizDraftToQuestionPayloads({
      ...adminAiQuizDraft,
      questions: [
        { questionText: 'Fill in the blank', questionType: 'fill blank', options: ['好', '坏'], correctAnswer: '好' },
        { questionText: 'Write an essay', questionType: 'essay', options: [], correctAnswer: '好' },
        {
          questionText: 'Is 茶 tea?',
          questionType: 'TRUE_FALSE',
          options: ['True', 'False', 'Maybe'],
          correctAnswer: 'True',
        },
        {
          questionText: 'Which word?',
          questionType: 'LISTEN_CHOOSE',
          options: ['茶', '水'],
          correctAnswer: '茶',
        },
        {
          questionText: 'Match picture',
          questionType: 'MATCH_WORD_IMAGE',
          options: ['茶', '水'],
          correctAnswer: '茶',
        },
      ],
    })
    expect(fillBlank.questionType).toBe('FILL_BLANK')
    expect(fillBlank.options).toEqual([])
    expect(essay.questionType).toBe('MULTIPLE_CHOICE')
    expect(threeOptionTrueFalse.questionType).toBe('MULTIPLE_CHOICE')
    expect(listenChoose.questionType).toBe('MULTIPLE_CHOICE')
    expect(matchImage.questionType).toBe('MULTIPLE_CHOICE')
  })

  it('marks the correct option for letter answer keys', () => {
    const [draft] = quizDraftToQuestionPayloads({
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
    expect(draft.options?.map((option) => option.correctOption)).toEqual([false, true])
  })

  it('marks only the literal answer when another option differs by Chinese punctuation', () => {
    const [draft] = quizDraftToQuestionPayloads({
      ...adminAiQuizDraft,
      questions: [{ ...adminAiQuizDraft.questions[0], options: ['茶。', '茶', '水'], correctAnswer: '茶' }],
    })
    expect(draft.options?.map((option) => option.correctOption)).toEqual([false, true, false])
  })
})
