import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import type { AdminAiResponseReportResponse } from '@/types/api'

const api = vi.hoisted(() => ({ reports: vi.fn(), reportCounts: vi.fn(), reviewReport: vi.fn() }))
const progress = vi.hoisted(() => ({ notifyProgressChanged: vi.fn() }))

vi.mock('@/api/ai', () => ({ adminAiApi: api }))
vi.mock('@/composables/useProgressRefresh', () => progress)
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminAiReportsPage from './AdminAiReportsPage.vue'

const OPEN_REPORT: AdminAiResponseReportResponse = {
  id: 7,
  studentName: 'Amira',
  studentEmail: 'amira@example.com',
  featureKey: 'CHAT_TUTOR',
  reasonCategory: 'INCORRECT_CONTENT',
  description: 'The tone mark looks wrong.',
  promptSnapshot: 'How do I say thank you?',
  aiResponseSnapshot: '谢谢 (xiéxie)',
  status: 'PENDING_REVIEW',
  reviewerName: null,
  reviewerNote: null,
  reviewedAt: null,
  createdAt: '2026-10-26T12:00:00Z',
}

const RESOLVED_REPORT: AdminAiResponseReportResponse = {
  ...OPEN_REPORT,
  id: 5,
  reasonCategory: 'UNCLEAR_EXPLANATION',
  status: 'RESOLVED',
  reviewerName: 'Admin Lee',
  reviewerNote: 'Thanks, we fixed the explanation.',
  reviewedAt: '2026-10-27T09:00:00Z',
}

function mountPage() {
  return mount(AdminAiReportsPage, { global: { stubs: { AdminAiTabs: true, IconGlyph: true } } })
}

describe('AdminAiReportsPage', () => {
  beforeEach(() => {
    api.reports.mockResolvedValue(pageOf([OPEN_REPORT, RESOLVED_REPORT]))
    api.reportCounts.mockResolvedValue({ open: 0, resolved: 2, dismissed: 0 })
  })

  it('shows the first report with what the student asked, what the AI answered and their note', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(api.reports).toHaveBeenCalledWith({ status: undefined, page: 0, size: 10 })
    const detail = wrapper.get('[data-testid="admin-ai-report-detail"]')
    expect(detail.text()).toContain('Amira · amira@example.com')
    expect(detail.text()).toContain('How do I say thank you?')
    expect(detail.text()).toContain('谢谢 (xiéxie)')
    expect(detail.text()).toContain('The tone mark looks wrong.')
  })

  it('needs a note for the student before a decision', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const resolve = wrapper.findAll('.admin-ai-decision-actions button')[1]!
    await resolve.trigger('click')

    expect(api.reviewReport).not.toHaveBeenCalled()
    expect(wrapper.find('.admin-ai-decision .field-error').exists()).toBe(true)
  })

  it('resolves a report with the note, reloads the list and refreshes the badges', async () => {
    api.reviewReport.mockResolvedValue({ ...OPEN_REPORT, status: 'RESOLVED' })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('.admin-ai-decision textarea').setValue('  You are right, it is xièxie.  ')
    await wrapper.findAll('.admin-ai-decision-actions button')[1]!.trigger('click')
    await flushPromises()

    expect(api.reviewReport).toHaveBeenCalledWith(7, 'RESOLVED', 'You are right, it is xièxie.')
    expect(api.reports).toHaveBeenCalledTimes(2)
    expect(api.reportCounts).toHaveBeenCalled()
    expect(progress.notifyProgressChanged).toHaveBeenCalled()
  })

  it('shows the admin note of a closed report instead of the decision form', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="admin-ai-report-5"]').trigger('click')

    const detail = wrapper.get('[data-testid="admin-ai-report-detail"]')
    expect(detail.find('.admin-ai-decision').exists()).toBe(false)
    expect(detail.text()).toContain('Thanks, we fixed the explanation.')
    expect(detail.text()).toContain('Admin Lee')
  })

  it('asks the server for open reports only when the Open filter is chosen', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const open = wrapper.findAll('.segmented-option').find((button) => button.text() === 'Open')!
    await open.trigger('click')
    await flushPromises()

    expect(api.reports).toHaveBeenLastCalledWith({ status: 'OPEN', page: 0, size: 10 })
  })
})
