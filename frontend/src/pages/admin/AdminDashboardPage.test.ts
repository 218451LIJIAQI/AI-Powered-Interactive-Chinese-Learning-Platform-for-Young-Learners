import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AdminDashboardResponse } from '@/types/ops'

import { mountAdminPage } from './adminPageTestSetup'

const overview = vi.hoisted(() => ({ dashboard: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminDashboardPage from './AdminDashboardPage.vue'

function dashboard(): AdminDashboardResponse {
  return {
    teacherApplications: {
      count: 3,
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

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    overview.dashboard.mockResolvedValue(dashboard())
  })

  it('shows the four queues in a fixed order with their counts, first items and links', async () => {
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Dashboard')
    expect(wrapper.text()).toContain('Needs attention')
    const order = wrapper.findAll('[data-testid^="admin-home-queue-"]').map((queue) => queue.attributes('data-testid'))
    expect(order).toEqual([
      'admin-home-queue-applications',
      'admin-home-queue-review',
      'admin-home-queue-reports',
      'admin-home-queue-support',
    ])

    const applications = wrapper.get('[data-testid="admin-home-queue-applications"]')
    expect(applications.get('h3').text()).toContain('Teacher applications')
    expect(applications.get('.count-badge').text()).toBe('3')
    expect(applications.findAll('.row-list li')).toHaveLength(2)
    expect(applications.text()).toContain('Farah Aziz')
    expect(applications.text()).toContain('2 documents')
    expect(applications.find('a[href="/admin/users/applications?teacher=7"]').exists()).toBe(true)
    expect(applications.find('a[href="/admin/users/applications"]').text()).toContain('View all')

    const review = wrapper.get('[data-testid="admin-home-queue-review"]')
    expect(review.text()).toContain('Chinese Culture and Festivals')
    expect(review.text()).toContain('Class · Li Wei')
    expect(review.find('a[href="/admin/content?item=class-3"]').exists()).toBe(true)

    const reports = wrapper.get('[data-testid="admin-home-queue-reports"]')
    expect(reports.text()).toContain('Nothing waiting.')
    expect(reports.find('.count-badge').exists()).toBe(false)

    const support = wrapper.get('[data-testid="admin-home-queue-support"]')
    expect(support.text()).toContain('Visitor')
    expect(support.find('a[href="/admin/support?request=41"]').exists()).toBe(true)
  })

  it('keeps the platform numbers, recent activity and system status off the dashboard', async () => {
    const { wrapper } = await mountAdminPage(AdminDashboardPage, '/admin/dashboard')
    await flushPromises()

    expect(wrapper.find('[data-testid="admin-home-numbers"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="admin-home-activity"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="admin-home-health"]').exists()).toBe(false)
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
