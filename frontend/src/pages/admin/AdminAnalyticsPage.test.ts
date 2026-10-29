import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AdminAnalyticsResponse } from '@/types/ops'

import { mountAdminPage } from './adminPageTestSetup'

const overview = vi.hoisted(() => ({ analytics: vi.fn() }))
const download = vi.hoisted(() => ({ saveBlobDownload: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('@/utils/download', () => download)

import AdminAnalyticsPage from './AdminAnalyticsPage.vue'

function analytics(days: number): AdminAnalyticsResponse {
  const registrations = Array.from({ length: days }, (_, index) => ({
    date: `2026-05-${String(index + 1).padStart(2, '0')}`,
    count: index % 3,
  }))
  return {
    days,
    from: registrations[0].date,
    to: registrations[days - 1].date,
    numbers: {
      users: { total: 15, students: 8, teachers: 6, admins: 1 },
      publishedContent: 68,
      totalContent: 81,
      aiRequests: 1284,
      successfulAiRequests: 1236,
      auditEvents: 412,
    },
    newRegistrations: registrations.reduce((sum, day) => sum + day.count, 0),
    registrations,
    learning: { activeStudents: 6, totalStudents: 8, lessonsCompleted: 42, quizAverage: 78.5, needsSupport: 1 },
    contentByStatus: { PUBLISHED: 68, SUBMITTED: 4, REJECTED: 2, DRAFT: 6, ARCHIVED: 1 },
    aiRequestsByStatus: { SUCCESS: 1236, UNAVAILABLE: 32, FAILED: 16 },
  }
}

/** The CSV text handed to the download, without the byte-order mark. */
async function savedCsv(): Promise<{ text: string; fileName: string }> {
  const [payload, fileName] = download.saveBlobDownload.mock.calls.at(-1) as [{ blob: Blob }, string]
  const text = await new Promise<string>((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.readAsText(payload.blob)
  })
  return { text: text.replace(/^\uFEFF/, ''), fileName }
}

describe('AdminAnalyticsPage', () => {
  beforeEach(() => {
    overview.analytics.mockImplementation(async (days: number) => analytics(days))
  })

  it('shows the last 7 days and switches to 30', async () => {
    const { wrapper } = await mountAdminPage(AdminAnalyticsPage, '/admin/analytics')
    await flushPromises()

    expect(overview.analytics).toHaveBeenCalledWith(7)
    const kpis = wrapper.findAll('[data-testid="admin-insights-kpi"]').map((kpi) => kpi.text())
    expect(kpis[0]).toContain('8 students · 6 teachers · 1 admin')
    expect(kpis[1]).toContain('In the last 7 days')
    expect(kpis[3]).toContain('1,236 successful')
    expect(wrapper.get('[data-testid="admin-insights-registrations-detail"]').text()).toContain('6 new accounts')
    expect(wrapper.text()).toContain('6 of 8')
    expect(wrapper.text()).toContain('78.5%')

    const content = wrapper.findAll('[data-testid="admin-insights-content-row"]').map((row) => row.text())
    expect(content[0]).toContain('Published')
    expect(content[0]).toContain('68 · 84%')
    const ai = wrapper.findAll('[data-testid="admin-insights-ai-row"]').map((row) => row.text())
    expect(ai[0]).toContain('1,236 · 96.3%')

    await wrapper.get('[data-testid="admin-insights-period-30"]').trigger('click')
    await flushPromises()
    expect(overview.analytics).toHaveBeenLastCalledWith(30)
    expect(wrapper.get('[data-testid="admin-insights-period-30"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('[data-testid="admin-insights-kpi"]')[1].text()).toContain('In the last 30 days')
    expect(wrapper.findAll('.admin-insights-axis li')).toHaveLength(30)
  })

  it('exports what the page shows as CSV and confirms with a toast', async () => {
    const { wrapper } = await mountAdminPage(AdminAnalyticsPage, '/admin/analytics')
    await flushPromises()

    await wrapper.get('[data-testid="admin-insights-export"]').trigger('click')
    const { text, fileName } = await savedCsv()

    expect(fileName).toBe('analytics-7d-2026-05-07.csv')
    const lines = text.split('\r\n')
    expect(lines.slice(0, 5)).toEqual([
      'MandarinSpark analytics',
      'Period,2026-05-01,2026-05-07',
      '',
      'Metric,Value',
      'Users,15',
    ])
    expect(lines).toContain('Quiz average,78.5')
    expect(lines).toContain('2026-05-03,2')
    expect(lines).toContain('Published,68')
    expect(lines).toContain('Unavailable,32')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('CSV downloaded.')
  })

  it('cannot export before the numbers are loaded and offers a retry', async () => {
    overview.analytics.mockRejectedValueOnce({ response: { status: 500, data: { message: 'Server error' } } })
    const { wrapper } = await mountAdminPage(AdminAnalyticsPage, '/admin/analytics')
    await flushPromises()

    expect(wrapper.text()).toContain('Analytics could not be loaded')
    expect(wrapper.get('[data-testid="admin-insights-export"]').attributes('disabled')).toBeDefined()

    await wrapper.get('.state-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="admin-insights-kpi"]')).toHaveLength(4)
  })
})
