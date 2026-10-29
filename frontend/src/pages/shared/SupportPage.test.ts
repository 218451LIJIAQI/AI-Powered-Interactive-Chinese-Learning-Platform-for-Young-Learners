import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { chooseFile } from '@/test/helpers'
import type { SupportRequestSummary } from '@/types/ops'

const supportApi = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn() }))
const auth = vi.hoisted(() => ({ activeRole: 'STUDENT' as 'STUDENT' | 'TEACHER', isApprovedTeacher: false }))

vi.mock('@/api/ops', () => ({ supportApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => ({ query: {} }),
}))

import SupportPage from './SupportPage.vue'

function request(overrides: Partial<SupportRequestSummary> = {}): SupportRequestSummary {
  return {
    id: 7,
    number: 1038,
    topic: 'CLASSES',
    subject: 'Live session link didn’t open',
    status: 'ANSWERED',
    requesterName: 'Amira Hassan',
    requesterEmail: 'student@fyp.local',
    requesterRole: 'STUDENT',
    createdAt: '2026-05-16T15:52:00',
    updatedAt: '2026-05-17T10:05:00',
    lastSupportReplyAt: '2026-05-17T10:05:00',
    closesAt: '2026-05-24T10:05:00',
    solvedAt: null,
    closedBy: null,
    ...overrides,
  }
}

async function mountPage() {
  const wrapper = mount(SupportPage, { global: { stubs: { teleport: true, IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('SupportPage', () => {
  beforeEach(() => {
    auth.activeRole = 'STUDENT'
    auth.isApprovedTeacher = false
    supportApi.list.mockResolvedValue([request()])
    supportApi.create.mockResolvedValue({ request: request({ id: 9, number: 1042, status: 'OPEN' }), messages: [] })
  })

  it('lists the requests with their status and the student quick answers', async () => {
    const wrapper = await mountPage()

    const rows = wrapper.find('[data-testid="support-requests"]')
    expect(rows.text()).toContain('#1038 · Live session link didn’t open')
    expect(rows.text()).toContain('Answered · your turn')
    expect(rows.text()).toContain('Classes & live sessions')
    expect(wrapper.text()).toContain('How do I book a live session?')
    expect(wrapper.find('h1').text()).toBe('Help & support')
  })

  it('shows the empty state when there are no requests yet', async () => {
    supportApi.list.mockResolvedValue([])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No requests yet')
  })

  it('sends a new request and confirms it with its number', async () => {
    const wrapper = await mountPage()
    await wrapper.find('[data-testid="support-new"]').trigger('click')

    await wrapper.find('#support-subject').setValue('Lesson video won’t play')
    await wrapper.find('#support-message').setValue('It stops after 10 seconds.')
    await wrapper.find('#support-new-form').trigger('submit')
    await flushPromises()

    expect(supportApi.create).toHaveBeenCalledWith({
      topic: 'ACCOUNT',
      subject: 'Lesson video won’t play',
      message: 'It stops after 10 seconds.',
      screenshot: null,
    })
    expect(wrapper.text()).toContain('Request #1042 sent.')
    expect(supportApi.list).toHaveBeenCalledTimes(2)
  })

  it('asks for the missing fields instead of sending', async () => {
    const wrapper = await mountPage()
    await wrapper.find('[data-testid="support-new"]').trigger('click')
    await wrapper.find('#support-new-form').trigger('submit')

    expect(wrapper.find('#support-subject-error').text()).toBe('Add a subject.')
    expect(wrapper.find('#support-message-error').text()).toBe('Tell us what happened.')
    expect(supportApi.create).not.toHaveBeenCalled()
  })

  it('refuses a screenshot that is too large or not an image', async () => {
    const wrapper = await mountPage()
    await wrapper.find('[data-testid="support-new"]').trigger('click')
    const input = wrapper.find('#support-screenshot')

    const huge = new File(['x'], 'screen.png', { type: 'image/png' })
    Object.defineProperty(huge, 'size', { value: 6 * 1024 * 1024 })
    await chooseFile(input, huge)
    expect(wrapper.find('#support-screenshot-error').text()).toBe(
      'The screenshot is larger than 5 MB. Choose a smaller image.',
    )

    await chooseFile(input, new File(['x'], 'notes.pdf', { type: 'application/pdf' }))
    expect(wrapper.find('#support-screenshot-error').text()).toBe('Choose a PNG or JPG image.')
  })

  it('starts a teacher applicant on the verification topic with applicant answers', async () => {
    auth.activeRole = 'TEACHER'
    const wrapper = await mountPage()
    await wrapper.find('[data-testid="support-new"]').trigger('click')

    expect((wrapper.find('#support-topic').element as HTMLSelectElement).value).toBe('VERIFICATION')
    expect(wrapper.text()).toContain('How long does verification take?')
  })
})
