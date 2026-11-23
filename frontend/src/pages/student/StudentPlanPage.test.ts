import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { mountAtRoute } from '@/test/routes'
import { buttonNamed, deferred } from '@/test/helpers'
import { learningPlan, planOptions, planWithClassChoice } from '@/test/planFixtures'

const api = vi.hoisted(() => ({
  plan: vi.fn(),
  options: vi.fn(),
  updateGoal: vi.fn(),
  pickClass: vi.fn(),
}))

vi.mock('@/api/plan', () => ({
  planApi: { plan: api.plan, options: api.options, updateGoal: api.updateGoal, pickClass: api.pickClass },
}))

import StudentPlanPage from './StudentPlanPage.vue'

async function mountPage(path = '/student/progress/plan') {
  return mountAtRoute(StudentPlanPage, path)
}

describe('StudentPlanPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    // The fixtures' day: before the plan's goal date (18 Jul 2026), so "Update my goal" starts with it picked.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 4, 18) })
    api.plan.mockResolvedValue(learningPlan())
    api.options.mockResolvedValue(planOptions())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the goal, the stats, this week and the road from You to the goal', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.get('h1').text()).toBe('My learning plan')
    expect(wrapper.text()).toContain('Travel to Shanghai · by Sat 18 Jul 2026 · week 2 of 10')
    const stats = wrapper.get('[data-testid="plan-stats"]').text()
    expect(stats).toContain('HSK 1 → HSK 2')
    expect(stats).not.toContain('Target reached')
    expect(stats).toContain('18%')
    expect(stats).toContain('2 of 7 days')
    expect(stats).toContain('On track')
    expect(stats).toContain('1–2 hours a day')
    expect(stats).not.toContain('XP')

    const path = wrapper.get('[data-testid="plan-path"]')
    expect(path.text()).toContain('You are here · week 2')
    expect(path.text()).toContain('4 lessons and 4 practice sets done so far')
    expect(path.text()).toContain('Your goal · Travel to Shanghai')
    expect(path.text()).toContain('Sat 18 Jul · target: HSK 2 (Survival)')
    const unit = wrapper.get('[data-testid="plan-step-1"]')
    expect(unit.text()).toContain('In progress · 2 of 7')
    expect(unit.text()).toContain('Next lesson: Introduce yourself · 4 min')
    expect(unit.get('a').attributes('href')).toBe('/student/learn/lessons/103')
    // The class the learner has: its progress and its next lesson, no teacher and no live session.
    const joined = wrapper.get('[data-testid="plan-step-2"]')
    expect(joined.text()).toContain('Your class')
    expect(joined.text()).toContain('Mandarin for Campus Life')
    expect(joined.text()).toContain('40%')
    expect(joined.text()).toContain('Next: Meeting Classmates')
    expect(joined.get('a').text()).toBe('Open class')
    // Later units stay in the list for screen readers; a step built from rules explains itself with its goals.
    const later = wrapper.get('[data-testid="plan-step-3"]')
    expect(later.classes()).toContain('sr-only')
    expect(later.text()).toContain('Why: matches your goal: Order food.')
    // One optional class: no fork and nothing to pick, only the way to it.
    const single = wrapper.get('[data-testid="plan-step-4"]')
    expect(single.classes()).toContain('is-center')
    expect(single.text()).toContain('Best after Unit 2')
    expect(single.get('a').text()).toBe('View class')
    expect(wrapper.find('[data-testid^="pick-class-"]').exists()).toBe(false)

    const tasks = wrapper.get('[data-testid="plan-tasks"]')
    expect(tasks.findAll('li.is-done')).toHaveLength(2)
    expect(tasks.text()).toContain('Listening set: At the Café')
    expect(tasks.text()).toContain('AI Helper · no XP')
    expect(wrapper.get('[data-testid="plan-ai-note"]').text()).toContain(
      'AI-made plan. It updates as you learn and can make mistakes.',
    )
    expect(wrapper.find('[data-testid="plan-report"]').exists()).toBe(true)
  })

  it('says the target is reached when the level is the plan’s target', async () => {
    const plan = learningPlan()
    api.plan.mockResolvedValue(learningPlan({ level: { ...plan.level, placed: 'BEGINNER_2', target: 'BEGINNER_2' } }))
    const { wrapper } = await mountPage()

    const stats = wrapper.get('[data-testid="plan-stats"]').text()
    expect(stats).toContain('HSK 2')
    expect(stats).not.toContain('→')
    expect(stats).toContain('Target reached')
  })

  it('confirms a level the retake changed, once', async () => {
    const plan = learningPlan()
    api.plan.mockResolvedValue(learningPlan({ level: { ...plan.level, placed: 'BEGINNER_2', target: 'BEGINNER_2' } }))
    const { wrapper, router } = await mountPage('/student/progress/plan?updated=level')
    await flushPromises()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Level updated to HSK 2 (Survival).')
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('opens Update my goal when Home asks for it', async () => {
    const { wrapper, router } = await mountPage('/student/progress/plan?edit=goal')
    await flushPromises()

    expect(wrapper.find('[data-testid="update-goal-drawer"]').exists()).toBe(true)
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('opens a goal edit link while already on the plan and preserves unrelated query and hash', async () => {
    const { wrapper, router } = await mountPage()
    await router.push('/student/progress/plan?edit=goal&from=home#weekly')
    await flushPromises()
    expect(wrapper.find('[data-testid="update-goal-drawer"]').exists()).toBe(true)
    expect(router.currentRoute.value.fullPath).toBe('/student/progress/plan?from=home#weekly')
  })

  it('loads goal options the first time the drawer opens', async () => {
    const { wrapper } = await mountPage()
    const open = wrapper.get('[data-testid="update-goal"]')

    expect(api.options).not.toHaveBeenCalled()
    await open.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="reason-choices"]').exists()).toBe(true)
    expect(api.options).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="update-goal-drawer"] button[aria-label="Close"]').trigger('click')
    expect(wrapper.find('[data-testid="update-goal-drawer"]').exists()).toBe(false)
    await open.trigger('click')
    expect(api.options).toHaveBeenCalledTimes(1)
  })

  it('offers the level checkpoint first on the unit behind it, never as a lesson', async () => {
    const base = learningPlan()
    const done = { ...base.steps[0]!, status: 'DONE' as const, completedCount: 7, percent: 100, nextLesson: null }
    const behind = {
      ...base.steps[2]!,
      unitNumber: 5,
      title: 'Time and completed actions',
      nextLesson: { id: 400, title: 'Level 1 checkpoint', kind: 'CHECKPOINT' as const },
    }
    api.plan.mockResolvedValue(
      learningPlan({
        steps: [done, base.steps[1]!, behind, base.steps[3]!],
        thisWeek: [{ kind: 'LEARN_NEXT', title: 'Level 1 checkpoint', minutes: 10, lessonId: 400 }],
      }),
    )
    const { wrapper } = await mountPage()

    const unit = wrapper.get('[data-testid="plan-step-3"]')
    expect(unit.text()).toContain('Learn · Unit 5')
    expect(unit.text()).toContain('First: Level 1 checkpoint · 10 min')
    expect(unit.text()).not.toContain('Next lesson')
    expect(unit.get('a').text()).toBe('Start')
    expect(unit.get('a').attributes('href')).toBe('/student/learn/lessons/400')
    expect(wrapper.get('[data-testid="plan-tasks"]').get('a').attributes('href')).toBe('/student/learn/lessons/400')
  })

  it('draws a fork for two optional classes and keeps the class the learner picks first with the plan', async () => {
    const withFork = planWithClassChoice()
    api.plan.mockResolvedValue(withFork)
    api.pickClass.mockImplementation(async (classId: number | null) => ({ ...withFork, pickedClassId: classId }))
    const { wrapper } = await mountPage()

    const path = wrapper.get('[data-testid="plan-path"]')
    expect(path.text()).toContain('Then: pick a class for your trip')
    const left = wrapper.get('[data-testid="plan-step-4"]')
    const right = wrapper.get('[data-testid="plan-step-5"]')
    expect(left.classes()).toContain('is-left')
    expect(right.classes()).toContain('is-right')
    expect(left.text()).toContain('Best after Unit 2')

    const pick = () => wrapper.get('[data-testid="pick-class-23"]')
    expect(pick().attributes('aria-pressed')).toBe('false')
    await pick().trigger('click')
    // The pick shows at once (S12.06) and is saved with the plan, so Home shows the same class (S3.19).
    expect(pick().attributes('aria-pressed')).toBe('true')
    expect(pick().text()).toBe('Picked · comes first')
    expect(wrapper.get('[data-testid="plan-path"]').text()).toContain(
      'Shopping and Prices first, then Travel Mandarin.',
    )
    expect(wrapper.get('[data-testid="plan-step-5"]').text()).toContain('Best after Unit 2')
    await flushPromises()
    expect(api.pickClass).toHaveBeenLastCalledWith(23)
    expect(pick().attributes('aria-pressed')).toBe('true')

    // A second click takes it back.
    await pick().trigger('click')
    await flushPromises()
    expect(api.pickClass).toHaveBeenLastCalledWith(null)
    expect(pick().attributes('aria-pressed')).toBe('false')
  })

  it('opens on the class the plan has picked, and goes back to it when a new pick cannot be saved', async () => {
    const plan = { ...planWithClassChoice(), pickedClassId: 23 }
    api.plan.mockResolvedValue(plan)
    const save = deferred<typeof plan>()
    api.pickClass.mockReturnValueOnce(save.promise)
    const { wrapper } = await mountPage()
    const pressed = (classId: number) => wrapper.get(`[data-testid="pick-class-${classId}"]`).attributes('aria-pressed')

    expect(pressed(23)).toBe('true')
    await wrapper.get(`[data-testid="pick-class-${plan.steps[3]!.refId}"]`).trigger('click')
    expect(pressed(plan.steps[3]!.refId)).toBe('true')
    save.reject(new Error('offline'))
    await flushPromises()

    expect(pressed(23)).toBe('true')
    expect(wrapper.get('[data-testid="pick-error"]').text()).toBe('Your pick couldn’t be saved. Try again.')
  })

  it('moves on to the next unit when a Learn unit of the plan was unpublished', async () => {
    const [first, joined, second, optional] = learningPlan().steps
    const lost = { ...first!, status: 'UNAVAILABLE' as const, unitNumber: null, titleZh: null, nextLesson: null }
    const next = { ...second!, nextLesson: { id: 120, title: 'Order a drink', kind: 'LESSON' as const } }
    api.plan.mockResolvedValue(learningPlan({ steps: [lost, joined!, next, optional!] }))
    const { wrapper } = await mountPage()

    const unit = wrapper.get('[data-testid="plan-step-3"]')
    expect(unit.classes()).not.toContain('sr-only')
    expect(unit.get('a').attributes('href')).toBe('/student/learn/lessons/120')
    const gone = wrapper.get('[data-testid="plan-step-1"]')
    expect(gone.classes()).toContain('sr-only')
    expect(gone.text()).toMatch(/^Say hello\./)
    expect(gone.text()).toContain('No longer available')
  })

  it('stops queued class picks when a different account signs in', async () => {
    const plan = planWithClassChoice()
    api.plan.mockResolvedValue(plan)
    const first = deferred<typeof plan>()
    api.pickClass.mockReturnValueOnce(first.promise)
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="pick-class-23"]').trigger('click')
    await wrapper.get(`[data-testid="pick-class-${plan.steps[3]!.refId}"]`).trigger('click')
    setAccessToken('another-plan-session')
    onTestFinished(() => setAccessToken(null))
    first.resolve({ ...plan, pickedClassId: 23 })
    await flushPromises()
    expect(api.pickClass).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="pick-class-23"]').attributes('aria-pressed')).toBe('false')
  })

  it('rebuilds the plan from a new goal, keeps the progress and says so', async () => {
    api.updateGoal.mockResolvedValue(
      learningPlan({ goal: { ...learningPlan().goal, reason: 'WORK', title: 'Chat with customers' } }),
    )
    const { wrapper } = await mountPage()
    expect(wrapper.find('[data-testid="deadline-passed"]').exists()).toBe(false)

    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    // Queried again after every change: the test's Teleport stub renders the drawer's content anew.
    const drawer = () => wrapper.get('[data-testid="update-goal-drawer"]')
    expect(drawer().text()).toContain('Your progress and level stay.')
    // The goal's date and daily time, and the hours they make.
    expect(drawer().get('[data-testid="date-field"]').text()).toBe('Sat 18 Jul 2026')
    expect(drawer().get('[data-testid="study-line"]').text()).toBe(
      '61 days × 1–2 hours a day = 61–122 hours of Chinese',
    )
    expect(drawer().get('[data-testid="pace-choices"] [data-value="HOURS_1_TO_2"]').attributes('aria-pressed')).toBe(
      'true',
    )
    // The plan's two focus picks start selected.
    expect(drawer().findAll('[data-testid="focus-choices"] [aria-pressed="true"]')).toHaveLength(2)
    await drawer().get('[data-testid="reason-choices"] [data-value="WORK"]').trigger('click')
    await drawer().get('[data-testid="pace-choices"] [data-value="MINUTES_30_TO_60"]').trigger('click')
    expect(drawer().get('[data-testid="study-line"]').text()).toBe('61 days × 30–60 min a day = 31–61 hours of Chinese')
    await drawer().get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()

    expect(api.updateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ reason: 'WORK', pace: 'MINUTES_30_TO_60', deadline: '2026-07-18' }),
      'en',
    )
    expect(wrapper.find('[data-testid="update-goal-drawer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Chat with customers')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Plan updated. Your progress is kept.')
  })

  it('picks a new goal date on the calendar from the drawer', async () => {
    api.updateGoal.mockResolvedValue(learningPlan())
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="date-field"]').trigger('click')
    const calendar = () => wrapper.get('[data-testid="goal-date-picker"]')
    const monthTitles = () =>
      calendar()
        .findAll('.plan-calendar-title')
        .map((title) => title.text())
    // The picked month ends the three months shown.
    expect(monthTitles()).toEqual(['May 2026', 'June 2026', 'July 2026'])
    for (let turn = 0; turn < 4; turn += 1) await calendar().get('[data-testid="calendar-later"]').trigger('click')
    expect(monthTitles()).toEqual(['September 2026', 'October 2026', 'November 2026'])
    await calendar().get('[data-day="2026-11-18"]').trigger('click')
    expect(calendar().get('[data-testid="calendar-picked"]').text()).toContain('184 days from today')
    // X2.17: the drawer has "No date" beside its date field, so the calendar's footer leaves it out.
    expect(calendar().find('[data-testid="calendar-no-date"]').exists()).toBe(false)
    await wrapper.get('[data-testid="calendar-use"]').trigger('click')

    const drawer = wrapper.get('[data-testid="update-goal-drawer"]')
    expect(drawer.get('[data-testid="date-field"]').text()).toBe('Wed 18 Nov 2026')
    expect(drawer.get('[data-testid="study-line"]').text()).toBe(
      '184 days × 1–2 hours a day = 184–368 hours of Chinese',
    )
    await wrapper.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()
    expect(api.updateGoal).toHaveBeenCalledWith(expect.objectContaining({ deadline: '2026-11-18' }), 'en')
  })

  it('asks for a new date when the goal date has passed, and rebuilds once one is chosen', async () => {
    // The platform's day is past the goal date, though the browser's is not: the page goes by the platform's.
    api.plan.mockResolvedValue(learningPlan({ today: '2026-07-19' }))
    api.options.mockResolvedValue({ ...planOptions(), today: '2026-07-19' })
    api.updateGoal.mockResolvedValue(learningPlan({ goal: { ...learningPlan().goal, deadline: null } }))
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('Travel to Shanghai · was due Sat 18 Jul 2026 · plan ended')
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('[data-testid="update-goal-drawer"]')
    expect(drawer.get('[data-testid="deadline-passed"]').text()).toBe(
      'Your goal date has passed. Pick a new date, or No date.',
    )
    // The old date is not offered again: nothing is chosen, so there is nothing to rebuild with yet.
    expect(drawer.get('[data-testid="date-field"]').text()).toBe('Pick a day on the calendar')
    expect(wrapper.get('[data-testid="rebuild"]').attributes('disabled')).toBeDefined()

    await drawer.get('[data-testid="date-none"]').trigger('click')
    expect(wrapper.find('[data-testid="deadline-passed"]').exists()).toBe(false)
    await wrapper.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()
    expect(api.updateGoal).toHaveBeenCalledWith(expect.objectContaining({ deadline: null }), 'en')
  })

  it('keeps a completed plan complete after its goal date', async () => {
    const plan = learningPlan()
    api.plan.mockResolvedValue({
      ...plan,
      today: '2026-07-19',
      progress: { ...plan.progress, percent: 100, status: 'COMPLETE' },
    })
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="plan-stats"]').text()).toContain('Plan complete')
    expect(wrapper.text()).not.toContain('plan ended')
  })

  it('counts the calendar from the platform’s day, which may be ahead of the browser’s', async () => {
    // 20:00 on 14 November in London is 15 November on the platform clock: a goal date of the 15th has passed.
    vi.setSystemTime(new Date(2026, 10, 14, 20, 0))
    const plan = learningPlan({ today: '2026-11-15', goal: { ...learningPlan().goal, deadline: '2026-11-15' } })
    api.plan.mockResolvedValue(plan)
    api.options.mockResolvedValue({ ...planOptions(), today: '2026-11-15' })
    api.updateGoal.mockResolvedValue(plan)
    const { wrapper } = await mountPage()

    expect(wrapper.text()).not.toContain('plan ended')
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="deadline-passed"]').exists()).toBe(true)
    await wrapper.get('[data-testid="date-field"]').trigger('click')
    const calendar = () => wrapper.get('[data-testid="goal-date-picker"]')
    expect(calendar().get('[data-day="2026-11-15"]').attributes('disabled')).toBeDefined()
    expect(calendar().get('[data-day="2026-11-16"]').attributes('disabled')).toBeUndefined()
    await calendar().get('[data-day="2026-11-29"]').trigger('click')
    expect(calendar().get('[data-testid="calendar-picked"]').text()).toContain('14 days from today')
    await wrapper.get('[data-testid="calendar-use"]').trigger('click')
    await wrapper.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()
    expect(api.updateGoal).toHaveBeenCalledWith(expect.objectContaining({ deadline: '2026-11-29' }), 'en')
  })

  it('opens on the plan’s own goal and offers Rebuild my plan only once something changes', async () => {
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    const rebuild = () => wrapper.get('[data-testid="rebuild"]')
    const pace = (value: string) => wrapper.get(`[data-testid="pace-choices"] [data-value="${value}"]`)

    expect(rebuild().attributes('disabled')).toBeDefined()
    await pace('MINUTES_30_TO_60').trigger('click')
    expect(rebuild().attributes('disabled')).toBeUndefined()
    // Back to the plan's own pace: nothing to rebuild again.
    await pace('HOURS_1_TO_2').trigger('click')
    expect(rebuild().attributes('disabled')).toBeDefined()
    expect(api.updateGoal).not.toHaveBeenCalled()
  })

  it('asks before discarding changes to the goal, and closes at once without changes', async () => {
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    const drawerOpen = () => wrapper.find('[data-testid="update-goal-drawer"]').exists()

    await wrapper.get('[data-testid="pace-choices"] [data-value="MINUTES_30_TO_60"]').trigger('click')
    await wrapper.get('[data-testid="update-goal-drawer"] button[aria-label="Close"]').trigger('click')
    // X2.31: the drawer stays behind the question.
    const dialog = () => wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog().text()).toContain('Discard your changes?')
    expect(dialog().text()).toContain(
      'Your goal, date and time changes aren’t saved. Your current plan stays as it is.',
    )
    expect(dialog().get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Keep editing')
    await dialog().get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(drawerOpen()).toBe(true)
    expect(wrapper.get('[data-testid="pace-choices"] [data-value="MINUTES_30_TO_60"]').attributes('aria-pressed')).toBe(
      'true',
    )

    await buttonNamed(wrapper, 'Cancel').trigger('click')
    expect(dialog().get('[data-testid="confirm-dialog-confirm"]').text()).toBe('Discard changes')
    await dialog().get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(drawerOpen()).toBe(false)
    expect(api.updateGoal).not.toHaveBeenCalled()

    // Opened again, it starts from the plan's own goal and closes without asking.
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    expect(wrapper.get('[data-testid="pace-choices"] [data-value="HOURS_1_TO_2"]').attributes('aria-pressed')).toBe(
      'true',
    )
    await buttonNamed(wrapper, 'Cancel').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(drawerOpen()).toBe(false)
  })

  it('says at the top of the drawer that a failed rebuild left the plan as it was', async () => {
    api.updateGoal.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="pace-choices"] [data-value="HOURS_2_PLUS"]').trigger('click')
    await wrapper.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()

    // S12.12: the drawer stays open with the learner's changes, and the error leads it.
    const drawer = wrapper.get('[data-testid="update-goal-drawer"]')
    const notice = drawer.get('[data-testid="rebuild-error"]')
    expect(drawer.get('[role="alert"], fieldset').element).toBe(notice.element)
    expect(notice.attributes('tabindex')).toBe('-1')
    expect(notice.text()).toBe('We couldn’t rebuild your plan. Your current plan hasn’t changed. Try again.')
    expect(drawer.get('[data-testid="pace-choices"] [data-value="HOURS_2_PLUS"]').attributes('aria-pressed')).toBe(
      'true',
    )
    expect(wrapper.text()).toContain('Travel to Shanghai · by Sat 18 Jul 2026 · week 2 of 10')
  })

  it('shows the rebuild in progress in the drawer footer', async () => {
    const rebuilt = deferred<ReturnType<typeof learningPlan>>()
    api.updateGoal.mockReturnValue(rebuilt.promise)
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="update-goal"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="pace-choices"] [data-value="HOURS_2_PLUS"]').trigger('click')
    await wrapper.get('[data-testid="rebuild"]').trigger('click')
    await flushPromises()

    const rebuild = wrapper.get('[data-testid="rebuild"]')
    expect(rebuild.text()).toBe('Rebuilding…')
    expect(rebuild.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="update-goal-drawer"]').text()).toContain('SparkBot is reading your new goal…')
    rebuilt.resolve(learningPlan())
    await flushPromises()
  })

  it('says when the plan was built from rules', async () => {
    api.plan.mockResolvedValue(
      learningPlan({ aiAssisted: false, goal: { ...learningPlan().goal, title: null, summary: null }, outcomes: [] }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('Travel · by')
    expect(wrapper.get('[data-testid="plan-ai-note"]').text()).toContain('Built from your answers')
    expect(wrapper.find('[data-testid="plan-report"]').exists()).toBe(false)
    // Outcomes come from the goals.
    expect(wrapper.get('[data-testid="plan-outcomes"]').text()).toContain('Have a short everyday chat')
  })

  it('offers to build a plan when there is none', async () => {
    api.plan.mockResolvedValue(null)
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('You don’t have a learning plan yet')
    expect(wrapper.find('[data-testid="update-goal"]').exists()).toBe(false)
  })
})
