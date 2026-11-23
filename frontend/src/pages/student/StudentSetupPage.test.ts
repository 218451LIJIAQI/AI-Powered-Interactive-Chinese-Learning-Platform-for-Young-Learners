import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RouterView } from 'vue-router'

import { testRouter, mountAtRoute } from '@/test/routes'
import { buttonNamed, checkChoice, deferred } from '@/test/helpers'
import { checkDone, checkStep, learningPlan, planOptions, planSummary, planWithClassChoice } from '@/test/planFixtures'

const api = vi.hoisted(() => ({
  options: vi.fn(),
  overview: vi.fn(),
  build: vi.fn(),
  updateGoal: vi.fn(),
  skip: vi.fn(),
  confirm: vi.fn(),
  pickClass: vi.fn(),
  next: vi.fn(),
  dailyGoal: vi.fn(),
  setDailyGoal: vi.fn(),
}))

vi.mock('@/api/plan', () => ({
  planApi: {
    options: api.options,
    overview: api.overview,
    build: api.build,
    updateGoal: api.updateGoal,
    skip: api.skip,
    confirm: api.confirm,
    pickClass: api.pickClass,
  },
}))
vi.mock('@/api/learn', () => ({
  levelCheckApi: { next: api.next },
  xpApi: { dailyGoal: api.dailyGoal, setDailyGoal: api.setDailyGoal },
}))

import StudentSetupPage from './StudentSetupPage.vue'

function mountPage() {
  return mountAtRoute(StudentSetupPage, '/student/setup')
}

async function primary(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[data-testid="setup-primary"]').trigger('click')
  await flushPromises()
}

async function pick(wrapper: VueWrapper, group: string, value: string): Promise<void> {
  await wrapper.get(`[data-testid="${group}"] [data-value="${value}"]`).trigger('click')
}

/** Opens the calendar from the date field and picks `day` with "Use this date". */
async function pickDate(wrapper: VueWrapper, day: string): Promise<void> {
  await wrapper.get('[data-testid="date-field"]').trigger('click')
  await wrapper.get(`[data-testid="goal-date-picker"] [data-day="${day}"]`).trigger('click')
  await wrapper.get('[data-testid="calendar-use"]').trigger('click')
}

async function chooseTravel(wrapper: VueWrapper): Promise<void> {
  await primary(wrapper)
  await pick(wrapper, 'reason-choices', 'TRAVEL')
  await primary(wrapper)
}

/** Welcome → why (travel) → goal (order food, own words) → the date and the daily time → how much Mandarin. */
async function answerTheGoal(wrapper: VueWrapper): Promise<void> {
  await chooseTravel(wrapper)
  await pick(wrapper, 'goal-choices', 'ORDER_FOOD')
  await wrapper.get('textarea').setValue('I am going to Shanghai in July.')
  expect(wrapper.text()).toContain('31 / 200')
  await primary(wrapper)
  // Neither a date nor "No date" yet: Continue waits.
  expect(wrapper.get('[data-testid="setup-primary"]').attributes('disabled')).toBeDefined()
  await pickDate(wrapper, '2026-07-18')
  await primary(wrapper)
}

async function startWithoutCheck(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[data-testid="skip-check"]').trigger('click')
  await primary(wrapper)
}

async function waitForBuildAnimation(): Promise<void> {
  await vi.advanceTimersByTimeAsync(3000)
  await flushPromises()
}

async function buildTravelPlan(wrapper: VueWrapper): Promise<void> {
  await answerTheGoal(wrapper)
  await startWithoutCheck(wrapper)
  await waitForBuildAnimation()
}

