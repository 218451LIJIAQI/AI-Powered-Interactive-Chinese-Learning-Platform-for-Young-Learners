import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { mountAtRoute } from '@/test/routes'
import { deferred } from '@/test/helpers'
import { progressOverview } from '@/test/learnFixtures'
import { planSummary } from '@/test/planFixtures'

const api = vi.hoisted(() => ({
  progress: vi.fn(),
  skills: vi.fn(),
  answers: vi.fn(),
  overview: vi.fn(),
}))

vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  xpApi: { progress: api.progress, progressSkills: api.skills, progressAnswers: api.answers },
}))
vi.mock('@/api/plan', () => ({ planApi: { overview: api.overview } }))

import StudentProgressPage from './StudentProgressPage.vue'

async function mountPage() {
  return (await mountAtRoute(StudentProgressPage, '/student/progress')).wrapper
}

describe('StudentProgressPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.progress.mockResolvedValue(progressOverview())
    api.answers.mockResolvedValue({
      sureAndRight: 0,
      luckyGuesses: 0,
      sureButWrong: 0,
      wrongGuesses: 0,
      hintsUsed: 0,
      mostHintsIn: null,
    })
    api.skills.mockResolvedValue({
      skills: [
        { skill: 'VOCABULARY', percent: 72, weeklyChange: 4 },
        { skill: 'LISTENING', percent: 45, weeklyChange: 2 },
        { skill: 'CHARACTERS', percent: 58, weeklyChange: 0 },
        { skill: 'SENTENCES', percent: 63, weeklyChange: 3 },
      ],
      weakest: 'LISTENING',
    })
    api.overview.mockResolvedValue({ plan: planSummary() })
  })

  it('answers “Am I improving?”: skills first, with the weakest to practise', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').classes()).toContain('sr-only')
    const skills = wrapper.get('[data-testid="skills"]')
    expect(skills.findAll('.progress-skill')).toHaveLength(4)
    expect(skills.text()).toContain('+4 this week')
    expect(skills.text()).toContain('72%')
    expect(skills.text()).toContain('Weakest right now: Listening.')
    const practise = skills.get('.progress-skills-foot a')
    expect(practise.text()).toBe('Practise listening')
    expect(practise.attributes('href')).toBe('/student/practice/listening')
  })

  it('says skills start after the first lesson, and leads to it', async () => {
    api.skills.mockResolvedValue({
      skills: [
        { skill: 'VOCABULARY', percent: 0, weeklyChange: 0 },
        { skill: 'LISTENING', percent: 0, weeklyChange: 0 },
        { skill: 'CHARACTERS', percent: 0, weeklyChange: 0 },
        { skill: 'SENTENCES', percent: 0, weeklyChange: 0 },
      ],
      weakest: null,
    })
    const wrapper = await mountPage()

    const skills = wrapper.get('[data-testid="skills"]')
    expect(skills.text()).toContain('Starts after your first lesson')
    expect(skills.text()).toContain('Not started')
    expect(skills.get('.progress-skills-foot a').text()).toBe('Start Unit 1')
  })

  it('keeps rank and streak visible while skills load and retries the skills request on its own', async () => {
    const pending = deferred<never>()
    api.skills.mockReturnValueOnce(pending.promise)
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="xp-level"]').text()).toContain('Rank 3')
    expect(wrapper.get('[data-testid="streak"]').text()).toContain('Best: 9 days')
    expect(wrapper.get('[data-testid="skills"]').text()).not.toContain('Starts after your first lesson')
    pending.reject(new Error('offline'))
    await flushPromises()
    await wrapper.get('[data-testid="skills"] .state-panel button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="skills"]').text()).toContain('Weakest right now: Listening.')
    expect(api.progress).toHaveBeenCalledTimes(1)
  })

  it('shows the rank with the XP still needed to the next one, and the streak', async () => {
    const wrapper = await mountPage()

    expect(api.progress).toHaveBeenCalledWith(null)
    const level = wrapper.get('[data-testid="xp-level"]')
    expect(level.get('.stat-figure').text()).toBe('3')
    expect(level.get('.progress-level-title').text()).toBe('Rank 3 · Regular')
    expect(level.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('40')
    expect(level.get('.progress-level-next').text()).toBe('60 XP to Rank 4 · Committed')
    // One line under the bar: no "240 of 300 XP" caption and no row of rank steps.
    expect(level.text()).not.toContain('240 of 300')
    expect(level.findAll('p').map((line) => line.text())).toEqual(['Rank 3 · Regular', '60 XP to Rank 4 · Committed'])
    expect(level.findAll('ol, ul')).toHaveLength(0)

    const streak = wrapper.get('[data-testid="streak"]')
    expect(streak.text()).toContain('7')
    expect(streak.text()).toContain('Best: 9 days')
  })

  it('keeps skills available when the rank and calendar fail to load', async () => {
    api.progress.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="skills"]').text()).toContain('Weakest right now: Listening.')
    await wrapper.get('.progress-row > .state-panel button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="xp-level"]').text()).toContain('Rank 3')
    expect(api.skills).toHaveBeenCalledOnce()
  })

  it('names every day of the calendar for screen readers, not only by colour', async () => {
    const wrapper = await mountPage()

    const days = wrapper.findAll('.progress-calendar-grid .progress-day')
    expect(days).toHaveLength(31)
    const labels = days.map((day) => day.attributes('aria-label'))
    expect(labels.filter((label) => label?.endsWith('daily goal reached'))).toHaveLength(7)
    expect(labels.some((label) => label?.includes('today'))).toBe(true)
    expect(labels.filter((label) => label?.endsWith('missed')).length).toBeGreaterThan(0)
    // Every reached goal is filled; today has a ring until it is.
    expect(wrapper.findAll('.progress-calendar-grid .progress-day.is-learned')).toHaveLength(7)
    expect(wrapper.findAll('.progress-calendar-grid .progress-day.is-today')).toHaveLength(1)
  })

  it('loads another month from the calendar arrows', async () => {
    api.progress.mockResolvedValue(
      progressOverview({ calendar: { ...progressOverview().calendar, previousMonth: '2026-04' } }),
    )
    const wrapper = await mountPage()

    await wrapper.get('button[aria-label="Previous month"]').trigger('click')
    await flushPromises()

    expect(api.progress).toHaveBeenLastCalledWith('2026-04')
  })

  it('keeps rank and calendar mounted while another month loads and shows an inline error on failure', async () => {
    const april = deferred<ReturnType<typeof progressOverview>>()
    api.progress
      .mockResolvedValueOnce(
        progressOverview({ calendar: { ...progressOverview().calendar, previousMonth: '2026-04' } }),
      )
      .mockReturnValueOnce(april.promise)
    const wrapper = await mountPage()

    await wrapper.get('button[aria-label="Previous month"]').trigger('click')
    expect(wrapper.find('[data-testid="xp-level"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="streak"]').exists()).toBe(true)

    april.reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.find('[data-testid="xp-level"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="streak"] [role="alert"]').text()).toBeTruthy()
  })

  it('marks the streak card busy while another month loads and ignores duplicate clicks for the same month', async () => {
    const april = deferred<ReturnType<typeof progressOverview>>()
    api.progress
      .mockResolvedValueOnce(
        progressOverview({ calendar: { ...progressOverview().calendar, previousMonth: '2026-04' } }),
      )
      .mockReturnValueOnce(april.promise)
    const wrapper = await mountPage()

    const prev = wrapper.get('button[aria-label="Previous month"]')
    await prev.trigger('click')
    await prev.trigger('click')
    expect(api.progress).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="streak"]').attributes('aria-busy')).toBe('true')

    april.resolve(progressOverview({ calendar: { ...progressOverview().calendar, month: '2026-04' } }))
    await flushPromises()
    expect(wrapper.get('[data-testid="streak"]').attributes('aria-busy')).toBeUndefined()
  })

  it('fills earned badges and leaves the rest dashed, and opens a badge to show how it is earned', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="badge-FIRST_STEPS"]').classes()).toContain('is-earned')
    expect(wrapper.get('[data-testid="badge-XP_STARTER"]').classes()).not.toContain('is-earned')
    // Line icons, not characters.
    expect(wrapper.get('[data-testid="badges"]').find('.seal').exists()).toBe(false)

    // The badges show themselves; no "2 of 8 earned" count above them.
    expect(wrapper.get('[data-testid="badges"]').text()).not.toContain('of 8')
    await wrapper.get('[data-testid="badge-XP_STARTER"]').trigger('click')

    const detail = wrapper.get('[data-testid="badge-detail"]')
    expect(detail.text()).toContain('XP Starter')
    expect(detail.text()).toContain('Not earned yet')
    expect(detail.text()).toContain('How to earn it')
  })

  it('says when an earned badge was earned', async () => {
    const base = progressOverview()
    api.progress.mockResolvedValue({
      ...base,
      badges: base.badges.map((badge) =>
        badge.key === 'FIRST_STEPS' ? { ...badge, earnedAt: '2026-05-11T12:00:00' } : badge,
      ),
    })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="badge-FIRST_STEPS"]').trigger('click')

    const detail = wrapper.get('[data-testid="badge-detail"]')
    expect(detail.text()).toContain('Earned 11 May 2026')
    expect(detail.text()).toContain('How you earned it')
    expect(detail.text()).not.toContain('Not earned yet')
  })

  it('starts with the learning plan: goal, date and week, Mandarin level and plan progress', async () => {
    const wrapper = await mountPage()

    const card = wrapper.get('[data-testid="plan-card"]')
    expect(card.text()).toContain('Travel to Shanghai')
    expect(card.get('[data-testid="plan-card-when"]').text()).toBe('by Sat 18 Jul 2026 · week 2 of 10')
    expect(card.text()).toContain('Mandarin level')
    expect(card.get('.plan-strip-value').text()).toBe('HSK 1 → HSK 2')
    expect(card.text()).toContain('Plan progress · 18%')
    expect(card.text()).toContain('On track this week')
    expect(card.get('a').attributes('href')).toBe('/student/progress/plan')
  })

  it.each([
    [{ milestoneDate: '2026-06-01', week: 1, totalWeeks: 2 }, 'first milestone by Mon 1 Jun 2026 · week 1 of 2'],
    [{ deadline: null }, 'week 2 of 10'],
  ])('names the first milestone, or the week alone without a date: %j', async (overrides, line) => {
    api.overview.mockResolvedValue({ plan: planSummary(overrides) })
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="plan-card-when"]').text()).toBe(line)
  })

  it('shows that an unfinished plan whose goal date has passed has ended', async () => {
    api.overview.mockResolvedValue({
      plan: planSummary({ deadline: '2026-05-10', today: '2026-05-18', status: 'ON_TRACK' }),
    })
    const wrapper = await mountPage()

    const card = wrapper.get('[data-testid="plan-card"]')
    expect(card.get('[data-testid="plan-card-when"]').text()).toBe('was due Sun 10 May 2026 · plan ended')
    expect(card.text()).toContain('Plan ended')
    expect(card.text()).not.toContain('On track this week')
  })

  it('offers to build a plan when the student has none', async () => {
    api.overview.mockResolvedValue({ plan: null })
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="plan-card"]').text()).toContain('Build your learning plan')
  })
})
