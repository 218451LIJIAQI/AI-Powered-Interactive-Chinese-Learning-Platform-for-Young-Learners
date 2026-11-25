import { mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { flashcardSet, practiceResult, xpResult } from '@/test/learnFixtures'
import type { PracticeResult, PracticeType } from '@/types/learn'

import type { RunTally } from './practiceTypes'
import SetDoneCard from './SetDoneCard.vue'

function mountCard(result: PracticeResult, tally: RunTally | null, xpReward = 15) {
  return mount(SetDoneCard, {
    props: { result, set: flashcardSet({ xpReward }), recap: '', tally },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

/** The card's lines: the stage's counter, the title, the line under it and the XP. */
function lines(wrapper: ReturnType<typeof mountCard>): string[] {
  return wrapper.findAll('.practice-counter, .practice-done-title, .practice-done-sub, .tag').map((line) => line.text())
}

describe('SetDoneCard (a finished set)', () => {
  it.each<[PracticeType, number, string]>([
    ['LISTENING', 5, '5 of 5 correct'],
    ['DRAG_DROP', 4, '4 of 4 sentences correct'],
    ['TYPING', 6, '6 of 6 words correct'],
  ])('%s with nothing new on the Mistakes list counts the items answered and correct', (type, count, title) => {
    const result = practiceResult({ type, correctCount: count, itemCount: count, xp: xpResult({ xpEarned: 15 }) })
    const wrapper = mountCard(result, { answered: count, mistakes: 0 })

    expect(lines(wrapper)).toEqual([
      `${count} of ${count} answered`,
      title,
      'Nothing new on your Mistakes list.',
      '+15 XP',
    ])
    expect(wrapper.get('[data-testid="practise-again"]').text()).toBe('Practise again')
    expect(wrapper.get('[data-testid="next-set"]').text()).toBe('Next set: Food cards')
  })

  it('does not count an item skipped because its audio did not play, and keeps the XP for every item right', () => {
    const result = practiceResult({
      type: 'LISTENING',
      correctCount: 4,
      itemCount: 5,
      allCorrect: false,
      xp: xpResult({ xpEarned: 0 }),
    })
    const wrapper = mountCard(result, { answered: 4, mistakes: 0 })

    expect(lines(wrapper)).toEqual([
      '4 of 5 answered',
      '4 of 5 correct',
      'Nothing new on your Mistakes list.',
      'Get every item right for +15 XP.',
    ])
    expect(wrapper.get('.practice-done-stamp').classes()).toContain('is-open')
  })

  it('keeps the count of what went to Mistakes, once-more answers included', () => {
    const skipped = practiceResult({
      type: 'LISTENING',
      correctCount: 3,
      itemCount: 5,
      allCorrect: false,
      xp: xpResult({ xpEarned: 0 }),
    })
    expect(lines(mountCard(skipped, { answered: 4, mistakes: 2 }))).toEqual([
      '4 of 5 answered',
      '3 of 5 right',
      'Your 2 mistakes are saved for a quick review.',
      'Get every item right for +15 XP.',
    ])

    // Every first answer right, but a guess that came back once more was answered wrong.
    const again = practiceResult({ type: 'TYPING', correctCount: 6, itemCount: 6, xp: xpResult({ xpEarned: 15 }) })
    expect(lines(mountCard(again, { answered: 6, mistakes: 1 }))).toEqual([
      '6 of 6 answered',
      '6 of 6 words correct',
      'Your mistake is saved for a quick review.',
      '+15 XP',
    ])
  })

  it('says how a flashcard set was rated, and what was added to Mistakes', () => {
    const result = practiceResult({ correctCount: 5, itemCount: 6, allCorrect: false, xp: xpResult({ xpEarned: 10 }) })
    const wrapper = mountCard(result, null, 10)

    expect(lines(wrapper)).toEqual(['6 of 6 cards', 'Set reviewed', '5 got it · 1 added to Mistakes', '+10 XP'])
  })
})
