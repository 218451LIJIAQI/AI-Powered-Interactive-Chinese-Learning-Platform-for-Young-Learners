import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Component } from 'vue'

import { answerCheck, flashcardSet, learnRouter, practiceResult } from '@/test/learnFixtures'
import type { PracticeItem, PracticeSet } from '@/types/learn'

const api = vi.hoisted(() => ({ check: vi.fn(), complete: vi.fn() }))

vi.mock('@/api/learn', () => ({
  practiceApi: api,
  PRACTICE_TYPE_SLUGS: {
    FLASHCARDS: 'flashcards',
    QUIZZES: 'quizzes',
    LISTENING: 'listening',
    TRACING: 'tracing',
    DRAG_DROP: 'drag-drop',
    MATCHING: 'matching',
    STORY: 'story',
  },
}))

import ItemsPlayer from './ItemsPlayer.vue'
import MatchingPlayer from './MatchingPlayer.vue'
import TracingPlayer from './TracingPlayer.vue'

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

const sentences: PracticeSet = {
  ...flashcardSet(),
  id: 11,
  type: 'DRAG_DROP',
  title: 'Places on Campus',
  done: false,
  xpReward: 15,
  items: [item('1', { meaning: 'I go to the library.', tiles: ['图书馆', '我', '去'] })],
}

const listening: PracticeSet = {
  ...flashcardSet(),
  id: 12,
  type: 'LISTENING',
  title: 'At the Café',
  items: [
    item('0', {
      prompt: 'Which phrase did you hear?',
      audioText: '一杯茶',
      options: [
        { text: '一杯茶', pinyin: 'yì bēi chá' },
        { text: '一碗米饭', pinyin: 'yì wǎn mǐfàn' },
      ],
    }),
  ],
}

const tracing: PracticeSet = {
  ...flashcardSet(),
  id: 13,
  type: 'TRACING',
  title: 'Write 学',
  stage: 'GUIDED',
  items: [item('0', { hanzi: '学', pinyin: 'xué', meaning: 'to learn' })],
  strokeCount: 2,
}

async function mountPlayer(component: Component, set: PracticeSet) {
  const router = await learnRouter('/student/practice')
  return mount(component, { props: { set }, global: { plugins: [router] } })
}

describe('Practice set players', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('1 of 2 pairs right')
    expect(wrapper.get('[data-testid="word-1"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-testid="word-2"]').classes()).toContain('is-wrong')

    await wrapper.get('[data-testid="continue"]').trigger('click')
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
    expect(wrapper.emitted('finished')![0]![1]).toBe('妈妈 dad · 爸爸 mum')
  })

  it('Drag & Drop builds the sentence by click or by dragging, resets it, and names the XP the last right answer earns', async () => {
    api.check.mockResolvedValue(
      answerCheck({ correctAnswer: '我去图书馆。', pinyin: 'Wǒ qù túshūguǎn.', meaning: null }),
    )
    const wrapper = await mountPlayer(ItemsPlayer, sentences)
    const check = () => wrapper.get('[data-testid="check"]')

    expect(check().attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="tile-1"]').trigger('click')
    expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('我')
    await wrapper.get('[data-testid="reset"]').trigger('click')
    expect(wrapper.find('[data-testid="build-answer"] .learn-tile').exists()).toBe(false)

    // Dragging a tile to the sentence places it, dragging it back to the bank takes it out.
    expect(wrapper.get('[data-testid="tile-2"]').attributes('draggable')).toBe('true')
    await wrapper.get('[data-testid="tile-2"]').trigger('dragstart')
    await wrapper.get('[data-testid="build-answer"]').trigger('drop')
    expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('去')
    await wrapper.get('[data-testid="build-answer"] .learn-tile').trigger('dragstart')
    await wrapper.get('.learn-build-bank').trigger('drop')
    expect(wrapper.find('[data-testid="build-answer"] .learn-tile').exists()).toBe(false)

    for (const tile of ['tile-1', 'tile-2', 'tile-0']) await wrapper.get(`[data-testid="${tile}"]`).trigger('click')
    await check().trigger('click')
    await flushPromises()

    expect(api.check).toHaveBeenCalledWith('DRAG_DROP', 11, { itemKey: '1', answer: '我 去 图书馆' })
    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.text()).toContain('Correct!')
    expect(bar.text()).toContain('Wǒ qù túshūguǎn.')
    expect(bar.text()).toContain('+15 XP')
    expect(wrapper.find('[data-testid="reset"]').exists()).toBe(false)
  })

  it('Listening plays from the strip above the question and checks only on Check', async () => {
    api.check.mockResolvedValue(answerCheck({ correct: false, correctAnswer: '一杯茶', explanation: 'Chá is tea.' }))
    const wrapper = await mountPlayer(ItemsPlayer, listening)

    await wrapper.get('[data-testid="option-1"]').trigger('click')
    expect(api.check).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="skip"]').exists()).toBe(false)
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()

    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.classes()).toContain('is-wrong')
    expect(bar.text()).toContain('Correct answer:')
    expect(bar.text()).toContain('Chá is tea.')
    expect(bar.text()).not.toContain('XP')
  })

  it('Tracing asks for every stroke, with Undo and Clear, and sends the strokes with the finished set', async () => {
    api.complete.mockResolvedValue(practiceResult({ type: 'TRACING', setId: 13 }))
    const wrapper = await mountPlayer(TracingPlayer, tracing)
    const done = () => wrapper.get('[data-testid="tracing-done"]')
    const pad = wrapper.getComponent({ name: 'TracingPad' })

    expect(done().attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('0 of 2 strokes')
    expect(wrapper.get('button[aria-pressed]').text()).toBe('Hide hint')

    const stroke = [
      { x: 1, y: 1 },
      { x: 2, y: 2 },
    ]
    pad.vm.$emit('update:strokes', [stroke, stroke])
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('2 of 2 strokes')
    await done().trigger('click')
    await flushPromises()

    expect(api.complete).toHaveBeenCalledWith('TRACING', 13, [], 2)
    expect(wrapper.emitted('finished')![0]![1]).toBe('xué · to learn')
  })
})
