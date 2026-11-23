import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { buttonNamed, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { AdminAiUsageLogResponse, AdminAiUsageSummary } from '@/types/ai'

const api = vi.hoisted(() => ({ usageSummary: vi.fn(), usageLogs: vi.fn(), exportUsageLogs: vi.fn() }))
const download = vi.hoisted(() => ({ saveBlobDownload: vi.fn() }))

vi.mock('@/api/ai', () => ({ adminAiApi: api }))
vi.mock('@/utils/download', () => download)

import AdminAiUsagePage from './AdminAiUsagePage.vue'

const SUMMARY: AdminAiUsageSummary = {
  totalRequests: 1284,
  successful: 1236,
  unavailable: 31,
  failed: 17,
  byFeature: [
    { featureKey: 'CHAT_TUTOR', requests: 580 },
    { featureKey: 'QUIZ_DRAFT', requests: 76 },
    { featureKey: 'HINT', requests: 42 },
  ],
}

function logRow(overrides: Partial<AdminAiUsageLogResponse> = {}): AdminAiUsageLogResponse {
  return {
    id: 1,
    userName: 'Amira Hassan',
    userRole: 'STUDENT',
    featureKey: 'CHAT_TUTOR',
    status: 'SUCCESS',
    errorSummary: null,
    createdAt: '2026-05-18T13:42:00',
    ...overrides,
  }
}

const ROWS = [
  logRow(),
  logRow({
    id: 2,
    userName: 'Olivia Tan',
    userRole: 'ADMIN',
    featureKey: 'QUIZ_DRAFT',
    status: 'FAILED',
    errorSummary: 'The provider timed out.',
  }),
  logRow({ id: 3, userName: 'Daniel Tan', featureKey: 'HINT', status: 'UNAVAILABLE' }),
]

const NO_FILTERS = { featureType: '', status: '', userRole: '' }

async function mountPage() {
  const { wrapper } = await mountAtRoute(AdminAiUsagePage, '/admin/ai/usage')
  return wrapper
}

function cellsOf(row: { findAll: (selector: string) => { text: () => string }[] }): string[] {
  return row.findAll('td').map((cell) => cell.text())
}

describe('AdminAiUsagePage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.usageSummary.mockResolvedValue(SUMMARY)
    api.usageLogs.mockResolvedValue(pageOf(ROWS, 0, 25))
  })

  it('shows the all-time totals with what each one means', async () => {
    const wrapper = await mountPage()

    const figures = wrapper.findAll('.admin-figure').map((figure) => figure.text())
    expect(figures).toEqual([
      expect.stringMatching(/^Requests\s*1,284\s*All time$/),
      expect.stringMatching(/^Successful\s*1,236\s*96\.3%$/),
      expect.stringMatching(/^Unavailable\s*31\s*The AI service did not answer$/),
      expect.stringMatching(/^Failed\s*17\s*An error stopped the answer$/),
    ])
    // Each number once: the request log and the feature list do not repeat the total.
    expect(wrapper.text().match(/1,284/g)).toHaveLength(1)
  })

  it('names every feature, the admin’s quiz drafts and the learners’ hints too, with its share', async () => {
    const wrapper = await mountPage()

    const shares = wrapper.findAll('.admin-shares li').map((row) => row.text())
    expect(shares).toEqual([
      expect.stringMatching(/^Ask SparkBot\s*580 · 45\.2%$/),
      expect.stringMatching(/^Quiz drafts\s*76 · 5\.9%$/),
      expect.stringMatching(/^Hints\s*42 · 3\.3%$/),
    ])
    const features = wrapper
      .findAll('select')[0]!
      .findAll('option')
      .map((option) => option.text())
    expect(features).toEqual([
      'All features',
      'Ask SparkBot',
      'Talk with Xiaomei',
      'Quick quiz',
      'Story',
      'Pronunciation & handwriting',
      'Quiz drafts',
      'Learning plans',
      'Hints',
    ])
  })

  it('lists the newest requests with the user, their role as a Learner or Admin, the feature and the result', async () => {
    const wrapper = await mountPage()

    expect(api.usageLogs).toHaveBeenCalledWith(NO_FILTERS, 0, 10)
    const rows = wrapper.findAll('.admin-ai-log tbody tr')
    expect(cellsOf(rows[0]!)).toEqual(['18 May, 1:42 PM', 'Amira Hassan', 'Learner', 'Ask SparkBot', 'Successful'])
    expect(cellsOf(rows[1]!)).toEqual(['18 May, 1:42 PM', 'Olivia Tan', 'Admin', 'Quiz drafts', 'Failed'])
    expect(cellsOf(rows[2]!).slice(2)).toEqual(['Learner', 'Hints', 'Unavailable'])
    // What went wrong is on the result, not in the table.
    expect(rows[1]!.findAll('td')[4]!.attributes('title')).toBe('The provider timed out.')
    expect(wrapper.text()).toContain('For prompts and replies, the usage log records only their length.')
  })

  it('offers the roles of today’s platform and sends the one chosen, from the first page', async () => {
    api.usageLogs.mockImplementation(async (_filters: unknown, page: number) => pageOf(ROWS, page, 25))
    const wrapper = await mountPage()
    await wrapper.get('nav.pagination button[aria-label="Next"]').trigger('click')
    await flushPromises()
    expect(api.usageLogs).toHaveBeenLastCalledWith(NO_FILTERS, 1, 10)

    const role = wrapper.findAll('select')[2]!
    expect(role.findAll('option').map((option) => option.text())).toEqual(['All roles', 'Learner', 'Admin'])
    await role.setValue('ADMIN')
    await flushPromises()

    expect(api.usageLogs).toHaveBeenLastCalledWith({ ...NO_FILTERS, userRole: 'ADMIN' }, 0, 10)
    expect(wrapper.text()).not.toMatch(/teacher/i)
  })

  it('sends the feature and result filters', async () => {
    const wrapper = await mountPage()

    await wrapper.findAll('select')[0]!.setValue('HINT')
    await wrapper.findAll('select')[1]!.setValue('FAILED')
    await flushPromises()

    expect(api.usageLogs).toHaveBeenLastCalledWith({ featureType: 'HINT', status: 'FAILED', userRole: '' }, 0, 10)
  })

  it('names no role for a request an account made before teachers were removed', async () => {
    api.usageLogs.mockResolvedValue(
      pageOf([logRow({ id: 9, userRole: 'TEACHER' }), logRow({ id: 10, userRole: null })]),
    )
    const wrapper = await mountPage()

    for (const row of wrapper.findAll('.admin-ai-log tbody tr')) {
      const role = row.findAll('td')[2]!
      expect(role.text()).toBe('—')
      expect(role.find('.tag').exists()).toBe(false)
    }
    expect(wrapper.text()).not.toMatch(/teacher/i)
  })

  it('pages the log as "Page 1 of 3" and hides the bar when one page holds every request', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('nav.pagination .pagination-info').text()).toBe('Page 1 of 3')

    api.usageLogs.mockResolvedValue(pageOf(ROWS))
    await wrapper.findAll('select')[1]!.setValue('SUCCESS')
    await flushPromises()
    expect(wrapper.find('nav.pagination').exists()).toBe(false)
  })

  it('says when no request matches the filters', async () => {
    api.usageLogs.mockResolvedValue(pageOf([]))
    const wrapper = await mountPage()

    expect(wrapper.get('.state-panel.is-empty h3').text()).toBe('No requests')
    expect(wrapper.text()).toContain('No AI request matches these filters.')
  })

  it('replaces old rows with a retry when loading a new filter fails', async () => {
    const wrapper = await mountPage()
    api.usageLogs.mockRejectedValueOnce({ response: { status: 503 } })

    await wrapper.findAll('select')[2]!.setValue('ADMIN')
    await flushPromises()

    expect(wrapper.find('.admin-ai-log').exists()).toBe(false)
    const retry = wrapper.get('.state-panel.is-error button')
    api.usageLogs.mockResolvedValueOnce(pageOf([ROWS[1]!]))
    await retry.trigger('click')
    await flushPromises()

    expect(api.usageLogs).toHaveBeenLastCalledWith({ ...NO_FILTERS, userRole: 'ADMIN' }, 0, 10)
    const rows = wrapper.findAll('.admin-ai-log tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0]!.text()).toContain('Olivia Tan')
  })

  it('exports the filtered log as CSV and says the export is in the audit log', async () => {
    const file = { blob: new Blob(['id\r\n']), fileName: 'ai-usage-log.csv' }
    api.exportUsageLogs.mockResolvedValue(file)
    const wrapper = await mountPage()
    await wrapper.findAll('select')[2]!.setValue('STUDENT')
    await flushPromises()

    const exportButton = buttonNamed(wrapper, 'Export CSV')
    await exportButton.trigger('click')
    await flushPromises()

    expect(api.exportUsageLogs).toHaveBeenCalledWith({ ...NO_FILTERS, userRole: 'STUDENT' })
    expect(download.saveBlobDownload).toHaveBeenCalledWith(file, 'ai-usage-log.csv')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('CSV downloaded. The export is recorded in the audit log.')

    api.exportUsageLogs.mockRejectedValue({ response: { status: 500, data: { message: 'Export failed' } } })
    await exportButton.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Export failed')
  })

  it('names roles and features in the interface language', async () => {
    const wrapper = await mountPage()
    const language = i18n.global.locale.value
    try {
      i18n.global.locale.value = 'zh-CN'
      await flushPromises()
      const roles = wrapper
        .findAll('select')[2]!
        .findAll('option')
        .map((option) => option.text())
      expect(roles).toEqual(['全部角色', '学习者', '管理员'])
      expect(cellsOf(wrapper.findAll('.admin-ai-log tbody tr')[2]!).slice(2, 4)).toEqual(['学习者', '提示'])
      i18n.global.locale.value = 'ms'
      await flushPromises()
      expect(wrapper.findAll('select')[2]!.findAll('option')[1]!.text()).toBe('Pelajar')
    } finally {
      i18n.global.locale.value = language
    }
  })
})
