import { describe, expect, it } from 'vitest'

import {
  accountTone,
  adminReportTone,
  adminSupportTone,
  adminVerificationTone,
  aiFeatureLabel,
  practiceStatusTone,
  teacherContentStatus,
} from './adminDisplay'

describe('aiFeatureLabel', () => {
  const translate = (key: string) => `[${key}]`

  it('names a known feature through the page translator, ignoring case and spaces', () => {
    expect(aiFeatureLabel('CHAT_TUTOR', translate)).toBe('[aiFeatures.chatTutor]')
    expect(aiFeatureLabel('  learning_plan ', translate)).toBe('[aiFeatures.learningPlan]')
  })

  it('reads an unknown feature as title case', () => {
    expect(aiFeatureLabel('SOMETHING_NEW', translate)).toBe('Something New')
  })
})

describe('admin status tones', () => {
  it('marks what waits for an admin as strong', () => {
    expect(adminVerificationTone('IN_REVIEW')).toBe('strong')
    expect(adminSupportTone('OPEN')).toBe('strong')
    expect(adminReportTone('PENDING_REVIEW')).toBe('strong')
  })

  it('marks positive ends as success and what is inactive as outline', () => {
    expect(accountTone(true)).toBe('success')
    expect(accountTone(false)).toBe('outline')
    expect(adminVerificationTone('APPROVED')).toBe('success')
    expect(adminSupportTone('SOLVED')).toBe('success')
    expect(adminReportTone('RESOLVED')).toBe('success')
    expect(practiceStatusTone('PUBLISHED')).toBe('success')
    expect(practiceStatusTone('DRAFT')).toBe('outline')
    expect(practiceStatusTone('ARCHIVED')).toBe('outline')
  })

  it('gives a teacher’s item one status, a pending update replacing Published', () => {
    expect(teacherContentStatus('PUBLISHED', null)).toEqual({
      key: 'adminClasses.content.statuses.PUBLISHED',
      tone: 'success',
    })
    expect(teacherContentStatus('PUBLISHED', 'SUBMITTED')).toEqual({
      key: 'adminClasses.content.statuses.REVISION_SUBMITTED',
      tone: 'strong',
    })
    expect(teacherContentStatus('PUBLISHED', 'REJECTED')).toEqual({
      key: 'adminClasses.content.statuses.REVISION_REJECTED',
      tone: 'outline',
    })
    expect(teacherContentStatus('SUBMITTED', null).tone).toBe('strong')
    expect(teacherContentStatus('DRAFT', null).tone).toBe('outline')
  })
})
