import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { learnPath, learnRouter, lessonResult, today } from '@/test/learnFixtures'
import type { LearnPath, PathStop, PathUnit } from '@/types/learn'

const api = vi.hoisted(() => ({
  path: vi.fn(),
  guidebook: vi.fn(),
  today: vi.fn(),
}))

vi.mock('@/api/learn', () => ({
  learnApi: { path: api.path, guidebook: api.guidebook },
  xpApi: { today: api.today },
}))

/** The fixture path with Unit 1 changed. */
function withUnit1(change: (unit: PathUnit) => PathUnit): LearnPath {
  const path = learnPath()
  const [level1, ...rest] = path.levels
  const [unit1, ...units] = level1!.units
  return { ...path, levels: [{ ...level1!, units: [change(unit1!), ...units] }, ...rest] }
}

const lockedStop: PathStop = {
  id: 14,
  position: 4,
  title: 'Numbers',
  kind: 'LESSON',
  minutes: 5,
  xpReward: 10,
  state: 'LOCKED',
  current: false,
  unlockAfter: 'Your name',
}

import StudentLearnPage from './StudentLearnPage.vue'

async function mountPage() {
  const router = await learnRouter('/student/learn')
  const wrapper = mount(StudentLearnPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentLearnPage (Learn path)', () => {
  beforeEach(() => {
    api.path.mockResolvedValue(learnPath())
    api.today.mockResolvedValue(today())
    window.history.replaceState({}, '')
  })

  it('pins the level and the unit the student is on, computed from the path', async () => {
    const wrapper = await mountPage()

    const header = wrapper.get('.learn-path-header')
    expect(header.get('h2').text()).toBe('Level 1 · Beginner 1 · HSK 1 basics')
    expect(header.text()).toContain('2 units · 6 lessons')
    expect(header.get('.learn-banner').classes()).toContain('is-here')
    expect(header.text()).toContain('You are here · Unit 1')
    expect(header.text()).toContain('Greetings · 你好')
    // The pinned unit's heading stays with its section, so the region keeps its name.
    expect(wrapper.get('#learn-unit-1 h3').text()).toBe('Greetings · 你好')
    expect(wrapper.find('#learn-unit-1 .learn-banner').exists()).toBe(false)
    expect(wrapper.get('#learn-unit-1').text()).toContain('2 of 3')
    // Only the road scrolls, and the keyboard can scroll it.
    const scroller = wrapper.get('.learn-path-scroll')
    expect(scroller.attributes('tabindex')).toBe('0')
    expect(scroller.attributes('aria-label')).toBe('The learning path')
  })

  it('draws the stops by state: done, current with its Start bubble, locked, bonus', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="stop-11"]').attributes('aria-label')).toBe('Hello and goodbye (done)')
    expect(wrapper.get('[data-testid="stop-13"]').attributes('aria-label')).toBe('Your name (ready to start)')
    expect(wrapper.findAll('.learn-node.is-current')).toHaveLength(1)
    expect(wrapper.get('.learn-node.is-current .learn-node-bubble').text()).toBe('Start')
    // Hanzi are not icons: a stop shows an icon or the word Bonus, never a numeral.
    expect(wrapper.find('.learn-node .seal').exists()).toBe(false)

    const unit2 = wrapper.get('#learn-unit-2')
    expect(unit2.text()).toContain('Unit 2 · Locked')
    expect(unit2.text()).toContain('Finish Unit 1 to unlock')
    expect(unit2.get('[data-testid="stop-21"]').attributes('aria-label')).toBe('Fruit (locked)')
    expect(unit2.get('.learn-node.is-bonus').text()).toContain('Bonus')
    expect(wrapper.find('[data-testid="guidebook-2"]').exists()).toBe(false)
  })

  it('draws the road: walked where a stop is done, dotted ahead', async () => {
    const wrapper = await mountPage()

    const lines = wrapper.findAll('#learn-unit-1 .learn-road-line line')
    expect(lines.map((line) => line.classes())).toEqual([['is-walked'], ['is-walked']])
    expect(lines[0]!.attributes('x1')).toBe('0')
    expect(lines[0]!.attributes('x2')).toBe('-85')
    expect(Number(lines[0]!.attributes('y2')) - Number(lines[0]!.attributes('y1'))).toBe(130)

    const ahead = wrapper.findAll('#learn-unit-2 .learn-road-line line')
    expect(ahead.every((line) => line.classes().includes('is-ahead'))).toBe(true)
  })

  it('keeps the next level as an overview until its checkpoint opens, and closes the level with the checkpoint', async () => {
    const wrapper = await mountPage()

    const overview = wrapper.get('[data-testid="locked-level"]')
    expect(overview.text()).toContain('Unit 3 · Travel')
    expect(overview.text()).toContain('4 lessons')
    expect(wrapper.find('[data-testid="stop-31"]').exists()).toBe(false)

    const checkpoint = wrapper.get('[data-testid="checkpoint"]')
    expect(checkpoint.text()).toContain('Locked')
    expect(checkpoint.text()).toContain('10 questions from Units 1–2 · +50 XP · unlocks after Unit 2')
  })

  it('opens a stop card with Start for an open lesson and what to finish first for a locked one', async () => {
    api.path.mockResolvedValue(
      withUnit1((unit) => ({ ...unit, lessonCount: 4, lessons: [...unit.lessons, lockedStop] })),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="stop-13"]').trigger('click')
    const card = wrapper.get('[role="dialog"]')
    expect(card.attributes('aria-label')).toBe('Your name')
    expect(card.text()).toContain('Lesson 3 of 4 · Unit 1 · about 5 min')
    expect(wrapper.get('[data-testid="start-13"]').text()).toBe('Start · +10 XP')
    expect(wrapper.get('[data-testid="start-13"]').attributes('href')).toBe('/student/learn/lessons/13')
    // The bubble gives way to the card.
    expect(wrapper.find('.learn-node-bubble').exists()).toBe(false)

    await wrapper.get('[data-testid="stop-14"]').trigger('click')
    expect(wrapper.find('[data-testid="start-13"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="start-14"]').exists()).toBe(false)
    const locked = wrapper.get('[role="dialog"]')
    expect(locked.text()).toContain('Finish “Your name” to unlock this lesson.')
    expect(locked.find('a').exists()).toBe(false)
    expect(locked.get('button').attributes('disabled')).toBeDefined()
  })

  it('offers Practise again on a finished stop, and Escape closes a card', async () => {
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="stop-11"]').trigger('click')
    expect(wrapper.get('[data-testid="start-11"]').text()).toBe('Practise again')

    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('shows a finished unit as a white sheet with its guidebook', async () => {
    const done = withUnit1((unit) => ({
      ...unit,
      state: 'DONE',
      completedCount: 3,
      lessons: unit.lessons.map((stop) => ({ ...stop, state: 'DONE' as const, current: false })),
    }))
    const [level1] = done.levels
    const unit2 = { ...level1!.units[1]!, state: 'ACTIVE' as const }
    api.path.mockResolvedValue({
      ...done,
      nextLesson: { ...done.nextLesson!, unitId: unit2.id, unitNumber: 2 },
      levels: [{ ...level1!, units: [level1!.units[0]!, unit2] }, ...done.levels.slice(1)],
    })
    const wrapper = await mountPage()

    expect(wrapper.get('.learn-path-header').text()).toContain('You are here · Unit 2')
    const unit1 = wrapper.get('#learn-unit-1')
    expect(unit1.get('.learn-banner').classes()).toContain('is-done')
    expect(unit1.text()).toContain('Unit 1 · Done')
    expect(unit1.find('[data-testid="guidebook-1"]').exists()).toBe(true)
    // The pinned banner carries this unit's guidebook, so the banner in the path does not repeat it.
    expect(wrapper.findAll('[data-testid="guidebook-2"]')).toHaveLength(1)
    expect(wrapper.get('#learn-unit-2 .learn-banner').text()).toContain('Unit 2')
  })

  it('shows the next goal, the whole path, the daily goal and the waiting mistakes in the rail', async () => {
    const wrapper = await mountPage()

    const goal = wrapper.get('[data-testid="next-goal"]')
    expect(goal.text()).toContain('Unlock Unit 2')
    expect(goal.text()).toContain('1 lesson to go.')
    expect(goal.findAll('.learn-step.is-done')).toHaveLength(2)
    expect(goal.get('.learn-step.is-here').text()).toBe('3')

    const map = wrapper.get('[data-testid="your-path"]')
    expect(map.text()).toContain('2 of 6 lessons')
    const rows = map.findAll('.learn-map-row').map((row) => row.text())
    expect(rows).toEqual([
      'Unit 1 · Greetings2 of 3',
      'Unit 2 · Food3 lessons',
      'Level 1 checkpointUnits 1–2',
      'Unit 3 · Travel4 lessons',
    ])

    expect(wrapper.get('[data-testid="daily-goal"]').text()).toContain('20 / 30 XP')
    const practise = wrapper.get('[data-testid="practise-card"]')
    expect(practise.text()).toContain('6 mistakes waiting.')
    expect(practise.get('a').text()).toBe('See mistakes')
    expect(practise.get('a').attributes('href')).toBe('/student/practice/mistakes')
  })

  it('leaves out the mistakes block when none are waiting, and has no plan block or classes note', async () => {
    api.today.mockResolvedValue(today({ mistakesWaiting: 0 }))
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="practise-card"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="plan-rail"]').exists()).toBe(false)
    expect(wrapper.find('.learn-rail').text()).not.toContain('Classes')
  })

  it('points to an open checkpoint when no lesson is left before it', async () => {
    const path = learnPath()
    const [level1, ...rest] = path.levels
    api.path.mockResolvedValue({
      ...path,
      nextLesson: null,
      levels: [{ ...level1!, checkpoint: { ...level1!.checkpoint!, state: 'AVAILABLE' as const } }, ...rest],
    })
    const wrapper = await mountPage()

    const goal = wrapper.get('[data-testid="next-goal"]')
    expect(goal.text()).toContain('Level 1 checkpoint')
    expect(goal.text()).toContain('Pass it with 70% or more to open Level 2.')
    expect(wrapper.get('[data-testid="checkpoint-start"]').attributes('href')).toBe('/student/learn/lessons/90')
  })

  it('opens the unit guidebook with its key phrases and tips', async () => {
    api.guidebook.mockResolvedValue({
      unitId: 1,
      number: 1,
      title: 'Greetings',
      titleZh: '问候',
      phrases: [{ hanzi: '你好', pinyin: 'nǐ hǎo', meaning: 'hello' }],
      tips: ['Say 你好 to anyone.'],
    })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="guidebook-1"]').trigger('click')
    await flushPromises()

    expect(api.guidebook).toHaveBeenCalledWith(1)
    const drawer = wrapper.get('[data-testid="guidebook"]')
    expect(drawer.text()).toContain('nǐ hǎo · hello')
    expect(drawer.text()).toContain('Say 你好 to anyone.')
    // No glyph tile beside a phrase: the phrase itself is the learning content.
    expect(drawer.find('.hanzi-tile').exists()).toBe(false)
  })

  it('announces the lesson the player just finished, once', async () => {
    window.history.replaceState({ lessonResult: lessonResult() }, '')
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('“Your name” done: +10 XP.')
    expect(wrapper.text()).toContain('“Fruit” is unlocked.')
    expect((window.history.state as { lessonResult?: unknown }).lessonResult).toBeUndefined()
  })
})
