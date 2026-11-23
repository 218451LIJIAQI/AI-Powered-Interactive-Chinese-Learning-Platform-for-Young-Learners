import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { vi } from 'vitest'
import type { Component } from 'vue'

import { flashcardSet, practiceItem } from '@/test/learnFixtures'
import type { HintStep, PracticeSet } from '@/types/learn'

export function hintStep(overrides: Partial<HintStep> = {}): HintStep {
  return {
    kind: 'RULE_OUT',
    words: ['Cup'],
    pinyin: [],
    text: null,
    count: null,
    ruledOut: ['Cup'],
    ...overrides,
  }
}

export const matchingSet: PracticeSet = {
  ...flashcardSet(),
  id: 9,
  type: 'MATCHING',
  items: [practiceItem('1', { hanzi: '妈妈', pinyin: 'māma' }), practiceItem('2', { hanzi: '爸爸', pinyin: 'bàba' })],
  matchingChoices: ['dad', 'mum'],
}

export const sentencesSet: PracticeSet = {
  ...flashcardSet(),
  id: 11,
  type: 'DRAG_DROP',
  done: false,
  xpReward: 15,
  items: [practiceItem('1', { meaning: 'I go to the library.', tiles: ['图书馆', '我', '去'] })],
}

export const listeningSet: PracticeSet = {
  ...flashcardSet(),
  id: 12,
  type: 'LISTENING',
  items: [
    practiceItem('0', {
      prompt: 'Which phrase did you hear?',
      audioText: '一杯茶',
      options: [
        { text: '一杯茶', pinyin: 'yì bēi chá' },
        { text: '一碗米饭', pinyin: 'yì wǎn mǐfàn' },
      ],
    }),
  ],
}

/** Two listening questions with four answers each (the audio is left out: the strip above them is not under test). */
export const menuSet: PracticeSet = {
  ...flashcardSet(),
  id: 14,
  type: 'LISTENING',
  done: false,
  xpReward: 15,
  items: ['1', '2'].map((key) =>
    practiceItem(key, {
      prompt: `Question ${key}`,
      options: [
        { text: 'Bill', pinyin: null },
        { text: 'Menu', pinyin: null },
        { text: 'Rice', pinyin: null },
        { text: 'Cup', pinyin: null },
      ],
    }),
  ),
}

export const tracingSet: PracticeSet = {
  ...flashcardSet(),
  id: 13,
  type: 'TRACING',
  stage: 'GUIDED',
  items: [practiceItem('0', { hanzi: '学', pinyin: 'xué', meaning: 'to learn' })],
  strokeCount: 2,
}

export const typingSet: PracticeSet = {
  ...flashcardSet(),
  id: 15,
  type: 'TYPING',
  items: [practiceItem('0', { meaning: 'Thank you' })],
}

export const flashcardsSet: PracticeSet = {
  ...flashcardSet(),
  id: 16,
  items: [
    practiceItem('1', { hanzi: '你好', pinyin: 'nǐ hǎo', meaning: 'hello' }),
    practiceItem('2', { hanzi: '谢谢', meaning: 'thank you' }),
    practiceItem('3', { hanzi: '再见', meaning: 'goodbye' }),
  ],
}

/** In the document (so the focus can be followed), with dialogs and toasts rendered in place. */
export async function mountPlayer(component: Component, set: PracticeSet): Promise<VueWrapper> {
  const wrapper = mount(component, { props: { set }, attachTo: document.body, global: { stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

export async function enterSpelling(wrapper: VueWrapper, text: string): Promise<void> {
  await wrapper.get('[data-testid="typing-input"]').setValue(text)
  vi.advanceTimersByTime(300)
  await flushPromises()
}
