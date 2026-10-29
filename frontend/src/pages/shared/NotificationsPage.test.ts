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

vi.mock('@/api/services', () => ({ notificationsApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ activeRole: 'STUDENT' }) }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

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
    notificationsApi.page.mockResolvedValue(
      pageOf([notification(1), notification(2, { status: 'READ', type: 'SUPPORT_REQUEST', targetPath: null })]),
    )
    notificationsApi.unreadCount.mockResolvedValue({ unreadCount: 1 })
    notificationsApi.markAsRead.mockResolvedValue({})
    notificationsApi.markAllRead.mockResolvedValue(undefined)
  })

  it('shows the unread total, the type of each notification and Open only with a target', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="notifications-unread"]').text()).toBe('1 unread')
    const items = wrapper.findAll('[data-testid="notification-item"]')
    expect(items[0].text()).toContain('Live session')
    expect(items[0].text()).toContain('Unread')
    expect(items[1].text()).toContain('Support')
    expect(items[0].find('[data-testid="notification-open"]').exists()).toBe(true)
    expect(items[1].find('[data-testid="notification-open"]').exists()).toBe(false)
    expect(items[1].find('[data-testid="notification-mark-read"]').exists()).toBe(false)
    // Both arrived on the same day, so they share one day heading.
    expect(wrapper.findAll('.notifications-day-title').map((title) => title.text())).toEqual(['Monday, May 18'])
  })

  it('filters on the server by status', async () => {
    const wrapper = await mountPage()
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

  it('shows the empty state of the chosen filter', async () => {
    notificationsApi.page.mockResolvedValue(pageOf([]))
    notificationsApi.unreadCount.mockResolvedValue({ unreadCount: 0 })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No notifications yet')
    expect(wrapper.find('[data-testid="notifications-mark-all"]').attributes('disabled')).toBeDefined()
  })
})
