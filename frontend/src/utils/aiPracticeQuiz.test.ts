import { describe, expect, it } from 'vitest'

import type { AiStoryGeneratorResponse } from '@/types/api'

import { answersMatch, resolveAnswerKey, storyVocabularyRows } from './aiPracticeQuiz'

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
  it('normalises answers with NFKC, trimming, collapsed spaces and case folding', () => {
    expect(answersMatch('  Ni   HAO ', 'ni hao')).toBe(true)
    expect(answersMatch('ＡＢＣ', 'abc')).toBe(true)
    expect(answersMatch('多少钱？', '多少钱?')).toBe(true)
    expect(answersMatch('It is delicious', 'It is delicious.')).toBe(true)
    expect(answersMatch('我要一杯茶', '我要一杯茶。')).toBe(true)
    expect(answersMatch('茶', '水')).toBe(false)
    expect(answersMatch('', '')).toBe(false)
  })

  it('resolves single-letter answer keys to option indexes only when no option text matches', () => {
    const indexes = (options: readonly string[], answer: string) => resolveAnswerKey(options, answer).indexes
    const options = ['Library', 'Canteen', 'Dormitory', 'Hall']
    expect(indexes(options, 'B')).toEqual([1])
    expect(indexes(options, '(c)')).toEqual([2])
    expect(indexes(options, 'D.')).toEqual([3])
    expect(indexes(options, 'E')).toEqual([])
    expect(indexes(options, 'Bus')).toEqual([])
    expect(indexes(['Library', 'Canteen', 'Dormitory'], 'b')).toEqual([1])
    expect(indexes(['A', 'B'], 'B')).toEqual([1])
    expect(indexes(['Yes', 'No'], 'D')).toEqual([])
    expect(indexes(['True', 'False'], ' true ')).toEqual([0])
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
