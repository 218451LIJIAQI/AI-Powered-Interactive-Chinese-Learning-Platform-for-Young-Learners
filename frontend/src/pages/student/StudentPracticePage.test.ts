import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RouterView } from 'vue-router'

import { buttonNamed, deferred, pageOf, rateFlashcard } from '@/test/helpers'
import { mountAtRoute, testRouter } from '@/test/routes'
import { answerCheck, flashcardSet, practiceItem, practiceResult, today, xpResult } from '@/test/learnFixtures'
import type { MistakeList, MistakeRow, PracticeSet, PracticeSetSummary } from '@/types/learn'

const api = vi.hoisted(() => ({
  sets: vi.fn(),
  set: vi.fn(),
  check: vi.fn(),
  complete: vi.fn(),
  today: vi.fn(),
  mistakes: vi.fn(),
}))

vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  practiceApi: { sets: api.sets, set: api.set, check: api.check, complete: api.complete },
  hintApi: { settings: async () => ({ mode: 'OFF', tier: 'HSK1', delaySeconds: 15, perSet: 0 }) },
  xpApi: { today: api.today },
  mistakesApi: { list: api.mistakes },
}))

import StudentPracticePage from './StudentPracticePage.vue'

function summary(id: number, title: string, done = false): PracticeSetSummary {
  return { id, title, hskLevel: 1, itemCount: 2, xpReward: 10, done, started: false }
}

/** Listening set 8, `count` questions on rice; `copy` names the copy of the set read, in each prompt. */
function foodListening(copy = 'First', count = 2): PracticeSet {
  return {
    ...flashcardSet(),
    id: 8,
    type: 'LISTENING',
    done: false,
    items: Array.from({ length: count }, (_, index) =>
      practiceItem(String(index + 1), {
        prompt: `${copy} question ${index + 1}`,
        audioText: '米饭',
        options: [
          { text: '米饭', pinyin: 'mǐfàn' },
          { text: '面条', pinyin: 'miàntiáo' },
        ],
      }),
    ),
  }
}

function mountAt(path: string) {
  return mountAtRoute(StudentPracticePage, path)
}

/** Turns every card of the open flashcard set and rates it, in order; the last rating finishes the set. */
async function rateCards(wrapper: VueWrapper, ...ratings: ('known' | 'learning')[]): Promise<void> {
  for (const rating of ratings) await rateFlashcard(wrapper, rating)
}

function oneCardSet(overrides: Partial<PracticeSet> = {}): PracticeSet {
  const set = flashcardSet(overrides)
  return { ...set, items: set.items.slice(0, 1) }
}

async function finishListeningItems(wrapper: VueWrapper, count: number, confidence: 'sure' | 'guess' = 'sure') {
  for (let item = 0; item < count; item++) {
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get(`[data-testid="answer-${confidence}"]`).trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
  }
}

