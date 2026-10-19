import { describe, expect, it } from 'vitest'

import { teacherAiQuizDraft, teacherAiStoryDraft } from '@/demo/data'

import { createEmptyActivityForm } from './activityForm'
import {
  applyStoryDraft,
  normalizeQuizQuestionType,
  quizDraftToQuestionDrafts,
  STORY_INSTRUCTIONS_MAX_LENGTH,
  storyVocabularyBlock,
  storyVocabularyLines,
} from './aiDraft'

describe('teacher AI drafts (D6)', () => {
  it('turns AI quiz questions into editable question-builder rows with the correct option marked', () => {
    const drafts = quizDraftToQuestionDrafts(teacherAiQuizDraft)

    expect(drafts).toHaveLength(3)
    expect(drafts[0]).toMatchObject({
      questionType: 'MULTIPLE_CHOICE',
      questionText: 'Which phrase means "I want one cup of tea"?',
      correctAnswerText: '我要一杯茶。',
      points: 1,
    })
    expect(drafts[0].options.filter((option) => option.correctOption).map((option) => option.optionText)).toEqual([
      '我要一杯茶。',
    ])
    expect(drafts[2].questionType).toBe('TRUE_FALSE')
  })

  it('falls back to multiple choice and drops empty AI questions', () => {
    expect(normalizeQuizQuestionType('fill blank')).toBe('FILL_BLANK')
    expect(normalizeQuizQuestionType('essay')).toBe('MULTIPLE_CHOICE')
    expect(
      quizDraftToQuestionDrafts({ ...teacherAiQuizDraft, questions: [{ questionText: '  ', options: [] }] }),
    ).toEqual([])
  })

  it('fills the story form in the interface language without touching unrelated fields', () => {
    const form = { ...createEmptyActivityForm('STORY_ACTIVITY'), topic: 'Food', learningStage: 'GUIDED' as const }
    const filled = applyStoryDraft(form, teacherAiStoryDraft, { language: 'ms', answerLabel: 'Answer:' })

    expect(filled.title).toBe('Lunch After Class')
    expect(filled.storyText).toContain('食堂')
    expect(filled.storyTranslation).toContain('kantin')
    expect(filled.topic).toBe('Food')
    expect(filled.learningStage).toBe('GUIDED')
    expect(filled.storyQuestions).toHaveLength(2)
    expect(filled.storyQuestions[0].optionsText).toBe('The canteen\nThe library\nThe train station')
    expect(filled.storyQuestions[0].explanation).toContain('Answer: The canteen')
  })

  it('carries the AI vocabulary list into empty story instructions in the interface language', () => {
    const form = createEmptyActivityForm('STORY_ACTIVITY')
    const malay = applyStoryDraft(form, teacherAiStoryDraft, { language: 'ms', answerLabel: 'Jawapan:' })
    expect(malay.instructions).toBe(
      'Kosa kata utama:\n食堂 (shítáng) - kantin\n面 (miàn) - mi\n一杯茶 (yì bēi chá) - secawan teh',
    )

    const english = applyStoryDraft(malay, teacherAiStoryDraft, { language: 'en', answerLabel: 'Answer:' })
    expect(english.instructions.split('\n')).toEqual([
      'Key vocabulary:',
      '食堂 (shítáng) - canteen',
      '面 (miàn) - noodles',
      '一杯茶 (yì bēi chá) - a cup of tea',
    ])
    expect(storyVocabularyLines(teacherAiStoryDraft, 'zh-CN')[0]).toBe('食堂 (shítáng) - canteen')
  })

  it('keeps instructions the teacher wrote and stays within the backend length limit', () => {
    const form = { ...createEmptyActivityForm('STORY_ACTIVITY'), instructions: 'Read aloud twice.' }
    expect(applyStoryDraft(form, teacherAiStoryDraft, { language: 'en', answerLabel: 'A:' }).instructions).toBe(
      'Read aloud twice.',
    )
    expect(
      applyStoryDraft(form, { ...teacherAiStoryDraft, vocabularyList: [] }, { language: 'en', answerLabel: 'A:' })
        .instructions,
    ).toBe('Read aloud twice.')

    const longList = Array.from({ length: 80 }, (_, index) => ({
      chineseWord: `词${index}`,
      pinyin: 'cí',
      englishMeaning: 'a fairly long English meaning for testing',
    }))
    const block = storyVocabularyBlock({ ...teacherAiStoryDraft, vocabularyList: longList }, 'en', 'Key vocabulary:')
    expect(block.length).toBeLessThanOrEqual(STORY_INSTRUCTIONS_MAX_LENGTH)
    expect(block.startsWith('Key vocabulary:\n词0 (cí)')).toBe(true)
  })

  it('marks the correct option for letter answer keys and loosely matching story answers', () => {
    const [draft] = quizDraftToQuestionDrafts({
      ...teacherAiQuizDraft,
      questions: [{ questionText: 'What does 食堂 mean?', options: ['Library', 'Canteen'], correctAnswer: 'B' }],
    })
    expect(draft.correctAnswerText).toBe('Canteen')
    expect(draft.options.map((option) => option.correctOption)).toEqual([false, true])

    const filled = applyStoryDraft(
      createEmptyActivityForm('STORY_ACTIVITY'),
      {
        ...teacherAiStoryDraft,
        comprehensionQuestions: [{ question: 'Where?', options: ['The canteen', 'Home'], answer: 'the canteen.' }],
      },
      { language: 'en', answerLabel: 'Answer:' },
    )
    expect(filled.storyQuestions[0].correctOptionIndex).toBe(0)
  })
})
