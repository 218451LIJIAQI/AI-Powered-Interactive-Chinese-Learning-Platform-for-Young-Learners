import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { adminUsers } from '@/demo/data'
import { i18n } from '@/plugins/i18n'
import type { AdminUserListItemResponse, PageResponse } from '@/types/api'

const api = vi.hoisted(() => ({
  users: vi.fn(),
  auditLogs: vi.fn(),
  updateUserStatus: vi.fn(),
}))
const router = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/api/services', () => ({ adminApi: api }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

import AdminUsersPage from './AdminUsersPage.vue'

function pageOf(items: AdminUserListItemResponse[], page = 0, totalItems = items.length, size = 10) {
  return {
    items,
    page,
    size,
    totalItems,
    totalPages: Math.ceil(totalItems / size),
  } satisfies PageResponse<AdminUserListItemResponse>
}

function mountPage() {
  return mount(AdminUsersPage, { global: { plugins: [i18n] } })
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

describe('AdminUsersPage server-side directory', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    vi.clearAllMocks()
    api.auditLogs.mockResolvedValue([])
    api.users.mockResolvedValue(pageOf(adminUsers.items.slice(0, 10), 0, 25))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('requests the first page and shows the server total and page count', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(api.users).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(wrapper.get('[data-testid="admin-users-total"]').text()).toContain('25')
    expect(wrapper.get('[data-testid="admin-users-page-label"]').text()).toContain('Page 1 of 3')
    expect(wrapper.findAll('[data-testid="admin-users-row"]')).toHaveLength(10)
  })

  it('pages, filters and debounces the keyword on the server, resetting to page 0', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="admin-users-next-page"]').trigger('click')
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith({ page: 1, size: 10 })

    await wrapper.get('[data-testid="admin-users-role-teacher"]').trigger('click')
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith({ role: 'TEACHER', page: 0, size: 10 })

    await wrapper.get('[data-testid="admin-users-active-filter"]').setValue('INACTIVE')
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith({ role: 'TEACHER', active: false, page: 0, size: 10 })

    await wrapper.get('[data-testid="admin-users-teacher-status-filter"]').setValue('PENDING')
    await wrapper.get('[data-testid="admin-users-page-size"]').setValue('20')
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith({
      role: 'TEACHER',
      status: 'PENDING',
      active: false,
      page: 0,
      size: 20,
    })

    const callsBeforeTyping = api.users.mock.calls.length
    await wrapper.get('[data-testid="admin-users-search"]').setValue('Mei')
    await flushPromises()
    expect(api.users.mock.calls.length).toBe(callsBeforeTyping)
    await wait(350)
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith({
      keyword: 'Mei',
      role: 'TEACHER',
      status: 'PENDING',
      active: false,
      page: 0,
      size: 20,
    })
  })

  it('deactivates a user found on a later page and refreshes that page', async () => {
    const secondPage = adminUsers.items.slice(10)
    api.users.mockImplementation(async (query: { page: number }) =>
      query.page === 1 ? pageOf(secondPage, 1, 12) : pageOf(adminUsers.items.slice(0, 10), 0, 12),
    )
    api.updateUserStatus.mockResolvedValue({ ...secondPage[0], active: false })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="admin-users-next-page"]').trigger('click')
    await flushPromises()
    const target = secondPage[0]
    expect(wrapper.text()).toContain(target.fullName)

    await wrapper.get('[data-testid="admin-users-toggle-status"]').trigger('click')
    await flushPromises()

    expect(api.updateUserStatus).toHaveBeenCalledWith(target.userId, { active: !target.active })
    expect(api.users).toHaveBeenLastCalledWith({ page: 1, size: 10 })
  })

  it('asks for confirmation before suspending an active account', async () => {
    const target = adminUsers.items[0]
    expect(target.active).toBe(true)
    api.updateUserStatus.mockResolvedValue({ ...target, active: false })
    const wrapper = mountPage()
    await flushPromises()

    vi.mocked(window.confirm).mockReturnValueOnce(false)
    await wrapper.get('[data-testid="admin-users-toggle-status"]').trigger('click')
    await flushPromises()
    expect(window.confirm).toHaveBeenCalledTimes(1)
    expect(api.updateUserStatus).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="admin-users-toggle-status"]').trigger('click')
    await flushPromises()
    expect(api.updateUserStatus).toHaveBeenCalledWith(target.userId, { active: false })
  })

  it('routes the role-specific shortcut buttons to the page their label names', async () => {
    const student = adminUsers.items.find((item) => item.roles.includes('STUDENT'))!
    const admin = adminUsers.items.find((item) => item.roles.includes('ADMIN'))!
    api.users.mockResolvedValue(pageOf([student, admin], 0, 2))
    const wrapper = mountPage()
    await flushPromises()

    const button = (label: string) => wrapper.findAll('button').find((item) => item.text() === label)!
    await button('Open analytics').trigger('click')
    expect(router.push).toHaveBeenLastCalledWith('/admin/analytics')

    await wrapper.findAll('[data-testid="admin-users-row"]')[1].trigger('click')
    await button('Open platform analytics').trigger('click')
    expect(router.push).toHaveBeenLastCalledWith('/admin/analytics')
  })
})
