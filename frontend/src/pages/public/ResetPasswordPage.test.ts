import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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

function mountResetPasswordPage() {
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
      token: 'raw-reset-token',
      tab: 'account',
      email: 'parent@example.com',
      redirect: '/auth?token=nested-token',
    }

    const wrapper = mountResetPasswordPage()

    expect(wrapper.get<HTMLInputElement>('#reset-token').element.value).toBe('raw-reset-token')
    expect(mocks.replace).toHaveBeenCalledWith({
      name: 'reset-password',
      query: { tab: 'account' },
      hash: '',
    })
  })

  it('removes sensitive hash fragments without dropping safe query context', () => {
    mocks.route.query = { tab: 'account' }
    mocks.route.hash = '#token=raw-reset-token'

    mountResetPasswordPage()

    expect(mocks.replace).toHaveBeenCalledWith({
      name: 'reset-password',
      query: { tab: 'account' },
      hash: '',
    })
  })

  it('submits the one-time token without persisting it after success', async () => {
    mocks.route.query = { token: 'raw-reset-token' }
    const wrapper = mountResetPasswordPage()

    await wrapper.get('#reset-password').setValue('Password123')
    await wrapper.get('#reset-confirm-password').setValue('Password123')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(mocks.resetPassword).toHaveBeenCalledWith({
      token: 'raw-reset-token',
      password: 'Password123',
      confirmPassword: 'Password123',
    })
    expect(wrapper.get<HTMLInputElement>('#reset-token').element.value).toBe('')
    expect(wrapper.text()).toContain('Password reset successfully')
  })
})
