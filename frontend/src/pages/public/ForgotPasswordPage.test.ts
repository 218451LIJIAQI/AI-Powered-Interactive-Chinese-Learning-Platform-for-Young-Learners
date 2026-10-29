import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { storageKeys } from '@/utils/storage'

const mocks = vi.hoisted(() => ({
  forgotPassword: vi.fn(),
  replace: vi.fn(),
  route: { path: '/forgot-password', query: {} as Record<string, string>, hash: '' },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))

vi.mock('@/api/services', () => ({ authApi: { forgotPassword: mocks.forgotPassword } }))

import ForgotPasswordPage from './ForgotPasswordPage.vue'

function mountPage(): VueWrapper {
  return mount(ForgotPasswordPage, {
    global: {
      stubs: {
        AppBrand: true,
        IconGlyph: true,
        LanguageSwitcher: true,
        AppToast: { props: ['message'], template: '<p data-testid="toast">{{ message }}</p>' },
      },
    },
  })
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

  it('checks the address before sending anything', async () => {
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue('not-an-email')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('#forgot-error').text()).toBe('Enter a valid email address.')
    expect(mocks.forgotPassword).not.toHaveBeenCalled()
  })

  it('sends the link, shows the inbox step and confirms "Send the link again" with a toast', async () => {
    const wrapper = mountPage()

    await wrapper.get('#forgot-email').setValue(' Mei@Example.com ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.forgotPassword).toHaveBeenCalledWith('mei@example.com')
    expect(wrapper.get('[data-testid="forgot-sent"]').text()).toContain('If an account exists for mei@example.com')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('')

    await wrapper.get('[data-testid="send-again"]').trigger('click')
    await flushPromises()

    expect(mocks.forgotPassword).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Link sent again. Check your inbox and spam folder.')
  })
})
