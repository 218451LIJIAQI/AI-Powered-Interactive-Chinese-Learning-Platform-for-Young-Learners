import { describe, expect, it } from 'vitest'

import { installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

import {
  accountOpsApi,
  adminOverviewApi,
  adminSupportApi,
  adminUsersApi,
  publicOpsApi,
  supportApi,
  teacherVerificationApi,
} from './ops'
import { xpApi } from './learn'

restoreAxiosAdapterAfterEach()

type Case = [run: () => Promise<unknown>, request: string]

async function expectRequests(cases: Case[]) {
  const calls = installAxiosCapture({ items: [] })
  for (const [run] of cases) await run()
  expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual(cases.map(([, request]) => request))
}

describe('ops API', () => {
  it('maps the public links, account, support and verification endpoints', async () => {
    const file = new File(['%PDF-1.4'], 'certificate.pdf', { type: 'application/pdf' })
    await expectRequests([
      [
        () => publicOpsApi.sendHelpMessage({ email: 'a@b.co', topic: 'ACCOUNT', message: 'Hi' }),
        'POST /public/support',
      ],
      [() => publicOpsApi.checkResetLink('t'), 'POST /auth/reset-password/check'],
      [() => publicOpsApi.confirmEmail('t'), 'POST /auth/confirm-email'],
      [() => publicOpsApi.checkAdminInvite('t'), 'POST /auth/admin-invite/check'],
      [
        () => publicOpsApi.acceptAdminInvite({ token: 't', fullName: 'A', password: 'p', confirmPassword: 'p' }),
        'POST /auth/admin-invite/accept',
      ],
      [() => accountOpsApi.settings(), 'GET /users/me/account'],
      [() => accountOpsApi.updateProfile({ displayName: 'A', preferredLanguage: 'en' }), 'PUT /users/me/profile'],
      [() => accountOpsApi.updatePreferences({ feedback: false }), 'PUT /users/me/notification-preferences'],
      [
        () => accountOpsApi.requestEmailChange({ newEmail: 'n@b.co', currentPassword: 'p' }),
        'POST /users/me/email-change',
      ],
      [() => accountOpsApi.deleteAccount('a@b.co'), 'POST /users/me/delete'],
      [() => xpApi.dailyGoal(), 'GET /students/daily-goal'],
      [() => xpApi.setDailyGoal(20), 'PUT /students/daily-goal'],
      [() => supportApi.list(), 'GET /users/me/support'],
      [() => supportApi.get(3), 'GET /users/me/support/3'],
      [() => supportApi.create({ topic: 'CLASSES', subject: 'S', message: 'M' }), 'POST /users/me/support'],
      [() => supportApi.reply(3, 'R'), 'POST /users/me/support/3/replies'],
      [() => supportApi.solve(3), 'POST /users/me/support/3/solve'],
      [() => teacherVerificationApi.profile(), 'GET /teachers/me'],
      [() => teacherVerificationApi.updateProfile({ fullName: 'A', bio: 'B' }), 'PUT /teachers/me/profile'],
      [() => teacherVerificationApi.upload('CERTIFICATE', file), 'POST /teachers/verification-documents'],
      [() => teacherVerificationApi.submit(), 'POST /teachers/verification/submit'],
    ])
  })

  it('maps the admin users, support, dashboard, analytics and audit endpoints', async () => {
    await expectRequests([
      [() => adminUsersApi.list({ keyword: 'li', role: 'TEACHER', active: true, page: 1 }), 'GET /admin/users'],
      [() => adminUsersApi.get(2), 'GET /admin/users/2'],
      [() => adminUsersApi.deactivate(2, 'Reason'), 'POST /admin/users/2/deactivate'],
      [() => adminUsersApi.reactivate(2, 'Reason'), 'POST /admin/users/2/reactivate'],
      [() => adminUsersApi.invites(), 'GET /admin/admin-invites'],
      [() => adminUsersApi.invite('new@b.co'), 'POST /admin/admin-invites'],
      [() => adminUsersApi.applications(), 'GET /admin/teacher-applications'],
      [() => adminUsersApi.application(5), 'GET /admin/teacher-applications/5'],
      [() => adminUsersApi.approve(5), 'POST /admin/teachers/5/approve'],
      [() => adminUsersApi.requestChanges(5, 'Clearer scan'), 'POST /admin/teachers/5/reject'],
      [() => adminSupportApi.queue('OPEN'), 'GET /admin/support'],
      [() => adminSupportApi.get(9), 'GET /admin/support/9'],
      [() => adminSupportApi.reply(9, 'R'), 'POST /admin/support/9/replies'],
      [() => adminSupportApi.solve(9), 'POST /admin/support/9/solve'],
      [() => adminOverviewApi.dashboard(), 'GET /admin/dashboard'],
      [() => adminOverviewApi.analytics(30), 'GET /admin/analytics'],
      [() => adminOverviewApi.auditLog({ actor: 'olivia', page: 0 }), 'GET /admin/audit-logs'],
      [() => adminOverviewApi.auditEntry(4), 'GET /admin/audit-logs/4'],
      [() => adminOverviewApi.auditActions(), 'GET /admin/audit-logs/actions'],
    ])
  })

  it('sends the support request and the verification document as multipart forms', async () => {
    const calls = installAxiosCapture({})
    const screenshot = new File(['png'], 'screen.png', { type: 'image/png' })

    await supportApi.create({ topic: 'CLASSES', subject: 'Video', message: 'It stops', screenshot })
    await teacherVerificationApi.upload('IDENTITY', new File(['%PDF'], 'ic.pdf', { type: 'application/pdf' }))

    const support = calls[0].data as FormData
    expect(support.get('topic')).toBe('CLASSES')
    expect(support.get('subject')).toBe('Video')
    expect(support.get('screenshot')).toBeInstanceOf(File)
    expect((calls[1].data as FormData).get('documentType')).toBe('IDENTITY')
  })
})
