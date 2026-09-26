import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { storageKeys } from '@/utils/storage'
import { i18n } from '@/plugins/i18n'

const parentEmail = 'parent@example.com'
const spacedParentEmail = ' Parent@Example.com '

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  forgotPassword: vi.fn(),
  route: {
    query: {} as Record<string, string | string[] | null>,
    hash: '',
  },
}))

vi.mock('vue-router', () => ({
  RouterLink: {
    name: 'RouterLink',
    props: ['to'],
    template: '<a><slot /></a>',
  },
  useRoute: () => mocks.route,
  useRouter: () => ({
    replace: mocks.replace,
  }),
}))

vi.mock('@/api/services', () => ({
  authApi: {
    forgotPassword: (...args: unknown[]) => mocks.forgotPassword(...args),
  },
}))

import ForgotPasswordPage from './ForgotPasswordPage.vue'

function mountForgotPasswordPage(): ReturnType<typeof mount> {
  return mount(ForgotPasswordPage, {
    global: {
      plugins: [i18n],
      stubs: {
        AppBrand: true,
        IconGlyph: true,
        PandaIllustration: true,
        LanguageSwitcher: true,
      },
    },
  })
}

describe('ForgotPasswordPage recovery email hygiene', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    mocks.replace.mockReset()
    mocks.forgotPassword.mockReset()
    mocks.forgotPassword.mockResolvedValue(undefined)
    mocks.route.query = {}
    mocks.route.hash = ''
    window.sessionStorage.clear()
  })

  it('prefills recovery email from one-time session storage and immediately clears it', () => {
    window.sessionStorage.setItem(storageKeys.recoveryEmail, spacedParentEmail)

    const wrapper = mountForgotPasswordPage()

    expect(wrapper.get<HTMLInputElement>('#forgot-email').element.value).toBe('Parent@Example.com')
    expect(window.sessionStorage.getItem(storageKeys.recoveryEmail)).toBeNull()
    expect(mocks.replace).not.toHaveBeenCalled()
  })

  it('prefills legacy email query once and removes sensitive URL state from history', () => {
    mocks.route.query = {
      email: parentEmail,
      source: 'login',
      redirect: '/auth?token=nested-token',
    }

    const wrapper = mountForgotPasswordPage()

    expect(wrapper.get<HTMLInputElement>('#forgot-email').element.value).toBe(parentEmail)
    expect(mocks.replace).toHaveBeenCalledWith({
      name: 'forgot-password',
      query: { source: 'login' },
      hash: '',
    })
  })

  it('submits a normalized email without persisting it after success', async () => {
    const wrapper = mountForgotPasswordPage()

    await wrapper.get('#forgot-email').setValue(spacedParentEmail)
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(mocks.forgotPassword).toHaveBeenCalledWith(parentEmail)
    expect(window.sessionStorage.getItem(storageKeys.recoveryEmail)).toBeNull()
    expect(wrapper.text()).toContain(`If ${parentEmail} is registered`)
  })
})
