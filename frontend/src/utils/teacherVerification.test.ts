import { describe, expect, it } from 'vitest'

import { canDecideVerification, nextSelectedTeacherId } from './teacherVerification'

describe('teacher verification decision guard (A5 #2)', () => {
  const ready = {
    selectedTeacherId: 7,
    loadedDetailTeacherId: 7,
    detailLoading: false,
    readOnly: false,
    submitting: false,
  }

  it('allows a decision only when the selected applicant detail is loaded', () => {
    expect(canDecideVerification(ready)).toBe(true)
    expect(canDecideVerification({ ...ready, loadedDetailTeacherId: null })).toBe(false)
    expect(canDecideVerification({ ...ready, loadedDetailTeacherId: 3 })).toBe(false)
    expect(canDecideVerification({ ...ready, detailLoading: true })).toBe(false)
  })

  it('blocks decisions in preview / read-only mode, while submitting and without a selection', () => {
    expect(canDecideVerification({ ...ready, readOnly: true })).toBe(false)
    expect(canDecideVerification({ ...ready, submitting: true })).toBe(false)
    expect(canDecideVerification({ ...ready, selectedTeacherId: 0, loadedDetailTeacherId: 0 })).toBe(false)
  })

  it('auto-selects the first applicant only when the current one is no longer listed', () => {
    expect(nextSelectedTeacherId([4, 7, 9], 7)).toBe(7)
    expect(nextSelectedTeacherId([4, 9], 7)).toBe(4)
    expect(nextSelectedTeacherId([], 7)).toBe(0)
  })
})
