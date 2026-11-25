import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick, provide } from 'vue'
import type { Router } from 'vue-router'

import { setAccessToken } from '@/api/http'
import { unreadNotificationsKey } from '@/composables/navigationCounts'
import { useResource } from '@/composables/useResource'
import { i18n } from '@/plugins/i18n'
import { deferred, pageOf } from '@/test/helpers'
import { testRouter } from '@/test/routes'
import type { NotificationResponse, NotificationStatus, PageResponse } from '@/types/api'

const notificationsApi = vi.hoisted(() => ({
  page: vi.fn(),
  unreadCount: vi.fn(),
  markAsRead: vi.fn(),
  markAllRead: vi.fn(),
  markUnread: vi.fn(),
}))
let router: Router
const auth = vi.hoisted(() => ({ activeRole: 'STUDENT' as 'STUDENT' | 'ADMIN' }))

vi.mock('@/api/services', () => ({ notificationsApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

import NotificationsPage from './NotificationsPage.vue'

function notification(id: number, overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id,
    title: `Daily goal reached ${id}`,
    message: 'You earned 30 XP today.',
    type: 'DAILY_GOAL',
    status: 'UNREAD',
    targetPath: '/student/progress',
    createdAt: '2026-05-18T15:30:00',
    ...overrides,
  }
}

/** The notifications as the server keeps them: every read, unread and undo request changes them. */
let stored: NotificationResponse[] = []

function setStatus(ids: readonly number[], status: NotificationStatus): void {
  stored = stored.map((row) => (ids.includes(row.id) ? { ...row, status } : row))
}

async function mountPage(attachTo?: HTMLElement) {
  router = await testRouter(auth.activeRole === 'ADMIN' ? '/admin/notifications' : '/student/notifications')
  vi.spyOn(router, 'push')
  const wrapper = mount(
    {
      setup() {
        provide(unreadNotificationsKey, useResource(notificationsApi.unreadCount))
        return () => h(NotificationsPage)
      },
    },
    { attachTo, global: { plugins: [router], stubs: { teleport: true, IconGlyph: true } } },
  )
  await flushPromises()
  return wrapper
}

type Page = Awaited<ReturnType<typeof mountPage>>

const rows = (wrapper: Page) => wrapper.findAll('[data-testid="notification-item"]')
const chip = (wrapper: Page, name: string) =>
  wrapper.get(`[data-testid="notifications-filter-${name}"]`).text().replace(/\s+/g, ' ')
const unreadPills = (wrapper: Page) => rows(wrapper).filter((row) => row.text().includes('Unread'))

describe('NotificationsPage', () => {
  beforeEach(() => {
    Object.values(notificationsApi).forEach((fn) => fn.mockReset())
    setAccessToken(null)
    auth.activeRole = 'STUDENT'
    stored = [notification(1), notification(2, { status: 'READ', targetPath: null })]
    notificationsApi.page.mockImplementation(
      async ({ status, page, size }: { status?: NotificationStatus; page: number; size: number }) => {
        const filtered = stored.filter((row) => !status || row.status === status)
        const start = page * size
        return pageOf(filtered.slice(start, start + size), page, filtered.length, size)
      },
    )
    notificationsApi.unreadCount.mockImplementation(async () => ({
      unreadCount: stored.filter((row) => row.status === 'UNREAD').length,
    }))
    notificationsApi.markAsRead.mockImplementation(async (id: number) => {
      setStatus([id], 'READ')
      return {}
    })
    notificationsApi.markAllRead.mockImplementation(async () => {
      const ids = stored.filter((row) => row.status === 'UNREAD').map((row) => row.id)
      setStatus(ids, 'READ')
      return ids
    })
    notificationsApi.markUnread.mockImplementation(async (ids: readonly number[]) => {
      const restored = stored.filter((row) => row.status === 'READ' && ids.includes(row.id)).map((row) => row.id)
      setStatus(restored, 'UNREAD')
      return restored
    })
  })

  it('shows each notification with its date, the Unread pill and its own actions', async () => {
    const wrapper = await mountPage()

    const [unread, read] = rows(wrapper)
    expect(unread.get('.row-list-title').text()).toBe('Daily goal reached 1')
    expect(unread.text()).toContain('You earned 30 XP today.')
    expect(unread.text()).toContain('Unread')
    expect(unread.get('time').text()).toBe('18 May, 3:30 PM')
    expect(unread.get('time').attributes('datetime')).toBe('2026-05-18T15:30:00')
    // [Mark as read] [Open ›]: a quiet button and the row's way on.
    expect(unread.get('[data-testid="notification-mark-read"]').classes()).toContain('btn-ghost')
    expect(unread.get('[data-testid="notification-open"]').classes()).toContain('btn-secondary')
    // A read notification has no Unread pill and no Mark as read; one without a target has no Open.
    expect(read.text()).not.toContain('Unread')
    expect(read.find('[data-testid="notification-mark-read"]').exists()).toBe(false)
    expect(read.find('[data-testid="notification-open"]').exists()).toBe(false)
    // No category tag: the title says what it is about.
    expect(unread.text()).not.toContain('DAILY_GOAL')
    expect(wrapper.get('h1').text()).toBe('Notifications')
  })

  it('groups the rows by day and marks what each is about, in colour while it is unread', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 4, 18, 20, 0) })
    stored = [
      notification(1, { type: 'DAILY_GOAL_REMINDER', createdAt: '2026-05-18T19:15:00' }),
      notification(2, { type: 'RANK_REACHED', status: 'READ', createdAt: '2026-05-17T09:00:00' }),
      notification(3, { type: 'SOMETHING_NEW', status: 'READ', createdAt: '2026-05-02T09:00:00' }),
    ]
    try {
      const wrapper = await mountPage()

      expect(wrapper.findAll('.notifications-day-title').map((title) => title.text())).toEqual([
        'Today',
        'Yesterday',
        'Earlier',
      ])
      const dayOf = (key: string) =>
        wrapper
          .get(`[data-testid="notifications-day-${key}"]`)
          .findAll('.row-list-title')
          .map((title) => title.text())
      expect(dayOf('today')).toEqual(['Daily goal reached 1'])
      expect(dayOf('yesterday')).toEqual(['Daily goal reached 2'])
      expect(dayOf('earlier')).toEqual(['Daily goal reached 3'])
      // The mark: its kind's tint, shown while the row is unread; a kind the page does not know gets a bell.
      const [today, yesterday, earlier] = rows(wrapper)
      expect(today!.classes()).toContain('is-unread')
      expect(today!.get('.notifications-type').classes()).toContain('tint-amber')
      expect(yesterday!.classes()).not.toContain('is-unread')
      expect(yesterday!.get('.notifications-type').classes()).toContain('tint-violet')
      expect(earlier!.get('.notifications-type').classes()).toContain('tint-neutral')
      expect(today!.get('.notifications-type').attributes('aria-hidden')).toBe('true')
    } finally {
      vi.useRealTimers()
    }
  })

  it('shows the stored English titles and messages in the interface language and follows a language switch', async () => {
    stored = [
      notification(1, {
        title: 'An admin replied to your AI report',
        message: "Your report was resolved. Read the admin's note in AI Helper › Ask SparkBot › My reports.",
      }),
      notification(2, { status: 'READ', title: 'A notice we never wrote', message: 'Kept as stored.' }),
    ]
    i18n.global.locale.value = 'ms'
    const wrapper = await mountPage()
    const label = (testId: string) => rows(wrapper)[0].get(`[data-testid="${testId}"]`).attributes('aria-label')

    expect(rows(wrapper)[0].get('.row-list-title').text()).toBe('Pentadbir telah membalas laporan AI anda')
    expect(rows(wrapper)[0].text()).toContain('Laporan anda telah diselesaikan.')
    expect(label('notification-open')).toBe('Buka: Pentadbir telah membalas laporan AI anda')
    expect(label('notification-mark-read')).toBe('Tandakan telah dibaca: Pentadbir telah membalas laporan AI anda')
    expect(rows(wrapper)[1].text()).toContain('A notice we never wrote')
    expect(rows(wrapper)[1].text()).toContain('Kept as stored.')
    const msTime = rows(wrapper)[0].get('time').text()

    i18n.global.locale.value = 'zh-CN'
    await nextTick()
    expect(rows(wrapper)[0].get('.row-list-title').text()).toBe('管理员回复了你的 AI 举报')
    expect(label('notification-open')).toBe('打开：管理员回复了你的 AI 举报')
    const zhTime = rows(wrapper)[0].get('time').text()
    expect(zhTime).not.toBe(msTime)

    i18n.global.locale.value = 'en'
    await nextTick()
    expect(rows(wrapper)[0].get('.row-list-title').text()).toBe('An admin replied to your AI report')
    expect(label('notification-mark-read')).toBe('Mark as read: An admin replied to your AI report')
    expect(rows(wrapper)[0].get('time').text()).toBe('18 May, 3:30 PM')
  })

  it('counts All · Unread · Read and filters on the server by status', async () => {
    const wrapper = await mountPage()
    expect(notificationsApi.page).toHaveBeenLastCalledWith({ page: 0, size: 20 })
    expect(chip(wrapper, 'all')).toBe('All · 2')
    expect(chip(wrapper, 'unread')).toBe('Unread · 1')
    expect(chip(wrapper, 'read')).toBe('Read · 1')

    await wrapper.get('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.page).toHaveBeenLastCalledWith({ status: 'UNREAD', page: 0, size: 20 })
    expect(wrapper.get('[data-testid="notifications-filter-unread"]').attributes('aria-pressed')).toBe('true')
    expect(rows(wrapper)).toHaveLength(1)
    // The All chip keeps the account-wide total while another filter is on screen.
    expect(chip(wrapper, 'all')).toBe('All · 2')
  })

  it('clears the previous filter’s rows while the new filter loads', async () => {
    const wrapper = await mountPage()
    const loadingRead = deferred<PageResponse<NotificationResponse>>()
    notificationsApi.page.mockReturnValueOnce(loadingRead.promise)

    await wrapper.get('[data-testid="notifications-filter-read"]').trigger('click')
    expect(rows(wrapper)).toHaveLength(0)
    expect(chip(wrapper, 'all')).toBe('All · 2')

    loadingRead.resolve(pageOf(stored.filter((row) => row.status === 'READ')))
    await flushPromises()
    expect(rows(wrapper).map((row) => row.get('.row-list-title').text())).toEqual(['Daily goal reached 2'])
  })

  it('marks one as read at once, tells the bell and offers Undo', async () => {
    stored = [notification(1), notification(2)]
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="notification-mark-read"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.markAsRead).toHaveBeenCalledWith(1)
    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(2)
    expect(rows(wrapper)[0].text()).not.toContain('Unread')
    expect(rows(wrapper)[1].text()).toContain('Unread')
    expect(chip(wrapper, 'unread')).toBe('Unread · 1')
    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.get('span').text()).toBe('Marked as read.')
    expect(toast.get('.toast-action').text()).toBe('Undo')

    await toast.get('.toast-action').trigger('click')
    await flushPromises()

    expect(notificationsApi.markUnread).toHaveBeenCalledWith([1])
    expect(unreadPills(wrapper)).toHaveLength(2)
    expect(chip(wrapper, 'unread')).toBe('Unread · 2')
    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(3)
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('keeps a row read on the Unread filter in place until the filter changes', async () => {
    stored = [notification(1), notification(2)]
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()
    const pageReads = notificationsApi.page.mock.calls.length

    await rows(wrapper)[0].get('[data-testid="notification-mark-read"]').trigger('click')
    await flushPromises()

    // S3.18: the row stays without its pill and button, so nothing moves under the pointer; the counts follow.
    expect(rows(wrapper)).toHaveLength(2)
    expect(rows(wrapper)[0].text()).not.toContain('Unread')
    expect(rows(wrapper)[0].find('[data-testid="notification-mark-read"]').exists()).toBe(false)
    expect(chip(wrapper, 'unread')).toBe('Unread · 1')
    expect(notificationsApi.page).toHaveBeenCalledTimes(pageReads)

    // Another filter and back reads the list again: only the unread one is left.
    await wrapper.get('[data-testid="notifications-filter-all"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()
    expect(rows(wrapper)).toHaveLength(1)
    expect(rows(wrapper)[0].get('.row-list-title').text()).toBe('Daily goal reached 2')
  })

  it('keeps keyboard focus in place after the last action on a notification disappears', async () => {
    stored = [notification(1, { targetPath: null }), notification(2)]
    const wrapper = await mountPage(document.body)
    try {
      const row = rows(wrapper)[0]!
      const mark = row.get<HTMLButtonElement>('[data-testid="notification-mark-read"]')
      mark.element.focus()
      await mark.trigger('click')
      await flushPromises()
      expect(document.activeElement).toBe(row.element)

      const markAll = wrapper.get<HTMLButtonElement>('[data-testid="notifications-mark-all"]')
      markAll.element.focus()
      await markAll.trigger('click')
      await flushPromises()
      expect(document.activeElement).toBe(wrapper.get('[data-testid="notifications-filter-all"]').element)
    } finally {
      wrapper.unmount()
    }
  })

  it('brings rows back to Unread when marking all as read there is undone', async () => {
    stored = [notification(1), notification(2)]
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="notifications-filter-empty"]').text()).toContain('You’re all caught up')

    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()
    expect(rows(wrapper)).toHaveLength(2)
    expect(unreadPills(wrapper)).toHaveLength(2)
  })

  it('keeps each row busy until its own mark-as-read answers', async () => {
    const first = deferred()
    const second = deferred()
    stored = [notification(1), notification(2)]
    notificationsApi.markAsRead.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const wrapper = await mountPage()
    const markButtons = () => wrapper.findAll<HTMLButtonElement>('[data-testid="notification-mark-read"]')

    await markButtons()[0].trigger('click')
    await markButtons()[1].trigger('click')
    expect(markButtons().map((button) => button.element.disabled)).toEqual([true, true])
    expect(wrapper.get<HTMLButtonElement>('[data-testid="notifications-mark-all"]').element.disabled).toBe(true)
    expect(rows(wrapper)[0].get<HTMLButtonElement>('[data-testid="notification-open"]').element.disabled).toBe(true)

    second.resolve({})
    await flushPromises()
    // The second row is read and loses its button; the first still waits for its answer.
    expect(markButtons()).toHaveLength(1)
    expect(markButtons()[0].element.disabled).toBe(true)

    first.resolve({})
    await flushPromises()
    expect(markButtons()).toHaveLength(0)
  })

  it('says so when Undo fails and keeps the notification read', async () => {
    notificationsApi.markUnread.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notification-mark-read"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice').text()).toContain('The notifications could not be marked unread again.')
    expect(unreadPills(wrapper)).toHaveLength(0)
  })

  it('opens the target page and reads the notification on the way, without a toast', async () => {
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="notification-open"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.markAsRead).toHaveBeenCalledWith(1)
    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(2)
    expect(router.push).toHaveBeenCalledWith('/student/progress')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it.each(['success', 'failure'])(
    'does not jump back after leaving while marking a notification: %s',
    async (result) => {
      const read = deferred()
      notificationsApi.markAsRead.mockReturnValueOnce(read.promise)
      const wrapper = await mountPage()
      await wrapper.get('[data-testid="notification-open"]').trigger('click')
      wrapper.unmount()

      if (result === 'success') read.resolve({})
      else read.reject(new Error('offline'))
      await flushPromises()
      expect(router.push).not.toHaveBeenCalled()
    },
  )

  it('keeps a sidebar destination when opening an unread notification finishes during navigation', async () => {
    const read = deferred<void>()
    notificationsApi.markAsRead.mockReturnValueOnce(read.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notification-open"]').trigger('click')
    const homeReady = deferred<void>()
    router.beforeResolve((to) => (to.name === 'student-home' ? homeReady.promise : true))
    const openingHome = router.push('/student/home')
    await flushPromises()
    read.resolve()
    await flushPromises()
    homeReady.resolve()
    await openingHome
    expect(router.currentRoute.value.path).toBe('/student/home')
  })

  it('keeps the newer notification target when an earlier read finishes later', async () => {
    const firstRead = deferred()
    stored = [notification(1), notification(2, { status: 'READ', targetPath: '/student/practice' })]
    notificationsApi.markAsRead.mockReturnValueOnce(firstRead.promise)
    const wrapper = await mountPage()
    const buttons = wrapper.findAll('[data-testid="notification-open"]')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    await flushPromises()
    expect(router.push).toHaveBeenCalledExactlyOnceWith('/student/practice')

    firstRead.resolve({})
    await flushPromises()
    expect(router.push).toHaveBeenCalledExactlyOnceWith('/student/practice')
  })

  it('marks all as read at once, tells the bell and undoes exactly the notifications it marked', async () => {
    stored = [notification(1), notification(2, { status: 'READ' }), notification(3)]
    const wrapper = await mountPage()
    expect(unreadPills(wrapper)).toHaveLength(2)

    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.markAllRead).toHaveBeenCalledTimes(1)
    expect(unreadPills(wrapper)).toHaveLength(0)
    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(2)
    expect(chip(wrapper, 'unread')).toBe('Unread · 0')
    // Nothing is left to mark.
    expect(wrapper.find('[data-testid="notifications-mark-all"]').exists()).toBe(false)
    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.get('span').text()).toBe('All notifications marked as read.')

    await toast.get('.toast-action').trigger('click')
    await flushPromises()

    // Only the two it marked are unread again; the one read before stays read.
    expect(notificationsApi.markUnread).toHaveBeenCalledWith([1, 3])
    expect(rows(wrapper).map((row) => row.text().includes('Unread'))).toEqual([true, false, true])
    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(3)
    expect(wrapper.find('[data-testid="notifications-mark-all"]').exists()).toBe(true)
  })

  it.each([false, true])('undoes a large read in bounded batches (last batch fails: %s)', async (fails) => {
    const ids = Array.from({ length: 1001 }, (_, index) => index + 1)
    stored = ids.map((id) => notification(id))
    if (fails) {
      notificationsApi.markUnread
        .mockImplementationOnce(async (batch: readonly number[]) => {
          setStatus(batch, 'UNREAD')
          return batch
        })
        .mockRejectedValueOnce(new Error('offline'))
    }
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()

    expect(notificationsApi.markUnread.mock.calls).toEqual([[ids.slice(0, 1000)], [[1001]]])
    expect(chip(wrapper, 'unread')).toBe(fails ? 'Unread · 1000' : 'Unread · 1001')
    expect(wrapper.find('.data-notice').exists()).toBe(fails)
  })

  it('offers no Undo when marking all found nothing unread', async () => {
    notificationsApi.markAllRead.mockResolvedValueOnce([])
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="toast"] span').text()).toBe('All notifications marked as read.')
    expect(wrapper.find('[data-testid="toast"] .toast-action').exists()).toBe(false)
  })

  it('stops undoing old notifications when another session opens between batches', async () => {
    stored = Array.from({ length: 1001 }, (_, index) => notification(index + 1))
    const first = deferred<number[]>()
    notificationsApi.markUnread.mockReturnValueOnce(first.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    setAccessToken('another-notification-session')
    first.resolve([])
    await flushPromises()
    expect(notificationsApi.markUnread).toHaveBeenCalledTimes(1)
  })

  it.each(['notification-mark-read', 'notifications-mark-all'])(
    'ignores an Undo displayed for another session after %s',
    async (action) => {
      const wrapper = await mountPage()
      await wrapper.get(`[data-testid="${action}"]`).trigger('click')
      await flushPromises()
      setAccessToken(`another-session-after-${action}`)
      await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
      await flushPromises()
      expect(notificationsApi.markUnread).not.toHaveBeenCalled()
    },
  )

  it('does not tell the bell when marking all as read fails', async () => {
    notificationsApi.markAllRead.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.data-notice').text()).toContain('The notifications were not marked as read.')
    expect(unreadPills(wrapper)).toHaveLength(1)
  })

  it('shows Mark all as read only while something is unread, and not on the Read filter', async () => {
    const wrapper = await mountPage()
    expect(wrapper.find('[data-testid="notifications-mark-all"]').exists()).toBe(true)

    await wrapper.get('[data-testid="notifications-filter-read"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="notifications-mark-all"]').exists()).toBe(false)

    stored = [notification(1, { status: 'READ' })]
    const allRead = await mountPage()
    expect(allRead.find('[data-testid="notifications-mark-all"]').exists()).toBe(false)
  })

  it.each([
    ['unread', 'READ', 'You’re all caught up', 'No unread notifications. Earlier ones are under All and Read.'],
    ['read', 'UNREAD', 'Nothing read yet', 'Notifications you have read appear here.'],
  ] as const)('explains the empty %s filter and offers the way home', async (filter, status, title, message) => {
    stored = [notification(1, { status })]
    const wrapper = await mountPage()

    await wrapper.get(`[data-testid="notifications-filter-${filter}"]`).trigger('click')
    await flushPromises()

    const empty = wrapper.get('[data-testid="notifications-filter-empty"]')
    expect(empty.get('h3').text()).toBe(title)
    expect(empty.text()).toContain(message)
    const home = empty.get('[data-testid="notifications-start-page"]')
    expect(home.text()).toBe('Go to Home')
    expect(home.attributes('href')).toBe('/student/home')
    expect(home.classes()).toEqual(['btn', 'btn-secondary'])
    // The filters stay, so the learner can switch back.
    expect(wrapper.find('[data-testid="notifications-filter-all"]').exists()).toBe(true)
  })

  it('shows one empty card with the way home when nothing has arrived yet', async () => {
    stored = []
    const wrapper = await mountPage()

    expect(wrapper.get('h2').text()).toBe('No notifications yet')
    expect(wrapper.text()).toContain('your daily goal, new practice sets, rank-ups and class updates')
    // The way out of an empty page is not a main action.
    const home = wrapper.get('[data-testid="notifications-start-page"]')
    expect(home.text()).toBe('Go to Home')
    expect(home.attributes('href')).toBe('/student/home')
    expect(home.classes()).toEqual(['btn', 'btn-secondary'])
    expect(wrapper.find('.btn-primary').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notifications-mark-all"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notifications-filter-all"]').exists()).toBe(false)
  })

  it('sends an admin with no notifications to the dashboard', async () => {
    auth.activeRole = 'ADMIN'
    stored = []
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('AI reports and support requests')
    const dashboard = wrapper.get('[data-testid="notifications-start-page"]')
    expect(dashboard.text()).toBe('Go to Dashboard')
    expect(dashboard.attributes('href')).toBe('/admin/dashboard')
  })

  it('paginates and steps back cleanly without flashing empty-state copy when a later page empties', async () => {
    stored = Array.from({ length: 21 }, (_, index) => notification(index + 1))
    const wrapper = await mountPage()
    expect(rows(wrapper)).toHaveLength(20)

    // Go to page 2 (1 item), then simulate that item disappearing while loading page 2 again.
    const nextButton = wrapper.get('nav[aria-label="Notification pages"] button:last-of-type')
    await nextButton.trigger('click')
    await flushPromises()
    expect(notificationsApi.page).toHaveBeenLastCalledWith({ page: 1, size: 20 })
    expect(rows(wrapper)).toHaveLength(1)

    stored = stored.slice(0, 20)
    const stepBackLoad = deferred<PageResponse<NotificationResponse>>()
    notificationsApi.page.mockResolvedValueOnce(pageOf([], 1, 20, 20)).mockReturnValueOnce(stepBackLoad.promise)

    // Reloading page 1 gets empty items and triggers step-back to page 0: loading state shows, not empty copy.
    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()
    expect(notificationsApi.page).toHaveBeenLastCalledWith({ page: 0, size: 20 })
    expect(rows(wrapper)).toHaveLength(0)
    expect(wrapper.get('.state-panel').text()).toContain('Loading notifications')
    expect(wrapper.find('[data-testid="notifications-filter-empty"]').exists()).toBe(false)

    stepBackLoad.resolve(pageOf(stored.slice(0, 20), 0, 20, 20))
    await flushPromises()
    expect(rows(wrapper)).toHaveLength(20)
  })

  it('shows a retryable error state when loading notifications fails', async () => {
    notificationsApi.page.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPage()

    expect(wrapper.get('.state-panel').text()).toContain('We couldn’t load your notifications')
    await wrapper.get('.state-panel button').trigger('click')
    await flushPromises()

    expect(rows(wrapper)).toHaveLength(2)
  })

  it('shows an error notice and clears busy state when marking one notification as read fails', async () => {
    notificationsApi.markAsRead.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPage()

    const button = wrapper.get<HTMLButtonElement>('[data-testid="notification-mark-read"]')
    await button.trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice').text()).toContain('The notification was not marked as read.')
    expect(button.element.disabled).toBe(false)
    expect(unreadPills(wrapper)).toHaveLength(1)
  })

  it('hides Open when a notification carries an external or unknown targetPath', async () => {
    stored = [
      notification(1, { targetPath: 'https://evil.example/phish' }),
      notification(2, { targetPath: '/unknown/route' }),
    ]
    const wrapper = await mountPage()

    expect(wrapper.findAll('[data-testid="notification-open"]')).toHaveLength(0)
  })

  it('resets to page 0 before reloading when Mark all as read is clicked from page 2 of Unread', async () => {
    stored = Array.from({ length: 21 }, (_, index) => notification(index + 1))
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()
    await wrapper.get('nav[aria-label="Notification pages"] button:last-of-type').trigger('click')
    await flushPromises()
    expect(notificationsApi.page).toHaveBeenLastCalledWith({ status: 'UNREAD', page: 1, size: 20 })

    notificationsApi.page.mockClear()
    await wrapper.get('[data-testid="notifications-mark-all"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.page).toHaveBeenCalledExactlyOnceWith({ status: 'UNREAD', page: 0, size: 20 })
    expect(wrapper.get('[data-testid="notifications-filter-empty"]').text()).toContain('You’re all caught up')
  })

  it('keeps filter counts consistent when new unread notifications arrive while viewing another filter', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="notifications-filter-read"]').trigger('click')
    await flushPromises()
    expect(chip(wrapper, 'all')).toBe('All · 2')
    expect(chip(wrapper, 'read')).toBe('Read · 1')

    // Three new unread notifications arrive while viewing Read.
    stored = [...stored, notification(3), notification(4), notification(5)]
    await wrapper.get('[data-testid="notifications-filter-unread"]').trigger('click')
    await flushPromises()
    // Simulate shared unread count updating from header refresh while viewing Unread.
    await wrapper.get('[data-testid="notification-mark-read"]').trigger('click')
    await flushPromises()
    expect(chip(wrapper, 'unread')).toBe('Unread · 3')
    expect(chip(wrapper, 'all')).toBe('All · 3')
    expect(chip(wrapper, 'read')).toBe('Read · 0')
  })

  it('re-translates action error notices on locale switch and catches failed open navigation', async () => {
    const wrapper = await mountPage()
    vi.mocked(router.push).mockRejectedValueOnce(new Error('chunk failed'))

    await wrapper.get('[data-testid="notification-open"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice').text()).toContain('Something went wrong')
    i18n.global.locale.value = 'ms'
    await nextTick()
    expect(wrapper.get('.data-notice').text()).not.toContain('Something went wrong')
    i18n.global.locale.value = 'en'
  })
})
