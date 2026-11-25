import { describe, expect, it } from 'vitest'

import type { QuestionView } from '@/types/learn'

import { isListeningQuestion } from './listeningQuestion'

function question(overrides: Partial<QuestionView>): QuestionView {
  return {
    id: 1,
    answerKind: 'CHOICE',
    type: null,
    prompt: null,
    hanzi: null,
    pinyin: null,
    meaning: null,
    audioText: null,
    options: [],
    tiles: [],
    ...overrides,
  }
}

describe('isListeningQuestion', () => {
  it.each([
    ['a Learn listen-and-choose question', question({ type: 'LISTEN_CHOOSE', audioText: '一杯茶' }), true],
    ['a Learn listen-and-build question', question({ type: 'LISTEN_BUILD', answerKind: 'BUILD' }), true],
    ['a Practice listening item: no type, audio, no characters', question({ audioText: '一杯茶' }), true],
    ['a level check item: no type, no audio', question({ prompt: 'Which one is rice?' }), false],
    ['an item with no type that shows its characters', question({ audioText: '米饭', hanzi: '米饭' }), false],
    ['a Learn meaning question read aloud', question({ type: 'MEANING', audioText: '名字', hanzi: '名字' }), false],
  ])('%s', (_name, asked, listening) => {
    expect(isListeningQuestion(asked)).toBe(listening)
  })
})
