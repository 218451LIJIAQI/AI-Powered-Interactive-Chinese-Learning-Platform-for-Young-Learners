import { describe, expect, it } from 'vitest'

import { axiosResponse, expectRequests, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

import { accountOpsApi, adminOverviewApi, adminSupportApi, adminUsersApi, supportApi } from './ops'

restoreAxiosAdapterAfterEach()

describe('ops API', () => {
  it('maps the account and support endpoints', async () => {
    await expectRequests([
      [() => accountOpsApi.settings(), 'GET /users/me/account'],
      [() => accountOpsApi.session(), 'GET /users/me/session'],
      [() => accountOpsApi.resendEmailConfirmation(), 'POST /users/me/email-confirmation'],
      [() => accountOpsApi.updateProfile({ displayName: 'A', preferredLanguage: 'en' }), 'PUT /users/me/profile'],
      [() => accountOpsApi.updateProfile({ preferredLanguage: 'ms' }), 'PUT /users/me/profile'],
      [() => accountOpsApi.updatePreferences({ reviewQueues: false }), 'PUT /users/me/notification-preferences'],
      [
        () => accountOpsApi.requestEmailChange({ newEmail: 'n@b.co', currentPassword: 'p' }),
        'POST /users/me/email-change',
      ],
      [() => accountOpsApi.resendEmailChange(), 'POST /users/me/email-change/resend'],
      [() => accountOpsApi.cancelEmailChange(), 'DELETE /users/me/email-change'],
      [
        () => accountOpsApi.changePassword({ currentPassword: 'p', newPassword: 'n', confirmPassword: 'n' }),
        'POST /users/me/password',
      ],
      [() => accountOpsApi.deleteAccount('a@b.co'), 'POST /users/me/delete'],
      [() => supportApi.create({ topic: 'CLASSES', subject: 'S', message: 'M' }), 'POST /users/me/support'],
    ])
  })

  it('maps the admin users, support, dashboard, analytics and audit endpoints', async () => {
    await expectRequests([
      [() => adminUsersApi.list({ keyword: 'li', role: 'STUDENT', active: true, page: 1 }), 'GET /admin/users'],
      [() => adminUsersApi.get(2), 'GET /admin/users/2'],
      [() => adminUsersApi.deactivate(2, 'Reason'), 'POST /admin/users/2/deactivate'],
      [() => adminUsersApi.reactivate(2), 'POST /admin/users/2/reactivate'],
      [() => adminUsersApi.invites(), 'GET /admin/admin-invites'],
      [() => adminUsersApi.invite('new@b.co'), 'POST /admin/admin-invites'],
      [() => adminUsersApi.cancelInvite(4), 'DELETE /admin/admin-invites/4'],
      [() => adminUsersApi.restoreInvite(4), 'POST /admin/admin-invites/4/restore'],
      [() => adminSupportApi.queue('OPEN', 0, 20), 'GET /admin/support'],
      [() => adminSupportApi.get(9), 'GET /admin/support/9'],
      [() => adminSupportApi.reply(9, 'R'), 'POST /admin/support/9/replies'],
      [() => adminSupportApi.solve(9), 'POST /admin/support/9/solve'],
      [() => adminSupportApi.reopen(9), 'POST /admin/support/9/reopen'],
      [() => adminOverviewApi.dashboard(), 'GET /admin/dashboard'],
      [() => adminOverviewApi.analytics(30), 'GET /admin/analytics'],
      [() => adminOverviewApi.auditLog({ actor: 'olivia', page: 0 }), 'GET /admin/audit-logs'],
      [() => adminOverviewApi.auditEntry(4), 'GET /admin/audit-logs/4'],
      [() => adminOverviewApi.auditActions(), 'GET /admin/audit-logs/actions'],
      [() => adminOverviewApi.exportAuditLog({ actor: 'olivia' }), 'GET /admin/audit-logs/export'],
    ])
  })

  it('sends a learner’s report of a problem as a multipart form', async () => {
    const calls = installAxiosCapture()
    const screenshot = new File(['png'], 'screen.png', { type: 'image/png' })

    await supportApi.create({ topic: 'CLASSES', subject: 'Video', message: 'It stops', screenshot })

    const support = calls[0].data as FormData
    expect(support.get('topic')).toBe('CLASSES')
    expect(support.get('subject')).toBe('Video')
    expect(support.get('message')).toBe('It stops')
    expect(support.get('screenshot')).toBe(screenshot)

    await supportApi.create({ topic: 'ACCOUNT', subject: 'Login', message: 'Cannot sign in' })
    const withoutScreenshot = calls[1].data as FormData
    expect(withoutScreenshot.has('screenshot')).toBe(false)
  })

  it('sends query parameters for admin listings and downloads audit CSV and support attachments as blobs', async () => {
    const calls = installAxiosCapture()
    await adminUsersApi.list({ keyword: 'li', role: 'STUDENT', active: true, page: 1, size: 15 })
    await adminSupportApi.queue('OPEN', 2, 10)
    await adminOverviewApi.analytics(30)
    await adminOverviewApi.auditLog({ actor: 'olivia', page: 1 })

    expect(calls.map((c) => c.params)).toEqual([
      { keyword: 'li', role: 'STUDENT', active: true, page: 1, size: 15 },
      { status: 'OPEN', page: 2, size: 10 },
      { days: 30 },
      { actor: 'olivia', page: 1 },
    ])

    const blobCalls = installAxiosCapture((config) => ({
      ...axiosResponse(config, 200, new Blob(['ok'])),
      headers: { 'content-disposition': 'attachment; filename="audit.csv"' },
    }))

    const exported = await adminOverviewApi.exportAuditLog({ actor: 'olivia' })
    const attachment = await adminSupportApi.attachment(9)
    expect(exported.fileName).toBe('audit.csv')
    expect(attachment.fileName).toBe('audit.csv')
    expect(blobCalls).toHaveLength(2)
    expect(blobCalls).toMatchObject([
      { url: '/admin/audit-logs/export', params: { actor: 'olivia' }, responseType: 'blob' },
      { url: '/admin/support/attachments/9', params: undefined, responseType: 'blob' },
    ])
  })
})
