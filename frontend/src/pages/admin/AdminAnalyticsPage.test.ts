import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { AdminAnalyticsResponse } from '@/types/ops'

const overview = vi.hoisted(() => ({ analytics: vi.fn() }))
const download = vi.hoisted(() => ({ saveBlobDownload: vi.fn() }))

vi.mock('@/api/ops', () => ({ adminOverviewApi: overview }))
vi.mock('@/utils/download', () => download)

import AdminAnalyticsPage from './AdminAnalyticsPage.vue'

function analytics(days: AdminAnalyticsResponse['days']): AdminAnalyticsResponse {
  const registrations = Array.from({ length: days }, (_, index) => ({
    date: `2026-05-${String(index + 1).padStart(2, '0')}`,
    count: index % 3,
  }))
  return {
    days,
    from: registrations[0].date,
    to: registrations[days - 1].date,
    numbers: {
      users: { total: 9, students: 8, admins: 1 },
      publishedContent: 68,
      aiRequests: 1284,
    },
    newRegistrations: registrations.reduce((sum, day) => sum + day.count, 0),
    registrations,
    learning: { activeStudents: 6, totalStudents: 8, lessonsCompleted: 42, quizAverage: 78, needsSupport: 1 },
    contentByStatus: { PUBLISHED: 68, DRAFT: 9, ARCHIVED: 3 },
    aiRequestsByStatus: { SUCCESS: 1236, UNAVAILABLE: 32, FAILED: 16 },
  }
}

/** The CSV text handed to the download, without the byte-order mark. */
async function savedCsv(): Promise<{ text: string; fileName: string }> {
  const [payload, fileName] = download.saveBlobDownload.mock.calls.at(-1) as [{ blob: Blob }, string]
  const text = await payload.blob.text()
  return { text: text.replace(/^\uFEFF/, ''), fileName }
}

describe('AdminAnalyticsPage', () => {
  beforeEach(() => {
    overview.analytics.mockReset()
    download.saveBlobDownload.mockReset()
    overview.analytics.mockImplementation(async (days: AdminAnalyticsResponse['days']) => analytics(days))
  })

  it('shows the last 7 days and switches to 30', async () => {
    const { wrapper } = await mountAtRoute(AdminAnalyticsPage, '/admin/analytics')

    expect(overview.analytics).toHaveBeenCalledWith(7)
    const users = wrapper.get('[data-testid="admin-insights-users"]').text()
    expect(users).toContain('9 users')
    expect(users).toContain('8 learners · 1 admin')
    expect(users).not.toMatch(/teacher/i)
    // Like the user totals, the new accounts leave out the accounts deleted since, and the line says so.
    expect(wrapper.get('[data-testid="admin-insights-registrations-detail"]').text()).toMatch(
      /^6 new accounts · .+ – .+ · deleted accounts not counted$/,
    )
    expect(wrapper.text()).toContain('6 of 8')
    expect(wrapper.text()).toContain('78%')

    const content = wrapper.findAll('[data-testid="admin-insights-content-row"]').map((row) => row.text())
    // Classes and lessons are Published, Draft or Archived: there is no review step any more (A5.06).
    expect(content).toHaveLength(3)
    expect(content[0]).toContain('Published')
    expect(content[0]).toContain('68 · 85%')
    expect(content[1]).toContain('Draft')
    expect(content[1]).toContain('9 · 11.3%')
    expect(content[2]).toContain('Archived')
    expect(wrapper.find('a[href="/admin/content"]').exists()).toBe(true)
    const ai = wrapper.findAll('[data-testid="admin-insights-ai-row"]').map((row) => row.text())
    expect(ai[0]).toContain('1,236 · 96.3%')

    await wrapper.get('[data-testid="admin-insights-period-30"]').trigger('click')
    await flushPromises()
    expect(overview.analytics).toHaveBeenLastCalledWith(30)
    expect(wrapper.get('[data-testid="admin-insights-period-30"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.admin-insights-axis li')).toHaveLength(30)
  })

  it('says so when another period cannot be loaded, and keeps the control on the numbers shown', async () => {
    const { wrapper } = await mountAtRoute(AdminAnalyticsPage, '/admin/analytics')
    overview.analytics.mockRejectedValueOnce({ response: { status: 500, data: { message: 'Server error' } } })

    await wrapper.get('[data-testid="admin-insights-period-30"]').trigger('click')
    await flushPromises()

    expect(overview.analytics).toHaveBeenLastCalledWith(30)
    expect(wrapper.get('[role="alert"]').text()).toContain('Analytics could not be loaded')
    // The 7 days still on screen are not passed off as the last 30.
    expect(wrapper.get('[data-testid="admin-insights-period-7"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="admin-insights-period-30"]').attributes('aria-pressed')).toBe('false')
    expect(wrapper.findAll('.admin-insights-axis li:not(.is-quiet)')).toHaveLength(7)

    await wrapper.get('[data-testid="admin-insights-period-30"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-insights-period-30"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.admin-insights-axis li')).toHaveLength(30)
  })

  it('keeps the visible period selected until the replacement figures arrive', async () => {
    const { wrapper } = await mountAtRoute(AdminAnalyticsPage, '/admin/analytics')
    const next = deferred<AdminAnalyticsResponse>()
    overview.analytics.mockReturnValueOnce(next.promise)
    const week = wrapper.get('[data-testid="admin-insights-period-7"]')
    const month = wrapper.get('[data-testid="admin-insights-period-30"]')

    await month.trigger('click')
    expect(week.attributes('aria-pressed')).toBe('true')
    expect(month.attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.admin-insights-axis li')).toHaveLength(7)

    next.resolve(analytics(30))
    await flushPromises()
    expect(month.attributes('disabled')).toBeUndefined()
    expect(month.attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.admin-insights-axis li')).toHaveLength(30)
  })

  it('exports what the page shows as CSV and confirms with a toast', async () => {
    const { wrapper } = await mountAtRoute(AdminAnalyticsPage, '/admin/analytics')

    await wrapper.get('[data-testid="admin-insights-export"]').trigger('click')
    const { text, fileName } = await savedCsv()

    expect(fileName).toBe('analytics-7d-2026-05-07.csv')
    const lines = text.split('\r\n')
    expect(lines.slice(0, 5)).toEqual([
      'MandarinSpark analytics',
      'Period,2026-05-01,2026-05-07',
      '',
      'Metric,Value',
      'Users,9',
    ])
    expect(lines).toContain('Quiz average,78')
    expect(lines).toContain('2026-05-03,2')
    expect(lines).toContain('Learners,8')
    expect(lines.some((line) => line.startsWith('Teachers'))).toBe(false)
    expect(lines).toContain('Published,68')
    expect(lines).toContain('Unavailable,32')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('CSV downloaded.')
  })

  it('cannot export before the numbers are loaded and offers a retry', async () => {
    overview.analytics.mockRejectedValueOnce({ response: { status: 500, data: { message: 'Server error' } } })
    const { wrapper } = await mountAtRoute(AdminAnalyticsPage, '/admin/analytics')

    expect(wrapper.text()).toContain('Analytics could not be loaded')
    expect(wrapper.get('[data-testid="admin-insights-export"]').attributes('disabled')).toBeDefined()

    await wrapper.get('.state-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-insights-users"]').exists()).toBe(true)
  })
})
