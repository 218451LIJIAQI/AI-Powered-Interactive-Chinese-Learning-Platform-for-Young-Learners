import { describe, expect, it } from 'vitest'

import { aiQuizReply, aiStoryReply } from '@/demo/data'

import {
  answersMatch,
  checkPracticeAnswer,
  correctOptionIndexes,
  letterKeyIndex,
  normalizeAnswer,
  preparePracticeQuiz,
  scorePracticeQuiz,
  storyTranslations,
  vocabularyMeaning,
} from './aiPracticeQuiz'

describe('local AI practice quiz checking (FR-AIGEN-03)', () => {
  it('normalises answers with NFKC, trimming, collapsed spaces and case folding', () => {
    expect(normalizeAnswer('  Ni   HAO ')).toBe('ni hao')
    expect(normalizeAnswer('ＡＢＣ')).toBe('abc')
    expect(answersMatch('多少钱？', '多少钱?')).toBe(true)
    expect(answersMatch('It is delicious', 'It is delicious.')).toBe(true)
    expect(answersMatch('我要一杯茶', '我要一杯茶。')).toBe(true)
    expect(answersMatch('茶', '水')).toBe(false)
    expect(answersMatch('', '')).toBe(false)
  })

  it('resolves single-letter answer keys to option indexes only when no option text matches', () => {
    expect(letterKeyIndex('B')).toBe(1)
    expect(letterKeyIndex('(c)')).toBe(2)
    expect(letterKeyIndex('D.')).toBe(3)
    expect(letterKeyIndex('E')).toBe(-1)
    expect(letterKeyIndex('Bus')).toBe(-1)
    expect(correctOptionIndexes(['Library', 'Canteen', 'Dormitory'], 'b')).toEqual([1])
    expect(correctOptionIndexes(['A', 'B'], 'B')).toEqual([1])
    expect(correctOptionIndexes(['Yes', 'No'], 'D')).toEqual([])
    expect(correctOptionIndexes(['True', 'False'], ' true ')).toEqual([0])
  })

  it('prepares every demo question and shows the option text for a letter key', () => {
    const questions = preparePracticeQuiz(aiQuizReply)
    expect(questions).toHaveLength(aiQuizReply.questions.length)
    expect(questions[1]).toMatchObject({
      correctAnswer: 'B',
      correctAnswerDisplay: 'Canteen',
      correctOptionIndexes: [1],
    })
    expect(questions[3].options).toEqual([])
    expect(preparePracticeQuiz({ ...aiQuizReply, questions: [{ questionText: ' ', options: ['x'] }] })).toEqual([])
    expect(preparePracticeQuiz(null)).toEqual([])
  })

  it('marks option, typed, skipped and key-less answers and totals the local score', () => {
    const questions = preparePracticeQuiz(aiQuizReply)
    expect(checkPracticeAnswer(questions[0], 0)).toBe('correct')
    expect(checkPracticeAnswer(questions[0], 2)).toBe('incorrect')
    expect(checkPracticeAnswer(questions[1], 1)).toBe('correct')
    expect(checkPracticeAnswer(questions[3], ' 茶 ')).toBe('correct')
    expect(checkPracticeAnswer(questions[3], '水')).toBe('incorrect')
    expect(checkPracticeAnswer(questions[3], '   ')).toBe('unanswered')
    expect(checkPracticeAnswer(questions[0], null)).toBe('unanswered')
    expect(checkPracticeAnswer({ ...questions[0], correctAnswer: '' }, 0)).toBe('noKey')

    const result = scorePracticeQuiz(questions, [0, 0, 0, '茶', null])
    expect(result.statuses).toEqual(['correct', 'incorrect', 'correct', 'correct', 'unanswered'])
    expect(result).toMatchObject({ score: 3, total: 5 })
  })

  it('picks vocabulary meanings and orders story translations by interface language', () => {
    const item = aiStoryReply.vocabularyList[0]
    expect(vocabularyMeaning(item, 'ms')).toBe('hujung minggu')
    expect(vocabularyMeaning(item, 'en')).toBe('weekend')
    expect(vocabularyMeaning(item, 'zh-CN')).toBe('weekend')
    expect(vocabularyMeaning({ chineseWord: '茶', englishMeaning: null, bahasaMelayuMeaning: 'teh' }, 'en')).toBe('teh')

    expect(storyTranslations(aiStoryReply, 'ms').map((item) => item.language)).toEqual(['ms', 'en'])
    expect(storyTranslations(aiStoryReply, 'en').map((item) => item.language)).toEqual(['en', 'ms'])
    expect(storyTranslations({ ...aiStoryReply, bahasaMelayuTranslation: ' ' }, 'ms')).toHaveLength(1)
  })
})
