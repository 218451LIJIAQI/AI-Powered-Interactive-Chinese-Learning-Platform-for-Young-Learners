import { describe, expect, it } from 'vitest'

import {
  canSubmitForReview,
  contentStatusMessageKey,
  contentStatusTone,
  editReturnsToReview,
  isTeacherEditLocked,
  normalizeContentStatus,
} from './contentStatus'
import { availableReviewActions, normalizeReviewContentType, reviewContentTypeMessageKey } from './contentReview'

describe('content review states (D4, D14, D24)', () => {
  it('maps every state to a badge label and tone, showing legacy APPROVED as Archived', () => {
    expect(
      (['DRAFT', 'SUBMITTED', 'PUBLISHED', 'REJECTED', 'ARCHIVED'] as const).map((status) => [
        contentStatusMessageKey(status),
        contentStatusTone(status),
      ]),
    ).toEqual([
      ['statusDraft', 'info'],
      ['statusSubmitted', 'warning'],
      ['statusPublished', 'success'],
      ['statusRejected', 'danger'],
      ['statusArchived', 'primary'],
    ])
    expect(normalizeContentStatus('APPROVED')).toBe('ARCHIVED')
    expect(normalizeContentStatus(undefined)).toBe('DRAFT')
  })

  it('lets teachers submit drafts and rejected items, and locks items in review or archived', () => {
    expect(['DRAFT', 'REJECTED', 'SUBMITTED', 'PUBLISHED', 'ARCHIVED'].map(canSubmitForReview)).toEqual([
      true,
      true,
      false,
      false,
      false,
    ])
    expect(['DRAFT', 'REJECTED', 'SUBMITTED', 'PUBLISHED', 'ARCHIVED'].map(isTeacherEditLocked)).toEqual([
      false,
      false,
      true,
      false,
      true,
    ])
    expect(editReturnsToReview('PUBLISHED')).toBe(true)
    expect(editReturnsToReview('DRAFT')).toBe(false)
  })

  it('offers only the admin decisions the state machine allows', () => {
    expect(availableReviewActions('SUBMITTED')).toEqual(['approve', 'reject', 'archive'])
    expect(availableReviewActions('PUBLISHED')).toEqual(['reject', 'archive'])
    expect(availableReviewActions('REJECTED')).toEqual(['archive'])
    expect(availableReviewActions('ARCHIVED')).toEqual(['restore'])
    expect(availableReviewActions('APPROVED')).toEqual(['restore'])
  })

  it('accepts review types from route queries', () => {
    expect(normalizeReviewContentType('matching-game')).toBe('MATCHING_GAME')
    expect(normalizeReviewContentType(['quiz'])).toBe('QUIZ')
    expect(normalizeReviewContentType('COURSE')).toBeNull()
    expect(reviewContentTypeMessageKey('PACKAGE')).toBe('typePackage')
  })
})
