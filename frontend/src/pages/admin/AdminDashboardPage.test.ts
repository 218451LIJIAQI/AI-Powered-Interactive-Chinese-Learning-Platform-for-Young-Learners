import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { AdminDashboardResponse } from '@/types/ops'

const overview = vi.hoisted(() => ({ dashboard: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview }))

import AdminDashboardPage from './AdminDashboardPage.vue'

function dashboard(): AdminDashboardResponse {
  return {
    classDrafts: {
      count: 2,
      items: [
        {
          id: 3,
          title: 'Chinese Culture and Festivals',
          lessonCount: 2,
          updatedAt: '2026-05-18T10:00:00',
        },
        {
          id: 4,
          title: 'Study Group Mandarin',
          lessonCount: 1,
          updatedAt: '2026-05-17T09:00:00',
        },
      ],
    },
    aiReports: {
      count: 2,
      items: [
        {
          id: 9,
          reason: 'INCORRECT_CONTENT',
          studentName: 'Amira Hassan',
          reportedAt: '2026-05-16T12:00:00',
        },
      ],
    },
    support: {
      count: 1,
      items: [
        {
          id: 41,
          number: 1041,
          topic: 'ACCOUNT',
          subject: 'Can’t sign in after resetting my password',
          status: 'OPEN',
          requesterName: null,
          requesterEmail: 'aisyah.k@example.com',
          requesterRole: 'VISITOR',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          closesAt: null,
          solvedAt: null,
          closedBy: null,
        },
      ],
    },
  }
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    overview.dashboard.mockReset()
    overview.dashboard.mockResolvedValue(dashboard())
  })

  it('shows Class drafts, AI reports and Support with their counts, first items and links (A1.01)', async () => {
    const { wrapper } = await mountAtRoute(AdminDashboardPage, '/admin/dashboard')

    expect(wrapper.get('h1').text()).toBe('Dashboard')
    expect(wrapper.text()).toContain('Needs attention')
    const order = wrapper.findAll('[data-testid^="admin-home-queue-"]').map((queue) => queue.attributes('data-testid'))
    expect(order).toEqual(['admin-home-queue-class-drafts', 'admin-home-queue-reports', 'admin-home-queue-support'])
    expect(wrapper.text()).not.toMatch(/teacher|review queue/i)

    const drafts = wrapper.get('[data-testid="admin-home-queue-class-drafts"]')
    expect(drafts.get('h3').text()).toContain('Class drafts')
    expect(drafts.get('.count-badge').text()).toBe('2')
    expect(drafts.findAll('.row-list li')).toHaveLength(2)
    expect(drafts.text()).toContain('Chinese Culture and Festivals')
    expect(drafts.text()).toContain('Class draft · 2 lessons · edited 18 May')
    expect(drafts.text()).toContain('Class draft · 1 lesson · edited 17 May')
    expect(drafts.find('a[href="/admin/content/classes/3"]').exists()).toBe(true)
    const viewAll = drafts.get('a[aria-label="View all: Class drafts"]')
    const target = new URL(viewAll.attributes('href')!, 'http://localhost')
    expect(target.pathname).toBe('/admin/content')
    expect(Object.fromEntries(target.searchParams)).toEqual({ type: 'CLASS', status: 'DRAFT' })

    const reports = wrapper.get('[data-testid="admin-home-queue-reports"]')
    expect(reports.text()).toContain('Incorrect content')
    expect(reports.text()).toContain('Amira Hassan · 16 May')
    expect(reports.find('a[href="/admin/ai?report=9"]').exists()).toBe(true)

    const support = wrapper.get('[data-testid="admin-home-queue-support"]')
    expect(support.text()).toContain('Visitor')
    expect(support.find('a[href="/admin/support?request=41"]').exists()).toBe(true)
  })

  it('says when there is nothing to show in a card', async () => {
    overview.dashboard.mockResolvedValue({
      ...dashboard(),
      classDrafts: { count: 0, items: [] },
      aiReports: { count: 0, items: [] },
    })
    const { wrapper } = await mountAtRoute(AdminDashboardPage, '/admin/dashboard')

    const drafts = wrapper.get('[data-testid="admin-home-queue-class-drafts"]')
    expect(drafts.text()).toContain('No class drafts.')
    expect(drafts.find('.count-badge').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-home-queue-reports"]').text()).toContain('Nothing waiting.')
  })

  it('shows placeholder cards while the dashboard loads (A1.06)', async () => {
    const pending = deferred<AdminDashboardResponse>()
    overview.dashboard.mockReturnValue(pending.promise)
    const { wrapper } = await mountAtRoute(AdminDashboardPage, '/admin/dashboard')

    const loading = wrapper.get('[data-testid="admin-home-loading"]')
    expect(loading.attributes('aria-busy')).toBe('true')
    expect(loading.findAll('.admin-home-card')).toHaveLength(3)
    pending.resolve(dashboard())
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-home-loading"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid^="admin-home-queue-"]')).toHaveLength(3)
  })

  it('offers a retry when the dashboard cannot be loaded', async () => {
    overview.dashboard.mockRejectedValueOnce({ response: { status: 500, data: { message: 'Server error' } } })
    const { wrapper } = await mountAtRoute(AdminDashboardPage, '/admin/dashboard')

    expect(wrapper.get('[role="alert"] h2').text()).toBe('The dashboard could not be loaded')
    await wrapper.get('.state-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Needs attention')
  })
})
