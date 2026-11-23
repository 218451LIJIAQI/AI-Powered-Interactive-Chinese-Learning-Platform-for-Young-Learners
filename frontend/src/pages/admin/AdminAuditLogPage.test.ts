import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuditEntry } from '@/types/ops'

import { i18n } from '@/plugins/i18n'
import { deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'

const overview = vi.hoisted(() => ({
  auditLog: vi.fn(),
  auditEntry: vi.fn(),
  auditActions: vi.fn(),
  exportAuditLog: vi.fn(),
}))
const users = vi.hoisted(() => ({ get: vi.fn() }))
const download = vi.hoisted(() => ({ saveBlobDownload: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview, adminUsersApi: users }))
vi.mock('@/utils/download', () => download)

import AdminAuditLogPage from './AdminAuditLogPage.vue'

function entry(overrides: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id: 5,
    actionType: 'ADMIN_USER_DEACTIVATE',
    category: 'ACCOUNT',
    actorName: 'Nur Admin',
    actorEmail: 'nur@example.com',
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
    Object.values(overview).forEach((fn) => fn.mockReset())
    users.get.mockReset()
    download.saveBlobDownload.mockReset()
    overview.auditLog.mockResolvedValue(
      pageOf(
        [
          entry(),
          entry({
            id: 6,
            actionType: 'ADMIN_CLASS_PUBLISH',
            category: 'CONTENT',
            actorName: null,
            actorEmail: null,
            targetLabel: 'Everyday Conversations',
            targetUserId: null,
            reason: null,
          }),
        ],
        0,
        412,
      ),
    )
    overview.auditActions.mockResolvedValue(['ADMIN_CLASS_PUBLISH', 'ADMIN_USER_DEACTIVATE', 'ACCOUNT_CREATED'])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('lists entries newest first with labels, the actor and the total', async () => {
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log')

    expect(overview.auditLog).toHaveBeenCalledWith({ page: 0, size: 10 })
    const rows = wrapper.findAll('[data-testid="admin-audit-row"]')
    expect(rows[0].text()).toContain('Account deactivated')
    expect(rows[0].text()).toContain('Daniel Tan')
    expect(rows[0].text()).toContain('Nur Admin')
    expect(rows[1].text()).toContain('Class published')
    expect(rows[1].text()).toContain('Everyday Conversations')
    expect(rows[1].text()).toContain('System')
    // A tab-less list: the bar says how many there are, once.
    expect(wrapper.get('nav.pagination .pagination-info').text()).toBe('412 events')
    // The action filter names each action the log has, as the rows do, in alphabetical order.
    const options = wrapper.findAll('[data-testid="admin-audit-action"] option').map((option) => option.text())
    expect(options).toEqual(['All actions', 'Account created', 'Account deactivated', 'Class published'])
  })

  it('names actions the platform no longer writes in plain words, never by their code', async () => {
    overview.auditLog.mockResolvedValue(pageOf([entry({ id: 7, actionType: 'LEGACY_IMPORT_DONE', reason: null })]))
    overview.auditActions.mockResolvedValue(['LEGACY_IMPORT_DONE', 'SUPPORT_REQUEST_REOPENED'])
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log')

    expect(wrapper.get('[data-testid="admin-audit-row"]').text()).toContain('Legacy Import Done')
    expect(wrapper.text()).not.toContain('LEGACY_IMPORT_DONE')
    const options = wrapper.findAll('[data-testid="admin-audit-action"] option').map((option) => option.text())
    expect(options).toEqual(['All actions', 'Legacy Import Done', 'Support request reopened'])
  })

  it('keeps a linked action selected and retries the filter options independently', async () => {
    overview.auditActions.mockRejectedValueOnce(new Error('Unavailable'))
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?action=ADMIN_USER_DEACTIVATE')

    const select = wrapper.get<HTMLSelectElement>('[data-testid="admin-audit-action"]')
    expect(select.element.value).toBe('ADMIN_USER_DEACTIVATE')
    expect(wrapper.get('.data-notice').text()).toContain('Action filters could not be loaded')
    await wrapper.get('.data-notice button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.data-notice').exists()).toBe(false)
    expect(select.element.value).toBe('ADMIN_USER_DEACTIVATE')
    expect(overview.auditLog).toHaveBeenCalledTimes(1)
  })

  it('takes its filters from the address and keeps changes there', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { wrapper, router } = await mountAtRoute(
      AdminAuditLogPage,
      '/admin/audit-log?q=Daniel&actor=nur@example.com&action=ADMIN_USER_DEACTIVATE&from=2026-05-01&to=yesterday',
    )

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

  it('lists exactly one account’s entries, names the account on a chip that removes the filter, and exports them', async () => {
    users.get.mockResolvedValue({ id: 9, fullName: 'Daniel Tan' })
    const file = { blob: new Blob(['id\r\n']), fileName: 'audit-log.csv' }
    overview.exportAuditLog.mockResolvedValue(file)
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?user=9&page=2')

    expect(overview.auditLog).toHaveBeenCalledWith({ userId: 9, page: 1, size: 10 })
    expect(users.get).toHaveBeenCalledWith(9)
    const chip = wrapper.get('[data-testid="admin-audit-user-chip"]')
    expect(chip.text()).toBe('By or about Daniel Tan')
    expect(chip.attributes('aria-label')).toBe('Remove the filter: by or about Daniel Tan')
    expect(wrapper.find('[role="group"][aria-label="Account filter"]').exists()).toBe(true)
    // Nothing is searched by the name: the search box stays empty.
    expect((wrapper.get('[data-testid="admin-audit-search"]').element as HTMLInputElement).value).toBe('')

    await wrapper.get('[data-testid="admin-audit-export"]').trigger('click')
    await flushPromises()
    expect(overview.exportAuditLog).toHaveBeenCalledWith({ userId: 9 })

    // Another filter keeps the account; removing the chip shows every account's entries again.
    await wrapper.get('[data-testid="admin-audit-action"]').setValue('ADMIN_USER_DEACTIVATE')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({
      actionType: 'ADMIN_USER_DEACTIVATE',
      userId: 9,
      page: 0,
      size: 10,
    })
    expect(router.currentRoute.value.query).toEqual({ action: 'ADMIN_USER_DEACTIVATE', user: '9' })
    await wrapper.get('[data-testid="admin-audit-user-chip"]').trigger('click')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ actionType: 'ADMIN_USER_DEACTIVATE', page: 0, size: 10 })
    expect(router.currentRoute.value.query).toEqual({ action: 'ADMIN_USER_DEACTIVATE' })
    expect(wrapper.find('[data-testid="admin-audit-user-chip"]').exists()).toBe(false)
  })

  it('follows the address to another account and back to every account', async () => {
    users.get.mockImplementation(async (id: number) => ({ id, fullName: id === 9 ? 'Daniel Tan' : 'Farah Aziz' }))
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?user=9')

    await router.push('/admin/audit-log?user=7')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ userId: 7, page: 0, size: 10 })
    expect(wrapper.get('[data-testid="admin-audit-user-chip"]').text()).toBe('By or about Farah Aziz')

    await router.push('/admin/audit-log')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ page: 0, size: 10 })
    expect(wrapper.find('[data-testid="admin-audit-user-chip"]').exists()).toBe(false)
  })

  it('names the account by its number when its name cannot be loaded, and Clear filters removes it', async () => {
    users.get.mockRejectedValue({ response: { status: 404, data: { message: 'User was not found.' } } })
    overview.auditLog.mockResolvedValue(pageOf([]))
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?user=42')

    expect(overview.auditLog).toHaveBeenCalledWith({ userId: 42, page: 0, size: 10 })
    expect(wrapper.get('[data-testid="admin-audit-user-chip"]').text()).toBe('By or about account #42')
    expect(wrapper.get('[data-testid="admin-audit-clear"]').text()).toBe('Clear filters')

    await wrapper.get('[data-testid="admin-audit-clear"]').trigger('click')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ page: 0, size: 10 })
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('names the account on the chip in the interface language', async () => {
    users.get.mockResolvedValue({ id: 9, fullName: 'Daniel Tan' })
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?user=9')
    const language = i18n.global.locale.value
    try {
      i18n.global.locale.value = 'ms'
      await flushPromises()
      expect(wrapper.get('[data-testid="admin-audit-user-chip"]').text()).toBe('Oleh atau tentang Daniel Tan')
      i18n.global.locale.value = 'zh-CN'
      await flushPromises()
      expect(wrapper.get('[data-testid="admin-audit-user-chip"]').text()).toBe('Daniel Tan 的操作及与其相关的记录')
    } finally {
      i18n.global.locale.value = language
    }
  })

  it('keeps the page in the address, starts from it and goes back to the first page on a filter change', async () => {
    overview.auditLog.mockImplementation(async (query: { page: number }) =>
      pageOf([entry({ id: 50 + query.page })], query.page, 25),
    )
    const { wrapper, router } = await mountAtRoute(
      AdminAuditLogPage,
      '/admin/audit-log?action=ADMIN_USER_DEACTIVATE&page=2',
    )
    expect(overview.auditLog).toHaveBeenCalledWith({ actionType: 'ADMIN_USER_DEACTIVATE', page: 1, size: 10 })

    await wrapper.get('nav.pagination button[aria-label="Next"]').trigger('click')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ actionType: 'ADMIN_USER_DEACTIVATE', page: 2, size: 10 })
    expect(router.currentRoute.value.query).toEqual({ action: 'ADMIN_USER_DEACTIVATE', page: '3' })

    await wrapper.get('[data-testid="admin-audit-action"]').setValue('')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ page: 0, size: 10 })
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('follows the address when the Audit log link opens the page again without a query', async () => {
    overview.auditLog.mockImplementation(async (query: { page: number }) => pageOf([entry()], query.page, 25))
    const { wrapper, router } = await mountAtRoute(
      AdminAuditLogPage,
      '/admin/audit-log?q=Daniel&from=2026-05-01&page=2',
    )
    expect(overview.auditLog).toHaveBeenLastCalledWith({ keyword: 'Daniel', from: '2026-05-01', page: 1, size: 10 })

    await router.push('/admin/audit-log')
    await flushPromises()

    expect(overview.auditLog).toHaveBeenLastCalledWith({ page: 0, size: 10 })
    expect((wrapper.get('[data-testid="admin-audit-search"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-testid="admin-audit-from"]').element as HTMLInputElement).value).toBe('')
    expect(router.currentRoute.value.query).toEqual({})

    await router.push('/admin/audit-log?q=Farah&to=2026-02-30&page=3')
    await flushPromises()

    expect(overview.auditLog).toHaveBeenLastCalledWith({ keyword: 'Farah', page: 2, size: 10 })
    expect(wrapper.get<HTMLInputElement>('[data-testid="admin-audit-to"]').element.value).toBe('')
    expect(router.currentRoute.value.query.page).toBe('3')
  })

  it.each([
    ['9', 8],
    ['9007199254740991', 2_147_483_647],
  ])('steps back from page %s to the last page the API returns', async (addressPage, requestedPage) => {
    overview.auditLog.mockImplementation(async (query: { page: number }) =>
      query.page > 1 ? pageOf([], query.page, 15) : pageOf([entry({ id: 60 + query.page })], query.page, 15),
    )
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, `/admin/audit-log?page=${addressPage}`)

    expect(overview.auditLog).toHaveBeenCalledWith({ page: requestedPage, size: 10 })
    expect(overview.auditLog).toHaveBeenLastCalledWith({ page: 1, size: 10 })
    expect(wrapper.findAll('[data-testid="admin-audit-row"]')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('No events match')
    expect(router.currentRoute.value.query).toEqual({ page: '2' })
  })

  it('names the CSV after the local day when the server sends no file name', async () => {
    // Half past midnight in Kuala Lumpur is still the day before in UTC.
    vi.stubEnv('TZ', 'Asia/Kuala_Lumpur')
    try {
      vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-05-17T16:30:00Z') })
      overview.exportAuditLog.mockResolvedValue({ blob: new Blob(['id\r\n']), fileName: null })
      const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log')

      await wrapper.get('[data-testid="admin-audit-export"]').trigger('click')
      await flushPromises()

      expect(download.saveBlobDownload).toHaveBeenLastCalledWith(expect.anything(), 'audit-log-2026-05-18.csv')
    } finally {
      vi.unstubAllEnvs()
    }
  })

  it('explains a From date after the To date instead of searching', async () => {
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log')

    await wrapper.get('[data-testid="admin-audit-to"]').setValue('2026-05-01')
    await flushPromises()
    overview.auditLog.mockClear()
    await wrapper.get('[data-testid="admin-audit-from"]').setValue('2026-05-10')
    await flushPromises()

    expect(wrapper.get('#admin-audit-range-error').text()).toBe('The From date must be on or before the To date.')
    expect(wrapper.get('[data-testid="admin-audit-from"]').attributes('aria-invalid')).toBe('true')
    expect(overview.auditLog).not.toHaveBeenCalled()
    expect(wrapper.findAll('[data-testid="admin-audit-row"]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('No events match')
    expect(wrapper.get('[data-testid="admin-audit-export"]').attributes('disabled')).toBeDefined()
  })

  it('names the search that matched nothing; the button says what it clears and is not the primary action', async () => {
    overview.auditLog.mockResolvedValue(pageOf([]))
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?q=Zhao%20Min')

    const empty = () => wrapper.get('.state-panel.is-empty')
    const clear = () => wrapper.get('[data-testid="admin-audit-clear"]')
    expect(empty().get('h3').text()).toBe('No events match “Zhao Min”')
    expect(empty().text()).toContain('Check the spelling, or clear the search to see all events.')
    expect(clear().text()).toBe('Clear search')
    // The filters above stay the way forward.
    expect(clear().classes()).toContain('btn-secondary')

    // With a filter as well, the button clears, and says it clears, the filters too.
    await wrapper.get('[data-testid="admin-audit-action"]').setValue('ADMIN_CLASS_PUBLISH')
    await flushPromises()
    expect(empty().get('h3').text()).toBe('No events match “Zhao Min”')
    expect(empty().text()).toContain('Try other dates, another action or a shorter search.')
    expect(clear().text()).toBe('Clear filters')

    await clear().trigger('click')
    await flushPromises()
    expect(overview.auditLog).toHaveBeenLastCalledWith({ page: 0, size: 10 })
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('says no entry matched the filters when there is no search', async () => {
    overview.auditLog.mockResolvedValue(pageOf([]))
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?actor=nur@example.com')

    expect(wrapper.get('.state-panel.is-empty h3').text()).toBe('No events match')
    expect(wrapper.get('[data-testid="admin-audit-clear"]').text()).toBe('Clear filters')
    expect(wrapper.get('[data-testid="admin-audit-clear"]').classes()).toContain('btn-secondary')
  })

  it('opens an entry in a drawer with the reason and a link to the account', async () => {
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log')
    expect(wrapper.find('[data-testid="admin-audit-drawer"]').exists()).toBe(false)

    await wrapper.findAll('[data-testid="admin-audit-row"]')[0].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.entry).toBe('5')
    const drawer = wrapper.get('[data-testid="admin-audit-drawer"]')
    expect(drawer.text()).toContain('Nur Admin: Account deactivated')
    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Repeated spam')
    expect(wrapper.get('[data-testid="admin-audit-open-target"]').attributes('href')).toBe('/admin/users?user=9')
    expect(wrapper.get('[data-testid="admin-audit-open-target"]').text()).toBe('Open Daniel Tan')
    expect(overview.auditEntry).not.toHaveBeenCalled()

    await drawer.get('.overlay-header button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.entry).toBeUndefined()
  })

  it('keeps a filter that is still on its way into the address when an entry opens', async () => {
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log')

    // The row is opened before the filter's own navigation has finished.
    await wrapper.get('[data-testid="admin-audit-action"]').setValue('ADMIN_USER_DEACTIVATE')
    await wrapper.findAll('[data-testid="admin-audit-row"]')[0]!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ action: 'ADMIN_USER_DEACTIVATE', entry: '5' })
    expect((wrapper.get('[data-testid="admin-audit-action"]').element as HTMLSelectElement).value).toBe(
      'ADMIN_USER_DEACTIVATE',
    )
    expect(overview.auditLog).toHaveBeenLastCalledWith({ actionType: 'ADMIN_USER_DEACTIVATE', page: 0, size: 10 })

    await wrapper.get('[data-testid="admin-audit-action"]').setValue('')
    await wrapper.get('[data-testid="admin-audit-drawer"] .overlay-header button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('loads a linked entry that is not on the current page', async () => {
    overview.auditEntry.mockResolvedValue(
      entry({ id: 77, actionType: 'SUPPORT_REPLY_SENT', category: 'SUPPORT', reason: null, targetUserId: null }),
    )
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?entry=77')

    expect(overview.auditEntry).toHaveBeenCalledWith(77)
    const drawer = wrapper.get('[data-testid="admin-audit-drawer"]')
    expect(drawer.text()).toContain('Nur Admin: Support reply sent')
    expect(wrapper.find('[data-testid="admin-audit-open-target"]').exists()).toBe(false)
  })

  it('exports the filtered log as CSV', async () => {
    const file = { blob: new Blob(['id\r\n']), fileName: 'audit-log.csv' }
    overview.exportAuditLog.mockResolvedValue(file)
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?action=ADMIN_CLASS_PUBLISH')

    await wrapper.get('[data-testid="admin-audit-export"]').trigger('click')
    await flushPromises()

    expect(overview.exportAuditLog).toHaveBeenCalledWith({ actionType: 'ADMIN_CLASS_PUBLISH' })
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

  it.each(['success', 'failure'])('keeps the newer linked entry after an older request %s', async (outcome) => {
    const earlier = deferred<AuditEntry>()
    overview.auditEntry.mockImplementation(async (id: number) =>
      id === 77 ? earlier.promise : entry({ id: 78, reason: 'Current entry reason' }),
    )
    const { wrapper, router } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?entry=77')
    await router.replace('/admin/audit-log?entry=78')
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Current entry reason')

    if (outcome === 'success') earlier.resolve(entry({ id: 77, reason: 'Earlier entry reason' }))
    else earlier.reject(new Error('Earlier entry failed'))
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Current entry reason')
    expect(wrapper.get('[data-testid="admin-audit-drawer"]').find('[role="alert"]').exists()).toBe(false)
  })

  it('keeps an on-screen entry visible when a closed linked entry fails', async () => {
    const linked = deferred<AuditEntry>()
    overview.auditEntry.mockReturnValue(linked.promise)
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?entry=77')
    await wrapper.get('[data-testid="admin-audit-drawer"] .overlay-header button').trigger('click')
    await flushPromises()
    await wrapper.findAll('[data-testid="admin-audit-row"]')[0]!.trigger('click')
    await flushPromises()
    linked.reject(new Error('Closed entry failed'))
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Repeated spam')
    expect(wrapper.get('[data-testid="admin-audit-drawer"]').find('[role="alert"]').exists()).toBe(false)
  })

  it('loads a linked entry even when the list request fails', async () => {
    overview.auditLog.mockRejectedValue(new Error('List unavailable'))
    overview.auditEntry.mockResolvedValue(entry({ id: 77, reason: 'Linked entry is available' }))
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?entry=77')

    expect(overview.auditEntry).toHaveBeenCalledWith(77)
    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Linked entry is available')
  })

  it('retries just the linked entry after a failed lookup', async () => {
    overview.auditEntry.mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValueOnce(entry({ id: 77 }))
    const { wrapper } = await mountAtRoute(AdminAuditLogPage, '/admin/audit-log?entry=77')
    const drawer = wrapper.get('[data-testid="admin-audit-drawer"]')
    expect(drawer.get('h2').text()).toBe('This entry could not be loaded')
    await drawer.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(overview.auditEntry.mock.calls).toEqual([[77], [77]])
    expect(overview.auditLog).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="admin-audit-reason"]').text()).toBe('Repeated spam')
  })
})
