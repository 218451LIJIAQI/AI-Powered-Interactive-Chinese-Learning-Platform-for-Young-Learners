import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AdminUserDetail, AdminUserListItem } from '@/types/ops'

import { pageOf } from '@/test/helpers'

import { mountAdminPage } from './adminPageTestSetup'

const usersApi = vi.hoisted(() => ({
  list: vi.fn(),
  get: vi.fn(),
  deactivate: vi.fn(),
  reactivate: vi.fn(),
  invites: vi.fn(),
  invite: vi.fn(),
}))
const navCounts = vi.hoisted(() => vi.fn())

vi.mock('@/api/ops', () => ({ adminUsersApi: usersApi }))
vi.mock('@/api/shell', () => ({ shellApi: { navCounts } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminUsersPage from './AdminUsersPage.vue'

const farah: AdminUserListItem = {
  id: 7,
  fullName: 'Farah Aziz',
  email: 'farah@example.com',
  role: 'TEACHER',
  active: true,
  verificationState: 'APPROVED',
  joinedAt: '2026-05-01T09:00:00',
}
const daniel: AdminUserListItem = {
  id: 9,
  fullName: 'Daniel Tan',
  email: 'daniel@example.com',
  role: 'STUDENT',
  active: false,
  verificationState: null,
  joinedAt: '2026-05-03T09:00:00',
}

function detailOf(user: AdminUserListItem, overrides: Partial<AdminUserDetail> = {}): AdminUserDetail {
  return {
    ...user,
    approvedAt: null,
    deactivation: null,
    self: false,
    deactivationImpact: { publishedClasses: [], upcomingSessions: 0, bookings: 0, reviewItems: 0 },
    recentAudit: [],
    ...overrides,
  }
}

/** The queries of the page's list requests. */
function listQueries(): unknown[] {
  return usersApi.list.mock.calls.map(([query]) => query)
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    usersApi.list.mockResolvedValue(pageOf([farah, daniel]))
    navCounts.mockResolvedValue({ teacherApplications: 3, reviewQueue: 5 })
    usersApi.invites.mockResolvedValue([])
    usersApi.get.mockImplementation(async (id: number) => detailOf(id === farah.id ? farah : daniel))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('lists users and sends only the filters that are set', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { wrapper } = await mountAdminPage(AdminUsersPage, '/admin/users')
    await flushPromises()

    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('Showing 1–2 of 2')
    expect(listQueries()).toEqual([{ page: 0, size: 10 }])
    // The applications tab shows the sidebar's nav count of applications waiting.
    expect(navCounts).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="admin-users-tab-applications"] .route-tab-count').text()).toBe('3')

    await wrapper.get('[data-testid="admin-users-search"]').setValue('  Farah ')
    vi.advanceTimersByTime(300)
    await flushPromises()
    await wrapper.get('[data-testid="admin-users-role"]').setValue('TEACHER')
    await flushPromises()
    await wrapper.get('[data-testid="admin-users-account"]').setValue('DEACTIVATED')
    await flushPromises()

    expect(listQueries().at(-1)).toEqual({ keyword: 'Farah', role: 'TEACHER', active: false, page: 0, size: 10 })
  })

  it('opens the user named in the address and lists the impact before deactivating', async () => {
    usersApi.get.mockResolvedValue(
      detailOf(farah, {
        deactivationImpact: {
          publishedClasses: ['Chinese Culture and Festivals'],
          upcomingSessions: 2,
          bookings: 5,
          reviewItems: 1,
        },
      }),
    )
    usersApi.deactivate.mockResolvedValue({
      user: detailOf({ ...farah, active: false }),
      cancelledSessions: 2,
      endedBookings: 5,
      itemsBackToDraft: 1,
    })
    const { wrapper } = await mountAdminPage(AdminUsersPage, '/admin/users?user=7')
    await flushPromises()

    expect(usersApi.get).toHaveBeenCalledWith(7)
    expect(wrapper.get('[data-testid="admin-users-detail"]').text()).toContain('Farah Aziz')

    await wrapper.get('[data-testid="admin-users-deactivate"]').trigger('click')
    const dialog = wrapper.get('[data-testid="admin-users-status-dialog"]')
    expect(dialog.attributes('role')).toBe('alertdialog')
    expect(dialog.get('h2').text()).toBe('Deactivate Farah Aziz?')
    expect(dialog.text()).toContain('farah@example.com · Teacher')
    expect(dialog.get('[data-testid="admin-users-status-dialog-confirm"]').classes()).toContain('btn-danger')
    expect(dialog.get('[data-testid="admin-users-status-dialog-confirm"]').text()).toBe('Deactivate')
    const impact = wrapper.get('[data-testid="admin-users-impact"]').text()
    expect(impact).toContain('Farah Aziz is signed out now')
    expect(impact).toContain('“Chinese Culture and Festivals”')
    expect(impact).toContain('Upcoming live sessions cancelled: 2. Bookings that end: 5')
    expect(impact).toContain('go back to Draft: 1')

    await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
    expect(wrapper.get('[data-testid="admin-users-status-dialog"]').text()).toContain('Write a reason first.')
    expect(wrapper.get('[data-testid="admin-users-reason"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('[data-testid="admin-users-reason"]').attributes('aria-describedby')).toBe(
      'admin-users-reason-error',
    )
    expect(usersApi.deactivate).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="admin-users-reason"]').setValue('  Repeated spam  ')
    await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(usersApi.deactivate).toHaveBeenCalledWith(7, 'Repeated spam')
    expect(wrapper.find('[data-testid="admin-users-status-dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Farah Aziz is deactivated and signed out. Live sessions cancelled: 2')
    expect(wrapper.text()).toContain('Reason: Repeated spam')
    expect(wrapper.find('[data-testid="admin-users-reactivate"]').exists()).toBe(true)
  })

  it('shows who deactivated an account and reactivates it with a reason', async () => {
    usersApi.get.mockResolvedValue(
      detailOf(daniel, {
        deactivation: {
          actorName: 'Nur Admin',
          actorEmail: 'nur@example.com',
          reason: 'Spam',
          at: '2026-05-10T10:00:00',
        },
      }),
    )
    usersApi.reactivate.mockResolvedValue(detailOf({ ...daniel, active: true }))
    const { wrapper, router } = await mountAdminPage(AdminUsersPage, '/admin/users')
    await flushPromises()

    await wrapper.findAll('[data-testid="admin-users-row"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.user).toBe('9')
    const deactivation = wrapper.get('[data-testid="admin-users-deactivation"]').text()
    expect(deactivation).toContain('by Nur Admin')
    expect(deactivation).toContain('Reason: Spam')

    await wrapper.get('[data-testid="admin-users-reactivate"]').trigger('click')
    const confirm = wrapper.get('[data-testid="admin-users-status-dialog-confirm"]')
    expect(confirm.classes()).toContain('btn-primary')
    expect(confirm.text()).toBe('Reactivate')
    expect(wrapper.find('[data-testid="admin-users-impact"]').exists()).toBe(false)
    await wrapper.get('[data-testid="admin-users-reason"]').setValue('Appeal accepted')
    await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(usersApi.reactivate).toHaveBeenCalledWith(9, 'Appeal accepted')
    expect(wrapper.text()).toContain('Daniel Tan is active again and can sign in.')
    expect(wrapper.find('[data-testid="admin-users-deactivate"]').exists()).toBe(true)
  })

  it('keeps the dialog open with the server’s message when the change fails', async () => {
    usersApi.deactivate.mockRejectedValue({
      response: { status: 409, data: { message: 'The last admin stays active.' } },
    })
    const { wrapper } = await mountAdminPage(AdminUsersPage, '/admin/users?user=7')
    await flushPromises()

    await wrapper.get('[data-testid="admin-users-deactivate"]').trigger('click')
    await wrapper.get('[data-testid="admin-users-reason"]').setValue('Testing')
    await wrapper.get('[data-testid="admin-users-status-dialog-confirm"]').trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[data-testid="admin-users-status-dialog"]')
    expect(dialog.get('[role="alert"]').text()).toBe('The last admin stays active.')

    // Cancel closes only the dialog; the user's drawer stays open.
    await wrapper.get('[data-testid="admin-users-status-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="admin-users-status-dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="admin-users-detail"]').exists()).toBe(true)
    expect(usersApi.deactivate).toHaveBeenCalledTimes(1)
  })

  it('does not offer to deactivate your own account', async () => {
    usersApi.get.mockResolvedValue(detailOf(farah, { self: true }))
    const { wrapper } = await mountAdminPage(AdminUsersPage, '/admin/users?user=7')
    await flushPromises()

    expect(wrapper.find('[data-testid="admin-users-deactivate"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('This is your account.')
  })

  it('invites an admin and lists the invites waiting for a password', async () => {
    usersApi.invite.mockResolvedValue({})
    const { wrapper } = await mountAdminPage(AdminUsersPage, '/admin/users')
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-users-invites"]').exists()).toBe(false)

    await wrapper.get('[data-testid="admin-users-invite"]').trigger('click')
    await wrapper.get('[data-testid="admin-users-invite-email"]').setValue('not-an-email')
    await wrapper.get('#admin-users-invite-form').trigger('submit')
    expect(wrapper.get('[data-testid="admin-users-invite-drawer"]').text()).toContain('Enter a valid email address.')
    expect(usersApi.invite).not.toHaveBeenCalled()

    usersApi.invites.mockResolvedValue([
      {
        id: 3,
        email: 'mei@example.com',
        sentAt: '2026-05-18T10:00:00',
        expiresAt: '2026-05-25T10:00:00',
      },
    ])
    await wrapper.get('[data-testid="admin-users-invite-email"]').setValue(' Mei@Example.com ')
    await wrapper.get('#admin-users-invite-form').trigger('submit')
    await flushPromises()

    expect(usersApi.invite).toHaveBeenCalledWith('mei@example.com')
    expect(wrapper.find('[data-testid="admin-users-invite-drawer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Invite sent to mei@example.com.')
    const invites = wrapper.get('[data-testid="admin-users-invites"]').text()
    expect(invites).toContain('1 pending')
    expect(invites).toContain('mei@example.com')
  })
})
