import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/plugins/i18n'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUserResponse, HealthStatusResponse, NavCounts, RewardSummaryResponse, UserRole } from '@/types/api'

import DashboardSidebar from './DashboardSidebar.vue'

const Empty = { template: '<div />' }

interface MountOptions {
  rewardSummary?: RewardSummaryResponse | null
  navCounts?: NavCounts
  health?: HealthStatusResponse | null
}

async function mountAt(path: string, setup?: () => void, options: MountOptions = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: Empty }],
  })
  await router.push(path)
  await router.isReady()
  const pinia = createPinia()
  setActivePinia(pinia)
  setup?.()
  return mount(DashboardSidebar, {
    props: { rewardSummary: options.rewardSummary ?? null, navCounts: options.navCounts ?? {}, health: options.health },
    global: { plugins: [pinia, router] },
  })
}

function signIn(role: UserRole, extra: Partial<CurrentUserResponse> = {}): () => void {
  return () => {
    const auth = useAuthStore()
    auth.currentUser = { id: 7, fullName: 'Amira Hassan', email: 'a@example.com', roles: [role], ...extra }
  }
}

function summary(totalPoints: number, currentStreak: number): RewardSummaryResponse {
  return { totalPoints, currentStreak }
}

function activeLinks(wrapper: Awaited<ReturnType<typeof mountAt>>): string[] {
  return wrapper.findAll('a.sidebar-link.is-active').map((link) => link.attributes('href') ?? '')
}

describe('DashboardSidebar navigation', () => {
  it('shows the learner sections, names the role and highlights the section of a nested page', async () => {
    const wrapper = await mountAt('/student/classes/7/lessons/3', signIn('STUDENT'))
    expect(wrapper.findAll('.sidebar-nav .sidebar-link-label').map((label) => label.text())).toEqual([
      'Home',
      'Learn',
      'Practice',
      'AI Helper',
      'Classes',
      'Progress',
    ])
    expect(activeLinks(wrapper)).toEqual(['/student/classes'])
    expect(wrapper.get('[data-testid="sidebar-role"]').text()).toBe('Learner')
    // Learners have no Help & support: they report a problem where it happens.
    expect(wrapper.findAll('a.sidebar-link')).toHaveLength(6)
    expect(wrapper.text()).not.toContain('Help & support')
    expect(wrapper.find('a[href*="support"]').exists()).toBe(false)
  })

  it('names the role in the interface language', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'))
    i18n.global.locale.value = 'zh-CN'
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="sidebar-role"]').text()).toBe('学习者')
    i18n.global.locale.value = 'ms'
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="sidebar-role"]').text()).toBe('Pelajar')
    i18n.global.locale.value = 'en'
  })

  it('shows badge counts from the nav-count response', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { navCounts: { mistakes: 6 } })
    expect(wrapper.get('a[href="/student/practice"] .sidebar-count').text()).toBe('6')
    expect(wrapper.findAll('.sidebar-count')).toHaveLength(1)
  })

  it('caps every badge at 99+ and keeps the exact count for assistive technology', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { navCounts: { mistakes: 120 } })
    const link = wrapper.get('a[href="/student/practice"]')
    const badge = link.get('.sidebar-count')
    expect(badge.text()).toBe('99+')
    expect(badge.attributes('aria-hidden')).toBe('true')
    expect(link.attributes('aria-label')).toBe('Practice, 120 waiting')
    expect(link.attributes('title')).toBe('120 waiting')
  })

  it('shows the admin sections with their waiting counts', async () => {
    const wrapper = await mountAt('/admin/support', signIn('ADMIN'), { navCounts: { aiReports: 2, supportOpen: 3 } })
    expect(wrapper.get('[data-testid="sidebar-role"]').text()).toBe('Admin')
    expect(activeLinks(wrapper)).toEqual(['/admin/support'])
    expect(wrapper.get('a[href="/admin/ai"] .sidebar-count').text()).toBe('2')
    expect(wrapper.get('a[href="/admin/support"] .sidebar-count').text()).toBe('3')
  })
})

describe('DashboardSidebar learner status card', () => {
  it('shows the rank title, XP and streak, and fills the bar towards the next rank', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { rewardSummary: summary(290, 7) })
    const card = wrapper.get('[data-testid="sidebar-status"]')
    expect(card.get('.sidebar-status-title').text()).toBe('Rank 3 · Regular')
    expect(card.get('.sidebar-status-tile').text()).toBe('3')
    expect(card.text()).toContain('290 XP · 7-day streak')
    expect(card.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('90')
    expect(card.attributes('href')).toBe('/student/progress')
  })

  it('shows a new learner as Rank 1 · Starter with no streak yet', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { rewardSummary: summary(0, 0) })
    const card = wrapper.get('[data-testid="sidebar-status"]')
    expect(card.get('.sidebar-status-title').text()).toBe('Rank 1 · Starter')
    expect(card.text()).toContain('0 XP · no streak yet')
  })

  it('moves to Rank 4 · Committed at 300 XP', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { rewardSummary: summary(300, 8) })
    expect(wrapper.get('.sidebar-status-title').text()).toBe('Rank 4 · Committed')
    expect(wrapper.get('[data-testid="sidebar-status"]').text()).toContain('300 XP · 8-day streak')
  })
})

describe('DashboardSidebar admin status card', () => {
  it.each<[HealthStatusResponse | null, string, string]>([
    [{ status: 'UP', ai: 'AVAILABLE' }, 'Running', 'success'],
    [{ status: 'UP', ai: 'UNAVAILABLE' }, 'Running without AI', 'warning'],
    [{ status: 'DOWN', ai: 'AVAILABLE' }, 'Unavailable', 'warning'],
    [null, 'Unavailable', 'warning'],
  ])('reads the health check %j as %s', async (health, label, tone) => {
    const wrapper = await mountAt('/admin/dashboard', signIn('ADMIN'), { health })
    const card = wrapper.get('[data-testid="sidebar-status"]')
    expect(card.text()).toContain('System status')
    const pill = card.get('.status-pill')
    expect(pill.text()).toBe(label)
    expect(pill.classes()).toContain(tone)
  })

  it('shows no badge until the first health check has answered', async () => {
    const wrapper = await mountAt('/admin/dashboard', signIn('ADMIN'))
    expect(wrapper.get('[data-testid="sidebar-status"]').text()).toContain('System status')
    expect(wrapper.find('[data-testid="sidebar-status"] .status-pill').exists()).toBe(false)
  })
})
