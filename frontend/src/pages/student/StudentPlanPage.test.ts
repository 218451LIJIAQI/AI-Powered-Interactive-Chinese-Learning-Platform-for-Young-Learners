import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { learningPlan, planOptions, planRouter } from '@/test/planFixtures'

const api = vi.hoisted(() => ({ plan: vi.fn(), options: vi.fn(), updateGoal: vi.fn(), report: vi.fn() }))

vi.mock('@/api/plan', () => ({
  planApi: { plan: api.plan, options: api.options, updateGoal: api.updateGoal },
}))
vi.mock('@/api/ai', () => ({ aiHelperApi: { report: api.report } }))

import StudentPlanPage from './StudentPlanPage.vue'

async function mountPage() {
  const router = await planRouter('/student/progress/plan')
  const wrapper = mount(StudentPlanPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('StudentPlanPage', () => {
  beforeEach(() => {
    api.plan.mockResolvedValue(learningPlan())
    api.options.mockResolvedValue(planOptions())
  })

  it('shows the goal, level, progress, this week and the path with each step’s status and action', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('My learning plan')
    expect(wrapper.text()).toContain('Travel to Shanghai · by')
    expect(wrapper.text()).toContain('week 2 of 8')
    // The week line: week 1 done, week 2 now, weeks 3–5 to come, weeks 6–8 for review.
    const weeks = wrapper.findAll('[data-testid="plan-weekline"] .plan-weekline-track li')
    expect(weeks.map((week) => week.classes()[0])).toEqual([
      'is-done',
      'is-now',
      'is-later',
      'is-later',
      'is-later',
      'is-review',
      'is-review',
      'is-review',
    ])
    expect(weeks[1].text()).toBe('Week 2: this week')
    const stats = wrapper.get('.plan-stats').text()
    // Placed at the target level: the level once, not "Beginner 1 → Beginner 1".
    expect(stats).toContain('Mandarin level Beginner 1Head start')
    expect(stats).toContain('Head start, from your level check on')
    expect(stats).toContain('18%')
    expect(stats).toContain('3 of 5 days')
    expect(stats).toContain('On track')
    expect(stats).toContain('15 min · 5 days')
    expect(stats).toContain('Daily goal 30 XP')

    const path = wrapper.get('[data-testid="plan-path"]')
    expect(path.text()).toContain('You are here · week 2')
    expect(path.text()).toContain('4 lessons and 4 practice sets done so far')
    const unit = wrapper.get('[data-testid="plan-step-1"]')
    expect(unit.text()).toContain('In progress · 2 of 7')
    expect(unit.text()).toContain('Next lesson: Introduce yourself')
    expect(unit.get('a').attributes('href')).toBe('/student/learn/lessons/103')
    const joined = wrapper.get('[data-testid="plan-step-2"]')
    expect(joined.text()).toContain('In My classes')
    expect(joined.text()).toContain('22%')
    expect(joined.get('a').text()).toBe('Open class')
    // A step built from rules explains itself with the goals it matches.
    expect(wrapper.get('[data-testid="plan-step-3"]').text()).toContain('Why: matches your goal: Order food.')
    expect(wrapper.get('[data-testid="plan-step-4"]').get('a').text()).toBe('View class')

    const tasks = wrapper.get('[data-testid="plan-tasks"]')
    expect(tasks.findAll('li.is-done')).toHaveLength(2)
    expect(tasks.text()).toContain('Listening set: At the Café')
    expect(wrapper.get('[data-testid="plan-coverage"]').text()).toContain(
      'Have short chats: the optional class Everyday Conversations, or Talk with Xiaomei.',
    )
    expect(wrapper.get('[data-testid="plan-ai-note"]').text()).toContain('AI-assisted.')
  })

  it('rebuilds the plan from a new goal and keeps the progress', async () => {
    api.updateGoal.mockResolvedValue(
      learningPlan({ goal: { ...learningPlan().goal, reason: 'WORK', title: 'Chat with customers' } }),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('[data-testid="update-goal-drawer"]')
    expect(drawer.text()).toContain('Your progress stays: 4 lessons, 4 practice sets and 1 class.')
    await drawer.get('[data-testid="reason-choices"] [data-value="WORK"]').trigger('click')
    await drawer.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()

    expect(api.updateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ reason: 'WORK', pace: 'REGULAR', deadline: '2026-07-05' }),
      'en',
    )
    expect(wrapper.find('[data-testid="update-goal-drawer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Chat with customers')
    expect(wrapper.get('[data-testid="toast"]').text()).toContain('Plan updated. Your progress is kept.')
  })

  it('says when the plan was built from rules', async () => {
    api.plan.mockResolvedValue(
      learningPlan({ aiAssisted: false, goal: { ...learningPlan().goal, title: null, summary: null }, outcomes: [] }),
    )
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Travel · by')
    expect(wrapper.get('[data-testid="plan-ai-note"]').text()).toContain('Built from your answers')
    expect(wrapper.find('[data-testid="plan-report"]').exists()).toBe(false)
    // Outcomes come from the goals.
    expect(wrapper.get('[data-testid="plan-outcomes"]').text()).toContain('Have a short everyday chat')
  })

  it('offers to build a plan when there is none', async () => {
    api.plan.mockResolvedValue(null)
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('You don’t have a learning plan yet')
    expect(wrapper.find('[data-testid="update-goal"]').exists()).toBe(false)
  })
})
