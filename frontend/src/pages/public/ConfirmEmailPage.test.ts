import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  confirmEmail: vi.fn(),
  confirmSignup: vi.fn(),
  clearSession: vi.fn(),
  replace: vi.fn(),
  auth: { isAuthenticated: false, activeRole: null as string | null },
  route: { path: '/confirm-email', query: {} as Record<string, string>, hash: '' },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkHrefStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))

vi.mock('@/api/ops', () => ({ publicOpsApi: { confirmEmail: mocks.confirmEmail } }))
vi.mock('@/api/plan', () => ({ emailConfirmationApi: { confirm: mocks.confirmSignup } }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ ...mocks.auth, clearSession: mocks.clearSession }),
}))

import ConfirmEmailPage from './ConfirmEmailPage.vue'

async function mountPage(): Promise<VueWrapper> {
  const wrapper = mount(ConfirmEmailPage, { global: { stubs: { IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  mocks.confirmEmail.mockReset()
  mocks.confirmSignup.mockReset()
  mocks.clearSession.mockReset()
  mocks.replace.mockReset()
  mocks.auth.isAuthenticated = false
  mocks.auth.activeRole = null
  mocks.route.query = {}
})

describe('ConfirmEmailPage (FRAUTH08)', () => {
  it('confirms the new address once, signs this browser out and links to sign in', async () => {
    mocks.route.query = { token: 'confirm-token' }
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'STUDENT'
    mocks.confirmEmail.mockResolvedValue({ email: 'new@example.com' })

    const wrapper = await mountPage()

    expect(mocks.confirmEmail).toHaveBeenCalledOnce()
    expect(mocks.confirmEmail).toHaveBeenCalledWith('confirm-token')
    // The token leaves the address bar.
    expect(mocks.replace).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain(
      'Your email address is now new@example.com.',
    )
    expect(mocks.clearSession).toHaveBeenCalledOnce()
    expect(wrapper.get('a').attributes('href')).toBe(JSON.stringify({ name: 'auth', query: { email: 'changed' } }))
  })

  it('shows the server reason and the Account page for a signed-in user when the link fails', async () => {
    mocks.route.query = { token: 'used-token' }
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'TEACHER'
    mocks.confirmEmail.mockRejectedValue({
      response: { status: 400, data: { message: 'This link was already used.' } },
    })

    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="confirm-email-error"]').text()).toBe('This link was already used.')
    expect(wrapper.get('a').attributes('href')).toBe('/teacher/account')
    expect(mocks.clearSession).not.toHaveBeenCalled()
  })

  it('does not call the server without a token', async () => {
    const wrapper = await mountPage()

    expect(mocks.confirmEmail).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="confirm-email-error"]').text()).toContain('Open the confirmation link')
    expect(wrapper.get('a').attributes('href')).toBe('/auth')
  })

  it('confirms a new account’s address without signing anyone out', async () => {
    mocks.route.query = { token: 'signup-token', kind: 'signup' }
    mocks.auth.isAuthenticated = true
    mocks.auth.activeRole = 'STUDENT'
    mocks.confirmSignup.mockResolvedValue({ email: 'new@example.com', expiresAt: null })

    const wrapper = await mountPage()

    expect(mocks.confirmSignup).toHaveBeenCalledWith('signup-token')
    expect(mocks.confirmEmail).not.toHaveBeenCalled()
    expect(wrapper.get('h1').text()).toBe('Email confirmed')
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain('new@example.com is confirmed')
    expect(wrapper.get('a').attributes('href')).toBe('/student/home')
    expect(mocks.clearSession).not.toHaveBeenCalled()
  })

  it('sends a signed-out student whose sign-up link failed to sign in', async () => {
    mocks.route.query = { token: 'old-token', kind: 'signup' }
    mocks.confirmSignup.mockRejectedValue({
      response: { status: 400, data: { message: 'This confirmation link has expired. Send a new one from Home.' } },
    })

    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="confirm-email-error"]').text()).toContain('expired')
    expect(wrapper.text()).toContain('Sign in and send a new link from Home.')
    expect(wrapper.get('a').attributes('href')).toBe('/auth')
  })
})
