import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PageResponse } from '@/types/api'
import type { AdminInvite, AdminUserDetail, AdminUserListItem, AdminUserQuery, AuditEntry } from '@/types/ops'

import { i18n } from '@/plugins/i18n'
import { buttonNamed, deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'

const usersApi = vi.hoisted(() => ({
  list: vi.fn(),
  get: vi.fn(),
  deactivate: vi.fn(),
  reactivate: vi.fn(),
  invites: vi.fn(),
  invite: vi.fn(),
  cancelInvite: vi.fn(),
  restoreInvite: vi.fn(),
}))
vi.mock('@/api/ops', () => ({ adminUsersApi: usersApi }))

import AdminUsersPage from './AdminUsersPage.vue'

const farah: AdminUserListItem = {
  id: 7,
  fullName: 'Farah Aziz',
  email: 'farah@example.com',
  role: 'STUDENT',
  active: true,
  joinedAt: '2026-05-01T09:00:00',
}
const daniel: AdminUserListItem = {
  id: 9,
  fullName: 'Daniel Tan',
  email: 'daniel@example.com',
  role: 'STUDENT',
  active: false,
  joinedAt: '2026-05-03T09:00:00',
}
const olivia: AdminUserListItem = {
  id: 1,
  fullName: 'Olivia Tan',
  email: 'olivia@example.com',
  role: 'ADMIN',
  active: true,
  joinedAt: '2026-03-01T10:00:00',
}
const inviteForMei: AdminInvite = { id: 3, email: 'mei@example.com', sentAt: '2026-05-18T10:00:00' }

function detailOf(user: AdminUserListItem, overrides: Partial<AdminUserDetail> = {}): AdminUserDetail {
  return {
    ...user,
    deactivation: null,
    self: false,
    recentAudit: [],
    ...overrides,
  }
}

function auditEntry(id: number, actionType: string, actorEmail: string, createdAt: string): AuditEntry {
  return {
    id,
    actionType,
    category: 'ACCOUNT',
    actorName: null,
    actorEmail,
    targetLabel: 'Farah Aziz',
    targetUserId: 7,
    reason: null,
    description: null,
    createdAt,
  }
}

/** The queries of the page's list requests (the tabs ask for one row, for the number of accounts). */
function listQueries(): AdminUserQuery[] {
  return usersApi.list.mock.calls.map(([query]) => query).filter((query) => query.size === 10)
}

/** Deactivate asks for a reason in its dialog; Reactivate runs at once. */
async function submitAccessChange(wrapper: VueWrapper, action: 'deactivate' | 'reactivate', reason = 'Reviewed') {
  await wrapper.get(`[data-testid="admin-users-${action}"]`).trigger('click')
  if (action === 'reactivate') return
  await wrapper.get('[data-testid="admin-users-reason"]').setValue(reason)
  await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
}

async function fillInvite(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="admin-users-invite"]').trigger('click')
  await wrapper.get('[data-testid="admin-users-invite-email"]').setValue(inviteForMei.email)
  return wrapper.get('#admin-users-invite-form')
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    Object.values(usersApi).forEach((fn) => fn.mockReset())
    usersApi.list.mockResolvedValue(pageOf([farah, daniel]))
    usersApi.invites.mockResolvedValue([])
    usersApi.get.mockImplementation(async (id: number) => detailOf(id === farah.id ? farah : daniel))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('lists users and sends only the filters that are set', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(2)
    // The tab's count says how many there are; one page needs no pager (A2.01).
    expect(wrapper.find('nav.pagination').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Showing')
    expect(listQueries()).toEqual([{ page: 0, size: 10 }])
    // One tab, All users with the number of accounts: there are no teacher applications any more.
    expect(wrapper.findAll('.route-tab')).toHaveLength(1)
    expect(wrapper.get('[data-testid="admin-users-tab-all"] .route-tab-count').text()).toBe('2')
    expect(wrapper.text()).not.toMatch(/teacher|verification/i)
    // Roles are Learner and Admin.
    const roles = wrapper.findAll('[data-testid="admin-users-role"] option').map((option) => option.text())
    expect(roles).toEqual(['All roles', 'Learner', 'Admin'])
    // Without a choice in the address, the first user opens beside the list, not in a drawer.
    expect(wrapper.get('[data-testid="admin-users-detail"]').attributes('role')).toBeUndefined()
    expect(wrapper.get('[data-testid="admin-users-detail"]').text()).toContain('Farah Aziz')

    await wrapper.get('[data-testid="admin-users-search"]').setValue('  Farah ')
    vi.advanceTimersByTime(300)
    await flushPromises()
    await wrapper.get('[data-testid="admin-users-role"]').setValue('ADMIN')
    await flushPromises()
    await wrapper.get('[data-testid="admin-users-account"]').setValue('DEACTIVATED')
    await flushPromises()

    expect(listQueries().at(-1)).toEqual({ keyword: 'Farah', role: 'ADMIN', active: false, page: 0, size: 10 })
  })

  it('gives every column a name of its own, the chevron column the details it opens', async () => {
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    const headers = wrapper.findAll('.admin-users-table thead th').map((header) => header.text())
    expect(headers).toEqual(['User', 'Role', 'Account', 'Joined', 'Details'])
  })

  it('starts from the search, filters and page in the address, so a reload keeps the list', async () => {
    usersApi.list.mockResolvedValue(pageOf([daniel], 1, 12))
    const { wrapper, router } = await mountAtRoute(
      AdminUsersPage,
      '/admin/users?q=Dan&role=STUDENT&account=DEACTIVATED&page=2&user=9',
    )

    expect(listQueries()).toEqual([{ keyword: 'Dan', role: 'STUDENT', active: false, page: 1, size: 10 }])
    expect((wrapper.get('[data-testid="admin-users-search"]').element as HTMLInputElement).value).toBe('Dan')
    expect((wrapper.get('[data-testid="admin-users-role"]').element as HTMLSelectElement).value).toBe('STUDENT')
    expect((wrapper.get('[data-testid="admin-users-account"]').element as HTMLSelectElement).value).toBe('DEACTIVATED')
    expect(wrapper.get('nav.pagination').text()).toContain('Page 2 of 2')
    expect(router.currentRoute.value.query).toEqual({
      q: 'Dan',
      role: 'STUDENT',
      account: 'DEACTIVATED',
      page: '2',
      user: '9',
    })
  })

  it('ignores filter values in the address that are not options', async () => {
    await mountAtRoute(AdminUsersPage, '/admin/users?role=OWNER&account=maybe&page=zero')

    expect(listQueries()).toEqual([{ page: 0, size: 10 }])
  })

  it('writes the page and every filter change to the address, back to the first page', async () => {
    usersApi.list.mockImplementation(async (query: { page: number; size: number }) =>
      pageOf(query.page === 1 ? [daniel] : [farah], query.page, 11),
    )
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users')

    await wrapper.get('nav.pagination button[aria-label="Next"]').trigger('click')
    await flushPromises()
    expect(listQueries().at(-1)).toEqual({ page: 1, size: 10 })
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(router.currentRoute.value.query.user).toBe('7')

    await wrapper.get('[data-testid="admin-users-role"]').setValue('ADMIN')
    await flushPromises()
    expect(listQueries().at(-1)).toEqual({ role: 'ADMIN', page: 0, size: 10 })
    expect(router.currentRoute.value.query).toEqual({ role: 'ADMIN', user: '7' })
  })

  it('follows the address when the Users link opens the page again without a query', async () => {
    usersApi.list.mockImplementation(async (query: { page: number; size: number }) =>
      pageOf([query.page === 1 ? daniel : farah], query.page, 11),
    )
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users?q=Tan&role=STUDENT&page=2')
    expect(listQueries().at(-1)).toEqual({ keyword: 'Tan', role: 'STUDENT', page: 1, size: 10 })

    await router.push('/admin/users')
    await flushPromises()

    expect(listQueries().at(-1)).toEqual({ page: 0, size: 10 })
    expect((wrapper.get('[data-testid="admin-users-search"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-testid="admin-users-role"]').element as HTMLSelectElement).value).toBe('')
    expect(router.currentRoute.value.query).toEqual({ user: '7' })

    // Only the page in the address changes: the same list, that page.
    await router.push('/admin/users?page=2&user=7')
    await flushPromises()
    expect(listQueries().at(-1)).toEqual({ page: 1, size: 10 })

    await router.push('/admin/users?role=ADMIN&page=2&user=7')
    await flushPromises()

    expect(listQueries().at(-1)).toEqual({ role: 'ADMIN', page: 1, size: 10 })
    expect(router.currentRoute.value.query).toEqual({ role: 'ADMIN', page: '2', user: '7' })
  })

  it('steps back to the last page with accounts when its page is left empty', async () => {
    usersApi.list.mockImplementation(async (query: { page: number; size: number }) =>
      query.page >= 1 ? pageOf([], query.page, 10) : pageOf([farah, daniel], 0, 10),
    )
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users?account=ACTIVE&page=2')

    expect(listQueries().map((query) => query.page)).toEqual([1, 0])
    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('No users match')
    expect(router.currentRoute.value.query.page).toBeUndefined()
    expect(router.currentRoute.value.query.account).toBe('ACTIVE')
  })

  it('steps back a page when deactivating its last account leaves it empty', async () => {
    let deactivated = false
    usersApi.list.mockImplementation(async (query: { page: number; size: number }) => {
      const total = deactivated ? 10 : 11
      return query.page === 1 ? pageOf(deactivated ? [] : [farah], 1, total) : pageOf([daniel], 0, total)
    })
    usersApi.deactivate.mockImplementation(async () => {
      deactivated = true
      return detailOf({ ...farah, active: false })
    })
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users?account=ACTIVE&page=2&user=7')

    await submitAccessChange(wrapper, 'deactivate', 'Spam')
    await flushPromises()

    expect(listQueries().at(-1)).toEqual({ active: true, page: 0, size: 10 })
    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="admin-users-detail"]').text()).toContain('Farah Aziz')
    expect(router.currentRoute.value.query).toEqual({ account: 'ACTIVE', user: '7' })
  })

  it('opens the user named in the address (A2.09) and asks for a reason before deactivating (A2.05, A2.08)', async () => {
    usersApi.deactivate.mockResolvedValue(
      detailOf(
        { ...farah, active: false },
        {
          deactivation: {
            actorName: 'Nur Admin',
            reason: 'Repeated spam',
            at: '2026-05-10T10:00:00',
          },
        },
      ),
    )
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=7')

    expect(usersApi.get).toHaveBeenCalledWith(7)
    const detail = wrapper.get('[data-testid="admin-users-detail"]')
    expect(detail.text()).toContain('Farah Aziz')
    expect(detail.text()).toContain('Learner')
    expect(detail.text()).not.toMatch(/teacher|verification/i)
    expect(wrapper.get('[data-testid="admin-users-access-hint"]').text()).toBe(
      'Signs them out. Their progress and learning plan are kept.',
    )

    await wrapper.get('[data-testid="admin-users-deactivate"]').trigger('click')
    const dialog = wrapper.get('[data-testid="admin-users-status-dialog"]')
    expect(dialog.attributes('role')).toBe('alertdialog')
    expect(dialog.get('h2').text()).toBe('Deactivate Farah Aziz?')
    expect(dialog.text()).toContain('farah@example.com · Learner')
    expect(dialog.get('[data-testid="admin-users-status-dialog-confirm"]').classes()).toContain('btn-danger')
    expect(dialog.get('[data-testid="admin-users-status-dialog-confirm"]').text()).toBe('Deactivate')
    expect(wrapper.get('[data-testid="admin-users-impact"]').text()).toBe(
      'Farah Aziz will be signed out and can’t sign in until an admin reactivates the account.' +
        'Their progress, learning plan and class history are kept.',
    )
    expect(dialog.text()).toContain('Saved to the audit log.')
    expect(wrapper.get('[data-testid="admin-users-reason"]').attributes('placeholder')).toBe(
      'e.g. Asked by email to pause the account',
    )

    // Untouched, the reason shows no error and Deactivate can be pressed: the press is what checks it.
    expect(wrapper.find('#admin-users-reason-error').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
    expect(wrapper.get('#admin-users-reason-error').text()).toBe('Enter a reason before you deactivate this account.')
    expect(wrapper.get('[data-testid="admin-users-reason"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('[data-testid="admin-users-reason"]').attributes('aria-describedby')).toBe(
      'admin-users-reason-error admin-users-reason-hint',
    )
    expect(wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').attributes('disabled')).toBeDefined()
    expect(usersApi.deactivate).not.toHaveBeenCalled()

    // Spaces are not a reason; real text clears the error as it is typed and frees the button.
    await wrapper.get('[data-testid="admin-users-reason"]').setValue('   ')
    expect(wrapper.find('#admin-users-reason-error').exists()).toBe(true)
    await wrapper.get('[data-testid="admin-users-reason"]').setValue('  Repeated spam  ')
    expect(wrapper.find('#admin-users-reason-error').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(usersApi.deactivate).toHaveBeenCalledWith(7, 'Repeated spam')
    expect(wrapper.find('[data-testid="admin-users-status-dialog"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Farah Aziz is deactivated.')
    const reason = wrapper.get('[data-testid="admin-users-deactivation"]').text()
    expect(reason).toContain('Repeated spam')
    expect(reason).toContain('by Nur Admin')
    expect(wrapper.find('[data-testid="admin-users-reactivate"]').exists()).toBe(true)
  })

  it('links "View all" to the audit log of exactly this account, not to a search for its name', async () => {
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=9')

    const viewAll = wrapper.get('[data-testid="admin-users-audit-all"]')
    expect(viewAll.attributes('href')).toBe('/admin/audit-log?user=9')
    expect(viewAll.attributes('aria-label')).toBe('View all audit entries for Daniel Tan')
  })

  it('opens a retained account from the audit log without offering to restore its access', async () => {
    usersApi.list.mockResolvedValue(pageOf([]))
    usersApi.get.mockResolvedValue(detailOf({ ...daniel, id: 42, fullName: 'Retained account' }, { role: null }))
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=42')

    const detail = wrapper.get('[data-testid="admin-users-detail"]')
    expect(detail.get('h2').text()).toBe('Retained account')
    expect(detail.get('.status-pill').text()).toBe('Deactivated')
    expect(detail.find('.tag').exists()).toBe(false)
    expect(detail.find('[data-testid="admin-users-reactivate"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-audit-all"]').attributes('href')).toBe('/admin/audit-log?user=42')
  })

  it('lists the account’s own recent events, naming who acted only when it was someone else', async () => {
    usersApi.get.mockResolvedValue(
      detailOf(
        { ...farah, active: false },
        {
          deactivation: { actorName: 'Olivia Tan', reason: 'Asked to pause the account', at: '2026-05-18T14:00:00' },
          recentAudit: [
            auditEntry(31, 'ADMIN_USER_DEACTIVATE', 'olivia@example.com', '2026-05-18T14:00:00'),
            auditEntry(30, 'ACCOUNT_CREATED', farah.email, '2026-03-20T09:12:00'),
          ],
        },
      ),
    )
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=7')

    const rows = wrapper
      .findAll('.row-list li')
      .map((row) => [row.get('.row-list-title'), ...row.findAll('.row-list-meta')].map((part) => part.text()))
    expect(rows).toEqual([
      ['Account deactivated', '18 May, 2:00 PM · olivia@example.com'],
      ['Account created', '20 Mar, 9:12 AM', 'Signed up as a learner'],
    ])
    expect(wrapper.find('a[href="/admin/audit-log?entry=31"]').exists()).toBe(true)
    // An admin's own account was not signed up as a learner.
    usersApi.get.mockResolvedValue(
      detailOf(olivia, { recentAudit: [auditEntry(40, 'ACCOUNT_CREATED', olivia.email, '2026-03-01T10:00:00')] }),
    )
    const admin = (await mountAtRoute(AdminUsersPage, '/admin/users?user=1')).wrapper
    expect(admin.text()).not.toContain('Signed up as a learner')
  })

  it('shows who deactivated an account and reactivates it at once, without a question or a reason (X1.20)', async () => {
    usersApi.get.mockResolvedValue(
      detailOf(daniel, {
        deactivation: {
          actorName: 'Nur Admin',
          reason: 'Spam',
          at: '2026-05-10T10:00:00',
        },
      }),
    )
    const reactivation = deferred<AdminUserDetail>()
    usersApi.reactivate.mockReturnValue(reactivation.promise)
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users')

    await wrapper.findAll('[data-testid="admin-users-row"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.user).toBe('9')
    const deactivation = wrapper.get('[data-testid="admin-users-deactivation"]').text()
    expect(deactivation).toContain('by Nur Admin')
    expect(deactivation).toContain('Spam')
    expect(wrapper.get('[data-testid="admin-users-access-hint"]').text()).toBe('They can sign in again straight away.')

    const reactivate = wrapper.get('[data-testid="admin-users-reactivate"]')
    expect(reactivate.classes()).toContain('btn-primary')
    await reactivate.trigger('click')
    expect(usersApi.reactivate).toHaveBeenCalledExactlyOnceWith(9)
    expect(wrapper.find('[data-testid="admin-users-status-dialog"]').exists()).toBe(false)
    // Pressed again while it runs, it does nothing.
    expect(wrapper.get('[data-testid="admin-users-reactivate"]').attributes('aria-busy')).toBe('true')
    await wrapper.get('[data-testid="admin-users-reactivate"]').trigger('click')
    expect(usersApi.reactivate).toHaveBeenCalledTimes(1)

    reactivation.resolve(detailOf({ ...daniel, active: true }))
    await flushPromises()
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Daniel Tan is reactivated. They can sign in again.')
    expect(wrapper.find('[data-testid="admin-users-deactivate"]').exists()).toBe(true)
  })

  it('says under Reactivate why the account could not be reactivated', async () => {
    usersApi.reactivate.mockRejectedValue({
      response: { status: 400, data: { message: "A deleted account can't be reactivated." } },
    })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=9')

    await submitAccessChange(wrapper, 'reactivate')
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-users-access-error"]').text()).toBe(
      "A deleted account can't be reactivated.",
    )
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-reactivate"]').attributes('disabled')).toBeUndefined()
  })

  it.each([
    ['ms', 'Kemajuan, pelan pembelajaran dan sejarah kelas mereka disimpan.'],
    ['zh-CN', '对方的学习进度、学习计划和课堂记录都会保留。'],
  ] as const)('says in %s what deactivating keeps', async (language, line) => {
    const previous = i18n.global.locale.value
    i18n.global.locale.value = language
    try {
      const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=7')
      await wrapper.get('[data-testid="admin-users-deactivate"]').trigger('click')
      expect(wrapper.get('[data-testid="admin-users-impact"]').text()).toContain(line)
    } finally {
      i18n.global.locale.value = previous
    }
  })
  it('keeps the dialog open with the server’s message when the change fails', async () => {
    usersApi.deactivate.mockRejectedValue({
      response: { status: 409, data: { message: 'The last admin stays active.' } },
    })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=7')

    await submitAccessChange(wrapper, 'deactivate', 'Testing')
    // The dialog asks for the deactivation only; a failure keeps it open with the server's reason.
    await flushPromises()

    const dialog = wrapper.get('[data-testid="admin-users-status-dialog"]')
    expect(dialog.get('[role="alert"]').text()).toBe('The last admin stays active.')

    // Cancel closes only the dialog; the person stays open beside the list.
    await wrapper.get('[data-testid="admin-users-status-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="admin-users-status-dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="admin-users-detail"]').exists()).toBe(true)
    expect(usersApi.deactivate).toHaveBeenCalledTimes(1)
  })

  it('does not offer to deactivate your own account', async () => {
    usersApi.get.mockResolvedValue(detailOf(farah, { self: true }))
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users?user=7')

    expect(wrapper.find('[data-testid="admin-users-deactivate"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('This is your account.')
  })

  it('says what no search matched and clears it', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    usersApi.list.mockResolvedValue(pageOf([]))
    await wrapper.get('[data-testid="admin-users-search"]').setValue('Zhao Min')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(wrapper.text()).toContain('No users match “Zhao Min”')
    expect(wrapper.text()).toContain('Check the spelling, or clear the search to see everyone.')
    expect(wrapper.find('[data-testid="admin-users-detail"]').exists()).toBe(false)
    // Only the search narrows the list, so that is what the button clears; the filters above stay the way forward.
    expect(wrapper.get('[data-testid="admin-users-clear"]').text()).toBe('Clear search')
    expect(wrapper.get('[data-testid="admin-users-clear"]').classes()).toContain('btn-secondary')

    // With a filter set as well, the button says what it clears: all of them.
    await wrapper.get('[data-testid="admin-users-role"]').setValue('ADMIN')
    await flushPromises()
    expect(wrapper.text()).toContain('No users match “Zhao Min”')
    expect(wrapper.text()).toContain('Try another name, role or account filter.')
    expect(wrapper.get('[data-testid="admin-users-clear"]').text()).toBe('Clear filters')

    usersApi.list.mockResolvedValue(pageOf([farah, daniel]))
    await wrapper.get('[data-testid="admin-users-clear"]').trigger('click')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(listQueries().at(-1)).toEqual({ page: 0, size: 10 })
    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(2)
  })

  it('opens the first match of a new search, and nobody from the last list while it loads', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const searched = deferred<PageResponse<AdminUserListItem>>()
    usersApi.list.mockImplementation(async (query: { keyword?: string }) =>
      query.keyword === 'Daniel' ? searched.promise : pageOf([farah, daniel]),
    )
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users')
    expect(router.currentRoute.value.query.user).toBe('7')

    await wrapper.get('[data-testid="admin-users-search"]').setValue('Daniel')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(router.currentRoute.value.query.user).toBeUndefined()
    expect(wrapper.find('[data-testid="admin-users-detail"]').exists()).toBe(false)

    searched.resolve(pageOf([daniel]))
    await flushPromises()
    expect(router.currentRoute.value.query.user).toBe('9')
    expect(wrapper.get('[data-testid="admin-users-detail"]').text()).toContain('Daniel Tan')
  })

  it('chooses nobody when a new search finds no one', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const searched = deferred<PageResponse<AdminUserListItem>>()
    usersApi.list.mockImplementation(async (query: { keyword?: string }) =>
      query.keyword ? searched.promise : pageOf([farah, daniel]),
    )
    const { wrapper, router } = await mountAtRoute(AdminUsersPage, '/admin/users')
    expect(router.currentRoute.value.query.user).toBe('7')

    // The address changes before the list answers, as it does in the app.
    await wrapper.get('[data-testid="admin-users-search"]').setValue('Zhao Min')
    vi.advanceTimersByTime(300)
    await flushPromises()
    searched.resolve(pageOf([]))
    await flushPromises()

    expect(wrapper.text()).toContain('No users match “Zhao Min”')
    expect(router.currentRoute.value.query.user).toBeUndefined()
  })

  it('names the filters, not a search, when only filters leave the list empty', async () => {
    usersApi.list.mockImplementation(async (query: { role?: string }) => pageOf(query.role ? [] : [farah, daniel]))
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    await wrapper.get('[data-testid="admin-users-role"]').setValue('ADMIN')
    await flushPromises()

    expect(wrapper.get('.state-panel.is-empty h3').text()).toBe('No users match')
    expect(wrapper.get('[data-testid="admin-users-clear"]').text()).toBe('Clear filters')
    expect(wrapper.get('[data-testid="admin-users-clear"]').classes()).toContain('btn-secondary')
  })

  it('retries a failed invite list independently of the user table', async () => {
    usersApi.invites.mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValueOnce([inviteForMei])
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    expect(wrapper.get('.data-notice').text()).toContain('Pending invites could not be loaded')
    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(2)
    await wrapper.get('.data-notice button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-users-invites"]').text()).toContain(inviteForMei.email)
    expect(wrapper.find('.data-notice').exists()).toBe(false)
  })

  it('invites an admin and lists the invites waiting for a password', async () => {
    usersApi.invite.mockResolvedValue({})
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    expect(wrapper.find('[data-testid="admin-users-invites"]').exists()).toBe(false)

    await wrapper.get('[data-testid="admin-users-invite"]').trigger('click')
    const drawer = wrapper.get('[data-testid="admin-users-invite-drawer"]')
    expect(drawer.get('h2').text()).toBe('Invite admin')
    expect(drawer.text()).toContain('Admins manage classes, users and AI settings.')
    expect(drawer.text()).toContain('They get an email link to set a password.')
    // A2.07: the field names the fix under itself, and Send invite waits until it is fixed.
    await wrapper.get('[data-testid="admin-users-invite-email"]').setValue('not-an-email')
    await wrapper.get('#admin-users-invite-form').trigger('submit')
    expect(wrapper.get('#admin-users-invite-error').text()).toBe('Enter a full email address, like name@example.com.')
    expect(wrapper.get('[data-testid="admin-users-send-invite"]').attributes('disabled')).toBeDefined()
    expect(usersApi.invite).not.toHaveBeenCalled()

    usersApi.invites.mockResolvedValue([inviteForMei])
    await wrapper.get('[data-testid="admin-users-invite-email"]').setValue(' Mei@Example.com ')
    expect(wrapper.find('#admin-users-invite-error').exists()).toBe(false)
    await wrapper.get('#admin-users-invite-form').trigger('submit')
    await flushPromises()

    expect(usersApi.invite).toHaveBeenCalledWith('mei@example.com')
    expect(wrapper.find('[data-testid="admin-users-invite-drawer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Invite sent to mei@example.com.')
    const invites = wrapper.get('[data-testid="admin-users-invites"]').text()
    expect(invites).toContain('Admin invite pending')
    expect(invites).toContain('mei@example.com · sent 18 May, 10:00 AM')
  })

  it('cancels a pending invite at once, says its link no longer works, and lists the rest (X1.19)', async () => {
    const farid: AdminInvite = { id: 4, email: 'farid.admin@example.com', sentAt: '2026-05-18T14:00:00' }
    usersApi.invites.mockResolvedValue([inviteForMei, farid])
    usersApi.cancelInvite.mockResolvedValue(null)
    usersApi.restoreInvite.mockResolvedValue(farid)
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    expect(wrapper.get('[data-testid="admin-users-invites"]').text()).toContain('2 admin invites pending')

    usersApi.invites.mockResolvedValue([inviteForMei])
    const cancel = wrapper.findAll('[data-testid="admin-users-cancel-invite"]')[1]!
    expect(cancel.attributes('aria-label')).toBe('Cancel the invite to farid.admin@example.com')
    await cancel.trigger('click')
    await flushPromises()

    expect(usersApi.cancelInvite).toHaveBeenCalledExactlyOnceWith(4)
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.text()).toContain('Invite cancelled. The link no longer works.')
    const invites = wrapper.get('[data-testid="admin-users-invites"]').text()
    expect(invites).toContain('Admin invite pending')
    expect(invites).not.toContain('farid.admin@example.com')

    // Undo brings the invite back, with the link already sent.
    usersApi.invites.mockResolvedValue([inviteForMei, farid])
    expect(toast.get('.toast-action').text()).toBe('Undo')
    await toast.get('.toast-action').trigger('click')
    await flushPromises()
    expect(usersApi.restoreInvite).toHaveBeenCalledExactlyOnceWith(4)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Invite restored. The link works again.')
    expect(wrapper.get('[data-testid="admin-users-invites"]').text()).toContain('farid.admin@example.com')
  })

  it('sends a pending invite again, beside Cancel invite', async () => {
    usersApi.invites.mockResolvedValue([inviteForMei])
    usersApi.invite.mockResolvedValue({ ...inviteForMei, sentAt: '2026-05-19T09:00:00' })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    const row = wrapper.get('.admin-users-invite')
    expect(row.findAll('button').map((button) => button.text())).toEqual(['Resend', 'Cancel invite'])
    const resend = row.get('[data-testid="admin-users-resend-invite"]')
    expect(resend.attributes('aria-label')).toBe('Send the invite to mei@example.com again')
    usersApi.invites.mockResolvedValue([{ ...inviteForMei, sentAt: '2026-05-19T09:00:00' }])
    await resend.trigger('click')
    await flushPromises()

    expect(usersApi.invite).toHaveBeenCalledExactlyOnceWith('mei@example.com')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Link sent again.')
    expect(wrapper.find('[data-testid="toast"] .toast-action').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-invites"]').text()).toContain('sent 19 May, 9:00 AM')
  })

  it('runs one invite change at a time', async () => {
    const sending = deferred<AdminInvite>()
    usersApi.invites.mockResolvedValue([inviteForMei])
    usersApi.invite.mockReturnValue(sending.promise)
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    await wrapper.get('[data-testid="admin-users-resend-invite"]').trigger('click')
    const buttons = wrapper.findAll<HTMLButtonElement>('.admin-users-invite button')
    expect(buttons.map((button) => button.element.disabled)).toEqual([true, true])
    expect(buttons.map((button) => button.attributes('aria-busy'))).toEqual(['true', 'false'])
    await wrapper.get('[data-testid="admin-users-cancel-invite"]').trigger('click')
    expect(usersApi.cancelInvite).not.toHaveBeenCalled()

    sending.resolve(inviteForMei)
    await flushPromises()
    expect(wrapper.findAll<HTMLButtonElement>('.admin-users-invite button').map((b) => b.element.disabled)).toEqual([
      false,
      false,
    ])
  })

  it('says why an invite accepted meanwhile cannot be cancelled, and shows the new admin', async () => {
    usersApi.invites.mockResolvedValue([inviteForMei])
    usersApi.cancelInvite.mockRejectedValue({
      response: { status: 409, data: { message: 'This invite was already accepted.' } },
    })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    const lists = usersApi.list.mock.calls.length

    usersApi.invites.mockResolvedValue([])
    await wrapper.get('[data-testid="admin-users-cancel-invite"]').trigger('click')
    await flushPromises()

    expect(usersApi.invites).toHaveBeenCalledTimes(2)
    expect(usersApi.list.mock.calls.length).toBeGreaterThan(lists)
    expect(wrapper.find('[data-testid="admin-users-invites"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-invite-error"]').text()).toBe('This invite was already accepted.')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('keeps the invite listed with the reason when it cannot be cancelled', async () => {
    usersApi.invites.mockResolvedValue([inviteForMei])
    usersApi.cancelInvite.mockRejectedValue({ response: { status: 500, data: { message: 'Server error' } } })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')

    await wrapper.get('[data-testid="admin-users-cancel-invite"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-users-invite-error"]').text()).not.toBe('')
    expect(wrapper.get('[data-testid="admin-users-invites"]').text()).toContain('mei@example.com')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('says under the strip when the cancel cannot be undone', async () => {
    usersApi.invites.mockResolvedValue([inviteForMei])
    usersApi.cancelInvite.mockResolvedValue(null)
    usersApi.restoreInvite.mockRejectedValue({
      response: { status: 409, data: { message: 'This invite was already accepted.' } },
    })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    usersApi.invites.mockResolvedValue([])
    await wrapper.get('[data-testid="admin-users-cancel-invite"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()
    expect(usersApi.restoreInvite).toHaveBeenCalledExactlyOnceWith(inviteForMei.id)
    expect(wrapper.get('[data-testid="admin-users-invite-error"]').text()).toBe('This invite was already accepted.')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('checks the invite email when the field is left, then on every change until it is fixed', async () => {
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    await wrapper.get('[data-testid="admin-users-invite"]').trigger('click')
    const email = () => wrapper.get('[data-testid="admin-users-invite-email"]')
    const send = () => wrapper.get('[data-testid="admin-users-send-invite"]')

    // Nothing is checked while the admin is still typing for the first time.
    await email().setValue('mei@')
    expect(wrapper.find('#admin-users-invite-error').exists()).toBe(false)
    expect(send().attributes('disabled')).toBeUndefined()

    await email().trigger('blur')
    expect(wrapper.get('#admin-users-invite-error').text()).toBe('Enter a full email address, like name@example.com.')
    expect(email().attributes('aria-invalid')).toBe('true')
    expect(email().attributes('aria-describedby')).toBe('admin-users-invite-error admin-users-invite-hint')
    expect(send().attributes('disabled')).toBeDefined()

    // While the error shows, each change is checked: an empty field says so, a full address clears it.
    await email().setValue('')
    expect(wrapper.get('#admin-users-invite-error').text()).toBe('Enter the email address to invite.')
    expect(send().attributes('disabled')).toBeDefined()
    await email().setValue('mei@example.com')
    expect(wrapper.find('#admin-users-invite-error').exists()).toBe(false)
    expect(email().attributes('aria-describedby')).toBe('admin-users-invite-hint')
    expect(send().attributes('disabled')).toBeUndefined()
    expect(usersApi.invite).not.toHaveBeenCalled()
  })

  it('starts the invite form clean again after it was closed with an error', async () => {
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    await wrapper.get('[data-testid="admin-users-invite"]').trigger('click')
    await wrapper.get('[data-testid="admin-users-invite-email"]').trigger('blur')
    expect(wrapper.find('#admin-users-invite-error').exists()).toBe(true)
    await buttonNamed(wrapper.get('[data-testid="admin-users-invite-drawer"]'), 'Cancel').trigger('click')

    await wrapper.get('[data-testid="admin-users-invite"]').trigger('click')
    expect(wrapper.find('#admin-users-invite-error').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-users-send-invite"]').attributes('disabled')).toBeUndefined()
  })

  it('sends an invite only once while the form is pending', async () => {
    const invite = deferred()
    usersApi.invite.mockReturnValue(invite.promise)
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    const form = await fillInvite(wrapper)
    await form.trigger('submit')
    await form.trigger('submit')
    invite.resolve({})
    await flushPromises()

    expect(usersApi.invite).toHaveBeenCalledTimes(1)
  })

  it('keeps the submitted invite draft locked until a failure permits retry', async () => {
    const invite = deferred()
    usersApi.invite.mockReturnValue(invite.promise)
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    const form = await fillInvite(wrapper)
    await form.trigger('submit')
    const drawer = wrapper.get('[data-testid="admin-users-invite-drawer"]')
    const buttons = drawer.findAll('button')
    const locked = buttons.every((button) => button.attributes('disabled') !== undefined)
    const emailLocked = wrapper.get('[data-testid="admin-users-invite-email"]').attributes('disabled')
    await buttonNamed(drawer, 'Cancel').trigger('click')
    const stayedOpen = wrapper.find('[data-testid="admin-users-invite-drawer"]').exists()
    invite.reject(new Error('Invite service unavailable'))
    await flushPromises()

    expect(locked).toBe(true)
    expect(emailLocked).toBeDefined()
    expect(stayedOpen).toBe(true)
    expect(wrapper.get('[data-testid="admin-users-invite-email"]').element).toHaveProperty('value', 'mei@example.com')
    expect(wrapper.get('[data-testid="admin-users-send-invite"]').attributes('disabled')).toBeUndefined()
    usersApi.invite.mockResolvedValue({})
    await wrapper.get('#admin-users-invite-form').trigger('submit')
    await flushPromises()
    expect(usersApi.invite).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="admin-users-invite-drawer"]').exists()).toBe(false)
  })

  it('shows why the server refused an invite without blocking a retry, until the address changes', async () => {
    usersApi.invite.mockRejectedValue({
      response: { status: 409, data: { message: 'This email already has an account.' } },
    })
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    const form = await fillInvite(wrapper)
    await form.trigger('submit')
    await flushPromises()

    // The server's answer sits under the field like any error, but the address itself is fine: Send invite stays free.
    expect(wrapper.get('#admin-users-invite-error').text()).toBe('This email already has an account.')
    expect(wrapper.get('[data-testid="admin-users-send-invite"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="admin-users-invite-email"]').trigger('blur')
    expect(wrapper.get('#admin-users-invite-error').text()).toBe('This email already has an account.')

    await wrapper.get('[data-testid="admin-users-invite-email"]').setValue('lin@example.com')
    expect(wrapper.find('#admin-users-invite-error').exists()).toBe(false)
  })

  it.each(['deactivate', 'reactivate'] as const)(
    'does not reload page data when a pending %s finishes after unmount',
    async (kind) => {
      const change = deferred()
      usersApi[kind].mockReturnValue(change.promise)
      const user = kind === 'deactivate' ? farah : daniel
      const { wrapper } = await mountAtRoute(AdminUsersPage, `/admin/users?user=${user.id}`)
      await submitAccessChange(wrapper, kind)
      const listCalls = usersApi.list.mock.calls.length
      wrapper.unmount()
      const changed = detailOf({ ...user, active: !user.active })
      change.resolve(changed)
      await flushPromises()
      expect(usersApi.list).toHaveBeenCalledTimes(listCalls)
    },
  )

  it.each(['success', 'failure'] as const)(
    'keeps the new selection and its dialog after an earlier reactivation’s %s',
    async (outcome) => {
      const change = deferred<AdminUserDetail>()
      usersApi.reactivate.mockReturnValue(change.promise)
      const { wrapper, router } = await mountAtRoute(AdminUsersPage, `/admin/users?user=${daniel.id}`)
      await submitAccessChange(wrapper, 'reactivate')
      // Back/forward or an address change can switch users while the change runs.
      await router.replace(`/admin/users?user=${farah.id}`)
      await flushPromises()
      await wrapper.get('[data-testid="admin-users-deactivate"]').trigger('click')
      await wrapper.get('[data-testid="admin-users-reason"]').setValue('New draft')
      if (outcome === 'success') change.resolve(detailOf({ ...daniel, active: true }))
      else change.reject(new Error('Earlier decision failed'))
      await flushPromises()

      expect(wrapper.get('[data-testid="admin-users-detail"]').text()).toContain(farah.fullName)
      const dialog = wrapper.get('[data-testid="admin-users-status-dialog"]')
      expect(dialog.get('h2').text()).toContain(farah.fullName)
      expect(dialog.get('[data-testid="admin-users-reason"]').element).toHaveProperty('value', 'New draft')
      expect(dialog.find('[role="alert"]').exists()).toBe(false)
      expect(dialog.get('[data-testid="admin-users-status-dialog-confirm"]').attributes('disabled')).toBeUndefined()
      expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    },
  )

  it.each(['success', 'failure'] as const)(
    'closes the dialog on another selection and frees its Reactivate after an earlier deactivation’s %s',
    async (outcome) => {
      const change = deferred<AdminUserDetail>()
      usersApi.deactivate.mockReturnValue(change.promise)
      const { wrapper, router } = await mountAtRoute(AdminUsersPage, `/admin/users?user=${farah.id}`)
      await submitAccessChange(wrapper, 'deactivate', 'First decision')
      await router.replace(`/admin/users?user=${daniel.id}`)
      await flushPromises()
      const closedOnSelection = !wrapper.find('[data-testid="admin-users-status-dialog"]').exists()
      if (outcome === 'success') change.resolve(detailOf({ ...farah, active: false }))
      else change.reject(new Error('Earlier decision failed'))
      await flushPromises()

      expect(closedOnSelection).toBe(true)
      expect(wrapper.get('[data-testid="admin-users-detail"]').text()).toContain(daniel.fullName)
      expect(wrapper.get('[data-testid="admin-users-reactivate"]').attributes('disabled')).toBeUndefined()
      expect(wrapper.find('[data-testid="admin-users-access-error"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    },
  )

  it('does not reload invites when a pending invitation finishes after unmount', async () => {
    const invite = deferred()
    usersApi.invite.mockReturnValue(invite.promise)
    const { wrapper } = await mountAtRoute(AdminUsersPage, '/admin/users')
    const form = await fillInvite(wrapper)
    await form.trigger('submit')
    const calls = usersApi.invites.mock.calls.length
    wrapper.unmount()
    invite.resolve({})
    await flushPromises()
    expect(usersApi.invites).toHaveBeenCalledTimes(calls)
  })
})
