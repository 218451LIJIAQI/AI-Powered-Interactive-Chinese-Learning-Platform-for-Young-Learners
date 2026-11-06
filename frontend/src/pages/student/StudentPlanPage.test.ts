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
    window.localStorage.removeItem('mandarinspark.planClassPick')
    api.plan.mockResolvedValue(learningPlan())
    api.options.mockResolvedValue(planOptions())
  })

  it('shows the goal, the stats, this week and the road from You to the goal', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('My learning plan')
    expect(wrapper.text()).toContain('Travel to Shanghai · by')
    expect(wrapper.text()).toContain('week 2 of 8')
    const stats = wrapper.get('.plan-stats').text()
    // Placed at the target level: the level once, not "Beginner 1 → Beginner 1".
    expect(stats).toContain('Beginner 1')
    expect(stats).not.toContain('→')
    expect(stats).toContain('18%')
    expect(stats).toContain('3 of 5 days')
    expect(stats).toContain('On track')
    expect(stats).toContain('15 min · 5 days')
    expect(stats).toContain('Daily goal 30 XP')

    const path = wrapper.get('[data-testid="plan-path"]')
    expect(path.text()).toContain('You are here · week 2')
    expect(path.text()).toContain('4 lessons and 4 practice sets done so far')
    expect(path.text()).toContain('Your goal · Travel to Shanghai')
    const unit = wrapper.get('[data-testid="plan-step-1"]')
    expect(unit.text()).toContain('In progress · 2 of 7')
    expect(unit.text()).toContain('Next lesson: Introduce yourself · 4 min')
    expect(unit.get('a').attributes('href')).toBe('/student/learn/lessons/103')
    const joined = wrapper.get('[data-testid="plan-step-2"]')
    expect(joined.text()).toContain('Your class · alongside Learn')
    expect(joined.text()).toContain('22%')
    expect(joined.get('a').text()).toBe('Open class')
    // Later units stay in the list for screen readers; a step built from rules explains itself with its goals.
    const later = wrapper.get('[data-testid="plan-step-3"]')
    expect(later.classes()).toContain('sr-only')
    expect(later.text()).toContain('Why: matches your goal: Order food.')
    // One optional class: no fork and nothing to pick, only the way to it.
    const single = wrapper.get('[data-testid="plan-step-4"]')
    expect(single.classes()).toContain('is-center')
    expect(single.get('a').text()).toBe('View class')
    expect(wrapper.find('[data-testid^="pick-class-"]').exists()).toBe(false)

    const tasks = wrapper.get('[data-testid="plan-tasks"]')
    expect(tasks.findAll('li.is-done')).toHaveLength(2)
    expect(tasks.text()).toContain('Listening set: At the Café')
    expect(wrapper.get('[data-testid="plan-ai-note"]').text()).toContain('AI-assisted.')
    expect(wrapper.find('[data-testid="plan-report"]').exists()).toBe(true)
  })

  it('draws a fork for two optional classes and remembers which one the student picks first', async () => {
    const base = learningPlan()
    const second = { ...base.steps[3]!, position: 5, refId: 23, title: 'Shopping and Prices' }
    api.plan.mockResolvedValue(learningPlan({ steps: [...base.steps, second] }))
    const wrapper = await mountPage()

    const path = wrapper.get('[data-testid="plan-path"]')
    expect(path.text()).toContain('Then: pick a class for your trip')
    const left = wrapper.get('[data-testid="plan-step-4"]')
    const right = wrapper.get('[data-testid="plan-step-5"]')
    expect(left.classes()).toContain('is-left')
    expect(right.classes()).toContain('is-right')
    expect(left.text()).toContain('Class · best after Unit 2')

    const pick = wrapper.get('[data-testid="pick-class-23"]')
    expect(pick.attributes('aria-pressed')).toBe('false')
    await pick.trigger('click')
    expect(pick.attributes('aria-pressed')).toBe('true')
    expect(pick.text()).toBe('Picked · comes first')
    expect(path.text()).toContain('Shopping and Prices first, then Everyday Conversations.')
    expect(right.text()).toContain('Class · after Unit 2 · 1st')
    expect(left.text()).toContain('Class · after Unit 2 · 2nd')
    expect(window.localStorage.getItem('mandarinspark.planClassPick')).toBe('23')

    // A second click takes it back.
    await pick.trigger('click')
    expect(pick.attributes('aria-pressed')).toBe('false')
    expect(window.localStorage.getItem('mandarinspark.planClassPick')).toBeNull()
  })

  it('rebuilds the plan from a new goal, keeps the progress and says so', async () => {
    api.updateGoal.mockResolvedValue(
      learningPlan({ goal: { ...learningPlan().goal, reason: 'WORK', title: 'Chat with customers' } }),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('[data-testid="update-goal-drawer"]')
    expect(drawer.text()).toContain('Your progress and level stay.')
    // A focus the form no longer offers does not take up one of the two picks.
    expect(drawer.findAll('[data-testid="focus-choices"] [aria-pressed="true"]')).toHaveLength(1)
    await drawer.get('[data-testid="reason-choices"] [data-value="WORK"]').trigger('click')
    await drawer.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()

    expect(api.updateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ reason: 'WORK', pace: 'REGULAR', deadline: '2026-07-05' }),
      'en',
    )
    expect(wrapper.find('[data-testid="update-goal-drawer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Chat with customers')
    expect(wrapper.get('[data-testid="plan-updated"]').text()).toContain('Plan updated. Your progress is kept.')
  })

  it('shows the rebuild in progress in the drawer footer', async () => {
    let finish: (plan: ReturnType<typeof learningPlan>) => void = () => {}
    api.updateGoal.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('[data-testid="update-goal-drawer"]')
    await drawer.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()

    const rebuild = wrapper.get('[data-testid="rebuild"]')
    expect(rebuild.text()).toBe('Rebuilding…')
    expect(rebuild.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="update-goal-drawer"]').text()).toContain('SparkBot is reading your new goal…')
    finish(learningPlan())
    await flushPromises()
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
