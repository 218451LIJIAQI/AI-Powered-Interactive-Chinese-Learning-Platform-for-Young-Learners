import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const rawResetToken = 'raw-reset-token'
const accountTab = 'account'
const newPassword = 'Password123'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  resetPassword: vi.fn(),
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
    resetPassword: (...args: unknown[]) => mocks.resetPassword(...args),
  },
}))

import ResetPasswordPage from './ResetPasswordPage.vue'

function mountResetPasswordPage(): ReturnType<typeof mount> {
  return mount(ResetPasswordPage, {
    global: {
      stubs: {
        AppBrand: true,
        IconGlyph: true,
        PandaIllustration: true,
      },
    },
  })
}

describe('ResetPasswordPage token hygiene', () => {
  beforeEach(() => {
    mocks.replace.mockReset()
    mocks.resetPassword.mockReset()
    mocks.resetPassword.mockResolvedValue(undefined)
    mocks.route.query = {}
    mocks.route.hash = ''
  })

  it('prefills the token once and removes sensitive query values from router history', () => {
    mocks.route.query = {
      token: rawResetToken,
      tab: accountTab,
      email: 'parent@example.com',
      redirect: '/auth?token=nested-token',
    }

    const wrapper = mountResetPasswordPage()

    expect(wrapper.get<HTMLInputElement>('#reset-token').element.value).toBe(rawResetToken)
    expect(mocks.replace).toHaveBeenCalledWith({
      name: 'reset-password',
      query: { tab: accountTab },
      hash: '',
    })
  })

  it('removes sensitive hash fragments without dropping safe query context', () => {
    mocks.route.query = { tab: accountTab }
    mocks.route.hash = `#token=${rawResetToken}`

    mountResetPasswordPage()

    expect(mocks.replace).toHaveBeenCalledWith({
      name: 'reset-password',
      query: { tab: accountTab },
      hash: '',
    })
  })

  it('submits the one-time token without persisting it after success', async () => {
    mocks.route.query = { token: rawResetToken }
    const wrapper = mountResetPasswordPage()

    await wrapper.get('#reset-password').setValue(newPassword)
    await wrapper.get('#reset-confirm-password').setValue(newPassword)
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(mocks.resetPassword).toHaveBeenCalledWith({
      token: rawResetToken,
      password: newPassword,
      confirmPassword: newPassword,
    })
    expect(wrapper.get<HTMLInputElement>('#reset-token').element.value).toBe('')
    expect(wrapper.text()).toContain('Password reset successfully')
  })

  it('keeps the ready strength meter on a styled state when every check passes', async () => {
    mocks.route.query = { token: rawResetToken }
    const wrapper = mountResetPasswordPage()

    await wrapper.get('#reset-password').setValue(newPassword)
    await wrapper.get('#reset-confirm-password').setValue(newPassword)

    const meter = wrapper.get('.auth-reset-strength-track span')
    expect(meter.classes()).toContain('is-3')
    expect(meter.classes()).not.toContain('is-4')
    expect(meter.attributes('style')).toContain('width: 100%')
  })
})
