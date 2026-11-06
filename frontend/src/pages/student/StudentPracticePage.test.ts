import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import { answerCheck, flashcardSet, learnRouter, practiceResult, today, xpResult } from '@/test/learnFixtures'
import type { MistakeRow, PracticeSet, PracticeSetSummary } from '@/types/learn'

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
  xpApi: { today: api.today },
  mistakesApi: { list: api.mistakes },
}))

import StudentPracticePage from './StudentPracticePage.vue'

function summary(id: number, title: string, done = false): PracticeSetSummary {
  return { id, title, stage: 'GUIDED', hskLevel: 1, itemCount: 2, xpReward: 10, done }
}

async function mountAt(path: string) {
  const router = await learnRouter(path)
  const wrapper = mount(StudentPracticePage, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('StudentPracticePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.today.mockResolvedValue(today())
    api.mistakes.mockResolvedValue({ page: pageOf([]), all: 0, learn: 0, practice: 0 })
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
      'Quizzes',
      'Listening',
      'Tracing',
      'Drag & Drop',
      'Matching',
      'Mistakes6',
    ])
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/student/practice/flashcards')
    expect(wrapper.get('[data-testid="type-flashcards"]').attributes('aria-current')).toBe('page')
    expect(api.sets).toHaveBeenCalledWith('FLASHCARDS')
    expect(wrapper.text()).not.toContain('Story')

    const goal = wrapper.get('[data-testid="daily-goal"]')
    expect(goal.text()).toContain('Daily goal')
    expect(goal.text()).toContain('20 / 30 XP')
    expect(goal.text()).toContain('10 XP to go')
    expect(goal.text()).toContain('7-day streak')
  })

  it('opens the first set not done yet, and Mark as reviewed waits until every card was seen', async () => {
    api.sets.mockResolvedValue([summary(4, 'Numbers cards', true), summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    api.complete.mockResolvedValue(practiceResult())
    const { wrapper, router } = await mountAt('/student/practice/flashcards')

    expect(api.sets).toHaveBeenCalledWith('FLASHCARDS')
    expect(api.set).toHaveBeenCalledWith('FLASHCARDS', 5)
    expect(wrapper.get('[data-testid="set-5"]').attributes('aria-current')).toBe('true')
    expect(wrapper.get('[data-testid="set-4"]').text()).toContain('Numbers cards (Done)')
    expect(wrapper.get('[data-testid="set-5"]').attributes('href')).toBe('/student/practice/flashcards?set=5')
    expect(wrapper.get('[role="group"][aria-label="2 sets of this activity"]').text()).toContain('2 sets')
    expect(wrapper.get('h2').text()).toBe('Greetings cards')
    expect(wrapper.text()).toContain('Flashcards · 2 cards · HSK 1 · +10 XP')

    const mark = wrapper.get('[data-testid="mark-reviewed"]')
    expect(mark.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('See every card to finish the set.')
    await wrapper.get('[data-testid="flip"]').trigger('click')
    expect(wrapper.text()).toContain('hello')
    await wrapper.get('[data-testid="next-card"]').trigger('click')
    expect(wrapper.get('[data-testid="mark-reviewed"]').attributes('disabled')).toBeUndefined()

    // The reloaded list marks the set done; the result of the set stays on screen all the same.
    api.sets.mockResolvedValue([
      summary(4, 'Numbers cards', true),
      summary(5, 'Greetings cards', true),
      summary(6, 'Food cards'),
    ])
    api.today.mockResolvedValue(today({ todayXp: 20, goalReached: true, currentStreak: 3 }))
    await wrapper.get('[data-testid="mark-reviewed"]').trigger('click')
    await flushPromises()
    expect(api.complete).toHaveBeenCalledTimes(1)
    expect(api.complete).toHaveBeenCalledWith('FLASHCARDS', 5, [], undefined)
    expect(router.currentRoute.value.query.set).toBe('5')
    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.text()).toContain('Set reviewed')
    expect(done.text()).toContain('+15 XP')
    expect(done.text()).toContain('2 of 2 · set complete')
    expect(wrapper.get('[data-testid="next-set"]').text()).toContain('Next set: Food cards')
    expect(wrapper.get('h2').text()).toBe('Greetings cards')
    // Today's goal and the streak are told once, in the strip above the stage.
    expect(wrapper.get('[data-testid="daily-goal"]').text()).toContain('Goal reached · streak now 3 days')
    expect(wrapper.get('[data-testid="daily-goal"]').text()).toContain('3-day streak')
  })

  it('checks each quiz answer on click and counts answers in a row, naming the bonus at the third', async () => {
    const question = (key: string) => ({
      key,
      prompt: 'Which one means “rice”?',
      hanzi: null,
      pinyin: null,
      meaning: null,
      audioText: null,
      example: null,
      exampleMeaning: null,
      options: [
        { text: '米饭', pinyin: 'mǐfàn' },
        { text: '面条', pinyin: 'miàntiáo' },
      ],
      tiles: [],
    })
    const quiz: PracticeSet = {
      ...flashcardSet(),
      id: 8,
      type: 'QUIZZES',
      title: 'Food quiz',
      done: false,
      items: [question('1'), question('2'), question('3'), question('4')],
    }
    api.sets.mockResolvedValue([summary(8, 'Food quiz')])
    api.set.mockResolvedValue(quiz)
    api.check.mockResolvedValue(answerCheck({ correctAnswer: '米饭', pinyin: 'mǐfàn', meaning: 'rice' }))
    const { wrapper } = await mountAt('/student/practice/quizzes')

    expect(wrapper.text()).toContain('Quiz · 2 questions')
    expect(wrapper.get('[data-testid="in-a-row"]').text()).toBe('0 in a row')
    expect(wrapper.text()).toContain('3 in a row = +5 XP bonus')
    expect(wrapper.find('[data-testid="check"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="skip"]').exists()).toBe(true)

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await flushPromises()

    expect(api.check).toHaveBeenCalledWith('QUIZZES', 8, { itemKey: '1', answer: '米饭' })
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Correct!')
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('mǐfàn')
    expect(wrapper.get('[data-testid="check-bar"]').text()).not.toContain('XP')
    expect(wrapper.get('[data-testid="in-a-row"]').text()).toBe('1 in a row')
    expect(wrapper.find('[data-testid="skip"]').exists()).toBe(false)

    for (const row of [2, 3]) {
      await wrapper.get('[data-testid="continue"]').trigger('click')
      await wrapper.get('[data-testid="option-0"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="in-a-row"]').text()).toBe(`${row} in a row`)
    }
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('+5 XP')
  })

  it('lists the latest mistakes with what was answered and filters them by where they came from', async () => {
    const row: MistakeRow = {
      id: 3,
      source: 'LEARN',
      type: 'MEANING',
      prompt: null,
      hanzi: '谢谢',
      meaning: 'thank you',
      yourAnswer: 'sorry',
      correctAnswer: 'thank you',
      lastWrongAt: '2026-05-17T03:00:00Z',
    }
    api.mistakes.mockResolvedValue({
      page: { ...pageOf([row]), totalItems: 6 },
      all: 6,
      learn: 4,
      practice: 2,
    })
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(api.sets).not.toHaveBeenCalled()
    expect(api.mistakes).toHaveBeenCalledWith(null, 0, 4)
    expect(wrapper.get('h2').text()).toBe('6 mistakes to fix')
    expect(wrapper.text()).toContain('One quick review clears them · +10 XP')
    const item = wrapper.get('[data-testid="mistake-row"]')
    expect(item.text()).toContain('What does 谢谢 mean?')
    expect(item.text()).toContain('You answered sorry · correct: thank you')
    expect(item.get('.tag').text()).toContain('Learn ·')
    expect(wrapper.text()).toContain('+5 more · all 6 are in the review')
    expect(wrapper.get('[data-testid="start-review"]').attributes('href')).toBe('/student/practice/review')
    expect(wrapper.get('[data-testid="filter-learn"]').text().replace(/\s+/g, ' ')).toBe('Learn · 4')

    await wrapper.get('[data-testid="filter-practice"]').trigger('click')
    await flushPromises()
    expect(api.mistakes).toHaveBeenLastCalledWith('PRACTICE', 0, 4)
    expect(wrapper.get('[data-testid="filter-practice"]').attributes('aria-pressed')).toBe('true')
  })

  it('does not promise the whole list when it is longer than one review', async () => {
    api.mistakes.mockResolvedValue({
      page: { ...pageOf([]), totalItems: 25 },
      all: 25,
      learn: 25,
      practice: 0,
    })
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(wrapper.get('.practice-mistakes-more').text()).toBe('+25 more')
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
    api.set.mockResolvedValue(flashcardSet({ items: flashcardSet().items.slice(0, 1) }))
    api.complete.mockResolvedValue(practiceResult({ nextSet: null }))
    const { wrapper } = await mountAt('/student/practice/flashcards')

    await wrapper.get('[data-testid="mark-reviewed"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="next-set"]').exists()).toBe(false)
    const next = wrapper.get('[data-testid="next-type"]')
    expect(next.text()).toContain('Try Quizzes')
    expect(next.attributes('href')).toBe('/student/practice/quizzes')
  })

  it('tells a set that was not all right, and why it earned no XP', async () => {
    api.sets.mockResolvedValue([summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    api.complete.mockResolvedValue(
      practiceResult({
        type: 'QUIZZES',
        correctCount: 1,
        itemCount: 2,
        allCorrect: false,
        xp: xpResult({ xpEarned: 0 }),
      }),
    )
    const { wrapper } = await mountAt('/student/practice/flashcards')
    await wrapper.get('[data-testid="next-card"]').trigger('click')
    await wrapper.get('[data-testid="mark-reviewed"]').trigger('click')
    await flushPromises()

    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.text()).toContain('1 of 2 right')
    expect(done.text()).toContain('Your mistake is saved for a quick review.')
    expect(done.text()).toContain('Get every item right for +10 XP.')
  })
})
