import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import type { SupportQueueResponse, SupportRequestDetail, SupportRequestSummary, SupportStatus } from '@/types/ops'

import { mountAdminPage } from './adminPageTestSetup'

const supportApi = vi.hoisted(() => ({
  queue: vi.fn(),
  get: vi.fn(),
  reply: vi.fn(),
  solve: vi.fn(),
  attachment: vi.fn(),
}))

vi.mock('@/api/ops', () => ({ adminSupportApi: supportApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminSupportPage from './AdminSupportPage.vue'

const now = new Date().toISOString()

function summary(overrides: Partial<SupportRequestSummary> = {}): SupportRequestSummary {
  return {
    id: 41,
    number: 1041,
    topic: 'CLASSES',
    subject: 'Booking shows the wrong time',
    status: 'OPEN',
    requesterName: 'Daniel Tan',
    requesterEmail: 'daniel@example.com',
    requesterRole: 'STUDENT',
    createdAt: now,
    updatedAt: now,
    lastSupportReplyAt: null,
    closesAt: null,
    solvedAt: null,
    closedBy: null,
    ...overrides,
  }
}

const visitor = summary({
  id: 42,
  number: 1042,
  topic: 'ACCOUNT',
  subject: 'Can’t sign in after a reset',
  requesterName: null,
  requesterEmail: 'aisyah@example.com',
  requesterRole: 'VISITOR',
})

function queueOf(status: SupportStatus, items: SupportRequestSummary[]): SupportQueueResponse {
  return {
    counts: { OPEN: 2, ANSWERED: 1, SOLVED: 0 },
    requests: pageOf(
      items.map((item) => ({ ...item, status })),
      0,
      items.length,
      20,
    ),
  }
}

function detailOf(request: SupportRequestSummary, overrides: Partial<SupportRequestDetail> = {}): SupportRequestDetail {
  return {
    request,
    requesterUserId: request.requesterRole === 'VISITOR' ? null : 90,
    messages: [
      {
        id: 1,
        fromSupport: false,
        authorName: request.requesterName,
        body: 'Please help.',
        createdAt: now,
        attachment: null,
      },
    ],
    ...overrides,
  }
}

describe('AdminSupportPage', () => {
  beforeEach(() => {
    supportApi.queue.mockImplementation(async (status: SupportStatus) =>
      status === 'OPEN' ? queueOf('OPEN', [summary(), visitor]) : queueOf(status, []),
    )
    supportApi.get.mockImplementation(async (id: number) => detailOf(id === visitor.id ? visitor : summary()))
  })

  it('shows the queue by status with the counts on the tabs and opens the oldest request', async () => {
    const { wrapper, router } = await mountAdminPage(AdminSupportPage, '/admin/support')
    await flushPromises()

    expect(supportApi.queue).toHaveBeenCalledWith('OPEN', 0, 20)
    expect(wrapper.get('[data-testid="admin-support-tab-open"]').text()).toBe('Open · 2')
    expect(wrapper.get('[data-testid="admin-support-tab-answered"]').text()).toBe('Answered · 1')
    const items = wrapper.findAll('[data-testid="admin-support-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('#1041')
    expect(items[0].text()).toContain('Daniel Tan\u00a0· student')
    expect(items[1].text()).toContain('Visitor\u00a0· aisyah@example.com')
    expect(router.currentRoute.value.query.request).toBe('41')

    await wrapper.get('[data-testid="admin-support-tab-solved"]').trigger('click')
    await flushPromises()
    expect(supportApi.queue).toHaveBeenLastCalledWith('SOLVED', 0, 20)
    expect(wrapper.get('[data-testid="admin-support-tab-solved"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('Nothing solved yet')
  })

  it('opens a request from the list beside the queue', async () => {
    const { wrapper, router } = await mountAdminPage(AdminSupportPage, '/admin/support')
    await flushPromises()

    await wrapper.findAll('[data-testid="admin-support-item"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.request).toBe('42')
    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain('#1042')

    await wrapper.findAll('[data-testid="admin-support-item"]')[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.request).toBe('41')
    const detail = wrapper.get('[data-testid="admin-support-detail"]')
    expect(detail.text()).toContain('#1041 · Booking shows the wrong time')
    expect(detail.text()).toContain('From Daniel Tan (student) · daniel@example.com · Classes')
    expect(detail.find('a[href="/admin/users?user=90"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="admin-support-reply-hint"]').text()).toBe(
      'Sending marks it Answered. The student gets a notification.',
    )
  })

  it('needs a reply before sending it and confirms who was told', async () => {
    supportApi.reply.mockResolvedValue(detailOf(summary({ status: 'ANSWERED' })))
    const { wrapper } = await mountAdminPage(AdminSupportPage, '/admin/support?request=41')
    await flushPromises()

    await wrapper.get('form.admin-support-reply').trigger('submit')
    expect(wrapper.text()).toContain('Write a reply first.')
    expect(supportApi.reply).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="admin-support-reply"]').setValue('  The time is now fixed.  ')
    await wrapper.get('form.admin-support-reply').trigger('submit')
    await flushPromises()

    expect(supportApi.reply).toHaveBeenCalledWith(41, 'The time is now fixed.')
    expect(wrapper.text()).toContain('Reply sent to Daniel Tan (#1041).')
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe('')
    expect(supportApi.queue).toHaveBeenCalledTimes(2)
  })

  it('speaks of a visitor by email and marks the request solved with a toast', async () => {
    const solved = { ...visitor, status: 'SOLVED' as const, solvedAt: now, closedBy: 'SUPPORT' as const }
    supportApi.solve.mockResolvedValue(detailOf(solved))
    const { wrapper } = await mountAdminPage(AdminSupportPage, '/admin/support?request=42')
    await flushPromises()

    const detail = wrapper.get('[data-testid="admin-support-detail"]')
    expect(detail.text()).toContain('From a visitor · aisyah@example.com · Account')
    expect(detail.text()).toContain('Visitor')
    expect(detail.text()).not.toContain('View user')
    expect(wrapper.get('[data-testid="admin-support-reply-hint"]').text()).toBe(
      'Sending marks it Answered and emails aisyah@example.com.',
    )

    await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')
    await flushPromises()

    expect(supportApi.solve).toHaveBeenCalledWith(42)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('#1042 marked as solved.')
    expect(wrapper.find('[data-testid="admin-support-reply"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Marked as solved by support on')
  })

  it('reports a reply that could not be sent and keeps the text', async () => {
    supportApi.reply.mockRejectedValue({
      response: { status: 409, data: { message: 'This request is already solved.' } },
    })
    const { wrapper } = await mountAdminPage(AdminSupportPage, '/admin/support?request=41')
    await flushPromises()

    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Following up')
    await wrapper.get('form.admin-support-reply').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('This request is already solved.')
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe(
      'Following up',
    )
  })
})
