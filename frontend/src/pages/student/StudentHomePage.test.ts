import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { today } from '@/test/learnFixtures'
import { homeView, planRouter } from '@/test/planFixtures'

const api = vi.hoisted(() => ({ home: vi.fn(), resend: vi.fn(), setDailyGoal: vi.fn() }))

vi.mock('@/api/plan', () => ({
  planApi: { home: api.home },
  emailConfirmationApi: { resend: api.resend },
}))
vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  xpApi: { setDailyGoal: api.setDailyGoal },
}))

import StudentHomePage from './StudentHomePage.vue'

async function mountPage() {
  const router = await planRouter('/student/home')
  const wrapper = mount(StudentHomePage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}

describe('StudentHomePage', () => {
  beforeEach(() => {
    api.home.mockResolvedValue(homeView())
  })

  it('makes the next lesson of the plan the one big action, with the goal under it and today’s plan beside it', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Welcome back, Amira')
    const streak = wrapper.get('[data-testid="home-streak"]')
    expect(streak.text()).toContain('7')
    // The week as seals: stamped for a reached goal, outlined for today while it is still open.
    expect(streak.findAll('.learn-week-seal:not(.is-outline):not(.is-muted)')).toHaveLength(6)
    expect(streak.findAll('.learn-week-seal.is-outline')).toHaveLength(1)
    expect(streak.findAll('.learn-week-seal').at(-1)!.text()).toBe('一')
    const goal = wrapper.get('[data-testid="home-goal"]')
    expect(goal.text()).toContain('Travel to Shanghai')
    expect(goal.text()).toContain('18% of your plan · week 2 of 8 · on track')

    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('Next in your plan · Learn · Unit 1 · Say hello')
    expect(next.text()).toContain('Introduce yourself')
    expect(next.text()).toContain('Lesson 3 of 7 · about 4 minutes · +10 XP')
    expect(next.text()).toContain('You’ll learn: “My name is Xiaomei.”')
    expect(next.get('[data-testid="home-continue"]').attributes('href')).toBe('/student/learn/lessons/103')
    expect(next.findAll('.home-next-stops li.is-done')).toHaveLength(2)
    expect(next.get('.home-next-stops li.is-current').attributes('aria-label')).toBe('Lesson 3: next')

    const todayPlan = wrapper.get('[data-testid="home-today"]')
    expect(todayPlan.text()).toContain('20 / 30 XP')
    expect(todayPlan.text()).toContain('10 more XP reaches today’s goal and keeps your 7-day streak.')
    expect(todayPlan.text()).toContain('Fix 6 mistakes')
    expect(todayPlan.text()).toContain('Live session · Greetings 问候')
    expect(todayPlan.text()).toContain('With Li Wei · Mandarin for Beginners · you’re booked')
    expect(todayPlan.text()).toContain('Talk with Xiaomei')
    expect(wrapper.find('[data-testid="home-email"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="home-build"]').exists()).toBe(false)
  })

  it('shows the first week of the plan on the first day', async () => {
    api.home.mockResolvedValue(
      homeView({ firstTime: true, today: today({ todayXp: 0, currentStreak: 0, mistakesWaiting: 0 }) }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Welcome to MandarinSpark, Amira')
    // The goal is there from day one: the levels it leads between and the first week.
    expect(wrapper.get('[data-testid="home-goal"]').text()).toContain('Beginner 1 → Beginner 2 · week 2 of 8')
    expect(wrapper.find('[data-testid="home-today"]').exists()).toBe(false)
    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('First in your plan · Learn · Unit 1')
    expect(next.text()).toContain('Unit 1 is a quick review')
    expect(next.get('[data-testid="home-continue"]').text()).toBe('Start Unit 1')
    const rail = wrapper.get('[data-testid="home-week"]').text()
    expect(rail).toContain('This week')
    expect(rail).toContain('Review Say hello')
    expect(rail).toContain('Listening set: At the Café')
    expect(rail).toContain('Say hi to Xiaomei')
    expect(rail).toContain('Everyday Conversations')
    expect(rail).toContain('Class · live practice from week 2')
  })

  it('offers the plan, the daily goal and a first week without a plan, and confirms the e-mail address', async () => {
    api.home.mockResolvedValue(
      homeView({
        firstTime: true,
        plan: null,
        setupState: 'SKIPPED',
        liveToday: null,
        email: { email: 'new@example.com', confirmed: false },
        today: today({ todayXp: 0, currentStreak: 0, dailyGoalXp: 20 }),
      }),
    )
    api.setDailyGoal.mockResolvedValue({ dailyGoalXp: 30 })
    api.resend.mockResolvedValue({ email: 'new@example.com', confirmed: false })
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="home-build"]').text()).toContain('Build your learning plan')
    expect(wrapper.get('[data-testid="home-next"]').text()).toContain('Start here · Learn · Unit 1')
    expect(wrapper.get('[data-testid="home-week"]').text()).toContain('Join a class')
    expect(wrapper.find('[data-testid="home-goal"]').exists()).toBe(false)

    await wrapper.get('.home-goal-picker button:nth-child(3)').trigger('click')
    await flushPromises()
    expect(api.setDailyGoal).toHaveBeenCalledWith(30)
    expect(api.home).toHaveBeenCalledTimes(2)

    const banner = wrapper.get('[data-testid="home-email"]')
    expect(banner.text()).toContain('We sent a link to new@example.com')
    await banner.get('[data-testid="send-again"]').trigger('click')
    await flushPromises()
    expect(api.resend).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="toast"]').text()).toContain('We sent a new link to new@example.com.')
  })

  it('makes an open level checkpoint the next step once every lesson of the level is done', async () => {
    const base = homeView()
    api.home.mockResolvedValue(
      homeView({
        next: { ...base.next!, lessonId: 400, title: 'Level 1 checkpoint', kind: 'CHECKPOINT', phrase: null },
      }),
    )
    const { wrapper } = await mountPage()

    const next = wrapper.get('[data-testid="home-next"]')
    expect(next.text()).toContain('Level checkpoint · about 4 minutes · +10 XP')
    expect(next.get('[data-testid="home-continue"]').text()).toBe('Take the checkpoint')
    expect(next.get('[data-testid="home-continue"]').attributes('href')).toBe('/student/learn/lessons/400')
  })

  it('sends a new student who has not seen the setup to it', async () => {
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
})
