import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import type { HealthStatusResponse } from '@/types/api'
import type { AdminDashboardResponse, AuditEntry } from '@/types/ops'

import { mountAdminPage } from './adminPageTestSetup'

const overview = vi.hoisted(() => ({ dashboard: vi.fn(), auditLog: vi.fn() }))
const system = vi.hoisted(() => ({ health: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview }))
vi.mock('@/api/services', () => ({ systemApi: system }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminDashboardPage from './AdminDashboardPage.vue'

function dashboard(): AdminDashboardResponse {
  return {
    teacherApplications: {
      count: 2,
      items: [
        {
          teacherId: 7,
          userId: 70,
          fullName: 'Farah Aziz',
          email: 'farah@example.com',
          submittedAt: '2026-05-13T16:00:00',
          documentCount: 2,
        },
        {
          teacherId: 8,
          userId: 80,
          fullName: 'Mei Lin',
          email: 'mei@example.com',
          submittedAt: '2026-05-14T09:00:00',
          documentCount: 3,
        },
      ],
    },
    reviewQueue: {
      count: 1,
      items: [
        {
          type: 'PACKAGE',
          id: 3,
          title: 'Chinese Culture and Festivals',
          ownerName: 'Li Wei',
          parentTitle: null,
          submittedAt: '2026-05-18T10:00:00',
        },
      ],
    },
    aiReports: { count: 0, items: [] },
    support: {
      count: 1,
      items: [
        {
          id: 41,
          number: 1041,
          topic: 'ACCOUNT',
          subject: 'Can’t sign in after a reset',
          status: 'OPEN',
          requesterName: null,
          requesterEmail: 'aisyah@example.com',
          requesterRole: 'VISITOR',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          lastSupportReplyAt: null,
          closesAt: null,
          solvedAt: null,
          closedBy: null,
        },
      ],
    },
    numbers: {
      users: { total: 15, students: 8, teachers: 6, admins: 1 },
      publishedContent: 68,
      totalContent: 81,
      aiRequests: 1284,
      successfulAiRequests: 1236,
      auditEvents: 412,
    },
    registrations: [
      { date: '2026-05-12', count: 0 },
      { date: '2026-05-13', count: 1 },
      { date: '2026-05-14', count: 1 },
      { date: '2026-05-15', count: 2 },
      { date: '2026-05-16', count: 1 },
      { date: '2026-05-17', count: 1 },
      { date: '2026-05-18', count: 0 },
    ],
  }
}

function health(overrides: Partial<HealthStatusResponse> = {}): HealthStatusResponse {
  return {
    status: 'UP',
    timestamp: new Date().toISOString(),
    database: 'UP',
    ai: 'AVAILABLE',
    ...overrides,
  }
}

const approval: AuditEntry = {
  id: 90,
  actionType: 'CLASS_APPROVED',
  category: 'CONTENT',
  actorName: 'Nur Admin',
  actorEmail: 'nur@example.com',
  targetType: 'CLASS',
  targetLabel: 'Chinese Culture and Festivals',
  targetUserId: null,
  reason: null,
  description: null,
  createdAt: new Date().toISOString(),
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    overview.dashboard.mockResolvedValue(dashboard())
    overview.auditLog.mockResolvedValue(pageOf([approval], 0, 1, 6))
    system.health.mockResolvedValue(health())
  })

  it('shows every queue with its first items, links and an empty line', async () => {
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()

    const applications = wrapper.get('[data-testid="admin-home-queue-applications"]')
    expect(applications.text()).toContain('Teacher applications (2)')
    expect(applications.text()).toContain('Farah Aziz')
    expect(applications.text()).toContain('2 documents')
    expect(applications.find('a[href="/admin/users/applications?teacher=7"]').exists()).toBe(true)

    const review = wrapper.get('[data-testid="admin-home-queue-review"]')
    expect(review.text()).toContain('Chinese Culture and Festivals')
    expect(review.text()).toContain('Class · Li Wei')

    expect(wrapper.get('[data-testid="admin-home-queue-reports"]').text()).toContain('Nothing waiting.')
    // Queues with work come first; the clear one moves to the end.
    const order = wrapper.findAll('[data-testid^="admin-home-queue-"]').map((queue) => queue.attributes('data-testid'))
    expect(order).toEqual([
      'admin-home-queue-applications',
      'admin-home-queue-review',
      'admin-home-queue-support',
      'admin-home-queue-reports',
    ])
    expect(wrapper.get('h1').text()).toBe('Dashboard')
    expect(wrapper.text()).toContain('4 items waiting in 3 queues, oldest first.')
    const support = wrapper.get('[data-testid="admin-home-queue-support"]')
    expect(support.text()).toContain('Visitor')
    expect(support.find('a[href="/admin/support?request=41"]').exists()).toBe(true)
  })

  it('shows the platform numbers and last week’s registrations', async () => {
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()

    const numbers = wrapper.get('[data-testid="admin-home-numbers"]').text()
    expect(numbers).toContain('15')
    expect(numbers).toContain('8 students · 6 teachers · 1 admin')
    expect(numbers).toContain('Of 81 classes and lessons')
    expect(numbers).toContain('1,236 successful')
    expect(wrapper.text()).toContain('Last 7 days · 6 new accounts')
    const bars = wrapper.findAll('[data-testid="admin-home-bars"] li')
    expect(bars).toHaveLength(7)
    expect(bars[3].text()).toContain('May 15 · 2')
  })

  it('lists the latest admin actions, each linking to its audit entry', async () => {
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()

    expect(overview.auditLog).toHaveBeenCalledWith({ page: 0, size: 6 })
    const feed = wrapper.get('[data-testid="admin-home-activity"]')
    expect(feed.text()).toContain('Chinese Culture and Festivals')
    expect(feed.text()).toContain('Nur Admin')
    expect(feed.find('a[href="/admin/audit-log?entry=90"]').exists()).toBe(true)
  })

  it('reports the system status from the health check', async () => {
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-home-health"]').text()).toContain('All systems running')

    system.health.mockResolvedValue(health({ ai: 'UNAVAILABLE' }))
    await wrapper.get('.admin-home-status button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-home-health"]').text()).toContain('Running without AI')

    system.health.mockResolvedValue(health({ status: 'DOWN', database: 'DOWN' }))
    await wrapper.get('.admin-home-status button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-home-health"]').text()).toContain('Database unavailable')

    system.health.mockRejectedValue(new Error('offline'))
    await wrapper.get('.admin-home-status button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-home-health"]').text()).toContain('Status check failed')
  })

  it('offers a retry when the dashboard cannot be loaded', async () => {
    overview.dashboard.mockRejectedValueOnce({ response: { status: 500, data: { message: 'Server error' } } })
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()

    expect(wrapper.text()).toContain('The dashboard could not be loaded')
    await wrapper.get('.state-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Needs attention')
  })
})
