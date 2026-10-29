import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  sendHelpMessage: vi.fn(),
  auth: { isAuthenticated: false, activeRole: null as string | null },
}))

vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkHrefStub }))

vi.mock('@/api/ops', () => ({ publicOpsApi: { sendHelpMessage: mocks.sendHelpMessage } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))

import HelpPage from './HelpPage.vue'

function mountPage(): VueWrapper {
  return mount(HelpPage, { global: { stubs: { IconGlyph: true } } })
}

async function fillForm(wrapper: VueWrapper, message = '  I cannot open my lesson.  '): Promise<void> {
  await wrapper.get('#help-email').setValue(' Mei@Example.com ')
  await wrapper.get('#help-topic').setValue('LEARNING')
  await wrapper.get('#help-message').setValue(message)
}

beforeEach(() => {
  mocks.sendHelpMessage.mockReset()
  mocks.auth.isAuthenticated = false
  mocks.auth.activeRole = null
})

describe('HelpPage (FRSUP01)', () => {
  it('lists the questions by topic, with a link to each topic', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    for (const id of ['getting-started', 'for-students', 'for-teachers', 'your-account', 'ai-and-safety', 'contact']) {
      expect(wrapper.find(`#${id}`).exists(), id).toBe(true)
      expect(wrapper.find(`a[href="#${id}"]`).exists(), `link to ${id}`).toBe(true)
    }
  })

  it('checks the visitor form before sending anything', async () => {
    const wrapper = mountPage()

    await wrapper.get('#help-email').setValue('not-an-email')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('#help-email-error').text()).toBe('Enter a valid email address.')
    expect(wrapper.find('#help-message-error').exists()).toBe(true)
    expect(mocks.sendHelpMessage).not.toHaveBeenCalled()
  })

  it('sends a visitor message and shows the request number', async () => {
    mocks.sendHelpMessage.mockResolvedValue({ email: 'mei@example.com', number: 42 })
    const wrapper = mountPage()

    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.sendHelpMessage).toHaveBeenCalledWith({
      email: 'mei@example.com',
      topic: 'LEARNING',
      message: 'I cannot open my lesson.',
    })
    const sent = wrapper.get('[data-testid="help-sent"]')
    expect(sent.text()).toContain('mei@example.com')
    expect(sent.text()).toContain('#42')
  })

  it('keeps the message and explains the failure when sending fails', async () => {
    mocks.sendHelpMessage.mockRejectedValue({ response: { status: 429, data: {} } })
    const wrapper = mountPage()

    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('#help-send-error').exists()).toBe(true)
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.value).toBe('  I cannot open my lesson.  ')
  })

  it('sends a signed-in user to their own support page instead of the visitor form', () => {
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'TEACHER'

    const wrapper = mountPage()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('[data-testid="help-signed-in"] a').attributes('href')).toBe('/teacher/support')
  })
})
