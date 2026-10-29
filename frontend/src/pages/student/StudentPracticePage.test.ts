import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import { answerCheck, flashcardSet, learnRouter, practiceResult, today } from '@/test/learnFixtures'
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
    api.today.mockResolvedValue(today())
  })

  it('opens the first set not done yet, and Mark as reviewed waits until every card was seen', async () => {
    api.sets.mockResolvedValue([summary(4, 'Numbers cards', true), summary(5, 'Greetings cards')])
    api.set.mockResolvedValue(flashcardSet())
    api.complete.mockResolvedValue(practiceResult())
    const { wrapper, router } = await mountAt('/student/practice/flashcards')

    expect(api.sets).toHaveBeenCalledWith('FLASHCARDS')
    expect(api.set).toHaveBeenCalledWith('FLASHCARDS', 5)
    expect(wrapper.get('[data-testid="set-5"]').attributes('aria-current')).toBe('true')
    expect(wrapper.get('h1').text()).toBe('Flashcards')
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
    await wrapper.get('[data-testid="mark-reviewed"]').trigger('click')
    await flushPromises()
    expect(api.complete).toHaveBeenCalledWith('FLASHCARDS', 5, [], undefined)
    expect(router.currentRoute.value.query.set).toBe('5')
    const done = wrapper.get('[data-testid="set-done"]')
    expect(done.text()).toContain('Set reviewed')
    expect(done.text()).toContain('+15 XP')
    expect(done.text()).toContain('Daily goal reached! Your streak is now 2 days.')
    expect(wrapper.get('[data-testid="next-set"]').text()).toContain('Next set: Food cards')
  })

  it('checks each quiz answer instantly and counts answers in a row', async () => {
    const quiz: PracticeSet = {
      ...flashcardSet(),
      id: 8,
      type: 'QUIZZES',
      title: 'Food quiz',
      items: [
        {
          key: '1',
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
        },
      ],
    }
    api.sets.mockResolvedValue([summary(8, 'Food quiz')])
    api.set.mockResolvedValue(quiz)
    api.check.mockResolvedValue(answerCheck({ correctAnswer: '米饭', pinyin: 'mǐfàn', meaning: 'rice' }))
    const { wrapper } = await mountAt('/student/practice/quizzes')

    expect(wrapper.get('[data-testid="in-a-row"]').text()).toBe('0 in a row')
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()

    expect(api.check).toHaveBeenCalledWith('QUIZZES', 8, { itemKey: '1', answer: '米饭' })
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Correct!')
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('mǐfàn')
    expect(wrapper.get('[data-testid="in-a-row"]').text()).toBe('1 in a row')
  })

  it('lists waiting mistakes with what was answered and filters them by where they came from', async () => {
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
    api.mistakes.mockResolvedValue({ page: pageOf([row]), all: 6, learn: 4, practice: 2 })
    const { wrapper } = await mountAt('/student/practice/mistakes')

    expect(api.sets).not.toHaveBeenCalled()
    expect(api.mistakes).toHaveBeenCalledWith(null, 0, 8)
    expect(wrapper.text()).toContain('6 mistakes to fix')
    const item = wrapper.get('[data-testid="mistake-row"]')
    expect(item.text()).toContain('What does 谢谢 mean?')
    expect(item.text()).toContain('You answered sorry · correct: thank you')
    expect(wrapper.get('[data-testid="start-review"]').attributes('href')).toBe('/student/practice/review')

    await wrapper.get('[data-testid="filter-practice"]').trigger('click')
    await flushPromises()
    expect(api.mistakes).toHaveBeenLastCalledWith('PRACTICE', 0, 8)
    expect(wrapper.get('[data-testid="filter-practice"]').attributes('aria-pressed')).toBe('true')
  })
  it('opens on the room: mistakes to fix first, then the seven activities with their sets on request', async () => {
    api.sets.mockImplementation((type: string) =>
      Promise.resolve(
        type === 'FLASHCARDS'
          ? [summary(4, 'Numbers cards', true), summary(5, 'Greetings cards')]
          : [summary(9, 'A set', true)],
      ),
    )
    const { wrapper } = await mountAt('/student/practice')

    expect(api.sets).toHaveBeenCalledTimes(7)
    expect(wrapper.get('h1').text()).toBe('Practice')
    const call = wrapper.get('[data-testid="mistakes-call"]')
    expect(call.get('h2').text()).toBe('6 mistakes to fix')
    expect(wrapper.get('[data-testid="fix-now"]').attributes('href')).toBe('/student/practice/review')
    expect(wrapper.get('[data-testid="see-mistakes"]').attributes('href')).toBe('/student/practice/mistakes')

    const room = wrapper.get('nav[aria-label="Practice activities"]')
    expect(room.findAll('.practice-room-name').map((link) => link.text())).toEqual([
      'Flashcards',
      'Quizzes',
      'Listening',
      'Tracing',
      'Drag & Drop',
      'Matching',
      'Story',
    ])
    expect(wrapper.get('[data-testid="type-flashcards"]').attributes('href')).toBe('/student/practice/flashcards')
    expect(room.text()).toContain('1 of 2 done')
    expect(room.text()).toContain('All 1 done')

    // The sets of a type open on request, each one a link to it.
    const toggle = wrapper.get('[data-testid="toggle-flashcards"]')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.attributes('aria-label')).toBe('2 sets of Flashcards')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    const sets = wrapper.get('#practice-sets-flashcards').findAll('a')
    expect(sets.map((link) => link.text())).toEqual([
      expect.stringContaining('Numbers cards (Done)'),
      expect.stringContaining('Greetings cards'),
    ])
    expect(sets[1]!.text()).toContain('Up next')
    expect(sets[1]!.attributes('href')).toBe('/student/practice/flashcards?set=5')

    expect(wrapper.get('[data-testid="practice-today"]').text()).toContain('2 sets done today')
  })

  it('leaves the mistakes call out when nothing is waiting', async () => {
    api.today.mockResolvedValue(today({ mistakesWaiting: 0 }))
    api.sets.mockResolvedValue([])
    const { wrapper } = await mountAt('/student/practice')

    expect(wrapper.find('[data-testid="mistakes-call"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="practice-today"]').text()).toContain('No mistakes yet')
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
})
