import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { progressVersion } from '@/composables/useProgressRefresh'
import { deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { SupportQueueResponse, SupportRequestDetail, SupportRequestSummary, SupportStatus } from '@/types/ops'

const supportApi = vi.hoisted(() => ({
  queue: vi.fn(),
  get: vi.fn(),
  reply: vi.fn(),
  solve: vi.fn(),
  reopen: vi.fn(),
  attachment: vi.fn(),
}))

vi.mock('@/api/ops', () => ({ adminSupportApi: supportApi }))

import AdminSupportPage from './AdminSupportPage.vue'

const now = new Date().toISOString()

function summary(overrides: Partial<SupportRequestSummary> = {}): SupportRequestSummary {
  return {
    id: 41,
    number: 1041,
    topic: 'CLASSES',
    subject: 'Lesson video won’t play',
    status: 'OPEN',
    requesterName: 'Daniel Tan',
    requesterEmail: 'daniel@example.com',
    requesterRole: 'STUDENT',
    createdAt: now,
    updatedAt: now,
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

function detailOf(request: SupportRequestSummary): SupportRequestDetail {
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
  }
}

function solved(request: SupportRequestSummary): SupportRequestDetail {
  return detailOf({ ...request, status: 'SOLVED', solvedAt: now, closedBy: 'SUPPORT' })
}

describe('AdminSupportPage', () => {
  beforeEach(() => {
    Object.values(supportApi).forEach((mock) => mock.mockReset())
    supportApi.queue.mockImplementation(async (status: SupportStatus) =>
      queueOf(status, status === 'OPEN' ? [summary(), visitor] : []),
    )
    supportApi.get.mockImplementation(async (id: number) => detailOf(id === visitor.id ? visitor : summary()))
  })

  it.each([
    ['reply', 'success'],
    ['reply', 'failure'],
    ['solve', 'success'],
    ['solve', 'failure'],
  ] as const)('keeps the new request and draft after an old %s %s', async (action, outcome) => {
    const change = deferred<SupportRequestDetail>()
    supportApi[action].mockReturnValue(change.promise)
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    if (action === 'reply') {
      await wrapper.get('[data-testid="admin-support-reply"]').setValue('Reply to Daniel')
      await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    } else await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')

    expect(wrapper.get('[data-testid="admin-support-send"]').attributes('aria-busy')).toBe(String(action === 'reply'))
    expect(wrapper.get('[data-testid="admin-support-solve"]').attributes('aria-busy')).toBe(String(action === 'solve'))
    await router.replace({ query: { request: '42' } })
    await flushPromises()
    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Visitor draft')
    if (outcome === 'success') change.resolve(detailOf(summary({ status: action === 'reply' ? 'ANSWERED' : 'SOLVED' })))
    else change.reject(new Error('Old request failed'))
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain('#1042')
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe(
      'Visitor draft',
    )
    expect(wrapper.find('.data-notice[role="alert"]').exists()).toBe(false)
  })

  it('still refreshes the sidebar counts when the admin leaves before the change is saved', async () => {
    const change = deferred<SupportRequestDetail>()
    supportApi.solve.mockReturnValue(change.promise)
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')
    const before = progressVersion.value

    wrapper.unmount()
    change.resolve(solved(summary()))
    await flushPromises()

    expect(progressVersion.value).toBe(before + 1)
  })

  it('locks the pending reply against edits and repeated form submission', async () => {
    supportApi.reply.mockReturnValue(new Promise(() => {}))
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Reply to Daniel')
    const form = wrapper.get('[data-testid="admin-support-detail"] form')
    await form.trigger('submit')
    await form.trigger('submit')
    expect(supportApi.reply).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="admin-support-reply"]').attributes('disabled')).toBeDefined()
  })

  it('tracks overlapping attachment downloads by message', async () => {
    const first = deferred()
    const second = deferred()
    supportApi.attachment.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const detail = detailOf(summary())
    const message = detail.messages[0]!
    detail.messages = [
      { ...message, attachment: { fileName: 'first.png', fileSize: 1024 } },
      { ...message, id: 2, attachment: { fileName: 'second.png', fileSize: 2048 } },
    ]
    supportApi.get.mockResolvedValue(detail)
    const popup = vi.spyOn(window, 'open').mockReturnValue(null)
    try {
      const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
      const buttons = wrapper.findAll('button.admin-support-attachment')
      await buttons[0]!.trigger('click')
      await buttons[1]!.trigger('click')
      expect(buttons.map((button) => button.attributes('aria-busy'))).toEqual(['true', 'true'])

      first.reject(new Error('First download failed'))
      await flushPromises()
      expect(buttons.map((button) => button.attributes('aria-busy'))).toEqual(['false', 'true'])

      second.reject(new Error('Second download failed'))
      await flushPromises()
      expect(buttons.map((button) => button.attributes('aria-busy'))).toEqual(['false', 'false'])
    } finally {
      popup.mockRestore()
    }
  })

  it('does not release the new request while its reply is still pending', async () => {
    const oldReply = deferred<SupportRequestDetail>()
    supportApi.reply.mockReturnValueOnce(oldReply.promise).mockReturnValueOnce(new Promise(() => {}))
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Reply to Daniel')
    await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    await router.replace({ query: { request: '42' } })
    await flushPromises()
    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Visitor reply')
    expect(wrapper.get('[data-testid="admin-support-send"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    oldReply.resolve(detailOf(summary({ status: 'ANSWERED' })))
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-support-send"]').attributes('disabled')).toBeDefined()
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe(
      'Visitor reply',
    )
    expect(supportApi.reply).toHaveBeenLastCalledWith(42, 'Visitor reply')
  })

  it('shows the queue by status with the counts on the tabs and opens the oldest request', async () => {
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support')

    expect(supportApi.queue).toHaveBeenCalledWith('OPEN', 0, 20)
    expect(wrapper.get('[data-testid="admin-support-tab-open"]').text()).toBe('Open\u00a0· 2')
    expect(wrapper.get('[data-testid="admin-support-tab-answered"]').text()).toBe('Answered\u00a0· 1')
    const items = wrapper.findAll('[data-testid="admin-support-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('#1041')
    expect(items[0].text()).toContain('Daniel Tan\u00a0· learner')
    expect(items[1].text()).toContain('Visitor\u00a0· aisyah@example.com')
    expect(router.currentRoute.value.query.request).toBe('41')

    await wrapper.get('[data-testid="admin-support-tab-solved"]').trigger('click')
    await flushPromises()
    expect(supportApi.queue).toHaveBeenLastCalledWith('SOLVED', 0, 20)
    expect(wrapper.get('[data-testid="admin-support-tab-solved"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('Nothing solved yet')
  })

  it.each([true, false])(
    'selects the oldest arrival only if the admin stays on Support (leave = %s)',
    async (leave) => {
      const loading = deferred<SupportQueueResponse>()
      const destination = deferred<boolean>()
      supportApi.queue.mockReturnValueOnce(loading.promise)
      const { router } = await mountAtRoute(AdminSupportPage, '/admin/support')
      router.beforeResolve((to) => (to.name === 'admin-dashboard' ? destination.promise : true))

      const navigation = router.push('/admin/dashboard')
      await flushPromises()
      loading.resolve(queueOf('OPEN', [summary()]))
      await flushPromises()
      destination.resolve(leave)
      await navigation
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe(leave ? '/admin/dashboard' : '/admin/support?request=41')
      expect(supportApi.get).toHaveBeenCalledTimes(leave ? 0 : 1)
    },
  )

  it('keeps the tab in the address, so a reload stays on it', async () => {
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support?status=ANSWERED&request=41')

    expect(supportApi.queue).toHaveBeenCalledWith('ANSWERED', 0, 20)
    expect(wrapper.get('[data-testid="admin-support-tab-answered"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain('#1041')

    await wrapper.get('[data-testid="admin-support-tab-solved"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ status: 'SOLVED', request: '41' })

    await wrapper.get('[data-testid="admin-support-tab-open"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ request: '41' })
  })

  it('opens the Open tab for an address without a known tab', async () => {
    await mountAtRoute(AdminSupportPage, '/admin/support?status=CLOSED')

    expect(supportApi.queue).toHaveBeenCalledWith('OPEN', 0, 20)
  })

  it('follows the address when the Support link opens the page again without a tab', async () => {
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support?status=SOLVED')
    expect(supportApi.queue).toHaveBeenLastCalledWith('SOLVED', 0, 20)

    await router.push('/admin/support')
    await flushPromises()

    expect(supportApi.queue).toHaveBeenLastCalledWith('OPEN', 0, 20)
    expect(wrapper.get('[data-testid="admin-support-tab-open"]').attributes('aria-pressed')).toBe('true')
  })

  it('steps back to the last page with requests when a reply empties the page on screen', async () => {
    let replied = false
    const firstPage = Array.from({ length: 20 }, (_, index) => summary({ id: 100 + index, number: 2000 + index }))
    supportApi.queue.mockImplementation(async (_status: SupportStatus, page: number) => {
      const total = replied ? 20 : 21
      return {
        counts: { OPEN: total, ANSWERED: replied ? 1 : 0, SOLVED: 0 },
        requests: page === 1 ? pageOf(replied ? [] : [summary()], 1, total, 20) : pageOf(firstPage, 0, total, 20),
      }
    })
    supportApi.reply.mockImplementation(async () => {
      replied = true
      return detailOf(summary({ status: 'ANSWERED' }))
    })
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    await wrapper.get('nav.pagination button[aria-label="Next"]').trigger('click')
    await flushPromises()
    expect(supportApi.queue).toHaveBeenLastCalledWith('OPEN', 1, 20)
    expect(wrapper.findAll('[data-testid="admin-support-item"]')).toHaveLength(1)

    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Fixed now.')
    await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    await flushPromises()

    expect(supportApi.queue).toHaveBeenLastCalledWith('OPEN', 0, 20)
    expect(wrapper.findAll('[data-testid="admin-support-item"]')).toHaveLength(20)
    expect(wrapper.text()).not.toContain('All caught up')
  })

  it('opens a request from the list beside the queue', async () => {
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support')

    await wrapper.findAll('[data-testid="admin-support-item"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.request).toBe('42')
    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain('#1042')

    await wrapper.findAll('[data-testid="admin-support-item"]')[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.request).toBe('41')
    const detail = wrapper.get('[data-testid="admin-support-detail"]')
    expect(detail.text()).toContain('#1041 · Lesson video won’t play')
    expect(detail.text()).toContain('From Daniel Tan (learner) · daniel@example.com · Classes')
    expect(detail.text()).toContain('View user')
    expect(detail.find('a[href="/admin/users?user=90"]').exists()).toBe(true)
  })

  it('waits for a reply before Send reply works, then confirms who was told (A1.03, A1.04)', async () => {
    supportApi.reply.mockResolvedValue(detailOf(summary({ status: 'ANSWERED' })))
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    const send = () => wrapper.get('[data-testid="admin-support-send"]')

    // Both actions sit under the reply; Send reply waits until there is one, Mark as solved never does.
    expect(send().attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="admin-support-solve"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="admin-support-reply"]').setValue('   ')
    expect(send().attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    expect(supportApi.reply).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await wrapper.get('[data-testid="admin-support-reply"]').setValue('  The time is now fixed.  ')
    expect(send().attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    await flushPromises()

    expect(supportApi.reply).toHaveBeenCalledWith(41, 'The time is now fixed.')
    expect(wrapper.text()).toContain('Reply sent to Daniel Tan (#1041).')
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe('')
    expect(supportApi.queue).toHaveBeenCalledTimes(2)
    // After the reply the composer stays, for another reply or to mark the request solved.
    expect(wrapper.get('[data-testid="admin-support-reply"]').attributes('placeholder')).toBe('Add another reply…')
    expect(wrapper.find('[data-testid="admin-support-solve"]').exists()).toBe(true)
    expect(send().attributes('disabled')).toBeDefined()
  })

  it('starts the reply clean on another request', async () => {
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')

    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Half a reply')
    await router.replace({ query: { request: '42' } })
    await flushPromises()

    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe('')
    expect(wrapper.get('[data-testid="admin-support-send"]').attributes('disabled')).toBeDefined()
    expect(supportApi.reply).not.toHaveBeenCalled()
  })

  it('speaks of a visitor by email and marks the request solved with Undo (A1.08)', async () => {
    supportApi.solve.mockResolvedValue(solved(visitor))
    supportApi.reopen.mockResolvedValue(detailOf(visitor))
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=42')

    const detail = wrapper.get('[data-testid="admin-support-detail"]')
    expect(detail.text()).toContain('From a visitor · aisyah@example.com · Account')
    expect(detail.text()).toContain('Visitor')
    expect(detail.text()).not.toContain('View user')

    // An unsent draft is not kept once the request is solved.
    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Unsent draft')
    await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')
    await flushPromises()

    expect(supportApi.solve).toHaveBeenCalledWith(42)
    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.get('span').text()).toBe('#1042 marked as solved.')
    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain('Solved')
    expect(wrapper.find('[data-testid="admin-support-reply"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Marked as solved by support on')
    // The queue and its counts catch up.
    expect(supportApi.queue).toHaveBeenCalledTimes(2)

    // Undo reopens it: the request is open again, with an empty reply box.
    await toast.get('.toast-action').trigger('click')
    await flushPromises()
    expect(supportApi.reopen).toHaveBeenCalledWith(42)
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe('')
    expect(supportApi.queue).toHaveBeenCalledTimes(3)
  })

  it('undoes a solve without replacing another request opened since', async () => {
    supportApi.solve.mockResolvedValue(solved(summary()))
    supportApi.reopen.mockResolvedValue(detailOf(summary({ status: 'ANSWERED' })))
    const { wrapper, router } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')

    await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')
    await flushPromises()
    await router.replace({ query: { request: '42' } })
    await flushPromises()
    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()

    expect(supportApi.reopen).toHaveBeenCalledWith(41)
    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain('#1042')
  })

  it('says when an undo could not reopen the request', async () => {
    supportApi.solve.mockResolvedValue(solved(summary()))
    supportApi.reopen.mockRejectedValue({
      response: { status: 409, data: { message: 'Only a solved request can be reopened.' } },
    })
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')

    await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice[role="alert"]').text()).toContain('Only a solved request can be reopened.')
  })

  it('names no role for a request an account sent before teachers were removed', async () => {
    const legacy = summary({ id: 43, number: 1043, requesterName: 'Wong Siew Ling', requesterRole: 'TEACHER' })
    supportApi.queue.mockResolvedValue(queueOf('OPEN', [legacy]))
    supportApi.get.mockResolvedValue(detailOf(legacy))
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=43')

    expect(wrapper.get('[data-testid="admin-support-item"]').text()).toContain('Wong Siew Ling\u00a0·')
    expect(wrapper.get('[data-testid="admin-support-detail"]').text()).toContain(
      'From Wong Siew Ling · daniel@example.com · Classes',
    )
    expect(wrapper.text()).not.toMatch(/teacher/i)
  })
  it('reports a reply that could not be sent, keeps the text, and resyncs stale server state on 409', async () => {
    supportApi.reply.mockRejectedValue({
      response: { status: 409, data: { message: 'This request is already solved.' } },
    })
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?request=41')
    const beforeProgress = progressVersion.value

    await wrapper.get('[data-testid="admin-support-reply"]').setValue('Following up')
    await wrapper.get('[data-testid="admin-support-detail"] form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('This request is already solved.')
    expect((wrapper.get('[data-testid="admin-support-reply"]').element as HTMLTextAreaElement).value).toBe(
      'Following up',
    )
    expect(supportApi.queue).toHaveBeenCalledTimes(2)
    expect(supportApi.get).toHaveBeenCalledTimes(2)
    expect(progressVersion.value).toBe(beforeProgress + 1)
  })

  it('offers Reopen on solved requests and clears selection without reloading a missing 404 detail', async () => {
    const solvedReq = summary({ id: 55, number: 1055, status: 'SOLVED', solvedAt: now, closedBy: 'SUPPORT' })
    supportApi.queue.mockResolvedValue(queueOf('SOLVED', [solvedReq]))
    supportApi.get.mockResolvedValue(detailOf(solvedReq))
    supportApi.reopen.mockResolvedValueOnce(
      detailOf({ ...solvedReq, status: 'ANSWERED', solvedAt: null, closedBy: null }),
    )
    const { wrapper } = await mountAtRoute(AdminSupportPage, '/admin/support?status=SOLVED&request=55')

    await wrapper.get('[data-testid="admin-support-reopen"]').trigger('click')
    await flushPromises()
    expect(supportApi.reopen).toHaveBeenCalledWith(55)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('#1055 reopened.')
    expect(wrapper.find('[data-testid="admin-support-reply"]').exists()).toBe(true)

    supportApi.get.mockClear()
    supportApi.queue.mockResolvedValueOnce(queueOf('SOLVED', []))
    supportApi.solve.mockRejectedValueOnce({
      response: { status: 404, data: { message: 'Support request not found.' } },
    })
    await wrapper.get('[data-testid="admin-support-solve"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('.data-notice[role="alert"]').text()).toContain('Support request not found.')
    expect(supportApi.get).not.toHaveBeenCalled()
  })
})
