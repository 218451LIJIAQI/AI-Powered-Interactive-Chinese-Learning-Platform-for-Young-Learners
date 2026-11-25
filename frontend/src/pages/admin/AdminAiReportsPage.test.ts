import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { AdminAiResponseReportResponse } from '@/types/ai'
import type { PageResponse } from '@/types/api'

const api = vi.hoisted(() => ({ reports: vi.fn(), report: vi.fn(), reviewReport: vi.fn() }))
const progress = vi.hoisted(() => ({ notifyProgressChanged: vi.fn() }))

vi.mock('@/api/ai', () => ({ adminAiApi: api }))
vi.mock('@/composables/useProgressRefresh', () => progress)

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

async function mountPage(path = '/admin/ai') {
  const { wrapper } = await mountAtRoute(AdminAiReportsPage, path)
  return wrapper
}

/** Dismiss, then Resolve. */
function decisionButtons(wrapper: Awaited<ReturnType<typeof mountPage>>) {
  return wrapper.findAll('.admin-ai-decision button')
}

describe('AdminAiReportsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.reports.mockResolvedValue(pageOf([OPEN_REPORT, RESOLVED_REPORT]))
    api.report.mockResolvedValue(RESOLVED_REPORT)
  })

  it('shows the first report with what the student asked, what the AI answered and their note', async () => {
    const wrapper = await mountPage()

    expect(api.reports).toHaveBeenCalledWith({ page: 0, size: 10 })
    const detail = wrapper.get('[data-testid="admin-ai-report-detail"]')
    expect(detail.text()).toContain('Amira · amira@example.com')
    expect(detail.text()).toContain('How do I say thank you?')
    expect(detail.text()).toContain('谢谢 (xiéxie)')
    expect(detail.text()).toContain('The tone mark looks wrong.')
    // The note is marked required on its label; the hint says who reads it.
    expect(detail.get('.field-hint').text()).toBe('Amira sees it in My reports.')
  })

  it('needs a note for the student before a decision, with an example of one (A5)', async () => {
    const wrapper = await mountPage()
    const error = () => wrapper.find('[data-testid="admin-ai-report-detail"] .field-error')
    const note = () => wrapper.get('[data-testid="admin-ai-report-detail"] textarea')
    // Untouched, the note shows no error and both decisions can be pressed.
    expect(error().exists()).toBe(false)
    expect(note().attributes('placeholder')).toBe('e.g. Thanks, we corrected the example.')
    expect(wrapper.get('.admin-ai-decision').text()).toContain(
      'Resolve: the answer was wrong and you acted on it. Dismiss: no change needed (the answer was fine or is already fixed).',
    )
    expect(decisionButtons(wrapper).map((button) => button.attributes('disabled'))).toEqual([undefined, undefined])

    await decisionButtons(wrapper)[1]!.trigger('click')

    expect(api.reviewReport).not.toHaveBeenCalled()
    expect(error().text()).toBe('Write a note to the learner before you resolve or dismiss.')
    expect(note().attributes('aria-invalid')).toBe('true')
    expect(note().attributes('aria-describedby')).toBe(`${error().attributes('id')} ${note().attributes('id')}-hint`)
    // Both decisions need the note, so both wait for it; the wording stays when the field is left again.
    expect(decisionButtons(wrapper).every((button) => button.attributes('disabled') !== undefined)).toBe(true)
    await note().trigger('blur')
    expect(error().text()).toBe('Write a note to the learner before you resolve or dismiss.')

    await note().setValue('   ')
    expect(error().exists()).toBe(true)
    await note().setValue('It is xièxie.')
    expect(error().exists()).toBe(false)
    expect(note().attributes('aria-invalid')).toBeUndefined()
    expect(decisionButtons(wrapper).map((button) => button.attributes('disabled'))).toEqual([undefined, undefined])
  })

  it('checks the note when the field is left, before any decision is pressed', async () => {
    const wrapper = await mountPage()
    const error = () => wrapper.find('[data-testid="admin-ai-report-detail"] .field-error')

    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').trigger('blur')
    expect(error().text()).toBe('Write a note to the learner before you resolve or dismiss.')
    expect(decisionButtons(wrapper).every((button) => button.attributes('disabled') !== undefined)).toBe(true)

    i18n.global.locale.value = 'zh-CN'
    await flushPromises()
    expect(error().text()).toBe('请先给学习者写备注，再解决或驳回。')
    i18n.global.locale.value = 'en'
    await flushPromises()

    // Dismiss asks the same once the note was fixed and emptied again.
    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').setValue('Fine.')
    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').setValue('')
    expect(error().exists()).toBe(false)
    await decisionButtons(wrapper)[0]!.trigger('click')
    expect(error().text()).toBe('Write a note to the learner before you resolve or dismiss.')
    expect(api.reviewReport).not.toHaveBeenCalled()
  })

  it('does not carry a note error over to another report', async () => {
    api.reports.mockResolvedValue(pageOf([OPEN_REPORT, { ...OPEN_REPORT, id: 8, studentName: 'Ben' }]))
    const wrapper = await mountPage()
    await decisionButtons(wrapper)[1]!.trigger('click')
    expect(wrapper.find('[data-testid="admin-ai-report-detail"] .field-error').exists()).toBe(true)

    await wrapper.get('[data-testid="admin-ai-report-8"]').trigger('click')

    expect(wrapper.find('[data-testid="admin-ai-report-detail"] .field-error').exists()).toBe(false)
    expect(decisionButtons(wrapper).map((button) => button.attributes('disabled'))).toEqual([undefined, undefined])
  })

  it('resolves a report with the note, reloads the list and refreshes the badges', async () => {
    api.reviewReport.mockResolvedValue({ ...OPEN_REPORT, status: 'RESOLVED' })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').setValue('  You are right, it is xièxie.  ')
    await decisionButtons(wrapper)[1]!.trigger('click')
    await flushPromises()

    expect(api.reviewReport).toHaveBeenCalledWith(7, 'RESOLVED', 'You are right, it is xièxie.')
    expect(api.reports).toHaveBeenCalledTimes(2)
    expect(progress.notifyProgressChanged).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Report resolved. Amira sees your note.')
  })

  it('still refreshes the badges when the admin leaves before the decision is saved', async () => {
    const review = deferred<AdminAiResponseReportResponse>()
    api.reviewReport.mockReturnValue(review.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').setValue('Checked, thank you.')
    await decisionButtons(wrapper)[1]!.trigger('click')

    wrapper.unmount()
    review.resolve({ ...OPEN_REPORT, status: 'RESOLVED' })
    await flushPromises()

    expect(progress.notifyProgressChanged).toHaveBeenCalledTimes(1)
  })

  it('shows the report as the server has it when another admin closed it first', async () => {
    api.reviewReport.mockRejectedValue({
      response: { status: 400, data: { message: 'This AI report is already closed.' } },
    })
    const closedByOther = {
      ...OPEN_REPORT,
      status: 'DISMISSED' as const,
      reviewerName: 'Admin Tan',
      reviewerNote: 'Fine.',
    }
    api.reports
      .mockResolvedValueOnce(pageOf([OPEN_REPORT, RESOLVED_REPORT]))
      .mockResolvedValueOnce(pageOf([closedByOther, RESOLVED_REPORT]))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').setValue('It is xièxie.')
    await decisionButtons(wrapper)[1]!.trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice[role="alert"]').text()).toContain('This AI report is already closed.')
    expect(api.reports).toHaveBeenCalledTimes(2)
    expect(progress.notifyProgressChanged).toHaveBeenCalledTimes(1)
    const detail = wrapper.get('[data-testid="admin-ai-report-detail"]')
    expect(detail.find('.admin-ai-decision').exists()).toBe(false)
    expect(detail.text()).toContain('Dismissed by Admin Tan')
  })

  it('does not carry a note over to the report that opens on another page', async () => {
    api.reports
      .mockResolvedValueOnce(pageOf([OPEN_REPORT], 0, 11))
      .mockResolvedValueOnce(pageOf([{ ...OPEN_REPORT, id: 8, studentName: 'Ben' }], 1, 11))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="admin-ai-report-detail"] textarea').setValue('Written for Amira.')
    await wrapper.findAll('.pagination button').at(-1)!.trigger('click')
    await flushPromises()

    expect(api.reports).toHaveBeenLastCalledWith({ page: 1, size: 10 })
    const detail = wrapper.get('[data-testid="admin-ai-report-detail"]')
    expect(detail.text()).toContain('Ben')
    expect(detail.get<HTMLTextAreaElement>('textarea').element.value).toBe('')
  })

  it('keeps a dashboard navigation when the next page of a linked report arrives', async () => {
    const response = deferred<PageResponse<AdminAiResponseReportResponse>>()
    api.reports.mockResolvedValueOnce(pageOf([OPEN_REPORT], 0, 11)).mockReturnValueOnce(response.promise)
    const { wrapper, router } = await mountAtRoute(AdminAiReportsPage, '/admin/ai?report=7')
    await wrapper.get('nav.pagination button[aria-label="Next"]').trigger('click')
    const destination = deferred<void>()
    router.beforeResolve((to) => (to.name === 'admin-dashboard' ? destination.promise : true))
    const navigation = router.push('/admin/dashboard')
    await flushPromises()
    response.resolve(pageOf([RESOLVED_REPORT], 1, 11))
    await flushPromises()
    destination.resolve()
    await navigation

    expect(router.currentRoute.value.fullPath).toBe('/admin/dashboard')
  })

  it('shows the admin note of a closed report instead of the decision form', async () => {
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="admin-ai-report-5"]').trigger('click')

    const detail = wrapper.get('[data-testid="admin-ai-report-detail"]')
    expect(detail.find('.admin-ai-decision').exists()).toBe(false)
    expect(detail.text()).toContain('Thanks, we fixed the explanation.')
    expect(detail.text()).toContain('Admin Lee')
  })

  it('opens the report the address names', async () => {
    const wrapper = await mountPage('/admin/ai?report=5')

    expect(wrapper.get('[data-testid="admin-ai-report-5"]').attributes('aria-current')).toBe('true')
    expect(wrapper.get('[data-testid="admin-ai-report-detail"]').text()).toContain('Thanks, we fixed the explanation.')
  })

  it('opens a linked report outside the current page without fetching the other pages', async () => {
    api.reports.mockResolvedValue(pageOf([OPEN_REPORT], 0, 11))
    const wrapper = await mountPage('/admin/ai?report=5')

    expect(api.report).toHaveBeenCalledExactlyOnceWith(5)
    expect(api.reports).toHaveBeenCalledExactlyOnceWith({ page: 0, size: 10 })
    expect(wrapper.get('[data-testid="admin-ai-report-detail"]').text()).toContain('Thanks, we fixed the explanation.')
  })

  it('keeps an unsent note when the selected report is clicked again', async () => {
    const wrapper = await mountPage()
    await wrapper.get('textarea').setValue('Note still being written.')
    await wrapper.get('[data-testid="admin-ai-report-7"]').trigger('click')
    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('Note still being written.')
  })

  it.each(['success', 'failure'])(
    'preserves a different report draft after an earlier decision %s',
    async (outcome) => {
      const decision = deferred<AdminAiResponseReportResponse>()
      api.reviewReport.mockReturnValue(decision.promise)
      api.reports.mockResolvedValue(pageOf([OPEN_REPORT, { ...OPEN_REPORT, id: 8, studentName: 'Ben' }]))
      const wrapper = await mountPage()
      await wrapper.get('textarea').setValue('Note for Amira.')
      await decisionButtons(wrapper)[1]!.trigger('click')
      await wrapper.get('[data-testid="admin-ai-report-8"]').trigger('click')
      await wrapper.get('textarea').setValue('Unsent note for Ben.')

      if (outcome === 'success') decision.resolve({ ...OPEN_REPORT, status: 'RESOLVED' })
      else decision.reject({ response: { status: 400, data: { message: 'The earlier decision failed' } } })
      await flushPromises()

      expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('Unsent note for Ben.')
      expect(wrapper.find('.data-notice[role="alert"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('The earlier decision failed')
      expect(wrapper.text()).not.toContain('The decision could not be saved.')
      expect(wrapper.get('[data-testid="admin-ai-report-8"]').attributes('aria-current')).toBe('true')
      expect(api.reviewReport).toHaveBeenCalledWith(7, 'RESOLVED', 'Note for Amira.')
    },
  )

  it('shows the decision error when switching away and back to the same report before rejection', async () => {
    const decision = deferred<AdminAiResponseReportResponse>()
    api.reviewReport.mockReturnValue(decision.promise)
    api.reports.mockResolvedValue(pageOf([OPEN_REPORT, { ...OPEN_REPORT, id: 8, studentName: 'Ben' }]))
    const wrapper = await mountPage()

    await wrapper.get('textarea').setValue('Note for Amira.')
    await decisionButtons(wrapper)[1]!.trigger('click')
    await wrapper.get('[data-testid="admin-ai-report-8"]').trigger('click')
    await wrapper.get('[data-testid="admin-ai-report-7"]').trigger('click')

    decision.reject(new Error('Already closed'))
    await flushPromises()

    expect(wrapper.get('.data-notice[role="alert"]').text()).toContain('The decision could not be saved.')
  })
})
