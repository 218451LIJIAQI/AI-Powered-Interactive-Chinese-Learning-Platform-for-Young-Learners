import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { learnPath, learnRouter, lessonResult, today } from '@/test/learnFixtures'
import type { LearnPath, PathStop, PathUnit } from '@/types/learn'
import { planSummary } from '@/test/planFixtures'

const api = vi.hoisted(() => ({
  path: vi.fn(),
  guidebook: vi.fn(),
  today: vi.fn(),
  overview: vi.fn(),
}))

vi.mock('@/api/learn', () => ({
  learnApi: { path: api.path, guidebook: api.guidebook },
  xpApi: { today: api.today },
}))
vi.mock('@/api/plan', () => ({ planApi: { overview: api.overview } }))

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
    api.overview.mockResolvedValue({ setupState: 'COMPLETED', plan: planSummary() })
    window.history.replaceState({}, '')
  })

  it('opens the unit being learnt with its lessons, folds a locked unit to its reason and keeps a locked level as a preview', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Unit 1')
    expect(wrapper.text()).toContain('Greetings')
    expect(wrapper.get('[data-testid="stop-11"]').attributes('aria-label')).toBe('Hello and goodbye (done)')
    expect(wrapper.get('[data-testid="stop-13"]').attributes('aria-label')).toBe('Your name (ready to start)')
    expect(wrapper.findAll('.learn-lesson.is-current')).toHaveLength(1)
    // Done lessons are stamped seals, the current one is outlined; the seal reads its place in the unit.
    expect(wrapper.get('[data-testid="stop-11"] .seal').classes()).not.toContain('is-outline')
    expect(wrapper.get('[data-testid="stop-13"] .seal').classes()).toContain('is-outline')
    expect(wrapper.get('[data-testid="stop-13"] .seal').text()).toBe('三')
    expect(wrapper.get('[data-testid="continue-13"]').attributes('href')).toBe('/student/learn/lessons/13')

    const unit2 = wrapper.get('#learn-unit-2')
    expect(unit2.text()).toContain('Unit 2 · Locked')
    expect(unit2.text()).toContain('Finish Unit 1 to unlock')
    expect(unit2.text()).toContain('Fruit\u00a0· Drinks\u00a0· Ordering')
    expect(wrapper.find('[data-testid="stop-21"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="guidebook-2"]').exists()).toBe(false)

    const preview = wrapper.get('[data-testid="locked-level"]')
    expect(preview.text()).toContain('Travel')
    expect(wrapper.find('[data-testid="stop-31"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="checkpoint"]').text()).toContain('Locked')
  })

  it('opens a lesson card with Start for an open lesson and what to finish first for a locked one', async () => {
    api.path.mockResolvedValue(
      withUnit1((unit) => ({ ...unit, lessonCount: 4, lessons: [...unit.lessons, lockedStop] })),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="stop-13"]').trigger('click')
    expect(wrapper.get('[data-testid="start-13"]').text()).toBe('Start · +10 XP')
    expect(wrapper.get('[data-testid="start-13"]').attributes('href')).toBe('/student/learn/lessons/13')

    expect(wrapper.get('[data-testid="stop-14"]').text()).toContain('Opens after “Your name”')
    await wrapper.get('[data-testid="stop-14"]').trigger('click')
    expect(wrapper.find('[data-testid="start-13"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="start-14"]').exists()).toBe(false)
    expect(wrapper.get('[role="dialog"]').text()).toContain('Finish “Your name” to unlock this lesson.')
  })

  it('stamps a finished unit and folds it away until its lessons are asked for', async () => {
    api.path.mockResolvedValue(
      withUnit1((unit) => ({
        ...unit,
        state: 'DONE',
        completedCount: 3,
        lessons: unit.lessons.map((stop) => ({ ...stop, state: 'DONE' as const, current: false })),
      })),
    )
    const wrapper = await mountPage()

    const unit1 = wrapper.get('#learn-unit-1')
    expect(unit1.get('.learn-unit-seal').text()).toBe('完')
    expect(unit1.text()).toContain('Unit 1 · done')
    expect(unit1.find('[data-testid="stop-11"]').exists()).toBe(false)

    const toggle = unit1.get('[data-testid="unit-toggle-1"]')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    await wrapper.get('[data-testid="stop-11"]').trigger('click')
    expect(wrapper.get('[data-testid="start-11"]').text()).toBe('Practise again')
  })

  it('shows the next goal, the daily goal and the lesson mistakes in the rail', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="next-goal"]').text()).toContain('Unlock Unit 2')
    expect(wrapper.get('[data-testid="daily-goal"]').text()).toContain('20 / 30 XP')
    const practise = wrapper.get('[data-testid="practise-card"]')
    expect(practise.text()).toContain('4 mistakes from your lessons are waiting.')
    expect(practise.get('a').attributes('href')).toBe('/student/practice/mistakes')
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
  })

  it('announces the lesson the player just finished, once', async () => {
    window.history.replaceState({ lessonResult: lessonResult() }, '')
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('“Your name” done: +10 XP.')
    expect(wrapper.text()).toContain('“Fruit” is unlocked.')
    expect((window.history.state as { lessonResult?: unknown }).lessonResult).toBeUndefined()
  })

  it('shows the learning plan in the rail: the goal and the unit it is reached at', async () => {
    const wrapper = await mountPage()

    const rail = wrapper.get('[data-testid="plan-rail"]')
    expect(rail.text()).toContain('Travel to Shanghai')
    expect(rail.text()).toContain('Your goal is at Unit 3 · Getting around · 出行')
    expect(rail.get('a').attributes('href')).toBe('/student/progress/plan')
  })
})