describe('StudentPracticePage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.today.mockResolvedValue(today())
    api.mistakes.mockResolvedValue({ page: pageOf([]), all: 0, learn: 0, practice: 0 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('has no hub: the seven tabs, today’s goal and the first tab open straight away', async () => {
    api.sets.mockResolvedValue([summary(4, 'Numbers cards', true), summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    const { wrapper, router } = await mountAt('/student/practice')

    expect(wrapper.get('h1').text()).toBe('Practice')
    expect(wrapper.get('h1').classes()).toContain('sr-only')
    const tabs = wrapper.get('nav[aria-label="Practice activities"]').findAll('a')
    expect(tabs.map((tab) => tab.text())).toEqual([
      'Flashcards',
      'Listening',
      'Tracing',
      'Drag & Drop',
      'Matching',
      'Typing',
      'Mistakes6',
    ])
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/student/practice/flashcards')
    expect(wrapper.get('[data-testid="type-flashcards"]').attributes('aria-current')).toBe('page')
    expect(api.sets).toHaveBeenCalledWith('FLASHCARDS')

    // The strip says only "X / Y XP"; the streak is shown once, in the sidebar's status card.
    const goal = wrapper.get('[data-testid="daily-goal"]')
    expect(goal.text()).toContain('Daily goal')
    expect(goal.get('strong').text()).toBe('20 / 30 XP')
    expect(goal.text()).not.toContain('streak')
    expect(goal.text()).not.toContain('to go')
    expect(goal.find('.practice-goal-note').exists()).toBe(false)
  })

  it('says “Goal reached” on the strip once today’s goal is reached', async () => {
    api.today.mockResolvedValue(today({ todayXp: 30, goalReached: true }))
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    const { wrapper } = await mountAt('/student/practice/flashcards')

    const goal = wrapper.get('[data-testid="daily-goal"]')
    expect(goal.classes()).toContain('is-reached')
    expect(goal.get('strong').text()).toBe('30 / 30 XP')
    expect(goal.get('.practice-goal-note').text()).toBe('Goal reached')
  })

  it('opens the first set not done yet; rating the last card finishes it with every rating', async () => {
    api.sets.mockResolvedValue([summary(4, 'Numbers cards', true), summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    api.complete.mockResolvedValue(practiceResult({ correctCount: 1 }))
    const { wrapper, router } = await mountAt('/student/practice/flashcards')

    expect(api.set).toHaveBeenCalledWith('FLASHCARDS', 5)
    expect(wrapper.get('[data-testid="set-5"]').attributes('aria-current')).toBe('true')
    expect(wrapper.get('[data-testid="set-4"]').text()).toContain('Completed')
    expect(wrapper.get('[data-testid="set-5"]').text()).toContain('2 cards · +10 XP')
    expect(wrapper.get('[data-testid="set-5"]').attributes('href')).toBe('/student/practice/flashcards?set=5')
    const list = wrapper.get('nav[aria-label="Practice sets"]')
    expect(list.text()).toContain('HSK 1 (Basic)')
    expect(list.text()).toContain('2 sets')
    // The open set is named for assistive technology only: the list already shows it.
    expect(wrapper.get('.practice-stage h2').text()).toBe('Greetings cards · Flashcards · 2 cards · HSK 1 · +10 XP')
    expect(wrapper.get('.practice-stage h2').classes()).toContain('sr-only')

    // The front: [Previous] … [Show meaning]; nothing to mark as reviewed.
    expect(wrapper.text()).toContain('Card 1 of 2')
    expect(wrapper.get('[data-testid="previous-card"]').attributes('aria-disabled')).toBe('true')
    expect(wrapper.get('[data-testid="flip"]').text()).toBe('Show meaning')
    expect(wrapper.find('[data-testid="mark-reviewed"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="rate-card"]').exists()).toBe(false)
    // The back: the meaning, then [Still learning] [Got it], which move on.
    await wrapper.get('[data-testid="flip"]').trigger('click')
    expect(wrapper.text()).toContain('hello')
    expect(wrapper.find('[data-testid="flip"]').exists()).toBe(false)
    await wrapper.get('[data-testid="rate-learning"]').trigger('click')
    expect(wrapper.text()).toContain('Card 2 of 2')
    expect(api.complete).not.toHaveBeenCalled()

    api.sets.mockResolvedValue([summary(4, 'Numbers cards', true), summary(5, 'Greetings cards', true)])
    await rateCards(wrapper, 'known')
    expect(api.complete).toHaveBeenCalledWith(
      'FLASHCARDS',
      5,
      [
        { itemKey: '1', answer: 'STILL_LEARNING' },
        { itemKey: '2', answer: 'GOT_IT' },
      ],
      undefined,
    )
    expect(router.currentRoute.value.query.set).toBe('5')
    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.text()).toContain('Set reviewed')
    expect(done.text()).toContain('1 got it · 1 added to Mistakes')
    expect(done.text()).toContain('+15 XP')
    expect(done.text()).toContain('2 of 2 cards')
    expect(wrapper.get('[data-testid="next-set"]').text()).toContain('Next set: Food cards')
  })

  it('goes back a card with Previous, and a new rating replaces the old one', async () => {
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    api.complete.mockResolvedValue(practiceResult())
    const { wrapper } = await mountAt('/student/practice/flashcards')

    await rateCards(wrapper, 'learning')
    await wrapper.get('[data-testid="previous-card"]').trigger('click')
    expect(wrapper.text()).toContain('Card 1 of 2')
    expect(wrapper.find('[data-testid="flip"]').exists()).toBe(true)
    await rateCards(wrapper, 'known', 'known')

    expect(api.complete).toHaveBeenCalledWith(
      'FLASHCARDS',
      5,
      [
        { itemKey: '1', answer: 'GOT_IT' },
        { itemKey: '2', answer: 'GOT_IT' },
      ],
      undefined,
    )
    expect(wrapper.get('[data-testid="set-done"]').text()).toContain('2 got it')
  })

  it('opens the rank-up dialog a moment after a set that passed a rank, over its result', async () => {
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(oneCardSet())
    api.complete.mockResolvedValue(
      practiceResult({ xp: xpResult({ currentStreak: 8, rankUp: { rank: 4, rankXp: 300, nextRankXp: 400 } }) }),
    )
    const { wrapper } = await mountAt('/student/practice/flashcards')
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })

    await rateCards(wrapper, 'known')
    expect(wrapper.find('[data-testid="set-done"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rank-up"]').exists()).toBe(false)
    vi.advanceTimersByTime(600)
    await flushPromises()

    const dialog = wrapper.get('[data-testid="rank-up"]')
    expect(dialog.text()).toContain('You reached Rank 4 · ')
    expect(dialog.text()).toContain(
      'You reached 300 XP with today’s practice. Keep the 8-day streak going — Rank 5 opens at 400 XP.',
    )
    await wrapper.get('[data-testid="rank-up-keep-going"]').trigger('click')
    expect(wrapper.find('[data-testid="rank-up"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="set-done"]').exists()).toBe(true)
  })

  it('checks each listening answer once the learner says how sure they are', async () => {
    api.sets.mockResolvedValue([summary(8, 'At the Café')])
    api.set.mockResolvedValue(foodListening())
    api.check.mockResolvedValue(answerCheck({ correctAnswer: '米饭', pinyin: 'mǐfàn', meaning: 'rice' }))
    const { wrapper } = await mountAt('/student/practice/listening')

    expect(wrapper.text()).toContain('Listening · 2 questions')
    expect(wrapper.text()).toContain('Question 1 of 2')
    expect(wrapper.find('[data-testid="in-a-row"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('How sure are you?')

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await flushPromises()
    expect(api.check).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="answer-sure"]').trigger('click')
    await flushPromises()

    expect(api.check).toHaveBeenCalledWith('LISTENING', 8, { itemKey: '1', answer: '米饭', confidence: 'SURE' })
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Correct!')
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('mǐfàn')
    expect(wrapper.find('[data-testid="skip"]').exists()).toBe(false)
  })

  it('practises a set again only once it is read again, as a new run on the fresh copy', async () => {
    api.sets.mockResolvedValue([summary(8, 'At the Café')])
    api.set.mockResolvedValue(foodListening())
    api.check.mockResolvedValue(answerCheck({ correctAnswer: '米饭', pinyin: 'mǐfàn', meaning: 'rice' }))
    api.complete.mockResolvedValue(practiceResult({ type: 'LISTENING' }))
    const { wrapper } = await mountAt('/student/practice/listening')
    await finishListeningItems(wrapper, 2)
    expect(api.complete).toHaveBeenCalledTimes(1)

    // Until the set is read again the result stays, its button busy: no run starts on the copy read before.
    const reread = deferred<PracticeSet>()
    api.set.mockReturnValue(reread.promise)
    await wrapper.get('[data-testid="practise-again"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="listening-player"]').exists()).toBe(false)
    const again = wrapper.get('[data-testid="practise-again"]')
    expect(again.attributes('disabled')).toBeDefined()
    expect(again.attributes('aria-busy')).toBe('true')
    await again.trigger('click')
    expect(api.set).toHaveBeenCalledTimes(2)

    reread.resolve(foodListening('Fresh'))
    await flushPromises()
    expect(wrapper.find('[data-testid="set-done"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Question 1 of 2')
    expect(wrapper.text()).toContain('Fresh question 1')

    await finishListeningItems(wrapper, 2)
    expect(api.complete).toHaveBeenLastCalledWith(
      'LISTENING',
      8,
      [
        { itemKey: '1', answer: '米饭' },
        { itemKey: '2', answer: '米饭' },
      ],
      undefined,
    )
  })

  it('starts a fresh run when returning to a set whose updated questions are still loading', async () => {
    api.sets.mockResolvedValue([summary(8, 'At the Café'), summary(9, 'Numbers You Hear')])
    api.set.mockResolvedValue(foodListening())
    api.check.mockResolvedValue(answerCheck({ correctAnswer: '米饭' }))
    const { wrapper, router } = await mountAt('/student/practice/listening')

    // Neither set can be answered on an older copy while its new questions are still on the way.
    const reread = deferred<PracticeSet>()
    api.set.mockImplementation((_type, id: number) => (id === 8 ? reread.promise : new Promise(() => {})))
    await router.push({ query: { set: '9' } })
    await flushPromises()
    await router.push({ query: { set: '8' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="option-0"]').exists()).toBe(false)
    expect(api.check).not.toHaveBeenCalled()

    reread.resolve(foodListening('Fresh'))
    await flushPromises()
    expect(wrapper.text()).toContain('Question 1 of 2')
    expect(wrapper.text()).toContain('Fresh question 1')
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="answer-sure"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="continue"]').trigger('click')
    expect(wrapper.text()).toContain('Fresh question 2')
  })

  it('tries Practise again once more from Try again when the set could not be read again', async () => {
    const set = oneCardSet()
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(set)
    api.complete.mockResolvedValue(practiceResult())
    const { wrapper } = await mountAt('/student/practice/flashcards')
    await rateCards(wrapper, 'known')

    api.set.mockRejectedValue(new Error('offline'))
    await wrapper.get('[data-testid="practise-again"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('The set could not be opened')
    expect(wrapper.find('[data-testid="flashcards-player"]').exists()).toBe(false)

    const reread = deferred<PracticeSet>()
    api.set.mockReturnValue(reread.promise)
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="practise-again"]').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('[data-testid="flashcards-player"]').exists()).toBe(false)

    reread.resolve({ ...set })
    await flushPromises()
    expect(wrapper.find('[data-testid="flashcards-player"]').exists()).toBe(true)
    expect(api.set).toHaveBeenCalledTimes(3)
  })

  it('lists the waiting mistakes, flashcards still being learned among them, filtered by where they came from', async () => {
    const answered: MistakeRow = {
      id: 3,
      source: 'LEARN',
      type: 'MEANING',
      prompt: null,
      hanzi: '谢谢',
      meaning: 'thank you',
      yourAnswer: 'sorry',
      correctAnswer: 'thank you',
      sureWrong: false,
      stillLearning: false,
    }
    const card: MistakeRow = {
      ...answered,
      id: 4,
      source: 'PRACTICE',
      hanzi: '你好',
      meaning: 'Hello',
      yourAnswer: null,
      correctAnswer: 'Hello',
      sureWrong: false,
      stillLearning: true,
    }
    api.mistakes.mockResolvedValue({
      page: pageOf([answered, card], 0, 8, 6),
      all: 8,
      learn: 4,
      practice: 4,
    })
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(api.sets).not.toHaveBeenCalled()
    expect(api.mistakes).toHaveBeenCalledWith(null, 6)
    // The chips carry the counts; the stage's heading is for assistive technology.
    expect(wrapper.get('.practice-stage h2').text()).toBe('8 mistakes to fix')
    expect(wrapper.get('.practice-stage h2').classes()).toContain('sr-only')
    const rows = wrapper.findAll('[data-testid="mistake-row"]')
    expect(rows[0]!.text()).toContain('What does 谢谢 mean?')
    expect(rows[0]!.text()).toContain('You answered sorry · correct: thank you')
    expect(rows[0]!.get('.tag').text()).toBe('Learn')
    expect(rows[1]!.text()).toContain('What does 你好 mean?')
    expect(rows[1]!.text()).toContain('Flashcards · marked “Still learning” · 你好 = Hello')
    expect(rows[1]!.get('.tag').text()).toBe('Practice')
    expect(wrapper.get('.practice-mistakes-more').text()).toBe('+6 more')
    // The footer says what the review takes: all 8, about half a minute each.
    expect(wrapper.get('.practice-mistakes-call').text()).toBe('Review all 8 · about 4 min · +10 XP')
    expect(wrapper.get('[data-testid="start-review"]').text()).toBe('Start review')
    expect(wrapper.get('[data-testid="start-review"]').attributes('href')).toBe('/student/practice/review')
    expect(wrapper.get('[data-testid="filter-learn"]').text().replace(/\s+/g, ' ')).toBe('Learn · 4')

    const filtered = deferred<MistakeList>()
    api.mistakes.mockReturnValueOnce(filtered.promise)
    await wrapper.get('[data-testid="filter-practice"]').trigger('click')
    expect(api.mistakes).toHaveBeenLastCalledWith('PRACTICE', 6)
    expect(wrapper.get('[data-testid="filter-practice"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('.practice-stage [role="status"]').text()).toContain('Loading your mistakes')
    expect(wrapper.find('[data-testid="mistake-row"]').exists()).toBe(false)
    expect(wrapper.find('.practice-mistakes-more').exists()).toBe(false)

    filtered.resolve({ page: pageOf([card], 0, 4, 6), all: 8, learn: 4, practice: 4 })
    await flushPromises()
    expect(wrapper.findAll('[data-testid="mistake-row"]').map((row) => row.get('.tag').text())).toEqual(['Practice'])
    expect(wrapper.get('.practice-mistakes-more').text()).toBe('+3 more')
  })

  it('does not promise the whole list when it is longer than one review', async () => {
    api.mistakes.mockResolvedValue({
      page: pageOf([], 0, 25, 6),
      all: 25,
      learn: 25,
      practice: 0,
    })
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(wrapper.get('.practice-mistakes-more').text()).toBe('+25 more')
    // One review takes 20 of them.
    expect(wrapper.get('.practice-mistakes-call').text()).toBe('Review 20 of 25 · about 10 min · +10 XP')
  })

  it('names a single waiting mistake as one', async () => {
    api.mistakes.mockResolvedValue({ page: pageOf([], 0, 1, 6), all: 1, learn: 1, practice: 0 })
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(wrapper.get('.practice-mistakes-call').text()).toBe('Review 1 mistake · about 1 min · +10 XP')
  })

  it('says so when no mistake is waiting, with one next step', async () => {
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(wrapper.text()).toContain('No mistakes yet')
    expect(wrapper.text()).toContain('Anything you get wrong shows up here.')
    expect(wrapper.find('[data-testid="start-review"]').exists()).toBe(false)
    expect(wrapper.get('.practice-footer a').text()).toBe('Start with flashcards')
    expect(wrapper.get('.practice-footer a').attributes('href')).toBe('/student/practice/flashcards')
  })

  it('offers the next activity on the done card once a type has no set left', async () => {
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(oneCardSet())
    api.complete.mockResolvedValue(practiceResult({ nextSet: null }))
    const { wrapper } = await mountAt('/student/practice/flashcards')

    await rateCards(wrapper, 'known')
    expect(wrapper.find('[data-testid="next-set"]').exists()).toBe(false)
    const next = wrapper.get('[data-testid="next-type"]')
    expect(next.text()).toContain('Try Listening')
    expect(next.attributes('href')).toBe('/student/practice/listening')
  })

  it('says a set that went to Mistakes was not all right, and why it earned no XP', async () => {
    api.sets.mockResolvedValue([summary(8, 'At the Café')])
    api.set.mockResolvedValue(foodListening())
    api.check
      .mockResolvedValueOnce(answerCheck({ correct: false, correctAnswer: '面条' }))
      .mockResolvedValueOnce(answerCheck({ correctAnswer: '米饭' }))
    api.complete.mockResolvedValue(
      practiceResult({
        type: 'LISTENING',
        correctCount: 1,
        itemCount: 2,
        allCorrect: false,
        xp: xpResult({ xpEarned: 0 }),
      }),
    )
    const { wrapper } = await mountAt('/student/practice/listening')
    await finishListeningItems(wrapper, 2)

    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.get('[data-testid="practice-counter"]').text()).toBe('2 of 2 answered')
    expect(done.get('.practice-done-title').text()).toBe('1 of 2 right')
    expect(done.text()).toContain('Your mistake is saved for a quick review.')
    expect(done.text()).toContain('Get every item right for +10 XP.')
  })

  it('says nothing new went to Mistakes after a set answered all right', async () => {
    api.sets.mockResolvedValue([summary(8, 'At the Café')])
    api.set.mockResolvedValue(foodListening())
    api.check.mockResolvedValue(answerCheck({ correctAnswer: '米饭' }))
    api.complete.mockResolvedValue(
      practiceResult({ type: 'LISTENING', xp: xpResult({ xpEarned: 15, bonusXp: 0 }), nextSet: null }),
    )
    const { wrapper } = await mountAt('/student/practice/listening')
    await finishListeningItems(wrapper, 2)

    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.get('[data-testid="practice-counter"]').text()).toBe('2 of 2 answered')
    expect(done.get('.practice-done-title').text()).toBe('2 of 2 correct')
    expect(done.get('.practice-done-sub').text()).toBe('Nothing new on your Mistakes list.')
    expect(done.get('.tag').text()).toBe('+15 XP')
    expect(wrapper.get('[data-testid="practise-again"]').text()).toBe('Practise again')
  })

  it('does not promise XP again for a set done before that was not all right this time', async () => {
    api.sets.mockResolvedValue([summary(8, 'At the Café', true)])
    api.set.mockResolvedValue({ ...foodListening('First', 1), done: true })
    api.check.mockResolvedValue(answerCheck({ correct: false, correctAnswer: '面条' }))
    api.complete.mockResolvedValue(
      practiceResult({
        type: 'LISTENING',
        correctCount: 0,
        itemCount: 1,
        allCorrect: false,
        xp: xpResult({ xpEarned: 0 }),
      }),
    )
    const { wrapper } = await mountAt('/student/practice/listening')
    await finishListeningItems(wrapper, 1, 'guess')

    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.text()).toContain('0 of 1 right')
    // The set's XP was paid the first time every item was right; a later run cannot earn it.
    expect(done.text()).toContain('You earned this set’s XP before.')
    expect(done.text()).not.toContain('Get every item right')
  })

  it('keeps the result on screen when the list cannot be read again after the set', async () => {
    api.sets.mockResolvedValueOnce([summary(5, 'Greetings cards')]).mockRejectedValue(new Error('offline'))
    api.set.mockResolvedValue(oneCardSet())
    api.complete.mockResolvedValue(practiceResult())
    const { wrapper } = await mountAt('/student/practice/flashcards')
    await rateCards(wrapper, 'known')

    expect(api.sets).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('The sets could not be loaded')
    expect(wrapper.get('[data-testid="set-done"]').text()).toContain('Set reviewed')
    expect(wrapper.get('[data-testid="set-5"]').text()).toContain('Greetings cards')
  })

  it('opens matching groups for a search and lets the learner fold them without changing the unfiltered list', async () => {
    api.sets.mockResolvedValue([summary(4, 'Numbers cards'), { ...summary(5, 'Greetings cards'), hskLevel: 2 }])
    api.set.mockResolvedValue(flashcardSet())
    const { wrapper } = await mountAt('/student/practice/flashcards')
    const search = wrapper.get('input[type="search"]')
    const toggles = () => wrapper.findAll('.practice-set-group-toggle')

    expect(toggles().map((button) => button.attributes('aria-expanded'))).toEqual(['true', 'false'])
    await search.setValue('cards')
    expect(toggles().map((button) => button.attributes('aria-expanded'))).toEqual(['true', 'true'])
    await toggles()[0]!.trigger('click')
    expect(toggles()[0]!.attributes('aria-expanded')).toBe('false')
    await search.setValue('')
    expect(toggles().map((button) => button.attributes('aria-expanded'))).toEqual(['true', 'false'])
  })

  it('says why the search found no set and offers to clear it', async () => {
    api.sets.mockResolvedValue([summary(4, 'Numbers cards'), summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    const { wrapper } = await mountAt('/student/practice/flashcards')

    await wrapper.get('input[type="search"]').setValue('pizza')
    const empty = wrapper.get('[data-testid="sets-no-match"]')
    expect(empty.text()).toContain('No sets match “pizza”')
    expect(empty.text()).toContain('Try another topic, or clear the search.')
    expect(empty.get('button').classes()).toContain('btn-secondary')

    await empty.get('button').trigger('click')
    expect(wrapper.find('[data-testid="sets-no-match"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="set-4"]').exists()).toBe(true)
  })

  it('does not show the previous result on a new set when its pinning navigation is cancelled', async () => {
    api.sets.mockResolvedValue([summary(5, 'Greetings cards'), summary(6, 'Food cards')])
    api.set.mockImplementation((_type, id: number) => Promise.resolve(oneCardSet({ id })))
    api.complete.mockResolvedValue(practiceResult())
    const { wrapper, router } = await mountAt('/student/practice/flashcards')
    let release!: () => void
    const stop = router.beforeEach((to) => {
      if (to.query.set === '5') return new Promise<void>((done) => (release = done))
    })
    await rateCards(wrapper, 'known')
    expect(release).toBeTypeOf('function')

    await router.push({ query: { set: '6' } })
    await flushPromises()
    expect(wrapper.get('.practice-stage h2').text()).toContain('Food cards')
    release()
    await flushPromises()

    expect(router.currentRoute.value.query.set).toBe('6')
    expect(wrapper.find('[data-testid="set-done"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="flashcards-player"]').exists()).toBe(true)
    expect(api.sets).toHaveBeenCalledOnce()
    stop()
    wrapper.unmount()
  })

  it('does not reload an abandoned activity when pinning redirects to another page', async () => {
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(oneCardSet())
    api.complete.mockResolvedValue(practiceResult())
    const router = await testRouter('/student/progress')
    router.addRoute({ path: '/student/practice/:type?', name: 'student-practice', component: StudentPracticePage })
    await router.push('/student/practice/flashcards')
    const wrapper = mount(RouterView, { global: { plugins: [router] } })
    await flushPromises()
    const stop = router.beforeEach((to) => {
      if (to.query.set === '5') return { name: 'student-progress' }
    })
    await wrapper.get('[data-testid="flip"]').trigger('click')
    await wrapper.get('[data-testid="rate-known"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('student-progress')
    expect(wrapper.findComponent(StudentPracticePage).exists()).toBe(false)
    expect(api.sets).toHaveBeenCalledOnce()
    stop()
    wrapper.unmount()
  })
})
