import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'
import { reactive } from 'vue'

import type { UserRole } from '@/types/api'
import { setAccessToken } from '@/api/http'
import { i18n } from '@/plugins/i18n'
import { deferred } from '@/test/helpers'

const mocks = vi.hoisted(() => ({
  confirmEmail: vi.fn(),
  confirmSignup: vi.fn(),
  clearSessionInAllTabs: vi.fn(),
  hasLiveSession: vi.fn(),
  logout: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  currentRoute: { value: { meta: {} as { roles?: UserRole[] } } },
  route: { query: {} as Record<string, string> },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push, replace: mocks.replace, currentRoute: mocks.currentRoute }),
}))

vi.mock('@/api/services', () => ({
  authApi: { confirmEmail: mocks.confirmEmail, confirmSignupEmail: mocks.confirmSignup },
}))

const authStore = reactive({
  activeRole: null as UserRole | null,
  isAuthenticated: false,
  clearSessionInAllTabs: mocks.clearSessionInAllTabs,
  hasLiveSession: mocks.hasLiveSession,
  logout: mocks.logout,
})
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))

import ConfirmEmailPage from './ConfirmEmailPage.vue'

async function mountPage(): Promise<VueWrapper> {
  const wrapper = mount(ConfirmEmailPage, { global: { stubs: { IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  mocks.confirmEmail.mockReset()
  mocks.confirmSignup.mockReset()
  mocks.clearSessionInAllTabs.mockReset()
  mocks.clearSessionInAllTabs.mockImplementation(() => {
    authStore.isAuthenticated = false
    authStore.activeRole = null
  })
  mocks.hasLiveSession.mockReset()
  mocks.logout.mockReset()
  mocks.logout.mockImplementation(async () => {
    authStore.isAuthenticated = false
    authStore.activeRole = null
    return true
  })
  mocks.push.mockReset()
  mocks.replace.mockReset()
  mocks.replace.mockResolvedValue(undefined)
  mocks.push.mockResolvedValue(undefined)
  mocks.currentRoute.value.meta = {}
  authStore.activeRole = null
  authStore.isAuthenticated = false
  mocks.route.query = {}
})

describe('ConfirmEmailPage (FRAUTH08)', () => {
  it('confirms the new address once, signs this browser out and links to sign in', async () => {
    mocks.route.query = { token: 'confirm-token' }
    authStore.activeRole = 'STUDENT'
    authStore.isAuthenticated = true
    // The server ended the changed account's sessions: this browser's no longer refreshes.
    mocks.hasLiveSession.mockResolvedValue(false)
    mocks.confirmEmail.mockResolvedValue({ email: 'new@example.com' })

    const wrapper = await mountPage()

    expect(mocks.confirmEmail).toHaveBeenCalledOnce()
    expect(mocks.confirmEmail).toHaveBeenCalledWith('confirm-token')
    // The token leaves the address bar.
    expect(mocks.replace).toHaveBeenCalledWith({ name: 'confirm-email', query: {} })
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain(
      'Your email address is now new@example.com.',
    )
    expect(mocks.clearSessionInAllTabs).toHaveBeenCalledOnce()
    expect(wrapper.get('a').attributes('href')).toBe(JSON.stringify({ name: 'auth', query: { email: 'changed' } }))
  })

  it('keeps another account signed in when the confirmed link belongs to a different account', async () => {
    mocks.route.query = { token: 'confirm-token' }
    authStore.activeRole = 'ADMIN'
    authStore.isAuthenticated = true
    const check = deferred<boolean>()
    mocks.hasLiveSession.mockReturnValue(check.promise)
    mocks.confirmEmail.mockResolvedValue({ email: 'new@example.com' })

    const wrapper = await mountPage()
    // Confirmation stays on checking until the session check completes.
    expect(wrapper.find('[data-testid="confirm-email-done"]').exists()).toBe(false)

    check.resolve(true)
    await flushPromises()

    expect(mocks.hasLiveSession).toHaveBeenCalledOnce()
    expect(mocks.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain('new@example.com')
    expect(wrapper.get('a').attributes('href')).toBe('/admin/dashboard')
  })

  it('shows the server reason and the Account page for a signed-in user when the link fails', async () => {
    mocks.route.query = { token: 'used-token' }
    authStore.activeRole = 'ADMIN'
    authStore.isAuthenticated = true
    mocks.confirmEmail.mockRejectedValue({
      response: { status: 400, data: { message: 'This link was already used.' } },
    })

    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="confirm-email-error"]').text()).toBe('This link was already used.')
    expect(wrapper.get('a').attributes('href')).toBe('/admin/account')
    expect(mocks.clearSessionInAllTabs).not.toHaveBeenCalled()
  })

  it('keeps the confirmed address and clears the unconfirmed session only when the visitor chooses Sign in', async () => {
    mocks.route.query = { token: 'confirm-token' }
    authStore.activeRole = 'STUDENT'
    authStore.isAuthenticated = true
    mocks.confirmEmail.mockResolvedValue({ email: 'new@example.com' })
    mocks.hasLiveSession.mockRejectedValue(new Error('offline'))

    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain('new@example.com')
    expect(mocks.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(wrapper.find('a').exists()).toBe(false)

    await wrapper.get('[data-testid="confirm-email-sign-in"]').trigger('click')
    expect(mocks.clearSessionInAllTabs).toHaveBeenCalledOnce()
    expect(mocks.push).toHaveBeenCalledWith({ name: 'auth', query: { email: 'changed' } })
  })

  it('keeps a new login when the original session check finishes later', async () => {
    const check = deferred<boolean>()
    mocks.route.query = { token: 'confirm-token' }
    authStore.isAuthenticated = true
    mocks.confirmEmail.mockResolvedValue({ email: 'new@example.com' })
    mocks.hasLiveSession.mockReturnValue(check.promise)
    await mountPage()

    setAccessToken('new-login')
    onTestFinished(() => setAccessToken(null))
    check.resolve(false)
    await flushPromises()
    expect(mocks.clearSessionInAllTabs).not.toHaveBeenCalled()
  })

  it('does not call the server without a token', async () => {
    const wrapper = await mountPage()

    expect(mocks.confirmEmail).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="confirm-email-error"]').text()).toContain('Open the confirmation link')
    expect(wrapper.get('a').attributes('href')).toBe('/auth')

    const previous = i18n.global.locale.value
    try {
      i18n.global.locale.value = 'zh-CN'
      await wrapper.vm.$nextTick()
      expect(wrapper.get('[data-testid="confirm-email-error"]').text()).toBe('请打开我们发送到你新邮箱的确认链接。')
    } finally {
      i18n.global.locale.value = previous
    }
  })

  it('confirms a new account’s address without signing anyone out', async () => {
    mocks.route.query = { token: 'signup-token', kind: 'signup' }
    authStore.isAuthenticated = true
    authStore.activeRole = 'STUDENT'
    mocks.hasLiveSession.mockResolvedValue(false)
    mocks.confirmSignup.mockResolvedValue({ email: 'new@example.com' })

    const wrapper = await mountPage()

    expect(mocks.confirmSignup).toHaveBeenCalledWith('signup-token')
    expect(mocks.confirmEmail).not.toHaveBeenCalled()
    expect(mocks.replace).toHaveBeenCalledWith({ name: 'confirm-email', query: { kind: 'signup' } })
    expect(wrapper.get('h1').text()).toBe('Email confirmed')
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain('new@example.com is confirmed')
    expect(wrapper.get('a').attributes('href')).toBe('/student/home')
    expect(mocks.hasLiveSession).not.toHaveBeenCalled()
    expect(mocks.clearSessionInAllTabs).not.toHaveBeenCalled()
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

  it('offers a retry for transient errors without losing the stripped token', async () => {
    mocks.route.query = { token: 'retry-token' }
    mocks.confirmEmail
      .mockRejectedValueOnce({ response: { status: 503, data: {} } })
      .mockResolvedValueOnce({ email: 'new@example.com' })

    const wrapper = await mountPage()
    await wrapper.get('[data-testid="confirm-email-retry"]').trigger('click')
    await flushPromises()

    expect(mocks.confirmEmail).toHaveBeenCalledTimes(2)
    expect(mocks.confirmEmail).toHaveBeenLastCalledWith('retry-token')
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain('new@example.com')
  })

  it('ignores rapid double-clicks on Retry so the single-use token is not sent twice', async () => {
    mocks.route.query = { token: 'retry-once-token' }
    const retryRequest = deferred<{ email: string }>()
    mocks.confirmEmail
      .mockRejectedValueOnce({ response: { status: 503, data: {} } })
      .mockReturnValueOnce(retryRequest.promise)

    const wrapper = await mountPage()
    const retry = wrapper.get<HTMLButtonElement>('[data-testid="confirm-email-retry"]').element
    retry.click()
    retry.click()
    retryRequest.resolve({ email: 'new@example.com' })
    await flushPromises()

    expect(mocks.confirmEmail).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="confirm-email-done"]').text()).toContain('new@example.com')
  })

  it('signs out a signed-in admin before opening sign-in on a failed signup confirmation link', async () => {
    mocks.route.query = { token: 'old-token', kind: 'signup' }
    authStore.isAuthenticated = true
    authStore.activeRole = 'ADMIN'
    mocks.confirmSignup.mockRejectedValue({ response: { status: 400, data: { message: 'Link expired.' } } })

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Sign in and send a new link from Home.')
    expect(wrapper.find('a').exists()).toBe(false)
    await wrapper.get('[data-testid="confirm-email-switch-account"]').trigger('click')
    await flushPromises()
    expect(mocks.logout).toHaveBeenCalledOnce()
    expect(mocks.push).toHaveBeenCalledWith('/auth')
  })

  it('redirects away from a protected route if email confirmation clears the session after leaving the page', async () => {
    const check = deferred<boolean>()
    mocks.route.query = { token: 'confirm-token' }
    authStore.isAuthenticated = true
    authStore.activeRole = 'STUDENT'
    mocks.confirmEmail.mockResolvedValue({ email: 'new@example.com' })
    mocks.hasLiveSession.mockReturnValue(check.promise)

    const wrapper = await mountPage()
    mocks.replace.mockClear()
    mocks.currentRoute.value.meta = { roles: ['STUDENT'] }
    wrapper.unmount()

    check.resolve(false)
    await flushPromises()

    expect(mocks.clearSessionInAllTabs).toHaveBeenCalledOnce()
    expect(mocks.replace).toHaveBeenCalledExactlyOnceWith({ name: 'auth', query: { email: 'changed' } })
  })
})
