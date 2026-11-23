import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { mountAtRoute } from '@/test/routes'
import { learnPath, lessonResult, pathStop, today } from '@/test/learnFixtures'
import type { LearnPath, PathUnit } from '@/types/learn'

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

const lockedStop = pathStop(14, 4, 'Numbers', { unlockAfter: 'Your name' })

import StudentLearnPage from './StudentLearnPage.vue'

async function mountPage() {
  return (await mountAtRoute(StudentLearnPage, '/student/learn')).wrapper
}

describe('StudentLearnPage (Learn level map)', () => {
  beforeEach(() => {
    api.path.mockResolvedValue(learnPath())
    api.today.mockResolvedValue(today())
    window.history.replaceState({}, '')
  })

  it('pins the level and the unit the learner is on, computed from the path', async () => {
    const wrapper = await mountPage()

    const header = wrapper.get('.learn-path-header')
    expect(header.get('h2').text()).toBe('HSK 1 (Basic)')
    expect(header.get('.learn-banner').classes()).toContain('is-here')
    expect(header.text()).toContain('You are here · Unit 1')
    expect(header.text()).toContain('Greetings · 你好')
    // The pinned unit's heading stays with its section, so the region keeps its name.
    expect(wrapper.get('#learn-unit-1 h3').text()).toBe('Greetings · 你好')
    expect(wrapper.find('#learn-unit-1 .learn-banner').exists()).toBe(false)
    expect(wrapper.get('#learn-unit-1').text()).toContain('2 of 3')
    // Only the map scrolls, and the keyboard can scroll it.
    const scroller = wrapper.get('.learn-path-scroll')
    expect(scroller.attributes('tabindex')).toBe('0')
    expect(scroller.attributes('aria-label')).toBe('The learning path')
  })

  it('draws every lesson as a numbered level that names its place, title and state', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="level-11"]').attributes('aria-label')).toBe('Level 1, Hello and goodbye, done')
    expect(wrapper.get('[data-testid="level-13"]').attributes('aria-label')).toBe('Level 3, Your name, current')
    expect(wrapper.get('[data-testid="level-11"] .learn-level-disc').text()).toBe('1')
    expect(wrapper.get('[data-testid="level-11"] .learn-level-label').text()).toBe('Hello and goodbye')
    // Done levels wear three stars; only the current one has the START tag, the halo and the panda.
    expect(wrapper.findAll('#learn-unit-1 .learn-level-stars')).toHaveLength(2)
    expect(wrapper.findAll('.learn-level.is-current')).toHaveLength(1)
    const current = wrapper.get('.learn-level.is-current')
    expect(current.get('.learn-level-start').text()).toBe('Start')
    expect(current.find('.learn-level-halo').exists()).toBe(true)
    expect(current.find('.learn-panda').exists()).toBe(true)

    // A locked unit lies under a fog; its bonus chest is a star, not a number.
    const unit2 = wrapper.get('#learn-unit-2')
    expect(unit2.find('.learn-map-fog').exists()).toBe(true)
    expect(unit2.get('.learn-banner').text()).toContain('Unit 2')
    expect(unit2.text()).toContain('Finish Unit 1 to unlock')
    expect(unit2.get('[data-testid="level-21"]').attributes('aria-label')).toBe('Level 1, Fruit, locked')
    expect(unit2.get('[data-testid="level-23"]').attributes('aria-label')).toBe('Ordering (optional), locked')
    expect(unit2.get('[data-testid="level-23"] .learn-level-disc').text()).toBe('')
    expect(wrapper.find('[data-testid="guidebook-2"]').exists()).toBe(false)
  })

  it('runs the amber line along the road from the unit’s entrance to the current level', async () => {
    const wrapper = await mountPage()

    const unit = wrapper.get('#learn-unit-1')
    const fullRoad = unit.get('.learn-map-road-surface').attributes('d')!
    const walked = unit.get('.learn-map-progress').attributes('d')!
    const current = unit.get<HTMLElement>('.learn-level.is-current').element.style
    const x = Number.parseFloat(current.getPropertyValue('--x'))
    const y = Number.parseFloat(current.getPropertyValue('--y'))
    expect(walked).toMatch(/^M-?\d+ -\d+/)
    expect(walked.endsWith(` ${x} ${y}`)).toBe(true)
    expect(fullRoad.startsWith(walked)).toBe(true)
    expect(fullRoad.length).toBeGreaterThan(walked.length)
    // Nothing walked in a locked unit.
    expect(wrapper.find('#learn-unit-2 .learn-map-progress').exists()).toBe(false)
  })

  it('lists the next level’s published units until its checkpoint opens, and closes the level with the checkpoint', async () => {
    const wrapper = await mountPage()

    expect(wrapper.findAll('.learn-path-road h2').map((title) => title.text())).toEqual(['HSK 2 (Survival)'])
    const overview = wrapper.get('[data-testid="locked-level"]')
    expect(overview.text()).toContain('Unit 3 · Travel · 你好')
    expect(wrapper.find('[data-testid="level-31"]').exists()).toBe(false)

    const checkpoint = wrapper.get('[data-testid="checkpoint"]')
    expect(checkpoint.text()).toContain('HSK 1 checkpoint · 关卡')
    expect(checkpoint.get('h3').text()).toBe('Pass the checkpoint to unlock HSK 2')
    expect(checkpoint.text()).toContain('10 questions from Units 1–2 · +50 XP')
    expect(checkpoint.text()).toContain('Finish Unit 2 to unlock')
    expect(wrapper.find('[data-testid="checkpoint-start"]').exists()).toBe(false)
  })

  it('names the checkpoint in Chinese once when the page is in Chinese', async () => {
    i18n.global.locale.value = 'zh-CN'
    const wrapper = await mountPage()

    expect(wrapper.get('.learn-checkpoint-overline').text()).toBe('HSK 1 关卡')
  })

  it('opens a level’s card with Start for an open lesson and what to finish first for a locked one', async () => {
    api.path.mockResolvedValue(
      withUnit1((unit) => ({ ...unit, lessonCount: 4, lessons: [...unit.lessons, lockedStop] })),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="level-13"]').trigger('click')
    expect(wrapper.get('[data-testid="level-13"]').attributes('aria-expanded')).toBe('true')
    const card = wrapper.get('[role="dialog"]')
    expect(card.attributes('aria-label')).toBe('Your name')
    expect(card.text()).toContain('About 5 min')
    expect(wrapper.get('[data-testid="start-13"]').text()).toBe('Start · +10 XP')
    expect(wrapper.get('[data-testid="start-13"]').attributes('href')).toBe('/student/learn/lessons/13')
    // The START tag gives way to the card.
    expect(wrapper.find('.learn-level-start').exists()).toBe(false)

    await wrapper.get('[data-testid="level-14"]').trigger('click')
    expect(wrapper.find('[data-testid="start-13"]').exists()).toBe(false)
    const locked = wrapper.get('[role="dialog"]')
    expect(locked.text()).toContain('Finish “Your name” to unlock this lesson.')
    expect(locked.find('a').exists()).toBe(false)
    expect(locked.find('button').exists()).toBe(false)
  })

  it('calls an open bonus chest an optional bonus, not one of the unit’s numbered lessons', async () => {
    const chest = pathStop(15, 3, 'Treasure', { kind: 'BONUS', state: 'AVAILABLE', minutes: 3, xpReward: 15 })
    api.path.mockResolvedValue(withUnit1((unit) => ({ ...unit, lessons: [...unit.lessons, chest] })))
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="level-15"]').attributes('aria-label')).toBe('Treasure (optional), ready to start')
    await wrapper.get('[data-testid="level-15"]').trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('Optional bonus · about 3 min')
    expect(wrapper.get('[data-testid="start-15"]').text()).toBe('Start · +15 XP')
  })

  it('draws the unit review as the unit’s crown, not a numbered level', async () => {
    const review = pathStop(16, 4, 'Unit 1 review', { kind: 'REVIEW', unlockAfter: 'Your name' })
    api.path.mockResolvedValue(withUnit1((unit) => ({ ...unit, lessons: [...unit.lessons, review] })))
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="level-16"]').attributes('aria-label')).toBe('Unit 1 review, locked')
    expect(wrapper.get('[data-testid="level-16"]').element.closest('.learn-level')!.classList).toContain('is-review')
    expect(wrapper.get('[data-testid="level-16"] .learn-level-disc').text()).toBe('')
  })

  it('offers Practise again on a finished level, and Escape closes the card', async () => {
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="level-11"]').trigger('click')
    expect(wrapper.get('[data-testid="start-11"]').text()).toBe('Practise again')

    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('shows a finished unit with its guidebook and its whole road walked', async () => {
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
      nextLesson: { ...done.nextLesson!, unitId: unit2.id },
      levels: [{ ...level1!, units: [level1!.units[0]!, unit2] }, ...done.levels.slice(1)],
    })
    const wrapper = await mountPage()

    expect(wrapper.get('.learn-path-header').text()).toContain('You are here · Unit 2')
    const unit1 = wrapper.get('#learn-unit-1')
    expect(unit1.get('.learn-banner').classes()).toContain('is-done')
    expect(unit1.text()).toContain('Unit 1 · Done')
    expect(unit1.find('[data-testid="guidebook-1"]').exists()).toBe(true)
    expect(unit1.get('.learn-map-progress').attributes('d')).toBe(unit1.get('.learn-map-road-surface').attributes('d'))
    // The pinned banner carries this unit's guidebook, so the banner in the path does not repeat it.
    expect(wrapper.findAll('[data-testid="guidebook-2"]')).toHaveLength(1)
  })

  it('shows the next goal, the daily goal and the waiting mistakes in the side column', async () => {
    const wrapper = await mountPage()

    const goal = wrapper.get('[data-testid="next-goal"]')
    expect(goal.text()).toContain('Unlock Unit 2')
    expect(goal.text()).toContain('1 lesson to go.')

    const daily = wrapper.get('[data-testid="daily-goal"]')
    expect(daily.text()).toContain('20 / 30 XP')
    expect(daily.text()).toContain('One more lesson reaches today’s goal.')
    // The streak is shown once, in the sidebar's status card.
    expect(daily.text()).not.toContain('streak')
    const practise = wrapper.get('[data-testid="practise-card"]')
    expect(practise.text()).toContain('6 mistakes waiting.')
    expect(practise.get('a').text()).toBe('See mistakes')
    expect(practise.get('a').attributes('href')).toBe('/student/practice/mistakes')
    expect(wrapper.findAll('.learn-rail-section')).toHaveLength(3)
  })

  it('counts a bonus chest out of the lessons to go', async () => {
    const chest = pathStop(15, 3, 'Bonus chest', { kind: 'BONUS', unlockAfter: 'Your name' })
    const review = pathStop(16, 4, 'Unit 1 review', { kind: 'REVIEW', unlockAfter: 'Your name' })
    api.path.mockResolvedValue(
      withUnit1((unit) => ({ ...unit, lessonCount: unit.lessonCount + 1, lessons: [...unit.lessons, chest, review] })),
    )
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="next-goal"]').text()).toContain('2 lessons to go.')
  })

  it('says the goal is reached, and leaves out the mistakes block when none are waiting', async () => {
    api.today.mockResolvedValue(today({ todayXp: 30, goalReached: true, mistakesWaiting: 0 }))
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="daily-goal"] .learn-goal-hint').text()).toBe('Goal reached')
    expect(wrapper.find('[data-testid="practise-card"]').exists()).toBe(false)
  })

  it('points to an open checkpoint when no lesson is left before it', async () => {
    const path = learnPath()
    const [level1, ...rest] = path.levels
    const units = level1!.units.map((unit) => ({
      ...unit,
      state: 'DONE' as const,
      completedCount: unit.lessonCount,
      lessons: unit.lessons.map((stop) => ({ ...stop, state: 'DONE' as const, current: false })),
    }))
    api.path.mockResolvedValue({
      ...path,
      nextLesson: null,
      levels: [{ ...level1!, units, checkpoint: { ...level1!.checkpoint!, state: 'AVAILABLE' as const } }, ...rest],
    })
    const wrapper = await mountPage()

    const goal = wrapper.get('[data-testid="next-goal"]')
    expect(goal.text()).toContain('HSK 1 checkpoint')
    expect(goal.text()).toContain('Pass it with 70% or more to open HSK 2.')
    expect(wrapper.get('[data-testid="checkpoint-start"]').attributes('href')).toBe('/student/learn/lessons/90')
    expect(wrapper.get('[data-testid="checkpoint-start"]').text()).toBe('Start · +50 XP')
    expect(wrapper.get('[data-testid="checkpoint"]').classes()).toContain('is-available')
  })

  it('aims at the end of the path, not a checkpoint, in the last unit of a level without one', async () => {
    const path = learnPath()
    const [level1, level2] = path.levels
    const unit3 = {
      ...level2!.units[0]!,
      state: 'ACTIVE' as const,
      completedCount: 0,
      lessonCount: 2,
      lessons: [
        pathStop(31, 1, 'Tickets', { state: 'AVAILABLE', current: true }),
        pathStop(32, 2, 'Unit 3 review', { kind: 'REVIEW', unlockAfter: 'Tickets' }),
      ],
    }
    api.path.mockResolvedValue({
      ...path,
      nextLesson: { unitId: 3, xpReward: 10 },
      levels: [
        { ...level1!, checkpoint: { ...level1!.checkpoint!, state: 'DONE' as const } },
        { ...level2!, locked: false, units: [unit3], checkpoint: null },
      ],
    })
    const wrapper = await mountPage()

    const goal = wrapper.get('[data-testid="next-goal"]')
    expect(goal.text()).toContain('Finish the path')
    expect(goal.text()).toContain('2 lessons to go.')
    expect(goal.text()).not.toContain('checkpoint')
    expect(wrapper.get('.learn-path-header h2').text()).toBe('HSK 2 (Survival)')
  })

  it('opens the unit guidebook with its key phrases and tips', async () => {
    api.guidebook.mockResolvedValue({
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
    expect(drawer.find('.hanzi-tile').exists()).toBe(false)
  })

  it('shows the map’s own skeleton while the path loads, with nothing to press', async () => {
    api.path.mockReturnValue(new Promise(() => {}))
    const wrapper = await mountPage()

    const loading = wrapper.get('[data-testid="learn-loading"]')
    expect(loading.get('[role="status"]').text()).toBe('Loading your learning path')
    expect(loading.findAll('.learn-level-placeholder')).toHaveLength(6)
    expect(loading.findAll('button')).toHaveLength(0)
  })

  it('says in a toast what the lesson just finished earned and unlocked, once, and pops the new level in', async () => {
    window.history.replaceState(
      { lessonResult: lessonResult({ nextLesson: { title: 'Your name', unlockedNow: true } }) },
      '',
    )
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Lesson complete: +10 XP. “Your name” is unlocked.')
    expect(wrapper.get('[data-testid="level-13"]').element.closest('.learn-level')!.classList).toContain('is-new')
    expect((window.history.state as { lessonResult?: unknown }).lessonResult).toBeUndefined()
    // A toast, not a banner above the map: the map keeps its height.
    expect(wrapper.find('.data-notice').exists()).toBe(false)
  })

  it('does not call a checkpoint that was not passed done', async () => {
    window.history.replaceState(
      {
        lessonResult: lessonResult({
          kind: 'CHECKPOINT',
          passed: false,
          passPercent: 70,
          accuracy: 40,
          nextLesson: null,
          xp: { ...lessonResult().xp, xpEarned: 0, newBadges: [] },
        }),
      },
      '',
    )
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    expect((window.history.state as { lessonResult?: unknown }).lessonResult).toBeUndefined()
  })

  it('announces a passed checkpoint as passed', async () => {
    window.history.replaceState(
      {
        lessonResult: lessonResult({
          kind: 'CHECKPOINT',
          passPercent: 70,
          nextLesson: null,
        }),
      },
      '',
    )
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Checkpoint passed: +10 XP.')
  })
})
