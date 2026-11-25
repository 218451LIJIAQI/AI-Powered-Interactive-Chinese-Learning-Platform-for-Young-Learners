import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred, rateFlashcard } from '@/test/helpers'
import { flashcardSet, practiceItem, practiceResult } from '@/test/learnFixtures'
import type { PracticeSet } from '@/types/learn'

import { mountPlayer } from './practicePlayerTestHelpers'

const flashcardsSet: PracticeSet = {
  ...flashcardSet(),
  id: 16,
  items: [
    practiceItem('1', { hanzi: '你好', pinyin: 'nǐ hǎo', meaning: 'hello' }),
    practiceItem('2', { hanzi: '谢谢', meaning: 'thank you' }),
    practiceItem('3', { hanzi: '再见', meaning: 'goodbye' }),
  ],
}

const api = vi.hoisted(() => ({ complete: vi.fn() }))

vi.mock('@/api/learn', () => ({ practiceApi: api }))

import FlashcardsPlayer from './FlashcardsPlayer.vue'

describe('FlashcardsPlayer', () => {
  beforeEach(() => {
    api.complete.mockReset().mockResolvedValue(practiceResult())
  })

  it('shows the meaning from Show meaning or a click on the card, and rates only on the back', async () => {
    const wrapper = await mountPlayer(FlashcardsPlayer, flashcardsSet)
    const card = wrapper.get('[data-testid="flashcard"]')

    // Front: the word, [Previous] … [Show meaning] in the footer, and no rating yet.
    expect(card.get('.practice-card-label').text()).toBe('Word')
    expect(card.text()).toContain('你好')
    expect(card.text()).not.toContain('hello')
    const flip = wrapper.get('[data-testid="flip"]')
    expect(flip.element.tagName).toBe('BUTTON')
    expect(flip.text()).toBe('Show meaning')
    expect(flip.classes()).toContain('btn-primary')
    expect(flip.element.closest('.practice-footer')).not.toBeNull()
    expect(wrapper.find('[data-testid="rate-card"]').exists()).toBe(false)

    await flip.trigger('click')
    expect(card.text()).toContain('hello')
    expect(card.classes()).toContain('is-flipped')
    expect(wrapper.find('[data-testid="flip"]').exists()).toBe(false)
    expect(card.get('[data-testid="flip-back"]').text()).toBe('Flip back')
    // Back: [Still learning] [Got it] take the footer's place, and the focus goes to them.
    const rating = wrapper.get('[data-testid="rate-card"]')
    expect(rating.element.closest('.practice-footer')).not.toBeNull()
    expect(rating.attributes('role')).toBe('group')
    expect(rating.attributes('aria-label')).toBe('How well do you know this card?')
    expect(rating.findAll('button').map((button) => [button.text(), button.classes()])).toEqual([
      ['Still learning', ['btn', 'btn-secondary']],
      ['Got it', ['btn', 'btn-secondary']],
    ])
    expect(document.activeElement).toBe(wrapper.get('[data-testid="rate-learning"]').element)

    await card.get('[data-testid="flip-back"]').trigger('click')
    expect(card.text()).not.toContain('hello')
    expect(wrapper.find('[data-testid="rate-card"]').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('[data-testid="flip"]').element)

    // A click on the card itself turns it too; a click on a button inside it only does the button's own thing.
    await card.get('.practice-card-hanzi').trigger('click')
    expect(card.text()).toContain('hello')
    await card.trigger('click')
    expect(card.text()).not.toContain('hello')
    expect(wrapper.text()).toContain('Card 1 of 3')
  })

  it('moves on after each rating and finishes the set with the ratings once the last card is rated', async () => {
    const wrapper = await mountPlayer(FlashcardsPlayer, flashcardsSet)
    const counter = () => wrapper.get('[data-testid="practice-counter"]').text()
    expect(counter()).toBe('Card 1 of 3')

    await rateFlashcard(wrapper, 'learning')
    expect(counter()).toBe('Card 2 of 3')
    expect(wrapper.get('[data-testid="flashcard"]').text()).toContain('谢谢')
    // The rating buttons went with the back of the card: the keyboard goes on from the new card's Show meaning.
    expect(document.activeElement).toBe(wrapper.get('[data-testid="flip"]').element)
    expect(api.complete).not.toHaveBeenCalled()

    await rateFlashcard(wrapper, 'known')
    expect(wrapper.text()).toContain('Card 3 of 3')
    // A card still being learned does not come back in this run: it waits in Mistakes.
    await rateFlashcard(wrapper, 'known')
    expect(api.complete).toHaveBeenCalledExactlyOnceWith(
      'FLASHCARDS',
      16,
      [
        { itemKey: '1', answer: 'STILL_LEARNING' },
        { itemKey: '2', answer: 'GOT_IT' },
        { itemKey: '3', answer: 'GOT_IT' },
      ],
      undefined,
    )
    expect(wrapper.emitted('finished')).toHaveLength(1)
  })

  it('goes back a card, and keeps the rating buttons busy while the finished set is saved', async () => {
    const saving = deferred<ReturnType<typeof practiceResult>>()
    api.complete.mockReturnValue(saving.promise)
    const wrapper = await mountPlayer(FlashcardsPlayer, flashcardsSet)
    const previous = () => wrapper.get<HTMLButtonElement>('[data-testid="previous-card"]')
    expect(previous().attributes('aria-disabled')).toBe('true')

    await rateFlashcard(wrapper, 'learning')
    expect(previous().attributes('aria-disabled')).toBe('false')
    previous().element.focus()
    await previous().trigger('click')
    expect(wrapper.text()).toContain('Card 1 of 3')
    expect(wrapper.find('[data-testid="flip"]').exists()).toBe(true)
    // Back on the first card Previous is off, yet keeps the focus; another click does nothing.
    expect(previous().attributes('aria-disabled')).toBe('true')
    expect(document.activeElement).toBe(previous().element)
    await previous().trigger('click')
    expect(wrapper.text()).toContain('Card 1 of 3')

    await rateFlashcard(wrapper, 'known')
    await rateFlashcard(wrapper, 'known')
    await wrapper.get('[data-testid="flip"]').trigger('click')
    await wrapper.get('[data-testid="rate-learning"]').trigger('click')
    expect(wrapper.get('[data-testid="rate-learning"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="rate-learning"]').attributes('aria-busy')).toBe('true')
    await wrapper.get('[data-testid="flashcard"]').trigger('click')
    expect(wrapper.find('[data-testid="rate-card"]').exists()).toBe(true)
    expect(previous().element.disabled).toBe(true)

    saving.resolve(practiceResult())
    await flushPromises()
    expect(api.complete).toHaveBeenCalledWith(
      'FLASHCARDS',
      16,
      [
        { itemKey: '1', answer: 'GOT_IT' },
        { itemKey: '2', answer: 'GOT_IT' },
        { itemKey: '3', answer: 'STILL_LEARNING' },
      ],
      undefined,
    )
  })
})
