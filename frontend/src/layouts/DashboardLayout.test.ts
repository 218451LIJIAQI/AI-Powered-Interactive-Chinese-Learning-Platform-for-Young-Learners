import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const mocks = vi.hoisted(() => ({
  rewards: vi.fn(),
  unreadCount: vi.fn(),
  navCounts: vi.fn(),
  health: vi.fn(),
}))

vi.mock('@/api/services', () => ({
  studentApi: { rewards: mocks.rewards },
  notificationsApi: { unreadCount: mocks.unreadCount },
  systemApi: { health: mocks.health },
}))
vi.mock('@/api/shell', () => ({ shellApi: { navCounts: mocks.navCounts } }))

import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUserResponse, RewardSummaryResponse } from '@/types/api'

import DashboardLayout from './DashboardLayout.vue'

// The progress counter is shared by the whole app, so a layout left mounted by an earlier test would reload too.
enableAutoUnmount(afterEach)

function summary(totalPoints: number, currentStreak: number): RewardSummaryResponse {
  return { totalPoints, currentStreak }
}

async function mountSignedInStudent() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/student',
        component: DashboardLayout,
        children: [
          { path: 'home', component: { template: '<div class="home-page" />' } },
          { path: 'learn/lessons/:id', component: { template: '<div class="player" />' }, meta: { focus: true } },
          // The catch-all resolves the sidebar's other links.
          { path: ':pathMatch(.*)*', component: { template: '<div />' } },
        ],
      },
    ],
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.hasSession = true
  auth.currentUser = {
    id: 7,
    fullName: 'Amira Hassan',
    email: 'amira@example.com',
    roles: ['STUDENT'],
  } as CurrentUserResponse

  await router.push('/student/home')
  await router.isReady()
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { router, wrapper }
}

const statusText = (wrapper: Awaited<ReturnType<typeof mountSignedInStudent>>['wrapper']) =>
  wrapper.get('[data-testid="sidebar-status"]').text()

describe('DashboardLayout', () => {
  beforeEach(() => {
    mocks.unreadCount.mockResolvedValue({ unreadCount: 0 })
    mocks.navCounts.mockResolvedValue({ mistakes: 2 })
  })

  it('re-reads XP and streak after a navigation, so the sidebar stays current', async () => {
    mocks.rewards.mockResolvedValueOnce(summary(0, 0)).mockResolvedValueOnce(summary(45, 1))
    const { router, wrapper } = await mountSignedInStudent()
    expect(statusText(wrapper)).toContain('0 XP · no streak yet')

    await router.push('/student/practice')
    await flushPromises()

    expect(mocks.rewards).toHaveBeenCalledTimes(2)
    expect(mocks.navCounts).toHaveBeenCalledTimes(2)
    expect(statusText(wrapper)).toContain('45 XP · 1-day streak')
  })

  it('re-reads once when a page reports progress without navigating', async () => {
    mocks.rewards.mockResolvedValueOnce(summary(20, 0)).mockResolvedValueOnce(summary(65, 1))
    const { wrapper } = await mountSignedInStudent()
    expect(mocks.rewards).toHaveBeenCalledTimes(1)

    notifyProgressChanged()
    await flushPromises()

    expect(mocks.rewards).toHaveBeenCalledTimes(2)
    expect(statusText(wrapper)).toContain('65 XP · 1-day streak')
  })

  it('keeps the last summary when a re-read fails', async () => {
    mocks.rewards.mockResolvedValueOnce(summary(130, 2)).mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountSignedInStudent()

    notifyProgressChanged()
    await flushPromises()

    expect(statusText(wrapper)).toContain('130 XP · 2-day streak')
  })

  it('shows a focus-mode task without the sidebar and header', async () => {
    mocks.rewards.mockResolvedValue(summary(0, 0))
    const { router, wrapper } = await mountSignedInStudent()
    expect(wrapper.find('.sidebar').exists()).toBe(true)

    await router.push('/student/learn/lessons/3')
    await flushPromises()

    expect(wrapper.find('.player').exists()).toBe(true)
    expect(wrapper.find('.sidebar').exists()).toBe(false)
    expect(wrapper.find('.dashboard-topbar').exists()).toBe(false)
  })
})
