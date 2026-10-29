import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { SupportRequestDetail, SupportStatus } from '@/types/ops'

const supportApi = vi.hoisted(() => ({ get: vi.fn(), reply: vi.fn(), solve: vi.fn(), attachment: vi.fn() }))

vi.mock('@/api/ops', () => ({ supportApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true, activeRole: 'STUDENT' }) }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => ({ name: 'student-support-ticket', params: { ticketId: '7' } }),
}))

import SupportTicketPage from './SupportTicketPage.vue'

function detail(status: SupportStatus, extra: Partial<SupportRequestDetail['request']> = {}): SupportRequestDetail {
  return {
    request: {
      id: 7,
      number: 1038,
      topic: 'CLASSES',
      subject: 'Live session link didn’t open',
      status,
      requesterName: 'Amira Hassan',
      requesterEmail: 'student@fyp.local',
      requesterRole: 'STUDENT',
      createdAt: '2026-05-16T15:52:00',
      updatedAt: '2026-05-17T10:05:00',
      lastSupportReplyAt: '2026-05-17T10:05:00',
      closesAt: status === 'ANSWERED' ? '2026-05-24T10:05:00' : null,
      solvedAt: status === 'SOLVED' ? '2026-05-18T09:00:00' : null,
      closedBy: status === 'SOLVED' ? 'REQUESTER' : null,
      ...extra,
    },
    requesterUserId: 3,
    messages: [
      {
        id: 1,
        fromSupport: false,
        authorName: 'Amira Hassan',
        body: 'The Join button didn’t open Zoom.',
        createdAt: '2026-05-16T15:52:00',
        attachment: null,
      },
      {
        id: 2,
        fromSupport: true,
        authorName: 'Olivia Tan',
        body: 'The join link opens 15 minutes before the start.',
        createdAt: '2026-05-17T10:05:00',
        attachment: null,
      },
    ],
  }
}

async function mountPage() {
  const wrapper = mount(SupportTicketPage, { global: { stubs: { teleport: true, IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('SupportTicketPage', () => {
  beforeEach(() => {
    supportApi.get.mockResolvedValue(detail('ANSWERED'))
  })

  it('shows the thread with the subject as the page title and the closing date', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('h1').text()).toBe('Live session link didn’t open')
    expect(wrapper.text()).toContain('Answered · your turn')
    const thread = wrapper.find('[data-testid="support-thread"]')
    expect(thread.text()).toContain('You')
    expect(thread.text()).toContain('Olivia Tan')
    expect(thread.text()).toContain('MandarinSpark support')
    expect(wrapper.text()).toContain('If you don’t reply, it closes by itself on')
  })

  it('sends a reply and then waits for support', async () => {
    supportApi.reply.mockResolvedValue(detail('OPEN'))
    const wrapper = await mountPage()

    await wrapper.find('#support-reply').setValue('Copy link worked, thanks!')
    await wrapper.find('form.support-reply').trigger('submit')
    await flushPromises()

    expect(supportApi.reply).toHaveBeenCalledWith(7, 'Copy link worked, thanks!')
    expect(wrapper.text()).toContain('Waiting for support')
    expect(wrapper.text()).toContain('Reply sent on #1038.')
  })

  it('does not send an empty reply', async () => {
    const wrapper = await mountPage()
    await wrapper.find('form.support-reply').trigger('submit')

    expect(wrapper.find('#support-reply-error').text()).toBe('Write a reply first.')
    expect(supportApi.reply).not.toHaveBeenCalled()
  })

  it('marks the request solved and hides the reply box', async () => {
    supportApi.solve.mockResolvedValue(detail('SOLVED'))
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="support-solve"]').trigger('click')
    await flushPromises()

    expect(supportApi.solve).toHaveBeenCalledWith(7)
    expect(wrapper.find('form.support-reply').exists()).toBe(false)
    expect(wrapper.find('[data-testid="support-solved"]').text()).toContain('If it happens again, send a new request.')
  })

  it('explains a request that closed by itself', async () => {
    supportApi.get.mockResolvedValue(detail('SOLVED', { closedBy: 'AUTO' }))
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="support-solved"]').text()).toContain(
      'It closed by itself 7 days after our last reply.',
    )
  })

  it('says when the request cannot be opened', async () => {
    supportApi.get.mockRejectedValue({ response: { status: 404, data: { message: 'Support request was not found.' } } })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('We couldn’t open this request')
    expect(wrapper.text()).toContain('Back to Help & support')
    expect(wrapper.findAll('h1')).toHaveLength(1)
  })
})
