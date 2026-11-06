import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import type { NotificationResponse } from '@/types/api'
import { NOTIFICATION_READ_EVENT } from '@/utils/notifications'

const notificationsApi = vi.hoisted(() => ({
  page: vi.fn(),
  unreadCount: vi.fn(),
  markAsRead: vi.fn(),
  markAllRead: vi.fn(),
}))
const router = vi.hoisted(() => ({ push: vi.fn() }))
const auth = vi.hoisted(() => ({ activeRole: 'STUDENT' as 'STUDENT' | 'TEACHER' | 'ADMIN', isApprovedTeacher: false }))

vi.mock('@/api/services', () => ({ notificationsApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRouter: () => router,
}))

import NotificationsPage from './NotificationsPage.vue'

function notification(id: number, overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id,
    title: `Live session starts soon ${id}`,
    message: '“Greetings” begins in 30 minutes.',
    type: 'LIVE_CLASS_REMINDER',
    status: 'UNREAD',
    targetPath: '/student/classes/live',
    createdAt: '2026-05-18T15:30:00',
    readAt: null,
    ...overrides,
  }
}

async function mountPage() {
  const wrapper = mount(NotificationsPage, { global: { stubs: { teleport: true, IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    auth.activeRole = 'STUDENT'
    notificationsApi.page.mockResolvedValue(
      pageOf([notification(1), notification(2, { status: 'READ', type: 'SUPPORT_REQUEST', targetPath: null })]),
    )
    notificationsApi.unreadCount.mockResolvedValue({ unreadCount: 1 })
    notificationsApi.markAsRead.mockResolvedValue({})
    notificationsApi.markAllRead.mockResolvedValue(undefined)
  })

  it('shows the unread total, the type and date of each notification and Open only with a target', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="notifications-unread"]').text()).toBe('1 unread')
    const items = wrapper.findAll('[data-testid="notification-item"]')
    expect(items[0].text()).toContain('Live session')
    expect(items[0].text()).toContain('Unread')
    expect(items[0].find('time').text()).toContain('May 18')
    expect(items[1].text()).toContain('Support')
    expect(items[1].text()).not.toContain('Unread')
    expect(items[0].find('[data-testid="notification-open"]').exists()).toBe(true)
    expect(items[1].find('[data-testid="notification-open"]').exists()).toBe(false)
    expect(items[1].find('[data-testid="notification-mark-read"]').exists()).toBe(false)
    expect(wrapper.find('h1').text()).toBe('Notifications')
  })

  it('counts every filter and filters on the server by status', async () => {
    const wrapper = await mountPage()

    const chip = (name: string) =>
      wrapper.find(`[data-testid="notifications-filter-${name}"]`).text().replace(/\s+/g, ' ')
    expect(chip('all')).toBe('All · 2')
    expect(chip('unread')).toBe('Unread · 1')
    expect(chip('read')).toBe('Read · 1')

    await wrapper.find('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.page).toHaveBeenLastCalledWith({ status: 'UNREAD', page: 0, size: 20 })
    expect(wrapper.find('[data-testid="notifications-filter-unread"]').attributes('aria-pressed')).toBe('true')
  })

  it('marks one as read, tells the topbar and confirms with a toast', async () => {
    const listener = vi.fn()
    window.addEventListener(NOTIFICATION_READ_EVENT, listener)
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="notification-mark-read"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.markAsRead).toHaveBeenCalledWith(1)
    expect(listener).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="toast"]').text()).toBe('Marked as read.')
    expect(wrapper.findAll('[data-testid="notification-item"]')[0].text()).not.toContain('Unread')
    window.removeEventListener(NOTIFICATION_READ_EVENT, listener)
  })

  it('opens the target page and reads the notification on the way', async () => {
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="notification-open"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.markAsRead).toHaveBeenCalledWith(1)
    expect(router.push).toHaveBeenCalledWith('/student/classes/live')
  })

  it('marks all as read', async () => {
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.markAllRead).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="toast"]').text()).toBe('Marked all as read.')
  })

  it('keeps the filters when one filter is empty', async () => {
    notificationsApi.page.mockImplementation(async (query: { status?: string }) =>
      query.status === 'READ' ? pageOf([]) : pageOf([notification(1)]),
    )
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="notifications-filter-read"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Nothing read yet')
    expect(wrapper.find('[data-testid="notifications-filter-all"]').exists()).toBe(true)
  })

  it('shows one empty card with the way home when nothing has arrived yet', async () => {
    notificationsApi.page.mockResolvedValue(pageOf([]))
    notificationsApi.unreadCount.mockResolvedValue({ unreadCount: 0 })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No notifications yet')
    expect(wrapper.text()).toContain('live sessions, teacher feedback and support replies')
    expect(wrapper.text()).toContain('Go to Home')
    expect(wrapper.find('[data-testid="notifications-mark-all"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notifications-filter-all"]').exists()).toBe(false)
  })

  it('sends a teacher with no notifications to the dashboard', async () => {
    auth.activeRole = 'TEACHER'
    auth.isApprovedTeacher = true
    notificationsApi.page.mockResolvedValue(pageOf([]))
    notificationsApi.unreadCount.mockResolvedValue({ unreadCount: 0 })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('new students, content reviews and support replies')
    expect(wrapper.text()).toContain('Go to Dashboard')
  })
})
