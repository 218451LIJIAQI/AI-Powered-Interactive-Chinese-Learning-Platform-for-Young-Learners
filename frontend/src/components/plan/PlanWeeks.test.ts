import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { learningPlan } from '@/test/planFixtures'
import type { LearningPlan } from '@/types/plan'

import PlanWeeks from './PlanWeeks.vue'

function weekSchedule(plan: LearningPlan, picked: number | null = null) {
  const wrapper = mount(PlanWeeks, { props: { plan, picked } })
  const rows = wrapper.findAll('.plan-rail-rows > div').map((row) => [row.get('dt').text(), row.get('dd').text()])
  return { heading: wrapper.get('h3').text(), rows }
}

describe('PlanWeeks', () => {
  it('counts the calendar weeks to the date, each Learn step with the classes after it, then the review weeks', () => {
    const { heading, rows } = weekSchedule(learningPlan())
    expect(heading).toBe('Your 10 weeks')
    expect(rows).toEqual([
      ['Wk 1', 'Review Unit 1 · Say hello + Mandarin for Campus Life'],
      ['Wk 2–5', 'Unit 2 · Food and drink + your first class'],
      ['Wk 9–10', 'Review and practise for your goal'],
    ])
  })

  it('names the optional classes in the order the learner picked, and leaves out one no longer available', () => {
    const base = learningPlan()
    const optional = base.steps[3]!
    const second = { ...optional, position: 5, refId: 23, title: 'Shopping and Prices' }
    const gone = { ...optional, position: 6, refId: 24, title: 'Old class', status: 'UNAVAILABLE' as const }
    const plan = learningPlan({ steps: [...base.steps, second, gone] })

    expect(weekSchedule(plan).rows[1]).toEqual([
      'Wk 2–5',
      'Unit 2 · Food and drink + your first class + your next class',
    ])
    // Named, each comes after the unit it is best taken after (here the unit before it in the plan).
    expect(weekSchedule(plan, 23).rows[1]).toEqual([
      'Wk 2–5',
      'Unit 2 · Food and drink, then Shopping and Prices, then Travel Mandarin',
    ])
  })

  it('runs a class alongside a unit it is not best taken after, as the class says', () => {
    const base = learningPlan()
    const travel = { ...base.steps[3]!, bestAfterUnit: 1 }
    const shopping = { ...base.steps[3]!, position: 5, refId: 23, title: 'Shopping and Prices', bestAfterUnit: 2 }
    const plan = learningPlan({ steps: [...base.steps.slice(0, 3), travel, shopping] })

    // "Unit 2, then Shopping and Prices" (best after Unit 2); "Unit 2 + Travel Mandarin" (best after Unit 1).
    expect(weekSchedule(plan, 23).rows[1]).toEqual([
      'Wk 2–5',
      'Unit 2 · Food and drink, then Shopping and Prices + Travel Mandarin',
    ])
    expect(weekSchedule(plan, 22).rows[1]).toEqual([
      'Wk 2–5',
      'Unit 2 · Food and drink + Travel Mandarin, then Shopping and Prices',
    ])
  })

  it('has no review row when the Learn steps fill every week', () => {
    const plan = learningPlan({ weeks: { current: 2, total: 8, content: 8 } })

    expect(weekSchedule(plan).rows.map(([when]) => when)).toEqual(['Wk 1', 'Wk 2–5'])
  })

  it('follows the daily time when there is no date', () => {
    const plan = learningPlan()
    const { heading } = weekSchedule({ ...plan, goal: { ...plan.goal, deadline: null } })

    expect(heading).toBe('Your weeks at 1–2 hours a day')
  })

  it('counts a first milestone’s weeks and keeps what comes after it for later', () => {
    const plan = learningPlan({
      weeks: { current: 1, total: 2, content: 8 },
      milestone: { date: '2026-06-01', text: null, stepPositions: [1] },
    })
    const { heading, rows } = weekSchedule({ ...plan, goal: { ...plan.goal, deadline: '2026-06-01' } })

    expect(heading).toBe('Your first 2 weeks')
    expect(rows).toEqual([
      ['Wk 1', 'Review Unit 1 · Say hello + Mandarin for Campus Life'],
      ['Wk 2', 'Unit 2 · Food and drink'],
      ['Later', 'your first class'],
    ])
  })

  it('gathers the Learn units after the milestone in one Later row', () => {
    const base = learningPlan()
    const unit3 = { ...base.steps[2]!, position: 5, refId: 13, unitNumber: 3, title: 'Numbers', weekFrom: 6, weekTo: 7 }
    const unit4 = { ...base.steps[2]!, position: 6, refId: 14, unitNumber: 4, title: 'Prices', weekFrom: 7, weekTo: 8 }
    const plan = learningPlan({
      steps: [...base.steps, unit3, unit4],
      weeks: { current: 1, total: 5, content: 8 },
      milestone: { date: '2026-06-21', text: null, stepPositions: [1, 3] },
    })

    expect(weekSchedule(plan).rows.at(-1)).toEqual(['Later', 'Units 3–4 + your first class'])
  })

  it('keeps an optional class after the milestone even when its anchored unit sits in the milestone window', () => {
    const base = learningPlan()
    const anchoredOptional = { ...base.steps[3]!, weekFrom: 2, weekTo: 5 }
    const plan = learningPlan({
      steps: [...base.steps.slice(0, 3), anchoredOptional],
      weeks: { current: 1, total: 2, content: 8 },
      milestone: { date: '2026-06-01', text: null, stepPositions: [1] },
    })

    expect(weekSchedule(plan).rows).toEqual([
      ['Wk 1', 'Review Unit 1 · Say hello + Mandarin for Campus Life'],
      ['Wk 2', 'Unit 2 · Food and drink'],
      ['Later', 'your first class'],
    ])
  })

  it('falls back to the unit title alone when unitNumber is null', () => {
    const base = learningPlan()
    const unnumbered = { ...base.steps[2]!, unitNumber: null }
    const plan = learningPlan({ steps: [base.steps[0]!, unnumbered] })

    expect(weekSchedule(plan).rows[1]).toEqual(['Wk 2–5', 'Food and drink'])
  })
})
