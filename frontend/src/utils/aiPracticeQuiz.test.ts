import { describe, expect, it } from 'vitest'

import type { AiStoryGeneratorResponse } from '@/types/api'

import { draftAnswerIndex, matchesAnswer, storyVocabularyRows } from './aiPracticeQuiz'

const story: AiStoryGeneratorResponse = {
  title: '周末',
  lines: [],
  vocabularyList: [
    { chineseWord: '周末', pinyin: 'zhōumò', englishMeaning: 'weekend', bahasaMelayuMeaning: 'hujung minggu' },
  ],
  comprehensionQuestions: [],
  status: 'SUCCESS',
}

describe('AI practice answer matching', () => {
  it.each([
    ['  Ni   HAO ', 'ni hao', true],
    ['ＡＢＣ', 'abc', true],
    ['多少钱？', '多少钱?', true],
    ['It is delicious', 'It is delicious.', true],
    ['我要一杯茶', '我要一杯茶。', true],
    ['茶', '水', false],
    ['', '', false],
  ])('compares option %j against answer %j', (option, answer, expected) => {
    expect(matchesAnswer(answer)(option)).toBe(expected)
  })

  it('keeps punctuation-only options distinct', () => {
    expect(['？', '!!', '?'].filter(matchesAnswer('?'))).toEqual(['？', '?'])
  })

  it('resolves single-letter answer keys to option indexes only when no option text matches', () => {
    const options = ['Library', 'Canteen', 'Dormitory', 'Hall']
    expect(draftAnswerIndex(options, 'B')).toBe(1)
    expect(draftAnswerIndex(options, '(c)')).toBe(2)
    expect(draftAnswerIndex(options, 'D.')).toBe(3)
    expect(draftAnswerIndex(options, 'E')).toBe(-1)
    expect(draftAnswerIndex(options, 'Bus')).toBe(-1)
    expect(draftAnswerIndex(['Library', 'Canteen', 'Dormitory'], 'b')).toBe(1)
    expect(draftAnswerIndex(['B', 'A'], 'B')).toBe(0)
    expect(draftAnswerIndex(['Yes', 'No'], 'D')).toBe(-1)
    expect(draftAnswerIndex(['True', 'False'], ' true ')).toBe(0)
  })

  it('picks vocabulary meanings by interface language', () => {
    expect(storyVocabularyRows(story, 'ms')[0].meaning).toBe('hujung minggu')
    expect(storyVocabularyRows(story, 'en')[0].meaning).toBe('weekend')
    expect(storyVocabularyRows(story, 'zh-CN')[0].meaning).toBe('weekend')
    const teaOnly = {
      ...story,
      vocabularyList: [{ chineseWord: '茶', englishMeaning: null, bahasaMelayuMeaning: 'teh' }],
    }
    expect(storyVocabularyRows(teaOnly, 'en')[0].meaning).toBe('teh')
  })
})
