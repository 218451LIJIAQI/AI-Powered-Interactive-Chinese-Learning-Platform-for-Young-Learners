import { describe, expect, it } from 'vitest'

import type { AdminStoryActivityResponse } from '@/types/api'

import {
  activityFormFromDetail,
  buildActivityPayload,
  createEmptyActivityForm,
  isActivityType,
  LISTENING_PAYLOAD_EXAMPLE,
  listeningPayloadWarnings,
  optionsPayloadToText,
  optionsTextToPayload,
  storyCorrectOptionIndex,
  storyOptionList,
  validateActivityForm,
} from './activityForm'

const storyDetail: AdminStoryActivityResponse = {
  id: 9,
  title: 'Lunch After Class',
  instructions: 'Read and answer.',
  storyText: '下课了。我们去食堂。',
  pinyin: 'Xià kè le. Wǒmen qù shítáng.',
  translation: 'Class is over. We go to the canteen.',
  imageUrl: null,
  status: 'REJECTED',
  rejectionReason: 'Add one more question.',
  learningStage: 'SCAFFOLDED',
  hskLevel: 1,
  difficulty: 'EASY',
  topic: 'Food',
  tags: ['story', 'campus'],
  xpReward: 30,
  estimatedSeconds: 400,
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-02T00:00:00Z',
  questions: [
    {
      id: 1,
      questionText: 'Where do they go?',
      optionsPayload: '["The canteen","The library"]',
      sortOrder: 0,
      correctOptionIndex: 0,
    },
  ],
}

describe('shared practice-activity form (admin library and teacher activities)', () => {
  it('round-trips a saved story activity into the same request body, including the learning stage', () => {
    const form = activityFormFromDetail('STORY_ACTIVITY', storyDetail)

    expect(form.learningStage).toBe('SCAFFOLDED')
    expect(form.storyQuestions[0].optionsText).toBe('The canteen\nThe library')
    expect(form.storyQuestions[0].correctOptionIndex).toBe(0)
    expect(buildActivityPayload(form)).toEqual({
      title: 'Lunch After Class',
      instructions: 'Read and answer.',
      storyText: '下课了。我们去食堂。',
      pinyin: 'Xià kè le. Wǒmen qù shítáng.',
      translation: 'Class is over. We go to the canteen.',
      imageUrl: null,
      learningStage: 'SCAFFOLDED',
      hskLevel: 1,
      difficulty: 'EASY',
      topic: 'Food',
      tags: ['story', 'campus'],
      xpReward: 30,
      estimatedSeconds: 400,
      questions: [
        {
          questionText: 'Where do they go?',
          optionsPayload: '["The canteen","The library"]',
          explanation: null,
          sortOrder: 0,
          correctOptionIndex: 0,
        },
      ],
    })
  })

  it('stores the story correct option 0-based and drops an index that no longer matches an option', () => {
    const form = createEmptyActivityForm('STORY_ACTIVITY')
    expect(form.storyQuestions[0].correctOptionIndex).toBeNull()
    form.title = 'Story'
    form.storyText = '我坐轻快铁。'
    form.storyQuestions = [
      { questionText: 'How?', optionsText: 'By LRT\n\nBy bus', explanation: '', sortOrder: 0, correctOptionIndex: 1 },
      { questionText: 'Who?', optionsText: 'Ali\nMei', explanation: '', sortOrder: 1, correctOptionIndex: 5 },
      { questionText: 'When?', optionsText: 'Today\nTomorrow', explanation: '', sortOrder: 2 },
    ]

    expect(storyOptionList(form.storyQuestions[0].optionsText)).toEqual(['By LRT', 'By bus'])
    const payload = buildActivityPayload(form) as { questions: Array<{ correctOptionIndex: number | null }> }
    expect(payload.questions.map((question) => question.correctOptionIndex)).toEqual([1, null, null])
    expect(storyCorrectOptionIndex({ optionsText: 'A\nB', correctOptionIndex: -1 })).toBeNull()

    const loaded = activityFormFromDetail('STORY_ACTIVITY', {
      ...storyDetail,
      questions: [{ ...storyDetail.questions[0], correctOptionIndex: null }],
    })
    expect(loaded.storyQuestions[0].correctOptionIndex).toBeNull()
  })

  it('warns (without blocking) about listening payloads that do not follow the structured format', () => {
    expect(listeningPayloadWarnings('')).toEqual([])
    expect(listeningPayloadWarnings(LISTENING_PAYLOAD_EXAMPLE)).toEqual([])
    expect(listeningPayloadWarnings('{"prompt":"Listen"')).toEqual([{ key: 'listeningWarningInvalidJson' }])
    expect(listeningPayloadWarnings('{"prompt":"Listen and identify the word"}')).toEqual([
      { key: 'listeningWarningNoQuestions' },
    ])
    expect(listeningPayloadWarnings('{"questions":[{"question":"Which word?","options":["你好"]}]}')).toEqual([
      { key: 'listeningWarningNoQuestions' },
    ])
    expect(
      listeningPayloadWarnings(
        '{"questions":[{"question":"Type it","answer":"谢谢"},{"question":"Pick","options":["你好","再见"],"answer":"早上好"}]}',
      ),
    ).toEqual([{ key: 'listeningWarningAnswerNotOption', number: 2 }])

    const form = createEmptyActivityForm('LISTENING_EXERCISE')
    form.title = 'Listen for prices'
    form.questionPayload = 'not json'
    expect(validateActivityForm(form)).toBeNull()
  })

  it('sends an unset learning stage as null and drops incomplete matching pairs', () => {
    const form = createEmptyActivityForm('MATCHING_GAME')
    form.title = ' Campus places '
    form.matchingItems = [
      {
        leftText: '图书馆',
        rightText: 'library',
        leftImageUrl: '',
        rightImageUrl: '',
        pinyin: 'túshūguǎn',
        sortOrder: 0,
      },
      { leftText: '食堂', rightText: '', leftImageUrl: '', rightImageUrl: '', pinyin: '', sortOrder: 1 },
    ]

    const payload = buildActivityPayload(form) as { learningStage: unknown; title: string; items: unknown[] }
    expect(payload.learningStage).toBeNull()
    expect(payload.title).toBe('Campus places')
    expect(payload.items).toHaveLength(1)
  })

  it('validates the required fields per type', () => {
    expect(validateActivityForm(createEmptyActivityForm('FLASHCARD'))).toBe('flashcardFrontRequired')
    expect(validateActivityForm(createEmptyActivityForm('LISTENING_EXERCISE'))).toBe('titleRequired')
    const story = createEmptyActivityForm('STORY_ACTIVITY')
    story.title = 'Story'
    expect(validateActivityForm(story)).toBe('storyTextRequired')
    story.storyText = '你好。'
    expect(validateActivityForm(story)).toBe('storyQuestionRequired')
  })

  it('converts story options between text lines and the JSON payload', () => {
    expect(optionsTextToPayload(' A \n\nB')).toBe('["A","B"]')
    expect(optionsPayloadToText('Xiao Ming\nXiao Hong')).toBe('Xiao Ming\nXiao Hong')
    expect(isActivityType('TRACING_EXERCISE')).toBe(true)
    expect(isActivityType('QUIZ')).toBe(false)
  })
})
