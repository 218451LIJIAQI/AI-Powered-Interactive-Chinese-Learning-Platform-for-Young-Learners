import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuditEntry } from '@/types/ops'

import { pageOf } from '@/test/helpers'

import { mountAdminPage } from './adminPageTestSetup'

const overview = vi.hoisted(() => ({
  auditLog: vi.fn(),
  auditEntry: vi.fn(),
  auditActions: vi.fn(),
  exportAuditLog: vi.fn(),
}))
const download = vi.hoisted(() => ({ saveBlobDownload: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('@/utils/download', () => download)

import AdminAuditLogPage from './AdminAuditLogPage.vue'

function entry(overrides: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id: 5,
    actionType: 'ADMIN_USER_DEACTIVATE',
    category: 'ACCOUNT',
    actorName: 'Nur Admin',
    actorEmail: 'nur@example.com',
    targetType: 'USER',
    targetLabel: 'Daniel Tan',
    targetUserId: 9,
    reason: 'Repeated spam',
    description: null,
    createdAt: '2026-05-18T10:00:00',
    ...overrides,
  }
}

describe('AdminAuditLogPage', () => {
  beforeEach(() => {
    overview.auditLog.mockResolvedValue(
      pageOf(
        [
          entry(),
          entry({
            id: 6,
            actionType: 'CONTENT_SUBMIT',
            category: 'CONTENT',
            actorName: null,
            actorEmail: null,
            targetType: 'PACKAGE',
            targetLabel: 'Chinese Culture and Festivals',
            targetUserId: null,
            reason: null,
          }),
        ],
        0,
        412,
      ),
    )
    overview.auditActions.mockResolvedValue(['CONTENT_SUBMIT', 'ADMIN_USER_DEACTIVATE'])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('lists entries newest first with labels, the actor and the total', async () => {
    const { wrapper } = await mountAdminPage(AdminAuditLogPage, '/admin/audit-log')
    await flushPromises()

    expect(overview.auditLog).toHaveBeenCalledWith({ page: 0, size: 10 })
    const rows = wrapper.findAll('[data-testid="admin-audit-row"]')
    expect(rows[0].text()).toContain('Account deactivated')
    expect(rows[0].text()).toContain('Daniel Tan')
    expect(rows[0].text()).toContain('Nur Admin')
    expect(rows[1].text()).toContain('System')
    const options = wrapper.findAll('[data-testid="admin-audit-action"] option').map((option) => option.text())
    expect(options).toEqual(['All actions', 'Account deactivated', 'Submitted for review'])
  })

  it('takes its filters from the address and keeps changes there', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { wrapper, router } = await mountAdminPage(
      AdminAuditLogPage,
      '/admin/audit-log?q=Daniel&actor=nur@example.com&action=ADMIN_USER_DEACTIVATE&from=2026-05-01&to=yesterday',
    )
    await flushPromises()

    expect(overview.auditLog).toHaveBeenCalledWith({
      keyword: 'Daniel',
      actionType: 'ADMIN_USER_DEACTIVATE',
      actor: 'nur@example.com',
      from: '2026-05-01',
      page: 0,
      size: 10,
    })
    expect((wrapper.get('[data-testid="admin-audit-search"]').element as HTMLInputElement).value).toBe('Daniel')
    expect((wrapper.get('[data-testid="admin-audit-to"]').element as HTMLInputElement).value).toBe('')

    await wrapper.get('[data-testid="admin-audit-search"]').setValue('Farah')
    vi.advanceTimersByTime(300)
    await flushPromises()
    await wrapper.get('[data-testid="admin-audit-to"]').setValue('2026-05-31')
    await flushPromises()

    expect(overview.auditLog).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'Farah', to: '2026-05-31' }))
    expect(router.currentRoute.value.query).toEqual({
      q: 'Farah',
      actor: 'nur@example.com',
      action: 'ADMIN_USER_DEACTIVATE',
      from: '2026-05-01',
      to: '2026-05-31',
    })
  })

  it('explains a From date after the To date instead of searching', async () => {
    const { wrapper } = await mountAdminPage(AdminAuditLogPage, '/admin/audit-log')
    await flushPromises()
    overview.auditLog.mockClear()

    await wrapper.get('[data-testid="admin-audit-to"]').setValue('2026-05-01')
    await flushPromises()
    overview.auditLog.mockClear()
    await wrapper.get('[data-testid="admin-audit-from"]').setValue('2026-05-10')
    await flushPromises()

    expect(wrapper.get('.admin-audit-range-error').text()).toBe('The From date must be on or before the To date.')
    expect(wrapper.get('[data-testid="admin-audit-from"]').attributes('aria-invalid')).toBe('true')
    expect(overview.auditLog).not.toHaveBeenCalled()
    expect(wrapper.findAll('[data-testid="admin-audit-row"]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('No entries match')
    expect(wrapper.get('[data-testid="admin-audit-export"]').attributes('disabled')).toBeDefined()
  })

  it('opens an entry in a drawer with the reason and a link to the account', async () => {
    const { wrapper, router } = await mountAdminPage(AdminAuditLogPage, '/admin/audit-log')
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-audit-drawer"]').exists()).toBe(false)

    await wrapper.findAll('[data-testid="admin-audit-row"]')[0].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.entry).toBe('5')
    const drawer = wrapper.get('[data-testid="admin-audit-drawer"]')
    expect(drawer.text()).toContain('Nur Admin: Account deactivated')
    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Repeated spam')
    expect(wrapper.get('[data-testid="admin-audit-open-target"]').attributes('href')).toBe('/admin/users?user=9')
    expect(overview.auditEntry).not.toHaveBeenCalled()

    await drawer.get('.overlay-header button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.entry).toBeUndefined()
  })

  it('loads a linked entry that is not on the current page', async () => {
    overview.auditEntry.mockResolvedValue(
      entry({ id: 77, actionType: 'SUPPORT_REPLY_SENT', category: 'SUPPORT', reason: null, targetUserId: null }),
    )
    const { wrapper } = await mountAdminPage(AdminAuditLogPage, '/admin/audit-log?entry=77')
    await flushPromises()

    expect(overview.auditEntry).toHaveBeenCalledWith(77)
    const drawer = wrapper.get('[data-testid="admin-audit-drawer"]')
    expect(drawer.text()).toContain('Nur Admin: Support reply sent')
    expect(wrapper.find('[data-testid="admin-audit-open-target"]').exists()).toBe(false)
  })

  it('exports the filtered log as CSV', async () => {
    const file = { blob: new Blob(['id\r\n']), fileName: 'audit-log.csv' }
    overview.exportAuditLog.mockResolvedValue(file)
    const { wrapper } = await mountAdminPage(AdminAuditLogPage, '/admin/audit-log?action=CONTENT_SUBMIT')
    await flushPromises()

    await wrapper.get('[data-testid="admin-audit-export"]').trigger('click')
    await flushPromises()

    expect(overview.exportAuditLog).toHaveBeenCalledWith({ actionType: 'CONTENT_SUBMIT' })
    expect(download.saveBlobDownload).toHaveBeenCalledWith(
      file,
      expect.stringMatching(/^audit-log-\d{4}-\d{2}-\d{2}\.csv$/),
    )
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('CSV downloaded.')

    overview.exportAuditLog.mockRejectedValue({ response: { status: 500, data: { message: 'Export failed' } } })
    await wrapper.get('[data-testid="admin-audit-export"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Export failed')
  })
})
