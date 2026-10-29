import { describe, expect, it } from 'vitest'

import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { AuditCategory } from '@/types/ops'

import { auditActionIcon, auditActionKey, auditCategoryKey } from './auditActions'

function message(key: string): unknown {
  const [namespace, name] = key.split('.')
  expect(namespace).toBe('auditActions')
  return (auditActionMessages.en as Record<string, string>)[name]
}

describe('audit action labels', () => {
  it('names the account, verification and support actions with full keys of the bundle', () => {
    for (const action of [
      'ADMIN_USER_DEACTIVATE',
      'ADMIN_TEACHER_APPROVE',
      'TEACHER_APPLICATION_SUBMITTED',
      'ADMIN_INVITE_SENT',
      'ACCOUNT_DELETED',
      'EMAIL_CHANGED',
      'SUPPORT_REPLY_SENT',
      'CONTENT_SUBMIT',
      'AI_RESPONSE_REPORT_REVIEW',
      'ADMIN_PLATFORM_SETTINGS_UPDATED',
    ]) {
      const key = auditActionKey(action)
      expect(key, action).not.toBeNull()
      expect(message(key as string), action).toEqual(expect.any(String))
    }
    expect(message(auditActionKey('ADMIN_USER_DEACTIVATE') as string)).toBe('Account deactivated')
    expect(message(auditActionKey('ADMIN_PLATFORM_SETTINGS_UPDATED') as string)).toBe('AI settings updated')
  })

  // The shared labels and their readable fallback are tested in plugins/statusLabelMessages.test.ts.
  it('leaves actions it does not name to the shared labels', () => {
    expect(auditActionKey('TEACHER_LIVE_CLASS_CREATE')).toBeNull()
  })

  it('has a label for every category', () => {
    const categories: AuditCategory[] = ['ACCOUNT', 'CONTENT', 'AI', 'SUPPORT', 'LEARNING', 'SYSTEM']
    expect(categories.map((category) => message(auditCategoryKey(category)))).toEqual([
      'Account',
      'Content',
      'AI',
      'Support',
      'Learning',
      'System',
    ])
  })

  it('picks an icon for what happened, falling back to the category icon', () => {
    expect(auditActionIcon('ADMIN_USER_DEACTIVATE', 'ACCOUNT')).toBe('lock')
    expect(auditActionIcon('ADMIN_USER_REACTIVATE', 'ACCOUNT')).toBe('unlock')
    expect(auditActionIcon('TEACHER_APPLICATION_SUBMITTED', 'ACCOUNT')).toBe('upload')
    expect(auditActionIcon('CONTENT_SUBMIT', 'CONTENT')).toBe('send')
    expect(auditActionIcon('CONTENT_REJECT', 'CONTENT')).toBe('edit')
    expect(auditActionIcon('AI_RESPONSE_REPORT_REVIEW', 'AI')).toBe('check')
    expect(auditActionIcon('SOMETHING_NEW', 'SUPPORT')).toBe('support')
  })
})