describe('StudentSetupPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] })
    for (const mock of Object.values(api)) mock.mockReset()
    api.options.mockResolvedValue(planOptions())
    api.overview.mockResolvedValue({ plan: null })
    const plan = learningPlan()
    api.build.mockResolvedValue(plan)
    api.skip.mockResolvedValue(undefined)
    api.confirm.mockResolvedValue(plan)
    api.dailyGoal.mockResolvedValue({ dailyGoalXp: 20 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('walks from the goal through the level check to the plan and its path', async () => {
    api.next.mockResolvedValueOnce(checkStep()).mockResolvedValueOnce(checkDone())
    const { wrapper, router } = await mountPage()

    await answerTheGoal(wrapper)
    await pick(wrapper, 'self-level-choices', 'BEGINNER_1')
    await primary(wrapper)

    // The level check starts at HSK 1 for "HSK 1 · Basic"; the top bar names the flow, the bar shows where it is.
    expect(api.next).toHaveBeenCalledWith('BEGINNER_1', [])
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Level check')
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Press 1–2 to choose · Enter to check')
    await checkChoice(wrapper, 1)
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Nice!')
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()

    // The headline is the result (S2.18): no repeated level below it and no scoring rules.
    const done = wrapper.get('[data-testid="level-check-done"]')
    expect(done.get('h2').text()).toBe('Your starting level: HSK 1 (Basic)')
    expect(done.text()).toBe('Your starting level: HSK 1 (Basic)')
    await primary(wrapper)

    // Focus: listening is suggested because it was new in the check.
    expect(wrapper.get('[data-testid="focus-suggested"]').text()).toBe('Suggested for your goal.')
    await pick(wrapper, 'focus-choices', 'LISTENING')
    await primary(wrapper)
    await waitForBuildAnimation()

    expect(api.build).toHaveBeenCalledOnce()
    const payload = api.build.mock.calls[0][0]
    expect(payload.goal).toEqual({
      reason: 'TRAVEL',
      reasonText: null,
      goals: ['ORDER_FOOD'],
      goalText: 'I am going to Shanghai in July.',
      deadline: '2026-07-18',
      pace: 'HOURS_1_TO_2',
      focus: ['LISTENING'],
    })
    expect(payload.levelCheck).toEqual({
      selfLevel: 'A_LITTLE',
      skipped: false,
      answers: [{ questionId: 1, optionId: 12 }],
    })

    // The result shows the level, what it means and the typical week at once.
    const result = wrapper.get('[data-testid="plan-result"]')
    expect(result.text()).toContain('Travel to Shanghai')
    expect(result.text()).toContain('AI-assisted summary. AI can make mistakes. Is this right?')
    expect(result.text()).toContain('You can already')
    expect(result.text()).toContain('1–2 hours a day')
    await primary(wrapper)
    expect(wrapper.get('[data-testid="plan-path"]').text()).toContain('Learn · Unit 1 · Say hello')
    expect(wrapper.get('[data-testid="path-ai"]').text()).toContain('AI-assisted')
    expect(wrapper.get('[data-testid="path-ai"] [data-testid="plan-report"]').text()).toBe('Report')
    expect(wrapper.text()).toContain('Your 10 weeks')
    expect(wrapper.text()).toContain('How you’ll know it’s working')
    expect(wrapper.text()).toContain('Retake the level check')
    // "Start my path" makes the setup's draft the learner's plan.
    expect(api.confirm).not.toHaveBeenCalled()
    await primary(wrapper)
    expect(api.confirm).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('picks the goal date on a calendar and shows it in the same row, with the hours it makes', async () => {
    const { wrapper } = await mountPage()
    await chooseTravel(wrapper)
    await primary(wrapper)

    // 1–2 hours is chosen to start with; the four bands come from the server.
    expect(wrapper.get('[data-testid="pace-choices"] [data-value="HOURS_1_TO_2"]').attributes('aria-pressed')).toBe(
      'true',
    )
    expect(wrapper.findAll('[data-testid="pace-choices"] .plan-choice-label').map((label) => label.text())).toEqual([
      'Under 30 min',
      '30–60 min',
      '1–2 hours',
      '2+ hours',
    ])

    await wrapper.get('[data-testid="date-field"]').trigger('click')
    // Queried again after every change: the test's Teleport stub renders the dialog's content anew.
    const calendar = () => wrapper.get('[data-testid="goal-date-picker"]')
    const day = (date: string) => calendar().get(`[data-day="${date}"]`)
    expect(calendar().text()).toContain('Pick your target date')
    expect(
      calendar()
        .findAll('.plan-calendar-title')
        .map((title) => title.text()),
    ).toEqual(['May 2026', 'June 2026', 'July 2026'])
    // Today is ringed but cannot be the goal date; the days before it cannot either.
    expect(day('2026-05-18').classes()).toContain('is-today')
    expect(day('2026-05-18').attributes('disabled')).toBeDefined()
    expect(day('2026-05-10').attributes('disabled')).toBeDefined()
    // No date yet (X2.26): nothing is picked and "Use this date" waits for a day.
    expect(calendar().findAll('.plan-calendar-day.is-picked')).toHaveLength(0)
    expect(calendar().findAll('.plan-calendar-day.is-path')).toHaveLength(0)
    expect(calendar().get('[data-testid="calendar-picked"]').text()).toBe('No date picked yet')
    expect(calendar().get('[data-testid="calendar-use"]').attributes('disabled')).toBeDefined()

    await calendar().get('[data-testid="calendar-later"]').trigger('click')
    expect(calendar().get('.plan-calendar-day[tabindex="0"]').attributes('data-day')).toBe('2026-06-01')
    await day('2026-06-01').trigger('keydown', { key: 'ArrowRight' })
    expect(calendar().get('.plan-calendar-day[tabindex="0"]').attributes('data-day')).toBe('2026-06-02')
    expect(calendar().get('[data-testid="calendar-use"]').attributes('disabled')).toBeDefined()
    await calendar().get('[data-testid="calendar-earlier"]').trigger('click')

    await day('2026-07-18').trigger('click')
    expect(day('2026-07-18').classes()).toContain('is-picked')
    expect(day('2026-06-30').classes()).toContain('is-path')
    expect(day('2026-07-19').classes()).not.toContain('is-path')
    expect(calendar().get('[data-testid="calendar-picked"] strong').text()).toBe('Sat 18 Jul 2026')
    expect(calendar().get('[data-testid="calendar-picked"]').text()).toContain('· 61 days from today')
    await calendar().get('[data-testid="calendar-use"]').trigger('click')

    expect(wrapper.find('[data-testid="goal-date-picker"]').exists()).toBe(false)
    // The row keeps its shape (S1.18): the field shows the date and the days to go, "No date" stays beside it.
    expect(wrapper.get('[data-testid="date-field"]').text()).toBe('Sat 18 Jul 2026 · 61 days to go')
    expect(wrapper.get('[data-testid="date-none"]').attributes('aria-pressed')).toBe('false')
    // The line says what the two choices add up to, without repeating the date and the days (S1.18).
    expect(wrapper.get('[data-testid="study-line"]').text()).toBe('1–2 hours a day adds up to 61–122 hours of Chinese.')
    await pick(wrapper, 'pace-choices', 'MINUTES_30_TO_60')
    expect(wrapper.get('[data-testid="study-line"]').text()).toBe('30–60 min a day adds up to 31–61 hours of Chinese.')
    expect(wrapper.get('[data-testid="setup-primary"]').attributes('disabled')).toBeUndefined()

    // The field reopens the calendar with the picked day still picked.
    await wrapper.get('[data-testid="date-field"]').trigger('click')
    expect(wrapper.get('[data-testid="goal-date-picker"] [data-day="2026-07-18"]').classes()).toContain('is-picked')
    expect(wrapper.get('[data-testid="calendar-use"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="goal-date-picker"] [data-day="2026-06-01"]').trigger('click')
    await wrapper.get('[data-testid="calendar-use"]').trigger('click')
    expect(wrapper.get('[data-testid="date-field"]').text()).toBe('Mon 1 Jun 2026 · 14 days to go')
  })

  it('goes on without a date and says the plan follows the learner’s pace', async () => {
    const { wrapper } = await mountPage()
    await chooseTravel(wrapper)
    await primary(wrapper)

    await wrapper.get('[data-testid="date-none"]').trigger('click')
    expect(wrapper.get('[data-testid="date-none"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="study-line"]').text()).toBe(
      'No date: your plan follows your pace. You can add a date any time.',
    )
    expect(wrapper.get('[data-testid="setup-primary"]').attributes('disabled')).toBeUndefined()

    await primary(wrapper)
    await startWithoutCheck(wrapper)
    await waitForBuildAnimation()
    expect(api.build.mock.calls[0][0].goal.deadline).toBeNull()
  })

  it('chooses "No date" from the calendar too', async () => {
    const { wrapper } = await mountPage()
    await chooseTravel(wrapper)
    await primary(wrapper)

    await wrapper.get('[data-testid="date-field"]').trigger('click')
    await wrapper.get('[data-testid="calendar-no-date"]').trigger('click')

    expect(wrapper.find('[data-testid="goal-date-picker"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="date-none"]').attributes('aria-pressed')).toBe('true')
  })

  it('keeps the top bar moving forward through the level check instead of starting it again', async () => {
    const answered = { correct: true, skipped: false, correctAnswer: 'Hello', explanation: null }
    api.next
      .mockResolvedValueOnce(checkStep())
      .mockResolvedValueOnce(checkStep({ feedback: answered, answeredCount: 1 }))
      .mockResolvedValueOnce(checkDone({ answeredCount: 2 }))
    const { wrapper } = await mountPage()
    const bar = () => Number(wrapper.get('[role="progressbar"]').attributes('aria-valuenow'))
    const next = async () => {
      await wrapper.get('[data-testid="continue"]').trigger('click')
      await flushPromises()
    }

    // The four steps before the check take the bar to 80%.
    expect(bar()).toBe(0)
    await answerTheGoal(wrapper)
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Your plan')
    expect(bar()).toBe(80)
    await pick(wrapper, 'self-level-choices', 'BEGINNER_1')
    await primary(wrapper)

    // The check goes on from there, a question at a time.
    const first = bar()
    expect(first).toBeGreaterThan(80)
    await checkChoice(wrapper, 1)
    expect(bar()).toBe(first)
    await next()
    const second = bar()
    expect(second).toBeGreaterThan(first)
    expect(second).toBeLessThan(100)
    await checkChoice(wrapper, 1)
    await next()

    // Only the result of the check fills the bar, and the steps after it leave it full.
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Level check')
    expect(bar()).toBe(100)
    await primary(wrapper)
    expect(bar()).toBe(100)
  })

  it('holds the top bar where the steps left it until the first question of the check is in', async () => {
    const first = deferred<ReturnType<typeof checkStep>>()
    api.next.mockReturnValueOnce(first.promise)
    const { wrapper } = await mountPage()
    const bar = () => Number(wrapper.get('[role="progressbar"]').attributes('aria-valuenow'))
    await answerTheGoal(wrapper)
    await pick(wrapper, 'self-level-choices', 'BEGINNER_1')
    await primary(wrapper)

    expect(bar()).toBe(80)
    first.resolve(checkStep())
    await flushPromises()
    expect(bar()).toBeGreaterThan(80)
  })

  it('names the levels by HSK code and word, and says how long the check is', async () => {
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)

    const choices = wrapper
      .findAll('[data-testid="self-level-choices"] .plan-choice-head')
      .map((head) => [head.get('.plan-choice-label').text(), head.find('.tag').exists() ? head.get('.tag').text() : ''])
    expect(choices).toEqual([
      ['Pre-HSK', 'New'],
      ['HSK 1', 'Basic'],
      ['HSK 2', 'Survival'],
      ['HSK 3', 'Conversational'],
      ['HSK 4+', 'Confident'],
      ['Not sure', ''],
    ])
    expect(wrapper.text()).toContain('Level check: up to 8 short questions. You can’t fail it.')
  })

  it('lets a brand-new learner skip the check and builds a plan from the first lesson', async () => {
    const { wrapper } = await mountPage()

    await buildTravelPlan(wrapper)

    expect(api.next).not.toHaveBeenCalled()
    expect(api.build.mock.calls[0][0].levelCheck).toEqual({ selfLevel: 'NEW_TO_MANDARIN', skipped: true, answers: [] })
  })

  it('starts a learner who picks Pre-HSK from the first lesson, and says the check was skipped', async () => {
    api.build.mockResolvedValue(
      learningPlan({ level: { placed: 'NEW_TO_MANDARIN', target: 'BEGINNER_1', knownSkills: [], newSkills: [] } }),
    )
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)

    // S1.32: Pre-HSK turns the main button into "Start from the first lesson"; the check stays on offer as a link.
    await pick(wrapper, 'self-level-choices', 'NEW')
    expect(wrapper.get('[data-testid="setup-primary"]').text()).toBe('Start from the first lesson')
    expect(wrapper.find('[data-testid="skip-check"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="take-check"]').text()).toBe('Not new after all? Take the level check instead')
    await primary(wrapper)
    expect(api.next).not.toHaveBeenCalled()
    await pick(wrapper, 'focus-choices', 'LISTENING')
    await primary(wrapper)

    // S1.29: the plan is built from the first lesson, under the plan's own label.
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Your plan')
    expect(wrapper.get('.plan-building-steps').text()).toContain('Starting you at the first lesson')
    await waitForBuildAnimation()
    expect(api.build.mock.calls[0][0].levelCheck).toEqual({ selfLevel: 'NEW_TO_MANDARIN', skipped: true, answers: [] })

    // S1.25: the result says the check was skipped instead of what the learner can already do.
    expect(wrapper.get('.plan-result-level').text()).toBe('Pre-HSK (New)')
    expect(wrapper.get('[data-testid="plan-result-skipped"]').text()).toContain('Level check skipped')
    expect(wrapper.find('[data-testid="plan-result-known"]').exists()).toBe(false)
  })

  it('lets a learner who picked Pre-HSK take the level check after all', async () => {
    api.next.mockResolvedValueOnce(checkStep())
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)
    await pick(wrapper, 'self-level-choices', 'NEW')

    await wrapper.get('[data-testid="take-check"]').trigger('click')
    await flushPromises()
    expect(api.next).toHaveBeenCalledWith('NEW_TO_MANDARIN', [])
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Level check')
  })

  it('sets the daily goal from the plan result and confirms it (S1.09)', async () => {
    api.setDailyGoal.mockResolvedValue({ dailyGoalXp: 30 })
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)

    expect(wrapper.get('[data-testid="daily-goal-20"]').attributes('aria-pressed')).toBe('true')
    await wrapper.get('[data-testid="daily-goal-30"]').trigger('click')
    await flushPromises()
    expect(api.setDailyGoal).toHaveBeenCalledWith(30)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Daily goal set to 30 XP.')
  })

  it('rebuilds the plan with an edited goal and date, and keeps the level', async () => {
    api.updateGoal.mockResolvedValue(learningPlan({ goal: { ...learningPlan().goal, title: 'Eat well in Shanghai' } }))
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)

    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    const dialog = () => wrapper.get('[data-testid="edit-goal-dialog"]')
    expect(dialog().get('[data-testid="date-field"]').text()).toBe('Sat 18 Jul 2026')
    expect(dialog().get('[data-testid="study-line"]').text()).toBe(
      '61 days × 1–2 hours a day = 61–122 hours of Chinese',
    )
    await dialog().get('[data-testid="goal-choices"] [data-value="ASK_DIRECTIONS"]').trigger('click')
    await dialog().get('[data-testid="date-none"]').trigger('click')
    expect(dialog().get('[data-testid="study-line"]').text()).toBe('No date: your plan follows your pace.')
    await wrapper.get('[data-testid="save-rebuild"]').trigger('click')
    await waitForBuildAnimation()

    expect(api.updateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ goals: ['ORDER_FOOD', 'ASK_DIRECTIONS'], deadline: null }),
      'en',
    )
    expect(wrapper.get('[data-testid="plan-result"]').text()).toContain('Eat well in Shanghai')
  })

  it('keeps the class the learner picks first with the plan before it starts (S1.12)', async () => {
    const built = planWithClassChoice()
    api.build.mockResolvedValue(built)
    api.pickClass.mockImplementation(async (classId: number | null) => ({ ...built, pickedClassId: classId }))
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)
    await primary(wrapper)

    expect(wrapper.get('.plan-road-fork .plan-road-title').text()).toBe('Then: pick a class for your trip')
    await wrapper.get('[data-testid="pick-class-23"]').trigger('click')
    await flushPromises()
    expect(api.pickClass).toHaveBeenCalledWith(23)
    expect(wrapper.get('[data-testid="pick-class-23"]').text()).toBe('Picked')
    expect(wrapper.find('[data-testid="pick-error"]').exists()).toBe(false)
  })

  it('says a plan built from rules is built from the answers, with no AI label or Pick this first', async () => {
    api.build.mockResolvedValue({ ...planWithClassChoice(), aiAssisted: false })
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)
    await primary(wrapper)

    expect(wrapper.find('[data-testid="path-ai"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="pick-class-22"]').exists()).toBe(false)
    // S1.16: with nothing to pick, the fork names the classes instead of asking for a pick.
    expect(wrapper.get('.plan-road-fork .plan-road-title').text()).toBe('Then: a class for your trip')
    expect(wrapper.text()).toContain('SparkBot isn’t available, so this plan is built from your answers.')
  })

  it('sends a learner who already has a plan to My learning plan instead of starting over', async () => {
    api.overview.mockResolvedValue({ plan: planSummary() })
    const { wrapper, router } = await mountPage()

    expect(router.currentRoute.value.name).toBe('student-plan')
    // Nothing of the first-time setup was offered meanwhile: no "Let's start", no "Set up later".
    expect(wrapper.find('[data-testid="setup-primary"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="setup-secondary"]').exists()).toBe(false)
    expect(api.build).not.toHaveBeenCalled()
    expect(api.skip).not.toHaveBeenCalled()
  })

  it('stays on the setup it just finished, with the plan it built', async () => {
    const { wrapper, router } = await mountPage()
    await buildTravelPlan(wrapper)

    expect(router.currentRoute.value.name).toBe('student-setup')
    expect(wrapper.find('[data-testid="plan-result"]').exists()).toBe(true)
  })

  it('sets the plan up later from the welcome screen, after asking', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Set up your plan later?')
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').text()).toBe('Set up later')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.skip).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it.each(['skip', 'confirm'] as const)(
    'can reopen Home without repeating a saved setup %s if navigation stops',
    async (action) => {
      const { wrapper, router } = await mountPage()
      if (action === 'confirm') {
        await buildTravelPlan(wrapper)
        await primary(wrapper)
      }
      const unblock = router.beforeEach((to) => to.name !== 'student-home')
      if (action === 'skip') {
        await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
        await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
      } else {
        await primary(wrapper)
      }
      await flushPromises()
      expect(api[action]).toHaveBeenCalledOnce()
      expect(router.currentRoute.value.name).toBe('student-setup')
      expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
      unblock()
      await buttonNamed(wrapper, 'Home').trigger('click')
      await flushPromises()
      expect(router.currentRoute.value.name).toBe('student-home')
      expect(api[action]).toHaveBeenCalledOnce()
    },
  )

  it('asks over the page when × is pressed before the plan is built', async () => {
    const { wrapper, router } = await mountPage()
    await primary(wrapper)

    // S1.02: the step stays behind the dialog (X2.01); Keep going closes it.
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Set up your plan later?')
    expect(dialog.text()).toContain('Your answers so far aren’t saved.')
    expect(wrapper.find('[data-testid="reason-choices"]').exists()).toBe(true)
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('student-setup')
  })

  it.each(['result', 'path'])('asks before leaving once the plan is built, on the %s', async (screen) => {
    const { wrapper, router } = await mountPage()
    await buildTravelPlan(wrapper)
    if (screen === 'path') await primary(wrapper)

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave before saving your plan?')
    expect(dialog.text()).toContain('Your plan and answers aren’t saved yet.')
    // X2.28: the button says what leaving now loses.
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').text()).toBe('Leave without saving')
    expect(router.currentRoute.value.name).toBe('student-setup')

    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    // The draft that was not started is discarded.
    expect(api.skip).toHaveBeenCalledOnce()
    expect(api.confirm).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('builds a plan again from the same answers when it is too old to start', async () => {
    api.confirm
      .mockRejectedValueOnce({
        response: { status: 409, data: { message: 'This plan was built more than a day ago. Build it again.' } },
      })
      .mockResolvedValueOnce(learningPlan())
    const { wrapper, router } = await mountPage()
    await buildTravelPlan(wrapper)
    await primary(wrapper)

    await primary(wrapper)
    expect(router.currentRoute.value.name).toBe('student-setup')
    expect(wrapper.get('.plan-start-error').text()).toContain(
      'This plan was built more than a day ago. Build it again.',
    )
    await wrapper.get('[data-testid="setup-build-again"]').trigger('click')
    await waitForBuildAnimation()

    expect(api.build).toHaveBeenCalledTimes(2)
    expect(api.build.mock.calls[1][0]).toEqual(api.build.mock.calls[0][0])
    expect(wrapper.find('.plan-start-error').exists()).toBe(false)
    expect(wrapper.find('[data-testid="plan-result"]').exists()).toBe(true)
    await primary(wrapper)
    await primary(wrapper)
    expect(api.confirm).toHaveBeenCalledTimes(2)
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('asks for a new date when the plan’s goal date has passed, since the same date cannot be built again', async () => {
    api.confirm.mockRejectedValueOnce({
      response: { status: 409, data: { message: "This plan's goal date has passed. Build it again." } },
    })
    api.updateGoal.mockResolvedValue(learningPlan())
    const { wrapper, router } = await mountPage()
    await buildTravelPlan(wrapper)
    await primary(wrapper)

    await primary(wrapper)
    expect(router.currentRoute.value.name).toBe('student-setup')
    expect(wrapper.find('[data-testid="setup-build-again"]').exists()).toBe(false)
    await wrapper.get('[data-testid="setup-pick-new-date"]').trigger('click')

    const dialog = () => wrapper.get('[data-testid="edit-goal-dialog"]')
    expect(wrapper.find('.plan-start-error').exists()).toBe(false)
    expect(wrapper.get('[data-testid="save-rebuild"]').attributes('disabled')).toBeDefined()
    await dialog().get('[data-testid="date-none"]').trigger('click')
    await wrapper.get('[data-testid="save-rebuild"]').trigger('click')
    await waitForBuildAnimation()

    expect(api.build).toHaveBeenCalledOnce()
    expect(api.updateGoal).toHaveBeenCalledWith(expect.objectContaining({ deadline: null }), 'en')
    expect(wrapper.find('[data-testid="plan-result"]').exists()).toBe(true)
  })

  it('keeps the learner on the path when starting fails for another reason', async () => {
    api.confirm.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)
    await primary(wrapper)

    await primary(wrapper)
    expect(wrapper.get('.plan-start-error').text()).toBe('Your path could not be started. Try again.')
    expect(wrapper.find('[data-testid="setup-build-again"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="setup-primary"]').text()).toBe('Start my path')
  })

  it('explains a failed build and builds again', async () => {
    api.build.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)
    await startWithoutCheck(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('We couldn’t build your plan')
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await waitForBuildAnimation()
    expect(api.build).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="plan-result"]').exists()).toBe(true)
  })

  it('does not leave the current page when a previous setup skip finishes', async () => {
    const skipped = deferred<void>()
    api.skip.mockReturnValue(skipped.promise)
    const router = await testRouter('/student/home')
    router.addRoute({ path: '/student/setup', name: 'student-setup', component: StudentSetupPage })
    await router.push({ name: 'student-setup' })
    const wrapper = mount(RouterView, { global: { plugins: [router], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')

    await router.push({ name: 'student-plan' })
    await flushPromises()
    expect(wrapper.findComponent(StudentSetupPage).exists()).toBe(false)
    skipped.resolve()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('student-plan')
    expect(api.skip).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('keeps the destination already opening when a setup skip finishes', async () => {
    const skipped = deferred<void>()
    const opened = deferred<void>()
    api.skip.mockReturnValue(skipped.promise)
    const { wrapper, router } = await mountPage()
    router.beforeResolve((to) => (to.name === 'student-plan' ? opened.promise : undefined))
    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    const navigation = router.push({ name: 'student-plan' })
    await flushPromises()
    skipped.resolve()
    await flushPromises()
    opened.resolve()
    await navigation
    expect(router.currentRoute.value.name).toBe('student-plan')
    expect(api.skip).toHaveBeenCalledOnce()
  })

  it('discards a draft only after the build already in flight settles', async () => {
    const pending = deferred<ReturnType<typeof learningPlan>>()
    api.build.mockReturnValueOnce(pending.promise)
    const { wrapper, router } = await mountPage()
    await answerTheGoal(wrapper)
    await startWithoutCheck(wrapper)
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    expect(api.skip).not.toHaveBeenCalled()
    pending.resolve(learningPlan())
    await flushPromises()
    expect(api.skip).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('recovers from building with the skip error visible when skipping fails during a build', async () => {
    const pending = deferred<ReturnType<typeof learningPlan>>()
    api.build.mockReturnValueOnce(pending.promise)
    api.skip.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)
    await startWithoutCheck(wrapper)
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    pending.resolve(learningPlan())
    await flushPromises()

    expect(wrapper.find('.is-building').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').text()).toBe('We could not save that. Try again.')
    wrapper.unmount()
  })

  it('sends a skip only once when confirmation clicks arrive before the button is disabled', async () => {
    const skipped = deferred<void>()
    api.skip.mockReturnValue(skipped.promise)
    const { wrapper, router } = await mountPage()
    await wrapper.get('[data-testid="setup-secondary"]').trigger('click')
    const button = wrapper.get<HTMLButtonElement>('[data-testid="confirm-dialog-confirm"]').element
    button.click()
    button.click()
    await flushPromises()
    const callsWhilePending = api.skip.mock.calls.length
    skipped.resolve()
    await flushPromises()

    expect(callsWhilePending).toBe(1)
    expect(router.currentRoute.value.name).toBe('student-home')
    wrapper.unmount()
  })

  it('refreshes setup options today when picking a new date after day rollover', async () => {
    api.confirm.mockRejectedValueOnce({
      response: { status: 409, data: { message: "This plan's goal date has passed. Build it again." } },
    })
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)
    // Result -> Path
    await primary(wrapper)
    // Start my path fails with 409 goal date passed
    await primary(wrapper)
    await flushPromises()

    api.options.mockResolvedValueOnce({ ...planOptions(), today: '2026-06-15' })
    await wrapper.get('[data-testid="setup-pick-new-date"]').trigger('click')
    await flushPromises()

    expect(api.options).toHaveBeenCalledTimes(2)
    await wrapper.get('[data-testid="edit-goal-dialog"] [data-testid="date-field"]').trigger('click')
    expect(wrapper.get('[data-day="2026-06-15"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('leaves directly to Home without calling skip when × is clicked after an initial load failure', async () => {
    api.options.mockRejectedValueOnce(new Error('offline'))
    const { wrapper, router } = await mountPage()

    expect(wrapper.text()).toContain('Your plan could not load')
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(api.skip).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('student-home')
  })

  it('lets a learner who completed the level check step back past checkDone to edit pace and recover from a 400 build error', async () => {
    api.next.mockResolvedValueOnce(checkStep()).mockResolvedValueOnce(checkDone())
    api.build.mockRejectedValueOnce({
      response: { status: 400, data: { message: 'Pick a date in the future, within 2 years.' } },
    })
    const { wrapper } = await mountPage()
    await answerTheGoal(wrapper)
    await pick(wrapper, 'self-level-choices', 'BEGINNER_1')
    await primary(wrapper)
    await checkChoice(wrapper, 1)
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()

    // checkDone -> focus -> build fails with 400 -> Edit my goal returns to pace with refreshed today
    await primary(wrapper)
    await primary(wrapper)
    await flushPromises()
    expect(wrapper.text()).toContain('Pick a date in the future, within 2 years.')

    api.options.mockResolvedValueOnce({ ...planOptions(), today: '2026-07-19' })
    await wrapper.get('[data-testid="setup-edit-pace"]').trigger('click')
    await flushPromises()
    // Expired 2026-07-18 deadline was cleared after day rollover; choosing No date preserves the completed check.
    expect(wrapper.get('[data-testid="setup-primary"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="date-none"]').trigger('click')
    await primary(wrapper)
    expect(wrapper.find('[data-testid="level-check-done"]').exists()).toBe(true)
    await primary(wrapper)
    await primary(wrapper)
    await waitForBuildAnimation()

    expect(api.build).toHaveBeenCalledTimes(2)
    expect(api.build.mock.calls[1]![0].goal.deadline).toBeNull()
    expect(api.build.mock.calls[1]![0].levelCheck.skipped).toBe(false)
    expect(api.build.mock.calls[1]![0].levelCheck.answers).toEqual([{ questionId: 1, optionId: 12 }])
  })

  it('opens the date picker instead of submitting an expired date when Build it again detects day rollover', async () => {
    api.confirm.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'This plan was built more than a day ago. Build it again.' } },
    })
    const { wrapper } = await mountPage()
    await buildTravelPlan(wrapper)
    await primary(wrapper)
    await primary(wrapper)

    // Clock rolled past the draft's 2026-07-18 deadline while the plan sat idle.
    api.options.mockResolvedValue({ ...planOptions(), today: '2026-07-19' })
    await wrapper.get('[data-testid="setup-build-again"]').trigger('click')
    await flushPromises()

    expect(api.build).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="edit-goal-dialog"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="save-rebuild"]').attributes('disabled')).toBeDefined()
  })
})
