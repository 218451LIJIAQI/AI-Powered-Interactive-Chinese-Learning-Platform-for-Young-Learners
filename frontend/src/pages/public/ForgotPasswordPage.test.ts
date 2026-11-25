import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred, toastMessageStub } from '@/test/helpers'
import { storageKeys } from '@/utils/storage'

const mocks = vi.hoisted(() => ({ forgotPassword: vi.fn() }))

vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

vi.mock('@/api/services', () => ({ authApi: { forgotPassword: mocks.forgotPassword } }))

import ForgotPasswordPage from './ForgotPasswordPage.vue'

const SUBMIT = '[data-testid="forgot-submit"]'
const SENT = '[data-testid="forgot-sent"]'

function mountPage(): VueWrapper {
  return mount(ForgotPasswordPage, {
    global: {
      stubs: {
        AppBrand: true,
        IconGlyph: true,
        LanguageSwitcher: true,
        AppToast: toastMessageStub,
      },
    },
  })
}

async function send(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  mocks.forgotPassword.mockReset()
  mocks.forgotPassword.mockResolvedValue(undefined)
})

describe('ForgotPasswordPage (FRAUTH04)', () => {
  it('starts with the address typed on the sign-in page, read once', () => {
    window.sessionStorage.setItem(storageKeys.recoveryEmail, 'mei@example.com')

    const wrapper = mountPage()

    expect(wrapper.get<HTMLInputElement>('#forgot-email').element.value).toBe('mei@example.com')
    expect(window.sessionStorage.getItem(storageKeys.recoveryEmail)).toBeNull()
  })

  it('checks the address when the visitor leaves the field, and keeps the button off until it is fixed', async () => {
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue('not-an-email')
    await wrapper.get('#forgot-email').trigger('blur')

    expect(wrapper.get('#forgot-email-error').text()).toBe('Enter a valid email address.')
    expect(wrapper.get('#forgot-email').attributes('aria-describedby')).toBe('forgot-email-error')
    expect(wrapper.get(SUBMIT).attributes('disabled')).toBeDefined()

    await wrapper.get('#forgot-email').setValue('mei@example.com')
    expect(wrapper.find('#forgot-email-error').exists()).toBe(false)
    expect(wrapper.get(SUBMIT).attributes('disabled')).toBeUndefined()
  })

  it('checks the address before sending anything', async () => {
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue('not-an-email')
    await send(wrapper)

    expect(wrapper.get('#forgot-email-error').text()).toBe('Enter a valid email address.')
    expect(mocks.forgotPassword).not.toHaveBeenCalled()
  })

  it('sends the link and shows the inbox step the real product shows, with no links to open', async () => {
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue(' Mei@Example.com ')
    await send(wrapper)

    expect(mocks.forgotPassword).toHaveBeenCalledWith('mei@example.com')
    const sent = wrapper.get(SENT)
    expect(sent.attributes('role')).toBe('status')
    expect(sent.get('strong').text()).toBe('Check your inbox')
    expect(sent.text()).toContain(
      'If an account uses this email, a reset link is on its way. It works once, for 60 minutes.',
    )
    expect(sent.text()).toContain('Only the newest link works. Older links expire.')
    expect(sent.find('a').exists()).toBe(false)
    // The same button now sends the link again, as a secondary action.
    expect(wrapper.get(SUBMIT).text()).toBe('Send the link again')
    expect(wrapper.get(SUBMIT).classes()).toContain('btn-secondary')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('')
  })

  it('confirms "Send the link again" with a toast', async () => {
    const wrapper = mountPage()
    await wrapper.get('#forgot-email').setValue('mei@example.com')
    await send(wrapper)

    await send(wrapper)

    expect(mocks.forgotPassword).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Link sent again. Check your inbox and spam folder.')
  })

  it('treats another address as a new request: the inbox step goes until the link is sent to it', async () => {
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue('mei@example.com')
    await send(wrapper)
    // The same address, as the server reads it.
    await wrapper.get('#forgot-email').setValue('Mei@Example.com ')
    expect(wrapper.find(SENT).exists()).toBe(true)
    await wrapper.get('#forgot-email').setValue('mei.tan@example.com')

    expect(wrapper.find(SENT).exists()).toBe(false)
    expect(wrapper.get(SUBMIT).text()).toBe('Send reset link')

    await send(wrapper)

    expect(mocks.forgotPassword).toHaveBeenLastCalledWith('mei.tan@example.com')
    expect(wrapper.find(SENT).exists()).toBe(true)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('')
  })

  it.each(['success', 'failure'])('ignores a %s from a request for a previous address', async (outcome) => {
    const request = deferred<void>()
    mocks.forgotPassword.mockReturnValueOnce(request.promise)
    const wrapper = mountPage()
    await wrapper.get('#forgot-email').setValue('mei@example.com')
    await wrapper.get('form').trigger('submit')

    await wrapper.get('#forgot-email').setValue('mei.tan@example.com')
    if (outcome === 'success') request.resolve()
    else request.reject(new Error('offline'))
    await flushPromises()

    expect(wrapper.find(SENT).exists()).toBe(false)
    expect(wrapper.find('#forgot-send-error').exists()).toBe(false)
  })

  it('reports a failed request beside Send and keeps the valid address editable', async () => {
    mocks.forgotPassword.mockRejectedValue({ response: { status: 500, data: {} } })
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue('mei@example.com')
    await send(wrapper)

    expect(wrapper.get('#forgot-send-error').text()).toBe(
      'The learning service is having trouble right now. Please try again shortly.',
    )
    expect(wrapper.get('#forgot-send-error').attributes('role')).toBe('alert')
    expect(wrapper.get('#forgot-email').attributes('aria-invalid')).toBe('false')
    expect(wrapper.get(SUBMIT).attributes('aria-describedby')).toBe('forgot-send-error')
    expect(wrapper.get<HTMLInputElement>('#forgot-email').element.value).toBe('mei@example.com')
    expect(wrapper.find(SENT).exists()).toBe(false)
  })

  it('prevents mousedown on Submit from blurring the email field before submit runs', () => {
    const wrapper = mountPage()
    const event = new MouseEvent('mousedown', { cancelable: true })
    wrapper.get(SUBMIT).element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })
})
