import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { learnRouter, progressOverview } from '@/test/learnFixtures'
import { planSummary } from '@/test/planFixtures'

const api = vi.hoisted(() => ({ progress: vi.fn(), overview: vi.fn() }))

vi.mock('@/api/learn', () => ({ xpApi: { progress: api.progress } }))
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
    api.overview.mockResolvedValue({ setupState: 'COMPLETED', plan: planSummary() })
  })

  it('shows the XP level with the XP still needed, the streak and where the XP came from', async () => {
    const wrapper = await mountPage()

    expect(api.progress).toHaveBeenCalledWith(null)
    const level = wrapper.get('[data-testid="xp-level"]')
    expect(level.text()).toContain('240 of 300 XP')
    expect(level.text()).toContain('60 XP to Level 4')
    expect(level.findAll('li.is-reached')).toHaveLength(3)
    expect(level.get('li.is-you').text()).toContain('You')

    const streak = wrapper.get('[data-testid="streak"]')
    expect(streak.text()).toContain('7')
    expect(streak.text()).toContain('Best: 9 days · reach today’s goal to make it 8')

    const sources = wrapper.get('[data-testid="xp-sources"]')
    expect(sources.text()).toContain('120 XP')
    expect(sources.text()).toContain('Learn · Polite phrases')
    expect(sources.text()).toContain('Daily goal reached')
  })

  it('names every day of the calendar for screen readers, not only by colour', async () => {
    const wrapper = await mountPage()

    const days = wrapper.findAll('.progress-calendar-grid .progress-day')
    expect(days).toHaveLength(31)
    const labels = days.map((day) => day.attributes('aria-label'))
    expect(labels.filter((label) => label?.endsWith('daily goal reached'))).toHaveLength(7)
    expect(labels.some((label) => label?.includes('today'))).toBe(true)
    expect(labels.filter((label) => label?.endsWith('missed')).length).toBeGreaterThan(0)
    // Every reached goal is stamped with a seal; today, still open, is the outlined seal.
    expect(wrapper.findAll('.progress-calendar-grid .progress-day.seal:not(.is-outline)')).toHaveLength(7)
    expect(wrapper.find('.progress-calendar-grid .progress-day.is-today').classes()).toContain('is-outline')
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

  it('stamps earned badges and greys the rest, and opens a badge to show how it is earned', async () => {
    const wrapper = await mountPage()
    const earned = wrapper.get('[data-testid="badge-FIRST_STEPS"] .seal')
    expect(earned.text()).toBe('始')
    expect(earned.classes()).not.toContain('is-muted')
    expect(wrapper.get('[data-testid="badge-XP_STARTER"] .seal').classes()).toContain('is-muted')

    expect(wrapper.get('[data-testid="badges"]').text()).toContain('2 of 8 earned')
    await wrapper.get('[data-testid="badge-XP_STARTER"]').trigger('click')

    const detail = wrapper.get('[data-testid="badge-detail"]')
    expect(detail.text()).toContain('XP Starter')
    expect(detail.text()).toContain('Not earned yet')
    expect(detail.text()).toContain('How to earn it')
  })

  it('ends with the learning plan: goal, Mandarin level apart from XP, and plan progress', async () => {
    const wrapper = await mountPage()

    const card = wrapper.get('[data-testid="plan-card"]')
    expect(card.text()).toContain('Travel to Shanghai')
    expect(card.text()).toContain('week 2 of 8')
    expect(card.text()).toContain('From your level check · separate from XP')
    expect(card.text()).toContain('Plan progress · 18%')
    expect(card.get('[data-testid="level-ladder"] li.is-you').text()).toContain('Beginner 1')
  })

  it('offers to build a plan when the student has none', async () => {
    api.overview.mockResolvedValue({ setupState: 'SKIPPED', plan: null })
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="plan-card"]').text()).toContain('Build your learning plan')
  })
})
