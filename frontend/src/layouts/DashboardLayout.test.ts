import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const mocks = vi.hoisted(() => ({
  rewards: vi.fn(),
  unreadCount: vi.fn(),
  notificationPage: vi.fn(),
  markAsRead: vi.fn(),
  navCounts: vi.fn(),
  health: vi.fn(),
  lesson: vi.fn(),
  checkLesson: vi.fn(),
}))

vi.mock('@/api/services', () => ({
  shellApi: { navCounts: mocks.navCounts },
  notificationsApi: {
    unreadCount: mocks.unreadCount,
    page: mocks.notificationPage,
    markAsRead: mocks.markAsRead,
  },
  systemApi: { health: mocks.health },
}))
vi.mock('@/api/learn', () => ({
  learnApi: { lesson: mocks.lesson, checkLesson: mocks.checkLesson },
  hintApi: { settings: async () => null },
  xpApi: { rewards: mocks.rewards },
}))

import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import NotificationsPage from '@/pages/shared/NotificationsPage.vue'
import StudentLessonPlayerPage from '@/pages/student/StudentLessonPlayerPage.vue'
import { useAuthStore } from '@/stores/auth'
import { deferred, pageOf } from '@/test/helpers'
import { answerCheck, lessonPlay } from '@/test/learnFixtures'
import type { HealthStatusResponse, NotificationResponse, RewardSummaryResponse, UserRole } from '@/types/api'

import DashboardLayout from './DashboardLayout.vue'

function summary(totalPoints: number, currentStreak: number): RewardSummaryResponse {
  return { totalPoints, currentStreak }
}

async function mountSignedIn(role: UserRole = 'STUDENT', page = 'home', roles: UserRole[] = [role]) {
  const rootPath = role === 'ADMIN' ? '/admin' : '/student'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: rootPath,
        component: DashboardLayout,
        meta: { roles: [role] },
        children: [
          { path: 'home', component: { template: '<div />' } },
          { path: 'notifications', component: NotificationsPage },
          { path: 'ai', name: 'admin-ai-reports', component: AdminAiTabs },
          { path: 'ai/usage', name: 'admin-ai-usage', component: AdminAiTabs },
          { path: 'ai/settings', name: 'admin-ai-settings', component: AdminAiTabs },
          { path: 'learn/lessons/:lessonId', component: StudentLessonPlayerPage, meta: { focus: true } },
          // The catch-all resolves the sidebar's other links.
          { path: ':pathMatch(.*)*', component: { template: '<div />' } },
        ],
      },
    ],
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.currentUser = { id: 7, fullName: 'Amira Hassan', email: 'amira@example.com', roles }

  await router.push(`${rootPath}/${page}`)
  await router.isReady()
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { router, wrapper }
}

const statusText = (wrapper: Awaited<ReturnType<typeof mountSignedIn>>['wrapper']) =>
  wrapper.get('[data-testid="sidebar-status"]').text()

