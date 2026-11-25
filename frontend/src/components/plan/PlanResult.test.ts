import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { learningPlan } from '@/test/planFixtures'
import type { LearningPlan } from '@/types/plan'

const xp = vi.hoisted(() => ({ dailyGoal: vi.fn(), setDailyGoal: vi.fn() }))
vi.mock('@/api/learn', () => ({ xpApi: xp }))

import PlanResult from './PlanResult.vue'

function mountResult(plan: LearningPlan = learningPlan(), skippedCheck = false) {
  return mount(PlanResult, {
    props: { plan, skippedCheck },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
}

const NEW_LEVEL = { placed: 'NEW_TO_MANDARIN', target: 'BEGINNER_1', knownSkills: [], newSkills: [] } as const

describe('PlanResult', () => {
  beforeEach(() => {
    xp.dailyGoal.mockReset()
    xp.setDailyGoal.mockReset()
    xp.dailyGoal.mockResolvedValue({ dailyGoalXp: 20 })
  })

  it('leads with the level and shows what the learner can do and will learn at once, without a toggle', () => {
    const wrapper = mountResult()

    expect(wrapper.get('.plan-result-level').text()).toBe('HSK 1 (Basic)')
    expect(wrapper.get('[data-testid="plan-result-known"] h4').text()).toBe('You can already')
    expect(wrapper.get('[data-testid="plan-result-known"]').text()).toContain('Recognise common words')
    expect(wrapper.get('[data-testid="plan-result-learn"] h4').text()).toBe('For your trip, you’ll learn to')
    expect(wrapper.get('[data-testid="plan-result-learn"]').text()).toContain('Catch what you hear, word by word')
  })

  it('names the goal with its date and the daily time of the typical week', () => {
    const wrapper = mountResult()

    expect(wrapper.text()).toContain('Travel to Shanghai')
    expect(wrapper.get('[data-testid="plan-result-when"]').text()).toBe('by Sat 18 Jul 2026')
    const week = wrapper.findAll('.plan-result-week-rows > div')[0]!
    expect(week.get('dt').text()).toBe('1–2 hours a day')
    expect(week.get('dd').text()).toBe('Learn lessons, plus your class from week 1')
  })

  it('asks the learner to confirm SparkBot’s summary, which can be reported', () => {
    const wrapper = mountResult()

    expect(wrapper.get('.plan-result-quote').text()).toContain('You want enough Mandarin for your trip')
    expect(wrapper.get('.plan-result-ai').text()).toContain('AI-assisted summary. AI can make mistakes. Is this right?')
    expect(wrapper.get('[data-testid="plan-report"]').text()).toBe('Report')
  })

  it('says a plan without a date follows the learner’s pace', () => {
    const plan = learningPlan()
    const wrapper = mountResult({ ...plan, goal: { ...plan.goal, deadline: null } })

    expect(wrapper.get('[data-testid="plan-result-when"]').text()).toBe('No date · at your own pace')
  })

  it('explains a first milestone for a date that is too close, and asks about SparkBot’s milestone', () => {
    const plan = learningPlan()
    const wrapper = mountResult({
      ...plan,
      goal: { ...plan.goal, deadline: '2026-06-01' },
      weeks: { ...plan.weeks, total: 2 },
      milestone: {
        date: '2026-06-01',
        text: 'First milestone: order food and ask for directions.',
        stepPositions: [1],
      },
    })

    expect(wrapper.get('[data-testid="plan-result-when"]').text()).toBe('by Mon 1 Jun 2026')
    expect(wrapper.get('[data-testid="plan-result-milestone"]').text()).toBe(
      '14 days is short, so we set a first milestone.',
    )
    expect(wrapper.get('.plan-result-quote').text()).toBe('“First milestone: order food and ask for directions.”')
    expect(wrapper.get('.plan-result-ai').text()).toContain(
      'AI-suggested first milestone. AI can make mistakes. Is this right?',
    )
    expect(wrapper.get('[data-testid="plan-result-learn"] h4').text()).toBe('For your first milestone, you’ll learn to')
    expect(wrapper.findAll('.plan-result-week-rows dd')[0]!.text()).toBe(
      'Learn lessons first, then a class after your milestone',
    )
  })

  it('writes a rule-based milestone from the plan’s own steps, with no AI label', () => {
    const plan = learningPlan()
    const wrapper = mountResult({
      ...plan,
      aiAssisted: false,
      goal: { ...plan.goal, summary: null, deadline: '2026-06-01' },
      milestone: { date: '2026-06-01', text: null, stepPositions: [1] },
    })

    expect(wrapper.get('.plan-result-quote').text()).toBe('First milestone by Mon 1 Jun: Say hello.')
    expect(wrapper.find('.plan-result-ai').exists()).toBe(false)
  })

  it('shows the ambitious-goal milestone reason when weeks fit the deadline, keeps AI report when milestone text is null, and caps known skills at three', () => {
    const plan = learningPlan()
    const wrapper = mountResult({
      ...plan,
      aiAssisted: true,
      weeks: { current: 1, total: 10, content: 8 },
      milestone: { date: '2026-07-18', text: null, stepPositions: [1] },
      level: {
        ...plan.level,
        knownSkills: ['VOCABULARY', 'LISTENING', 'CHARACTERS', 'SENTENCES'],
      },
    })

    expect(wrapper.get('[data-testid="plan-result-milestone"]').text()).toContain('ambitious')
    expect(wrapper.get('.plan-result-ai').text()).toContain('AI-assisted plan. AI can make mistakes. Is this right?')
    expect(wrapper.find('[data-testid="plan-report"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="plan-result-known"] li')).toHaveLength(3)
  })

  it('omits the trailing colon when a rule-based milestone has no matching step positions', () => {
    const plan = learningPlan()
    const wrapper = mountResult({
      ...plan,
      aiAssisted: false,
      goal: { ...plan.goal, summary: null, deadline: '2026-06-01' },
      milestone: { date: '2026-06-01', text: null, stepPositions: [] },
    })

    expect(wrapper.get('.plan-result-quote').text()).toBe('First milestone by Mon 1 Jun.')
  })

  it('names the weekly practice of a reading focus as the server picks it: flashcard sets in Practice', async () => {
    const plan = learningPlan()
    const wrapper = mountResult({ ...plan, goal: { ...plan.goal, focus: ['READING'] } })
    const reading = () => wrapper.findAll('.plan-result-week-rows > div').at(-1)!
    const lines = () => [reading().get('dt').text(), reading().get('dd').text()]

    expect(lines()).toEqual(['+ flashcard sets each week', 'In Practice, for your reading focus'])
    i18n.global.locale.value = 'ms'
    await wrapper.vm.$nextTick()
    expect(lines()).toEqual(['+ set kad imbas setiap minggu', 'Dalam Latihan, untuk fokus membaca anda'])
    i18n.global.locale.value = 'zh-CN'
    await wrapper.vm.$nextTick()
    expect(lines()).toEqual(['+ 每周闪卡练习', '在练习中，针对阅读重点'])
  })

  it('says a learner who skipped the level check starts from the first lesson and can take it later (S1.25)', () => {
    const wrapper = mountResult({ ...learningPlan(), level: { ...NEW_LEVEL } }, true)

    expect(wrapper.get('.plan-result-level').text()).toBe('Pre-HSK (New)')
    expect(wrapper.find('[data-testid="plan-result-known"]').exists()).toBe(false)
    const skipped = wrapper.get('[data-testid="plan-result-skipped"]')
    expect(skipped.get('h4').text()).toBe('Level check skipped')
    expect(skipped.findAll('li').map((item) => item.text())).toEqual([
      'You start from the first lesson.',
      'Take the level check any time from My learning plan.',
    ])
    expect(wrapper.find('[data-testid="plan-result-learn"]').exists()).toBe(true)
  })

  it('sets the daily goal in place in the typical week, with what it means in lessons (S1.09)', async () => {
    xp.setDailyGoal.mockResolvedValue({ dailyGoalXp: 30 })
    const wrapper = mountResult()
    await flushPromises()

    const row = () => wrapper.findAll('.plan-result-week-rows > div')[1]!
    expect(row().get('dt').text()).toBe('Daily goal')
    expect(
      row()
        .findAll('.filter-chip')
        .map((chip) => chip.text()),
    ).toEqual(['10 XP', '20 XP', '30 XP'])
    expect(row().get('[data-testid="daily-goal-20"]').attributes('aria-pressed')).toBe('true')
    expect(row().get('[data-testid="plan-daily-goal-lessons"]').text()).toBe('About 2 lessons a day')

    await row().get('[data-testid="daily-goal-30"]').trigger('click')
    await flushPromises()
    expect(xp.setDailyGoal).toHaveBeenCalledWith(30)
    expect(row().get('[data-testid="daily-goal-30"]').attributes('aria-pressed')).toBe('true')
    expect(row().get('[data-testid="plan-daily-goal-lessons"]').text()).toBe('About 3 lessons a day')
    expect(wrapper.emitted('dailyGoal')).toEqual([['Daily goal set to 30 XP.']])
  })

  it('keeps the daily goal it has and says so when a new one cannot be saved', async () => {
    xp.setDailyGoal.mockRejectedValue(new Error('offline'))
    const wrapper = mountResult()
    await flushPromises()

    await wrapper.get('[data-testid="daily-goal-10"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="daily-goal-20"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('.plan-daily-goal [role="alert"]').text()).toBe('The daily goal could not be saved. Try again.')
    expect(wrapper.emitted('dailyGoal')).toBeUndefined()
  })

  it('lets learners retry loading their daily goal before choosing a new one', async () => {
    xp.dailyGoal.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ dailyGoalXp: 10 })
    const wrapper = mountResult()
    await flushPromises()

    expect(wrapper.get('[data-testid="daily-goal-20"]').attributes('disabled')).toBeDefined()
    await wrapper.get('.plan-daily-goal [role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="daily-goal-10"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="daily-goal-20"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.plan-daily-goal [role="alert"]').exists()).toBe(false)
  })

  it('names the level the learner was placed at as an HSK code with its word, in the interface language', async () => {
    const plan = learningPlan()
    const wrapper = mountResult({ ...plan, level: { ...NEW_LEVEL } })
    expect(wrapper.get('.plan-result-level').text()).toBe('Pre-HSK (New)')

    i18n.global.locale.value = 'zh-CN'
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.plan-result-level').text()).toBe('HSK 之前（零基础）')
    i18n.global.locale.value = 'ms'
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.plan-result-level').text()).toBe('Pra-HSK (Baharu)')
  })
})
