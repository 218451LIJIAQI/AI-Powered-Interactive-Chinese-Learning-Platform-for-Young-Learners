import { describe, expect, it, vi } from 'vitest'

import { learningPlan } from '@/test/planFixtures'

import {
  deadlinePassed,
  deadlineReady,
  draftFromPlan,
  earliestDeadline,
  emptyDraft,
  goalInput,
  latestDeadline,
  sameGoal,
  toggledFocus,
  toggledGoals,
} from './goalDraft'

describe('goal draft', () => {
  it('keeps "Not sure yet" on its own', () => {
    expect(toggledGoals(['ORDER_FOOD'], 'NOT_SURE')).toEqual(['NOT_SURE'])
    expect(toggledGoals(['NOT_SURE'], 'ORDER_FOOD')).toEqual(['ORDER_FOOD'])
    expect(toggledGoals(['ORDER_FOOD', 'GO_SHOPPING'], 'ORDER_FOOD')).toEqual(['GO_SHOPPING'])
  })

  it('allows two focus skills at most', () => {
    expect(toggledFocus(['LISTENING'], 'SPEAKING')).toEqual(['LISTENING', 'SPEAKING'])
    expect(toggledFocus(['LISTENING', 'SPEAKING'], 'READING')).toBeNull()
    expect(toggledFocus(['LISTENING', 'SPEAKING'], 'LISTENING')).toEqual(['SPEAKING'])
  })

  it('starts with 1–2 hours a day and no date chosen yet', () => {
    expect(emptyDraft()).toMatchObject({ pace: 'HOURS_1_TO_2', deadline: undefined })
    expect(deadlineReady(emptyDraft())).toBe(false)
    expect(() => goalInput(emptyDraft())).toThrow()
  })

  it('goes on once a date is picked or "No date" is chosen, and sends no date for "No date"', () => {
    const picked = { ...emptyDraft(), reason: 'TRAVEL' as const, deadline: '2026-07-18' }
    expect(deadlineReady(picked)).toBe(true)
    expect(goalInput(picked).deadline).toBe('2026-07-18')

    const none = { ...emptyDraft(), reason: 'TRAVEL' as const, deadline: null }
    expect(deadlineReady(none)).toBe(true)
    expect(goalInput(none).deadline).toBeNull()
  })

  it('sends own words only when there are some, and the reason text only for "Something else"', () => {
    const input = goalInput({
      ...emptyDraft(),
      reason: 'TRAVEL',
      reasonText: 'ignored',
      goalText: '   ',
      deadline: null,
      pace: 'UNDER_30_MIN',
    })
    expect(input).toMatchObject({
      reason: 'TRAVEL',
      reasonText: null,
      goalText: null,
      deadline: null,
      pace: 'UNDER_30_MIN',
    })
    expect(goalInput({ ...emptyDraft(), reason: 'OTHER', reasonText: ' My partner ', deadline: null }).reasonText).toBe(
      'My partner',
    )
  })

  it('edits a plan’s goal with its date and daily time', () => {
    const draft = draftFromPlan(learningPlan({ today: '2026-05-18' }))
    expect(draft).toMatchObject({ reason: 'TRAVEL', deadline: '2026-07-18', pace: 'HOURS_1_TO_2' })
    expect(goalInput(draft).deadline).toBe('2026-07-18')
  })

  it.each([
    ['the day after it', '2026-07-19'],
    ['the day itself', '2026-07-18'],
  ])('leaves a goal date that has passed unpicked, so a new one is chosen (%s)', (_when, today) => {
    const draft = draftFromPlan(learningPlan({ today }))
    expect(draft).toMatchObject({ deadline: undefined })
    expect(deadlineReady(draft)).toBe(false)
    expect(deadlinePassed('2026-07-18', today)).toBe(true)
    expect(deadlinePassed('2026-07-18', '2026-07-17')).toBe(false)
    expect(deadlinePassed(null, today)).toBe(false)
  })

  it('tells whether a draft still asks for the plan’s own goal', () => {
    const plan = learningPlan({ today: '2026-05-18' })
    const own = draftFromPlan(plan)
    expect(sameGoal(own, draftFromPlan(plan))).toBe(true)
    // The order of the picks does not count; a pick, the pace or the date does.
    expect(sameGoal({ ...own, goals: [...own.goals].reverse(), focus: [...own.focus].reverse() }, own)).toBe(true)
    expect(sameGoal({ ...own, pace: 'UNDER_30_MIN' }, own)).toBe(false)
    expect(sameGoal({ ...own, goals: toggledGoals(own.goals, 'GO_SHOPPING') }, own)).toBe(false)
    expect(sameGoal({ ...own, deadline: null }, own)).toBe(false)
    // Text the server would not keep is no change.
    expect(sameGoal({ ...own, goalText: `${own.goalText}  ` }, own)).toBe(true)

    // A passed date is left unpicked; choosing "No date" is a change although neither sends a date.
    const passed = draftFromPlan(learningPlan({ today: '2026-07-19' }))
    expect(sameGoal({ ...passed, deadline: null }, passed)).toBe(false)
  })

  it('keeps "No date" for a plan without a date', () => {
    const plan = learningPlan()
    expect(draftFromPlan({ ...plan, goal: { ...plan.goal, deadline: null } })).toMatchObject({
      deadline: null,
    })
  })

  /**
   * London at 20:00 on 14 November is already 15 November on the platform clock (Singapore): the server checks the
   * goal date against the 15th, so the page does too, whatever the browser's own day.
   */
  it('checks the goal date against the platform’s day, not the browser’s', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 14, 20, 0))
    const plan = learningPlan({ today: '2026-11-15', goal: { ...learningPlan().goal, deadline: '2026-11-15' } })

    expect(deadlinePassed(plan.goal.deadline, plan.today)).toBe(true)
    expect(draftFromPlan(plan)).toMatchObject({ deadline: undefined })
    expect(earliestDeadline(plan.today)).toBe('2026-11-16')
    expect(latestDeadline(plan.today)).toBe('2028-11-14')

    // And a browser a day ahead of the platform keeps a goal date that is still to come.
    vi.setSystemTime(new Date(2026, 10, 16, 1, 0))
    expect(deadlinePassed('2026-11-16', '2026-11-15')).toBe(false)
    expect(draftFromPlan({ ...plan, goal: { ...plan.goal, deadline: '2026-11-16' } })).toMatchObject({
      deadline: '2026-11-16',
    })
  })
})
