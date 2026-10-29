import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerCheck, flashcardSet, learnRouter, practiceResult } from '@/test/learnFixtures'
import type { PracticeItem, PracticeSet } from '@/types/learn'

const api = vi.hoisted(() => ({ check: vi.fn(), complete: vi.fn() }))

vi.mock('@/api/learn', () => ({ practiceApi: api }))

import MatchingPlayer from './MatchingPlayer.vue'
import StoryPlayer from './StoryPlayer.vue'

function item(key: string, overrides: Partial<PracticeItem> = {}): PracticeItem {
  return {
    key,
    prompt: null,
    hanzi: null,
    pinyin: null,
    meaning: null,
    audioText: null,
    example: null,
    exampleMeaning: null,
    options: [],
    tiles: [],
    ...overrides,
  }
}

const matching: PracticeSet = {
  ...flashcardSet(),
  id: 9,
  type: 'MATCHING',
  title: 'Family pairs',
  items: [item('1', { hanzi: '妈妈', pinyin: 'māma' }), item('2', { hanzi: '爸爸', pinyin: 'bàba' })],
  matchingChoices: ['dad', 'mum'],
}

const story: PracticeSet = {
  ...flashcardSet(),
  id: 12,
  type: 'STORY',
  title: 'At the canteen',
  items: [
    item('1', {
      prompt: 'What does Amira order?',
      options: [
        { text: 'Rice', pinyin: null },
        { text: 'Noodles', pinyin: null },
      ],
    }),
  ],
  storyLines: [{ hanzi: '我要米饭。', pinyin: 'Wǒ yào mǐfàn.', translation: 'I want rice.' }],
}

async function mountPlayer(component: typeof MatchingPlayer | typeof StoryPlayer, set: PracticeSet) {
  const router = await learnRouter('/student/practice')
  return mount(component, { props: { set }, global: { plugins: [router] } })
}

describe('Practice set players', () => {
  beforeEach(() => {
    api.complete.mockResolvedValue(practiceResult({ type: 'MATCHING', setId: 9 }))
  })

  it('Matching numbers each pair, checks every pair on the server and then finishes the set', async () => {
    api.check
      .mockResolvedValueOnce(answerCheck({ correct: true, correctAnswer: 'mum' }))
      .mockResolvedValueOnce(answerCheck({ correct: false, correctAnswer: 'dad' }))
    const wrapper = await mountPlayer(MatchingPlayer, matching)

    expect(wrapper.get('[data-testid="matching-check"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="word-1"]').trigger('click')
    await wrapper.get('[data-testid="meaning-mum"]').trigger('click')
    await wrapper.get('[data-testid="word-2"]').trigger('click')
    await wrapper.get('[data-testid="meaning-mum"]').trigger('click')
    // Pairing a meaning again moves it to the new word.
    expect(wrapper.text()).toContain('1 of 2 paired')
    await wrapper.get('[data-testid="word-1"]').trigger('click')
    await wrapper.get('[data-testid="meaning-dad"]').trigger('click')
    expect(wrapper.get('[data-testid="meaning-dad"]').attributes('aria-label')).toBe('dad, paired with 妈妈')

    await wrapper.get('[data-testid="matching-check"]').trigger('click')
    await flushPromises()
    expect(api.check).toHaveBeenCalledWith('MATCHING', 9, { itemKey: '1', answer: 'dad' })
    expect(api.check).toHaveBeenCalledWith('MATCHING', 9, { itemKey: '2', answer: 'mum' })
    expect(wrapper.text()).toContain('1 of 2 pairs right')
    expect(wrapper.get('[data-testid="word-1"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-testid="word-2"]').classes()).toContain('is-wrong')

    await wrapper.findAll('.practice-footer .btn-primary').at(-1)!.trigger('click')
    await flushPromises()
    expect(api.complete).toHaveBeenCalledWith(
      'MATCHING',
      9,
      [
        { itemKey: '1', answer: 'dad' },
        { itemKey: '2', answer: 'mum' },
      ],
      undefined,
    )
    expect(wrapper.emitted('finished')).toHaveLength(1)
  })

  it('Story shows pinyin and translation on request and checks the answers together', async () => {
    api.check.mockResolvedValue(answerCheck({ correct: true, correctAnswer: 'Rice', explanation: '米饭 is rice.' }))
    const wrapper = await mountPlayer(StoryPlayer, story)

    expect(wrapper.text()).toContain('我要米饭。')
    expect(wrapper.text()).not.toContain('I want rice.')
    const switches = wrapper.findAll('input[role="switch"]')
    await switches[1]!.setValue(true)
    expect(wrapper.text()).toContain('I want rice.')

    expect(wrapper.get('[data-testid="story-check"]').attributes('disabled')).toBeDefined()
    await wrapper.findAll('.practice-story-option')[0]!.trigger('click')
    await wrapper.get('[data-testid="story-check"]').trigger('click')
    await flushPromises()

    expect(api.check).toHaveBeenCalledWith('STORY', 12, { itemKey: '1', answer: 'Rice' })
    expect(wrapper.findAll('.practice-story-option')[0]!.classes()).toContain('is-correct')
    expect(wrapper.text()).toContain('米饭 is rice.')
  })
})
