import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { CurrentUserResponse, RewardSummaryResponse, UserRole } from '@/types/api'

import DashboardSidebar from './DashboardSidebar.vue'

const Empty = { template: '<div />' }

interface MountOptions {
  rewardSummary?: RewardSummaryResponse | null
  navCounts?: Record<string, number>
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
    props: { rewardSummary: options.rewardSummary ?? null, navCounts: options.navCounts ?? {} },
    global: { plugins: [pinia, router] },
  })
}

function signIn(role: UserRole, extra: Partial<CurrentUserResponse> = {}): () => void {
  return () => {
    const auth = useAuthStore()
    auth.hasSession = true
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
  it('shows the student sections and highlights the section of a nested page', async () => {
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
    expect(wrapper.get('.sidebar-support').attributes('href')).toBe('/student/support')
  })

  it('shows badge counts from the nav-count response', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { navCounts: { mistakes: 6, supportReplies: 1 } })
    expect(wrapper.get('a[href="/student/practice"] .sidebar-count').text()).toBe('6')
    expect(wrapper.get('.sidebar-support .sidebar-count').text()).toBe('1')
  })

  it('locks the teaching tools of a teacher who is not approved and links them to the Locked page', async () => {
    const wrapper = await mountAt(
      '/teacher/dashboard',
      signIn('TEACHER', { teacherProfile: { teacherStatus: 'PENDING', verificationState: 'IN_REVIEW' } }),
    )
    const locked = wrapper.findAll('a.sidebar-link.is-locked')
    expect(locked.map((link) => link.attributes('href'))).toEqual([
      '/teacher/locked',
      '/teacher/locked',
      '/teacher/locked',
    ])
    expect(wrapper.get('[data-testid="sidebar-role"]').text()).toBe('Teacher applicant')
    expect(wrapper.get('[data-testid="sidebar-status"]').text()).toContain('In review')
  })

  it('shows an approved teacher every tool and the verified card', async () => {
    const wrapper = await mountAt(
      '/teacher/classes',
      signIn('TEACHER', { teacherProfile: { teacherStatus: 'APPROVED', verificationState: 'APPROVED' } }),
    )
    expect(wrapper.findAll('a.sidebar-link.is-locked')).toHaveLength(0)
    expect(activeLinks(wrapper)).toEqual(['/teacher/classes'])
    expect(wrapper.get('[data-testid="sidebar-status"]').text()).toContain('Verified teacher')
  })
})

describe('DashboardSidebar student status card', () => {
  it('shows the level title, XP and streak, and fills the bar towards the next level', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { rewardSummary: summary(250, 7) })
    const card = wrapper.get('[data-testid="sidebar-status"]')
    expect(card.text()).toContain('Level 3 · Regular Learner')
    expect(card.text()).toContain('250 XP · 7-day streak')
    expect(card.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('50')
  })

  it('says when there is no streak yet', async () => {
    const wrapper = await mountAt('/student/home', signIn('STUDENT'), { rewardSummary: summary(0, 0) })
    expect(wrapper.get('[data-testid="sidebar-status"]').text()).toContain('0 XP · no streak yet')
  })
})
