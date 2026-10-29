import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { checkDone, checkStep, learningPlan, planOptions, planRouter } from '@/test/planFixtures'

const api = vi.hoisted(() => ({
  options: vi.fn(),
  build: vi.fn(),
  updateGoal: vi.fn(),
  skip: vi.fn(),
  next: vi.fn(),
}))

vi.mock('@/api/plan', () => ({
  planApi: { options: api.options, build: api.build, updateGoal: api.updateGoal, skip: api.skip },
}))
vi.mock('@/api/learn', () => ({ levelCheckApi: { next: api.next } }))

import StudentSetupPage from './StudentSetupPage.vue'

async function mountPage() {
  const router = await planRouter('/student/setup')
  const wrapper = mount(StudentSetupPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}

async function primary(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[data-testid="setup-primary"]').trigger('click')
  await flushPromises()
}

async function pick(wrapper: VueWrapper, group: string, value: string): Promise<void> {
  await wrapper.get(`[data-testid="${group}"] [data-value="${value}"]`).trigger('click')
}

/** Welcome → why (travel) → goal (order food, own words) → pace (2 months, regular) → how much Mandarin. */
async function answerTheGoal(wrapper: VueWrapper): Promise<void> {
  await primary(wrapper)
  await pick(wrapper, 'reason-choices', 'TRAVEL')
  expect(wrapper.text()).toContain('Travel it is!')
  await primary(wrapper)
  await pick(wrapper, 'goal-choices', 'ORDER_FOOD')
  await wrapper.get('textarea').setValue('I am going to Shanghai in July.')
  expect(wrapper.text()).toContain('31 / 200')
  await primary(wrapper)
  expect(wrapper.get('[data-testid="setup-primary"]').attributes('disabled')).toBeDefined()
  await pick(wrapper, 'deadline-choices', '2m')
  await pick(wrapper, 'pace-choices', 'REGULAR')
  expect(wrapper.text()).toContain('Regular sets your daily goal to 20 XP.')
  await primary(wrapper)
}

describe('StudentSetupPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    api.options.mockResolvedValue(planOptions())
    api.build.mockResolvedValue(learningPlan())
    api.skip.mockResolvedValue({ setupState: 'SKIPPED', plan: null })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('walks from the goal through the level check to the plan and its path', async () => {
    api.next.mockResolvedValueOnce(checkStep()).mockResolvedValueOnce(checkDone())
    const { wrapper, router } = await mountPage()

    await answerTheGoal(wrapper)
    await pick(wrapper, 'self-level-choices', 'A_LITTLE')
    await primary(wrapper)

    // The level check starts at Beginner 1 for "A little" and gives friendly feedback.
    expect(api.next).toHaveBeenCalledWith('BEGINNER_1', [])
    await wrapper.get('[data-testid="option-1"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Nice!')
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="level-check-done"]').text()).toContain('You answered 3 questions')
    await primary(wrapper)

    // Focus: listening is suggested because it was new in the check.
    expect(wrapper.text()).toContain('Suggested from your level check: Listening was new to you.')
    await pick(wrapper, 'focus-choices', 'LISTENING')
    await primary(wrapper)
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(api.build).toHaveBeenCalledOnce()
    const payload = api.build.mock.calls[0][0]
    expect(payload.goal).toMatchObject({
      reason: 'TRAVEL',
      goals: ['ORDER_FOOD'],
      goalText: 'I am going to Shanghai in July.',
      pace: 'REGULAR',
      focus: ['LISTENING'],
    })
    expect(payload.goal.deadline).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(payload.levelCheck).toEqual({
      selfLevel: 'A_LITTLE',
      skipped: false,
      answers: [{ questionId: 1, optionId: 12 }],
    })

    const result = wrapper.get('[data-testid="plan-result"]')
    expect(result.text()).toContain('Travel to Shanghai')
    expect(result.text()).toContain('AI-assisted summary of what you wrote')
    await primary(wrapper)
    expect(wrapper.get('[data-testid="plan-path"]').text()).toContain('Learn · Unit 1 · Say hello')
    expect(wrapper.get('[data-testid="plan-coverage"]').text()).toContain('Learn covers 2 of your 3 goals.')
    await primary(wrapper)
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('lets a brand-new student skip the check and builds a plan from the first lesson', async () => {
    const { wrapper } = await mountPage()

    await answerTheGoal(wrapper)
    await wrapper.get('[data-testid="skip-check"]').trigger('click')
    await primary(wrapper)
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(api.next).not.toHaveBeenCalled()
    expect(api.build.mock.calls[0][0].levelCheck).toEqual({ selfLevel: 'NEW_TO_MANDARIN', skipped: true, answers: [] })
  })

  it('rebuilds the plan with an edited goal and keeps the level', async () => {
    api.updateGoal.mockResolvedValue(learningPlan({ goal: { ...learningPlan().goal, title: 'Eat well in Shanghai' } }))
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)
    await wrapper.get('[data-testid="skip-check"]').trigger('click')
    await primary(wrapper)
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    const dialog = wrapper.get('[data-testid="edit-goal-dialog"]')
    await dialog.get('[data-testid="goal-choices"] [data-value="ASK_DIRECTIONS"]').trigger('click')
    await dialog.findAll('button').at(-1)!.trigger('click')
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(api.updateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ goals: ['ORDER_FOOD', 'ASK_DIRECTIONS'] }),
      'en',
    )
    expect(wrapper.get('[data-testid="plan-result"]').text()).toContain('Eat well in Shanghai')
  })

  it('sets the plan up later from the welcome screen, after asking', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Set up your plan later?')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.skip).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('explains a failed build and builds again', async () => {
    api.build.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)
    await wrapper.get('[data-testid="skip-check"]').trigger('click')
    await primary(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('We couldn’t build your plan')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Try again')!
      .trigger('click')
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(api.build).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="plan-result"]').exists()).toBe(true)
  })
})