describe('DashboardLayout', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset())
    mocks.rewards.mockResolvedValue(summary(0, 0))
    mocks.unreadCount.mockResolvedValue({ unreadCount: 0 })
    mocks.navCounts.mockResolvedValue({ mistakes: 2 })
    mocks.health.mockResolvedValue({ status: 'UP', ai: 'AVAILABLE' })
    mocks.lesson.mockResolvedValue(lessonPlay())
  })

  it.each([true, false])(
    'keeps the latest health status when an older success=%s finishes last',
    async (olderSuccess) => {
      const running: HealthStatusResponse = { status: 'UP', ai: 'AVAILABLE' }
      const older = deferred<HealthStatusResponse>()
      mocks.health.mockReturnValueOnce(older.promise)
      if (olderSuccess) mocks.health.mockRejectedValueOnce(new Error('latest request failed'))
      else mocks.health.mockResolvedValueOnce(running)
      const { router, wrapper } = await mountSignedIn('ADMIN')
      expect(statusText(wrapper)).toBe('System status')

      await router.push('/admin/users')
      await flushPromises()
      const expected = olderSuccess ? 'Unavailable' : 'Running'
      expect(statusText(wrapper)).toContain(expected)
      if (olderSuccess) older.resolve(running)
      else older.reject(new Error('offline'))
      await flushPromises()
      expect(statusText(wrapper)).toContain(expected)
      expect(mocks.health).toHaveBeenCalledTimes(2)
    },
  )

  it('re-reads XP and streak after a navigation, so the sidebar stays current', async () => {
    mocks.rewards.mockResolvedValueOnce(summary(0, 0)).mockResolvedValueOnce(summary(45, 1))
    const { router, wrapper } = await mountSignedIn()
    expect(statusText(wrapper)).toContain('0 XP · no streak yet')

    await router.push('/student/practice')
    await flushPromises()

    expect(mocks.rewards).toHaveBeenCalledTimes(2)
    expect(mocks.navCounts).toHaveBeenCalledTimes(2)
    expect(statusText(wrapper)).toContain('45 XP · 1-day streak')
  })

  it('shows the learner sidebar, links and rewards to an account that can also open the admin dashboard', async () => {
    mocks.rewards.mockResolvedValue(summary(45, 1))
    const { wrapper } = await mountSignedIn('STUDENT', 'home', ['ADMIN', 'STUDENT'])
    expect(wrapper.get('[data-testid="sidebar-role"]').text()).toBe('Learner')
    expect(wrapper.get('[data-testid="topbar-account"]').attributes('href')).toBe('/student/account')
    expect(wrapper.get('[data-testid="topbar-notifications"]').attributes('href')).toBe('/student/notifications')
    expect(statusText(wrapper)).toContain('45 XP')
    expect(mocks.health).not.toHaveBeenCalled()
  })

  it('re-reads the bell’s unread count after a navigation and after a reported change, like the sidebar badges', async () => {
    mocks.unreadCount.mockResolvedValueOnce({ unreadCount: 0 }).mockResolvedValue({ unreadCount: 3 })
    const { router, wrapper } = await mountSignedIn()
    const bell = () => wrapper.get('[data-testid="topbar-notifications"]')
    expect(bell().find('.topbar-notification-badge').exists()).toBe(false)

    // A notification arrived meanwhile: the next page shows it on the bell.
    await router.push('/student/practice')
    await flushPromises()
    expect(mocks.unreadCount).toHaveBeenCalledTimes(2)
    expect(bell().get('.topbar-notification-badge').text()).toBe('3')

    notifyProgressChanged()
    await flushPromises()
    expect(mocks.unreadCount).toHaveBeenCalledTimes(3)
  })

  it('re-reads once when a page reports progress without navigating', async () => {
    mocks.rewards.mockResolvedValueOnce(summary(20, 0)).mockResolvedValueOnce(summary(65, 1))
    const { wrapper } = await mountSignedIn()
    expect(mocks.rewards).toHaveBeenCalledTimes(1)

    notifyProgressChanged()
    await flushPromises()

    expect(mocks.rewards).toHaveBeenCalledTimes(2)
    expect(statusText(wrapper)).toContain('65 XP · 1-day streak')
  })

  it('shares one unread count between the bell and the feed, including after marking a row read', async () => {
    mocks.unreadCount.mockResolvedValueOnce({ unreadCount: 2 }).mockResolvedValue({ unreadCount: 1 })
    mocks.notificationPage.mockResolvedValue(
      pageOf<NotificationResponse>(
        [1, 2].map((id) => ({
          id,
          title: `Notice ${id}`,
          message: 'Your practice is ready.',
          type: 'DAILY_GOAL',
          status: 'UNREAD',
          targetPath: null,
          createdAt: '2026-05-18T15:30:00',
        })),
      ),
    )
    mocks.markAsRead.mockResolvedValue({})
    const { wrapper } = await mountSignedIn('STUDENT', 'notifications')
    const bell = () => wrapper.get('.topbar-notification-badge').text()
    const unread = () => wrapper.get('[data-testid="notifications-filter-unread"]').text().replace(/\s+/g, ' ')
    expect(mocks.unreadCount).toHaveBeenCalledTimes(1)
    expect(bell()).toBe('2')
    expect(unread()).toBe('Unread · 2')

    await wrapper.get('[data-testid="notification-mark-read"]').trigger('click')
    await flushPromises()

    expect(mocks.markAsRead).toHaveBeenCalledWith(1)
    expect(mocks.unreadCount).toHaveBeenCalledTimes(2)
    expect(bell()).toBe('1')
    expect(unread()).toBe('Unread · 1')
  })

  it.each([true, false])(
    'shares one AI report count and ignores an older response with success=%s',
    async (succeeds) => {
      const older = deferred<{ aiReports: number }>()
      mocks.navCounts.mockReturnValueOnce(older.promise).mockResolvedValueOnce({ aiReports: 2 })
      const { wrapper } = await mountSignedIn('ADMIN', 'ai')
      expect(mocks.navCounts).toHaveBeenCalledTimes(1)

      notifyProgressChanged()
      await flushPromises()
      expect(mocks.navCounts).toHaveBeenCalledTimes(2)
      expect(wrapper.get('.route-tab-count').text()).toBe('2')
      expect(wrapper.get('.sidebar-count').text()).toBe('2')

      if (succeeds) older.resolve({ aiReports: 8 })
      else older.reject(new Error('Old failure'))
      await flushPromises()
      expect(wrapper.get('.route-tab-count').text()).toBe('2')
      expect(wrapper.get('.sidebar-count').text()).toBe('2')
    },
  )

  it('hides the Reports tab count if the latest refresh fails', async () => {
    mocks.navCounts.mockResolvedValueOnce({ aiReports: 8 }).mockRejectedValueOnce(new Error('Unavailable'))
    const { wrapper } = await mountSignedIn('ADMIN', 'ai')
    expect(wrapper.get('.route-tab-count').text()).toBe('8')

    notifyProgressChanged()
    await flushPromises()

    expect(wrapper.find('.route-tab-count').exists()).toBe(false)
    expect(wrapper.get('.sidebar-count').text()).toBe('8')

    const retry = deferred<{ aiReports: number }>()
    mocks.navCounts.mockReturnValueOnce(retry.promise)
    notifyProgressChanged()
    await flushPromises()
    expect(wrapper.find('.route-tab-count').exists()).toBe(false)

    retry.resolve({ aiReports: 2 })
    await flushPromises()
    expect(wrapper.get('.route-tab-count').text()).toBe('2')
    expect(wrapper.get('.sidebar-count').text()).toBe('2')
  })

  it('keeps the last summary when a re-read fails', async () => {
    mocks.rewards.mockResolvedValueOnce(summary(130, 2)).mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountSignedIn()

    notifyProgressChanged()
    await flushPromises()

    expect(statusText(wrapper)).toContain('130 XP · 2-day streak')
  })

  it('shows a focus-mode task without the sidebar and header', async () => {
    const { router, wrapper } = await mountSignedIn()
    expect(wrapper.find('.sidebar').exists()).toBe(true)

    await router.push('/student/learn/lessons/3')
    await flushPromises()

    expect(wrapper.find('[data-testid="question-stage"]').exists()).toBe(true)
    expect(wrapper.find('.sidebar').exists()).toBe(false)
    expect(wrapper.find('.dashboard-topbar').exists()).toBe(false)
  })

  it('loads sidebar data when leaving a directly opened focus-mode task', async () => {
    mocks.rewards.mockResolvedValue(summary(45, 1))
    const { router, wrapper } = await mountSignedIn('STUDENT', 'learn/lessons/3')
    expect(mocks.rewards).not.toHaveBeenCalled()
    expect(mocks.navCounts).not.toHaveBeenCalled()

    await router.push('/student/home')
    await flushPromises()

    expect(mocks.rewards).toHaveBeenCalledTimes(1)
    expect(mocks.navCounts).toHaveBeenCalledTimes(1)
    expect(statusText(wrapper)).toContain('45 XP · 1-day streak')
  })

  it('opens another lesson with fresh questions and ignores the previous lesson verdict', async () => {
    const verdict = deferred<ReturnType<typeof answerCheck>>()
    mocks.checkLesson.mockReturnValueOnce(verdict.promise)
    const { router, wrapper } = await mountSignedIn()
    await router.push('/student/learn/lessons/13')
    await flushPromises()
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    expect(mocks.checkLesson).toHaveBeenCalledWith(13, { questionId: 101, answer: 'name' })

    mocks.lesson.mockResolvedValue(lessonPlay({ title: 'Another lesson' }))
    await router.push('/student/learn/lessons/14')
    await flushPromises()
    expect(mocks.lesson).toHaveBeenLastCalledWith(14)
    expect(wrapper.text()).toContain('Another lesson')
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('false')
    verdict.resolve(answerCheck())
    await flushPromises()
    expect(wrapper.find('[data-testid="continue"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="check"]').attributes('disabled')).toBeDefined()
  })

  it('keeps the current answer when only a query or hash changes', async () => {
    const { router, wrapper } = await mountSignedIn()
    await router.push('/student/learn/lessons/13')
    await flushPromises()
    await wrapper.get('[data-testid="option-0"]').trigger('click')

    await router.push('/student/learn/lessons/13?from=path#question')
    await flushPromises()
    expect(mocks.lesson).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')
  })

  it('opens the navigation drawer on compact screens and docks it when the viewport widens', async () => {
    let matches = true
    const listeners = new Set<() => void>()
    const originalMatchMedia = window.matchMedia
    window.matchMedia = vi.fn().mockReturnValue({
      get matches() {
        return matches
      },
      addEventListener: (_event: string, listener: () => void) => listeners.add(listener),
      removeEventListener: (_event: string, listener: () => void) => listeners.delete(listener),
    }) as unknown as typeof window.matchMedia
    try {
      const { wrapper } = await mountSignedIn()
      expect(wrapper.find('.sidebar-docked').exists()).toBe(true)

      await wrapper.get('.navigation-toggle').trigger('click')
      await flushPromises()
      expect(wrapper.find('.sidebar-docked').exists()).toBe(false)

      matches = false
      listeners.forEach((listener) => listener())
      await flushPromises()
      expect(wrapper.find('.sidebar-docked').exists()).toBe(true)
      wrapper.unmount()
    } finally {
      window.matchMedia = originalMatchMedia
    }
  })
})
