import { describe, expect, it } from 'vitest'

import { learningPlan } from '@/test/planFixtures'

import {
  deadlineFor,
  deadlineReady,
  draftFromPlan,
  emptyDraft,
  goalInput,
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

  it('turns a deadline choice into a date from today', () => {
    const today = new Date(2026, 4, 18)
    expect(deadlineFor('2w', '', today)).toBe('2026-06-01')
    expect(deadlineFor('2m', '', today)).toBe('2026-07-18')
    expect(deadlineFor('none', '', today)).toBeNull()
    expect(deadlineFor('date', '2026-09-01', today)).toBe('2026-09-01')
    expect(deadlineReady({ ...emptyDraft(), deadlineChoice: 'date' })).toBe(false)
    expect(deadlineReady({ ...emptyDraft(), deadlineChoice: 'none' })).toBe(true)
  })

  it('sends own words only when there are some, and the reason text only for "Something else"', () => {
    const input = goalInput({
      ...emptyDraft(),
      reason: 'TRAVEL',
      reasonText: 'ignored',
      goalText: '   ',
      deadlineChoice: 'none',
      pace: 'LIGHT',
    })
    expect(input).toMatchObject({ reason: 'TRAVEL', reasonText: null, goalText: null, deadline: null, pace: 'LIGHT' })
    expect(goalInput({ ...emptyDraft(), reason: 'OTHER', reasonText: ' My partner ' }).reasonText).toBe('My partner')
  })

  it('edits a plan’s goal with its deadline as a picked date', () => {
    const draft = draftFromPlan(learningPlan())
    expect(draft).toMatchObject({ reason: 'TRAVEL', deadlineChoice: 'date', customDate: '2026-07-05', pace: 'REGULAR' })
    expect(goalInput(draft).deadline).toBe('2026-07-05')
  })
})
