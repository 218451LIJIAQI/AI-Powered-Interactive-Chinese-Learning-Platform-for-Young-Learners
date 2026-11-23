import { describe, expect, it } from 'vitest'

import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { AuditCategory } from '@/types/ops'

import { auditActionKey, auditActionLabel, auditCategoryLabel } from './auditActions'

function message(key: string): string {
  const [namespace, group, name] = key.split('.')
  expect(namespace).toBe('auditActions')
  return (auditActionMessages.en as Record<string, Record<string, string>>)[group][name]
}

function knownActionLabel(action: string): string {
  const key = auditActionKey(action)
  expect(key, action).not.toBeNull()
  return message(key!)
}

describe('audit action labels', () => {
  it('names the account, class authoring and support actions with full keys of the bundle', () => {
    for (const action of [
      'ACCOUNT_CREATED',
      'ADMIN_USER_DEACTIVATE',
      'ADMIN_INVITE_SENT',
      'ACCOUNT_DELETED',
      'EMAIL_CHANGED',
      'EMAIL_CHANGE_CANCELLED',
      'EMAIL_CONFIRMED',
      'SUPPORT_REPLY_SENT',
      'SUPPORT_REQUEST_REOPENED',
      'AI_RESPONSE_REPORT_REVIEW',
      'ADMIN_PLATFORM_SETTINGS_UPDATED',
    ]) {
      expect(knownActionLabel(action), action).toEqual(expect.any(String))
    }
    expect(knownActionLabel('ADMIN_USER_DEACTIVATE')).toBe('Account deactivated')
    expect(knownActionLabel('ADMIN_INVITE_CANCELLED')).toBe('Admin invite cancelled')
    expect(knownActionLabel('ADMIN_PLATFORM_SETTINGS_UPDATED')).toBe('AI settings updated')
    // A decision on a report names itself (A5.08); older entries kept one action for both.
    expect(knownActionLabel('AI_RESPONSE_REPORT_RESOLVED')).toBe('AI report resolved')
    expect(knownActionLabel('AI_RESPONSE_REPORT_DISMISSED')).toBe('AI report dismissed')
    expect(knownActionLabel('AI_RESPONSE_REPORT_REVIEW')).toBe('AI report reviewed')
    expect(knownActionLabel('EMAIL_CHANGE_CANCELLED')).toBe('Email change cancelled')
  })

  it.each(
    Object.entries({
      ADMIN_CLASS_CREATE: 'Class created',
      ADMIN_CLASS_EDIT: 'Class edited',
      ADMIN_CLASS_UNIT_ADD: 'Unit added',
      ADMIN_CLASS_UNIT_RENAME: 'Unit renamed',
      ADMIN_CLASS_PUBLISH: 'Class published',
      ADMIN_CLASS_ARCHIVE: 'Class archived',
      ADMIN_CLASS_RESTORE: 'Class restored',
      ADMIN_CLASS_DELETE: 'Draft class deleted',
      // Older entries: a new draft lesson is no longer logged.
      ADMIN_LESSON_CREATE: 'Lesson created',
      ADMIN_LESSON_PUBLISH: 'Lesson published',
      ADMIN_LESSON_EDIT: 'Lesson edited',
      ADMIN_LESSON_ARCHIVE: 'Lesson archived',
      ADMIN_LESSON_RESTORE: 'Lesson restored',
    }),
  )('names what admins do to classes and lessons (%s)', (action, label) => {
    expect(knownActionLabel(action)).toBe(label)
  })

  it.each(
    Object.entries({
      PRACTICE_SET_CREATE: 'Practice activity created',
      PRACTICE_SET_UPDATE: 'Practice activity edited',
      PRACTICE_SET_PUBLISH: 'Practice activity published',
      PRACTICE_SET_UNPUBLISH: 'Practice activity unpublished',
      PRACTICE_SET_ARCHIVE: 'Practice activity archived',
      PRACTICE_SET_RESTORE: 'Practice activity restored',
      LEVEL_CHECK_QUESTION_UPDATE: 'Level check question edited',
      LEVEL_CHECK_QUESTION_PUBLISH: 'Level check question published',
      LEVEL_CHECK_QUESTION_UNPUBLISH: 'Level check question unpublished',
      ADMIN_INVITE_RESTORED: 'Admin invite restored',
    }),
  )('names the learning content changes as the audit log rules call them (A5.08) (%s)', (action, label) => {
    expect(knownActionLabel(action)).toBe(label)
  })

  it('has no names for the teacher-era actions, which read in title case', () => {
    for (const action of ['ADMIN_TEACHER_APPROVE', 'TEACHER_APPLICATION_SUBMITTED', 'ADMIN_CLASS_APPROVE']) {
      expect(auditActionKey(action), action).toBeNull()
    }
    expect(auditActionLabel((key) => key, 'ADMIN_TEACHER_APPROVE')).toBe('Admin Teacher Approve')
  })

  it('reads an action it does not name in title case', () => {
    const t = (key: string) => `[${key}]`

    expect(auditActionKey('SOMETHING_NEW')).toBeNull()
    expect(auditActionKey('constructor')).toBeNull()
    expect(auditActionLabel(t, 'SOMETHING_NEW')).toBe('Something New')
    expect(auditActionLabel(t, 'LOGOUT')).toBe('[auditActions.action.LOGOUT]')
  })

  it('has a label for every category', () => {
    const categories: AuditCategory[] = ['ACCOUNT', 'CONTENT', 'AI', 'SUPPORT', 'LEARNING', 'SYSTEM']
    expect(categories.map((category) => auditCategoryLabel(message, category))).toEqual([
      'Account',
      'Content',
      'AI',
      'Support',
      'Learning',
      'System',
    ])
  })
})
