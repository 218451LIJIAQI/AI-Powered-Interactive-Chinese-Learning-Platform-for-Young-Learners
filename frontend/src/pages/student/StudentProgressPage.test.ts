import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { learnRouter, progressOverview } from '@/test/learnFixtures'
import { planSummary } from '@/test/planFixtures'

const api = vi.hoisted(() => ({ progress: vi.fn(), skills: vi.fn(), overview: vi.fn() }))

vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  xpApi: { progress: api.progress, progressSkills: api.skills },
}))
vi.mock('@/api/plan', () => ({ planApi: { overview: api.overview } }))

import StudentProgressPage from './StudentProgressPage.vue'

async function mountPage() {
  const router = await learnRouter('/student/progress')
  const wrapper = mount(StudentProgressPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentProgressPage', () => {
  beforeEach(() => {
    api.progress.mockResolvedValue(progressOverview())
    api.skills.mockResolvedValue({
      skills: [
        { skill: 'VOCABULARY', percent: 72, weeklyChange: 4 },
        { skill: 'LISTENING', percent: 58, weeklyChange: 2 },
        { skill: 'CHARACTERS', percent: 45, weeklyChange: 0 },
        { skill: 'SENTENCES', percent: 63, weeklyChange: 3 },
      ],
      weakest: 'LISTENING',
    })
    api.overview.mockResolvedValue({ setupState: 'COMPLETED', plan: planSummary() })
  })

  it('answers “Am I improving?”: skills first, with the weakest to practise', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').classes()).toContain('sr-only')
    const skills = wrapper.get('[data-testid="skills"]')
    expect(skills.findAll('.progress-skill')).toHaveLength(4)
    expect(skills.text()).toContain('+4 this week')
    expect(skills.text()).toContain('72%')
    expect(skills.text()).toContain('Weakest right now: Listening.')
    const practise = skills.get('a')
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
    expect(skills.get('a').text()).toBe('Start Unit 1')
  })

  it('shows the XP level with the XP still needed, and the streak', async () => {
    const wrapper = await mountPage()

    expect(api.progress).toHaveBeenCalledWith(null)
    const level = wrapper.get('[data-testid="xp-level"]')
    expect(level.text()).toContain('240 of 300 XP')
    expect(level.text()).toContain('60 XP to Level 4')
    expect(level.findAll('.progress-level-steps li')).toHaveLength(5)
    expect(level.findAll('.progress-level-steps li.is-reached')).toHaveLength(3)

    const streak = wrapper.get('[data-testid="streak"]')
    expect(streak.text()).toContain('7')
    expect(streak.text()).toContain('Best: 9 days')
    expect(wrapper.find('[data-testid="xp-sources"]').exists()).toBe(false)
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

  it('fills earned badges and leaves the rest dashed, and opens a badge to show how it is earned', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="badge-FIRST_STEPS"]').classes()).toContain('is-earned')
    expect(wrapper.get('[data-testid="badge-XP_STARTER"]').classes()).not.toContain('is-earned')
    // Line icons, not characters.
    expect(wrapper.get('[data-testid="badges"]').find('.seal').exists()).toBe(false)

    expect(wrapper.get('[data-testid="badges"]').text()).toContain('2 of 8 earned')
    await wrapper.get('[data-testid="badge-XP_STARTER"]').trigger('click')

    const detail = wrapper.get('[data-testid="badge-detail"]')
    expect(detail.text()).toContain('XP Starter')
    expect(detail.text()).toContain('Not earned yet')
    expect(detail.text()).toContain('How to earn it')
  })

  it('starts with the learning plan: goal, Mandarin level and plan progress', async () => {
    const wrapper = await mountPage()

    const card = wrapper.get('[data-testid="plan-card"]')
    expect(card.text()).toContain('Travel to Shanghai')
    expect(card.text()).toContain('week 2 of 8')
    expect(card.text()).toContain('Mandarin level')
    expect(card.text()).toContain('Beginner 1')
    expect(card.text()).toContain('Plan progress · 18%')
    expect(card.get('a').attributes('href')).toBe('/student/progress/plan')
  })

  it('offers to build a plan when the student has none', async () => {
    api.overview.mockResolvedValue({ setupState: 'SKIPPED', plan: null })
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="plan-card"]').text()).toContain('Build your learning plan')
  })
})
