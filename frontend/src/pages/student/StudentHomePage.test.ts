import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import { today } from '@/test/learnFixtures'
import { homeView, planSummary } from '@/test/planFixtures'
import type { HomeView } from '@/types/plan'

const api = vi.hoisted(() => ({ home: vi.fn(), resend: vi.fn(), setDailyGoal: vi.fn() }))

vi.mock('@/api/plan', () => ({
  planApi: { home: api.home },
}))
vi.mock('@/api/ops', () => ({
  accountOpsApi: { resendEmailConfirmation: api.resend },
}))
vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  xpApi: { setDailyGoal: api.setDailyGoal },
}))

import StudentHomePage from './StudentHomePage.vue'

function mountPage() {
  return mountAtRoute(StudentHomePage, '/student/home')
}

/** The first day: no XP yet. */
const FIRST_DAY = today({ todayXp: 0, mistakesWaiting: 0, dailyGoalXp: 20 })

describe('StudentHomePage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.home.mockResolvedValue(homeView())
  })

  it('shows the goal as a strip, the next lesson as the one big action, and today’s plan beside it', async () => {
    const { wrapper } = await mountPage()

    // The h1 is for screen readers: the header bar names the page.
    expect(wrapper.get('h1').text()).toBe('Welcome back, Amira')
    expect(wrapper.get('h1').classes()).toContain('sr-only')
    const goal = wrapper.get('[data-testid="home-goal"]')
    expect(goal.text()).toContain('Travel to Shanghai · by Sat 18 Jul')
    // Each number once: the weeks and the status, no percent beside them.
    expect(goal.get('[data-testid="home-goal-status"]').text()).toBe('Week 2 of 10 · on track')
    expect(goal.text()).not.toContain('%')
    const action = goal.get('[data-testid="home-goal-action"]')
    expect(action.text()).toBe('View my plan')
    expect(action.attributes('href')).toBe('/student/progress/plan')
    expect(wrapper.find('[data-testid="home-plan-notice"]').exists()).toBe(false)

    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('Next in your plan · Learn · Unit 1 · Say hello')
    expect(next.text()).toContain('Introduce yourself')
    expect(next.get('.home-next-meta').text()).toBe('About 4 minutes · +10 XP')
    expect(next.text()).toContain('You’ll learn: “My name is Xiaomei.”')
    const start = next.get('[data-testid="home-continue"]')
    expect(start.text()).toBe('Start lesson')
    expect(start.attributes('href')).toBe('/student/learn/lessons/103')
    expect(start.classes()).toContain('btn-accent')
    // Numbered steps, never Chinese numerals: a check when done.
    expect(next.findAll('.home-steps li.is-done')).toHaveLength(2)
    expect(next.get('.home-steps li.is-current .home-step').text()).toBe('3')

    const todayPlan = wrapper.get('[data-testid="home-today"]')
    expect(todayPlan.get('h2').text()).toBe('Today’s plan')
    expect(todayPlan.text()).toContain('20 / 30 XP')
    expect(todayPlan.text()).toContain('Fix 6 mistakes')
    // S3.01: where the review is, about half a minute a mistake, and what it pays.
    expect(todayPlan.text()).toContain('Practice · 3 min · +10 XP')
    expect(todayPlan.text()).toContain('Talk with Xiaomei')
    expect(todayPlan.text()).not.toContain('Live')
    // The next lesson is the big card; the rail does not repeat it, and every row has one small action.
    expect(todayPlan.text()).not.toContain('Introduce yourself')
    expect(todayPlan.findAll('.home-timeline-item').every((row) => row.find('a.btn-secondary.btn-sm').exists())).toBe(
      true,
    )
    expect(todayPlan.findAll('.home-timeline-when').map((when) => when.text())).toEqual(['Then', 'Anytime'])
    expect(wrapper.find('[data-testid="home-email"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="home-build"]').exists()).toBe(false)
  })

  it('labels the unit’s lessons done, next or not open yet for screen readers', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.findAll('.home-steps li .sr-only').map((stop) => stop.text())).toEqual([
      'Lesson 1: done',
      'Lesson 2: done',
      'Lesson 3: next',
      'Lesson 4: not open yet',
    ])
    expect(wrapper.get('.home-steps li.is-current').attributes('aria-current')).toBe('step')
  })

  it('says a plan behind its week once, in the banner, with the way to adjust it', async () => {
    api.home.mockResolvedValue(homeView({ plan: planSummary({ week: 3, status: 'BEHIND', daysBehind: 2 }) }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-goal-status"]').text()).toBe('Week 3 of 10 · behind')
    expect(wrapper.get('[data-testid="home-goal"]').text()).not.toContain('study days')
    expect(wrapper.get('[data-testid="home-plan-notice"]').text()).toBe(
      'You’re 2 study days behind. Add 10 minutes today to catch up, or move your goal date to Sat 25 Jul.',
    )
    const action = wrapper.get('[data-testid="home-goal-action"]')
    expect(action.text()).toBe('Adjust plan')
    expect(action.attributes('href')).toBe('/student/progress/plan?edit=goal')
  })

  it.each([
    [
      planSummary({ status: 'BEHIND', daysBehind: 1, deadline: null }),
      'You’re 1 study day behind. Add 10 minutes today to catch up.',
    ],
    [
      planSummary({ status: 'BEHIND', daysBehind: 0 }),
      'You’re behind your plan. Add 10 minutes today to catch up, or move your goal date to Sat 25 Jul.',
    ],
  ])('words the behind banner from what the plan knows (%#)', async (plan, notice) => {
    api.home.mockResolvedValue(homeView({ plan }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-plan-notice"]').text()).toBe(notice)
  })

  it('ends the plan once its date has passed, and leads to a new goal and on in Learn', async () => {
    api.home.mockResolvedValue(homeView({ plan: planSummary({ deadline: '2026-05-16' }) }))
    const { wrapper } = await mountPage()

    const goal = wrapper.get('[data-testid="home-goal"]')
    expect(goal.text()).toContain('Travel to Shanghai · was due Sat 16 May')
    expect(goal.get('[data-testid="home-goal-status"]').text()).toBe('Plan ended')
    const action = goal.get('[data-testid="home-goal-action"]')
    expect(action.text()).toBe('Set a new goal')
    expect(action.attributes('href')).toBe('/student/progress/plan?edit=goal')
    expect(wrapper.get('[data-testid="home-plan-notice"]').text()).toBe(
      'Your goal date has passed. Pick a new date or a new goal, and your plan picks up from where you are.',
    )
    expect(wrapper.get('[data-testid="home-next"] .page-eyebrow').text()).toBe('Next in Learn · Unit 1 · Say hello')
    expect(wrapper.get('[data-testid="home-today"] h2').text()).toBe('Today')
  })

  it('keeps the plan running on its goal date itself', async () => {
    api.home.mockResolvedValue(homeView({ plan: planSummary({ deadline: '2026-05-18' }) }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-goal-status"]').text()).toBe('Week 2 of 10 · on track')
  })

  // The path goes on past the plan's last unit (the plan is finished, or it ends before the path does).
  it.each([
    ['en', 'Next in Learn · Unit 1 · Say hello', 'Next in your plan'],
    ['ms', 'Seterusnya dalam Belajar · Unit 1 · Say hello', 'Seterusnya dalam pelan anda'],
    ['zh-CN', '学习的下一步 · 第 1 单元 · Say hello', '计划的下一步'],
  ] as const)('does not call a lesson past the plan “next in your plan” (%s)', async (lang, shown, notShown) => {
    const view = homeView()
    api.home.mockResolvedValue({ ...view, next: { ...view.next!, inPlan: false } })
    const { wrapper } = await mountPage()
    i18n.global.locale.value = lang
    await flushPromises()

    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain(shown)
    expect(next.text()).not.toContain(notShown)
    // The plan itself is still there, on the strip.
    expect(wrapper.find('[data-testid="home-goal"]').exists()).toBe(true)
  })

  it('shows the goal on the top strip and today’s first steps in the rail on the first day with a plan', async () => {
    api.home.mockResolvedValue(
      homeView({
        firstTime: true,
        today: FIRST_DAY,
        plan: planSummary({ week: 1, totalWeeks: 9, status: 'STARTS_TODAY' }),
      }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Welcome to MandarinSpark, Amira')
    // The same strip as any other day (S3.03), and no goal card in the rail.
    const goal = wrapper.get('.home-strip[data-testid="home-goal"]')
    expect(goal.text()).toContain('Travel to Shanghai · by Sat 18 Jul')
    expect(goal.get('[data-testid="home-goal-status"]').text()).toBe('Week 1 of 9 · just started')
    const action = goal.get('[data-testid="home-goal-action"]')
    expect(action.text()).toBe('View my plan')
    expect(action.attributes('href')).toBe('/student/progress/plan')
    expect(wrapper.findAll('[data-testid="home-goal"]')).toHaveLength(1)
    expect(wrapper.get('.home-rail').text()).not.toContain('Your goal')
    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('First in your plan · Learn · Unit 1')
    expect(next.text()).toContain('A quick review:')
    expect(next.get('[data-testid="home-continue"]').text()).toBe('Start Unit 1')

    const todayPlan = wrapper.get('[data-testid="home-today"]')
    expect(todayPlan.get('h2').text()).toBe('Today’s plan')
    expect(todayPlan.text()).toContain('0 / 20 XP')
    expect(todayPlan.findAll('.home-timeline-when').map((when) => when.text())).toEqual(['Then', 'Anytime', 'Optional'])
    expect(todayPlan.text()).toContain('Listening set: At the Café')
    expect(todayPlan.text()).toContain('Say hi to Xiaomei')
    expect(todayPlan.text()).toContain('Travel Mandarin')
    expect(todayPlan.text()).toContain('Class · best after Unit 3 · no XP')
    const find = todayPlan.findAll('a').at(-1)!
    expect(find.text()).toBe('Find classes')
    expect(find.attributes('href')).toBe('/student/classes/find')
  })

  it.each([
    [planSummary({ deadline: null, week: 1, totalWeeks: 8 }), 'Travel to Shanghai · no date', 'Week 1 of 8'],
    [
      planSummary({ deadline: '2026-06-01', milestoneDate: '2026-06-01', week: 1, totalWeeks: 2 }),
      'Travel to Shanghai · first milestone by Mon 1 Jun',
      'Week 1 of 2',
    ],
    [planSummary({ week: 1, totalWeeks: 9 }), 'Travel to Shanghai · by Sat 18 Jul', 'Week 1 of 9'],
  ])('names the first week of each kind of plan on the strip (%#)', async (plan, line, week) => {
    api.home.mockResolvedValue(
      homeView({ firstTime: true, today: FIRST_DAY, plan: { ...plan, status: 'STARTS_TODAY' } }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-goal"] .home-strip-title').text()).toBe(line)
    expect(wrapper.get('[data-testid="home-goal-status"]').text()).toBe(`${week} · just started`)
  })

  it('starts a first-time learner past the plan from “Start here”, not “First in your plan”', async () => {
    const view = homeView()
    api.home.mockResolvedValue({ ...view, firstTime: true, next: { ...view.next!, inPlan: false } })
    const { wrapper } = await mountPage()

    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('Start here · Learn · Unit 1')
    expect(next.text()).not.toContain('First in your plan')
  })

  it('offers the plan, the daily goal and a first week without a plan, and confirms the e-mail address', async () => {
    api.home.mockResolvedValue(
      homeView({
        firstTime: true,
        plan: null,
        setupState: 'SKIPPED',
        email: { email: 'new@example.com', confirmed: false },
        today: FIRST_DAY,
      }),
    )
    api.setDailyGoal.mockResolvedValue({ dailyGoalXp: 30 })
    api.resend.mockResolvedValue({ email: 'new@example.com', confirmed: false })
    const { wrapper } = await mountPage()

    const build = wrapper.get('[data-testid="home-build"]')
    expect(build.text()).toContain('Build your learning plan')
    // No duration: the three minutes are the whole setup, not the level check (S3.04).
    expect(build.text()).toContain('Tell us your goal and take a short level check.')
    expect(build.text()).not.toContain('minutes')
    expect(wrapper.get('[data-testid="home-next"]').text()).toContain('Start here · Learn · Unit 1')
    const week = wrapper.get('[data-testid="home-week"]').text()
    expect(week).toContain('Join a class')
    expect(week).toContain('Classes · from the MandarinSpark team · no XP')
    expect(wrapper.find('[data-testid="home-goal"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="home-today"]').exists()).toBe(false)

    await wrapper.get('.filter-chips button:nth-child(3)').trigger('click')
    await flushPromises()
    expect(api.setDailyGoal).toHaveBeenCalledWith(30)
    expect(api.home).toHaveBeenCalledTimes(2)

    const banner = wrapper.get('[data-testid="home-email"]')
    expect(banner.text()).toContain('we sent a link to new@example.com')
    await banner.get('[data-testid="send-again"]').trigger('click')
    await flushPromises()
    expect(api.resend).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="toast"]').text()).toContain('We sent a new link to new@example.com.')
  })

  it('shows today without a plan on a later day, next to the offer to build one', async () => {
    api.home.mockResolvedValue(homeView({ plan: null, setupState: 'SKIPPED' }))
    const { wrapper } = await mountPage()

    expect(wrapper.find('[data-testid="home-build"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="home-today"] h2').text()).toBe('Today')
    expect(wrapper.get('[data-testid="home-next"] .page-eyebrow').text()).toBe('Next in Learn · Unit 1 · Say hello')
  })

  it('removes the resend prompt when the email was already confirmed in another tab', async () => {
    api.home.mockResolvedValue(homeView({ email: { email: 'new@example.com', confirmed: false } }))
    api.resend.mockRejectedValueOnce({
      response: { status: 400, data: { message: 'Your email address is already confirmed.' } },
    })
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="send-again"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="home-email"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('shows the confirmed daily goal even if the following Home refresh fails', async () => {
    api.home
      .mockResolvedValueOnce(homeView({ firstTime: true, plan: null, setupState: 'SKIPPED', today: FIRST_DAY }))
      .mockRejectedValueOnce(new Error('offline'))
    api.setDailyGoal.mockResolvedValue({ dailyGoalXp: 30 })
    const { wrapper } = await mountPage()
    const goal = wrapper.get('.filter-chips button:nth-child(3)')
    await goal.trigger('click')
    await flushPromises()

    expect(goal.attributes('aria-pressed')).toBe('true')
  })

  it('says it is offline with a way to try again, and reads today again when the connection returns', async () => {
    const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    try {
      const { wrapper } = await mountPage()

      const offline = wrapper.get('[data-testid="home-offline"]')
      expect(offline.text()).toContain('You’re offline. Lessons and practice need a connection.')
      expect(offline.attributes('role')).toBeUndefined()
      expect(offline.get('.home-notice-copy').attributes('role')).toBe('status')
      const reloading = deferred<HomeView>()
      api.home.mockReturnValueOnce(reloading.promise)
      const retry = offline.get<HTMLButtonElement>('[data-testid="home-retry"]')
      await retry.trigger('click')
      await flushPromises()
      expect(api.home).toHaveBeenCalledTimes(2)
      // The page keeps what it had while the retry request is still in flight.
      expect(wrapper.find('[data-testid="home-next"]').exists()).toBe(true)
      expect(retry.element.disabled).toBe(true)

      reloading.resolve(homeView())
      await flushPromises()
      expect(retry.element.disabled).toBe(false)

      onLine.mockReturnValue(true)
      window.dispatchEvent(new Event('online'))
      await flushPromises()
      expect(wrapper.find('[data-testid="home-offline"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="toast"]').text()).toBe('You’re back online.')
      expect(api.home).toHaveBeenCalledTimes(3)
    } finally {
      onLine.mockRestore()
    }
  })

  it('surfaces a non-destructive refresh error with Retry and omits the back-online toast if reload fails', async () => {
    const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    try {
      const { wrapper } = await mountPage()
      api.home.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(homeView())

      onLine.mockReturnValue(true)
      window.dispatchEvent(new Event('online'))
      await flushPromises()

      expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="home-next"]').exists()).toBe(true)
      await wrapper.get('.data-notice button').trigger('click')
      await flushPromises()
      expect(wrapper.find('.data-notice').exists()).toBe(false)
    } finally {
      onLine.mockRestore()
    }
  })

  const WAIT_A_MINUTE = 'We just sent a link. Please wait a minute before asking for another one.'
  const TRY_TOMORROW = 'You asked for several links today. Please try again tomorrow.'

  // The server lets both limits of "Send the link again" through (not the general "too often" sentence).
  it.each([
    ['en', WAIT_A_MINUTE, WAIT_A_MINUTE],
    ['zh-CN', WAIT_A_MINUTE, '我们刚刚发送了链接。请等一分钟后再申请新的链接。'],
    ['ms', TRY_TOMORROW, 'Anda telah meminta beberapa pautan hari ini. Sila cuba lagi esok.'],
  ])('says which limit stopped “Send the link again” (%s)', async (lang, sent, shown) => {
    api.home.mockResolvedValue(homeView({ email: { email: 'new@example.com', confirmed: false } }))
    api.resend.mockRejectedValueOnce({ response: { status: 429, data: { message: sent } } })
    const { wrapper } = await mountPage()
    document.documentElement.lang = lang

    await wrapper.get('[data-testid="send-again"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="home-email"] [role="alert"]').text()).toBe(shown)
    document.documentElement.lang = 'en'
  })

  it('makes an open level checkpoint the next step once every lesson of the level is done', async () => {
    const base = homeView()
    api.home.mockResolvedValue({
      ...base,
      next: { ...base.next!, lessonId: 400, title: 'Level 1 checkpoint', kind: 'CHECKPOINT', phrase: null },
    })
    const { wrapper } = await mountPage()

    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('Level checkpoint · about 4 minutes · +10 XP')
    expect(next.get('[data-testid="home-continue"]').text()).toBe('Take the checkpoint')
    expect(next.get('[data-testid="home-continue"]').attributes('href')).toBe('/student/learn/lessons/400')
  })

  // The checkpoint is the gate of its level: it is named by the level, and its steps are the level's (done) units.
  it.each([
    ['en', true, 'Next in your plan · Learn · Level 1', 'Units of Level 1', 'Unit 2: done'],
    ['en', false, 'Next in Learn · Level 1', 'Units of Level 1', 'Unit 2: done'],
    ['ms', true, 'Seterusnya dalam pelan anda · Belajar · Tahap 1', 'Unit-unit Tahap 1', 'Unit 2: selesai'],
    ['zh-CN', false, '学习的下一步 · 第 1 级', '第 1 级的单元', '第 2 单元：已完成'],
  ] as const)(
    'shows a checkpoint with its level and its units (%s, in plan: %s)',
    async (lang, inPlan, eyebrow, label, unit) => {
      const base = homeView()
      const units = [1, 2, 3, 4].map((number) => ({
        id: 10 + number,
        position: number,
        state: 'DONE' as const,
        current: false,
      }))
      api.home.mockResolvedValue({
        ...base,
        next: {
          ...base.next!,
          lessonId: 400,
          title: 'Level 1 checkpoint',
          kind: 'CHECKPOINT',
          level: 1,
          unitNumber: 4,
          unitTitle: 'Questions and counting',
          phrase: null,
          stops: units,
          inPlan,
        },
      })
      const { wrapper } = await mountPage()
      i18n.global.locale.value = lang
      await flushPromises()

      const next = wrapper.get('[data-testid="home-next"]')
      expect(next.get('.page-eyebrow').text()).toBe(eyebrow)
      expect(next.get('.home-steps').attributes('aria-label')).toBe(label)
      expect(next.findAll('.home-steps li .sr-only').map((step) => step.text())).toContain(unit)
      expect(next.text()).not.toContain('Unit 4 ·')
    },
  )

  it('sends a new learner who has not seen the setup to it', async () => {
    api.home.mockResolvedValue(homeView({ setupState: 'NOT_STARTED', plan: null, firstTime: true }))
    const { router } = await mountPage()

    expect(router.currentRoute.value.name).toBe('student-setup')
  })

  it('shows a retry when Home cannot load', async () => {
    api.home.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('Home could not load')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Welcome back, Amira')
  })

  it('keeps a completed plan complete even after its goal date has passed', async () => {
    api.home.mockResolvedValue(homeView({ plan: planSummary({ status: 'COMPLETE', deadline: '2026-05-01' }) }))
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-goal-status"]').text()).toContain('plan complete')
    expect(wrapper.get('[data-testid="home-today"] h2').text()).toBe('Today')
    expect(wrapper.get('[data-testid="home-next"] .page-eyebrow').text()).toContain('Next in Learn')
    expect(wrapper.find('[data-testid="home-plan-notice"]').exists()).toBe(false)
  })

  it('shows a first milestone date on the goal strip even when the plan has no overall deadline', async () => {
    api.home.mockResolvedValue(
      homeView({ plan: planSummary({ deadline: null, milestoneDate: '2026-06-01', week: 1, totalWeeks: 4 }) }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-goal"] .home-strip-title').text()).toBe(
      'Travel to Shanghai · first milestone by Mon 1 Jun',
    )
  })
})
