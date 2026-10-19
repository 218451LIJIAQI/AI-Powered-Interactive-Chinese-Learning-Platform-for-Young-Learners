import { describe, expect, it } from 'vitest'

import { teacherPracticeSubmissions } from '@/demo/data'

import {
  PRACTICE_FEEDBACK_MAX_LENGTH,
  PRACTICE_REVIEW_FILTERS,
  matchesPracticeReviewFilter,
  pagePracticeSubmissions,
  practiceMediaKind,
  replacePracticeSubmission,
  validatePracticeFeedbackMessage,
} from './teacherPracticeReview'

describe('teacher practice review rules', () => {
  it('defaults to submissions awaiting review', () => {
    expect(PRACTICE_REVIEW_FILTERS[0]).toBe('SUBMITTED')
    expect(matchesPracticeReviewFilter({ reviewStatus: 'REVIEWED' }, 'SUBMITTED')).toBe(false)
    expect(matchesPracticeReviewFilter({ reviewStatus: 'REVIEWED' }, 'ALL')).toBe(true)
  })

  it('validates the feedback message like the backend (5..2000 characters)', () => {
    expect(validatePracticeFeedbackMessage('   ')).toBe('required')
    expect(validatePracticeFeedbackMessage(' Good ')).toBe('tooShort')
    expect(validatePracticeFeedbackMessage('Good tone')).toBeNull()
    expect(validatePracticeFeedbackMessage('a'.repeat(PRACTICE_FEEDBACK_MAX_LENGTH))).toBeNull()
    expect(validatePracticeFeedbackMessage('a'.repeat(PRACTICE_FEEDBACK_MAX_LENGTH + 1))).toBe('tooLong')
  })

  it('picks the media player from the submission type', () => {
    expect(practiceMediaKind({ submissionType: 'PRONUNCIATION_AUDIO', contentType: 'audio/webm' })).toBe('audio')
    expect(practiceMediaKind({ submissionType: 'HANDWRITING_IMAGE', contentType: 'image/png' })).toBe('image')
  })

  it('pages preview fixtures by review status, newest first', () => {
    const awaiting = pagePracticeSubmissions(teacherPracticeSubmissions, 'SUBMITTED', 0, 1)
    expect(awaiting.totalItems).toBe(2)
    expect(awaiting.totalPages).toBe(2)
    expect(awaiting.items.map((item) => item.id)).toEqual([1])
    expect(pagePracticeSubmissions(teacherPracticeSubmissions, 'SUBMITTED', 9, 1).page).toBe(1)
    expect(pagePracticeSubmissions(teacherPracticeSubmissions, 'ALL', 0, 10).items).toHaveLength(3)
    expect(pagePracticeSubmissions([], 'REVIEWED', 0, 5)).toMatchObject({ items: [], totalPages: 1, totalItems: 0 })
  })

  it('replaces the reviewed item with the server response', () => {
    const page = pagePracticeSubmissions(teacherPracticeSubmissions, 'ALL', 0, 10)
    const reviewed = { ...page.items[0], reviewStatus: 'REVIEWED' as const, reviewedAt: '2026-05-19T00:00:00Z' }
    const next = replacePracticeSubmission(page, reviewed)
    expect(next.items[0]).toBe(reviewed)
    expect(next.items[1]).toBe(page.items[1])
  })
})
