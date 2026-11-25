import { CanceledError } from 'axios'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { i18n } from '@/plugins/i18n'
import { deferred } from '@/test/helpers'

const { auth, route, router, authApi } = vi.hoisted(() => ({
  authApi: {
    checkResetLink: vi.fn(),
    checkAdminInvite: vi.fn(),
    acceptAdminInvite: vi.fn(),
    resetPassword: vi.fn(),
  },
  router: {
    replace: vi.fn(),
    push: vi.fn(),
    currentRoute: { value: { meta: {} as { roles?: string[] } } },
  },
  route: { query: {} as Record<string, string> },
  auth: { clearSessionInAllTabs: vi.fn(), logout: vi.fn(), currentUser: null as { email: string } | null },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('@/api/services', () => ({ authApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

import ResetPasswordPage from './ResetPasswordPage.vue'

const PASSWORD = 'New-spark-2026'
const INVALID_RESET_LINK = { response: { status: 400, data: { message: 'Reset token is invalid.' } } }

function validResetLink(): void {
  route.query = { token: 'reset-token' }
  authApi.checkResetLink.mockResolvedValue({ email: 'mei@example.com' })
}

async function mountPage(): Promise<VueWrapper> {
  const wrapper = mount(ResetPasswordPage, {
    global: { stubs: { AppBrand: true, IconGlyph: true, LanguageSwitcher: true } },
  })
  await flushPromises()
  return wrapper
}

async function choosePassword(wrapper: VueWrapper, confirm = PASSWORD): Promise<void> {
  await wrapper.get('#reset-password').setValue(PASSWORD)
  await wrapper.get('#reset-confirm').setValue(confirm)
}

async function submitForm(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  for (const mock of [
    ...Object.values(authApi),
    router.replace,
    router.push,
    auth.clearSessionInAllTabs,
    auth.logout,
  ]) {
    mock.mockReset()
  }
  router.replace.mockResolvedValue(undefined)
  router.push.mockResolvedValue(undefined)
  router.currentRoute.value.meta = {}
  route.query = {}
  auth.currentUser = null
})

describe('ResetPasswordPage password reset (FRAUTH05)', () => {
  it('checks the link, names the account and takes the token out of the address bar', async () => {
    validResetLink()

    const wrapper = await mountPage()

    expect(authApi.checkResetLink).toHaveBeenCalledWith('reset-token')
    expect(router.replace).toHaveBeenCalledWith({ name: 'reset-password', query: {} })
    expect(wrapper.get('[data-testid="link-email"]').text()).toBe('For mei@example.com')
  })

  it('lets both new-password fields show what is typed', async () => {
    validResetLink()
    const wrapper = await mountPage()

    for (const id of ['reset-password', 'reset-confirm']) {
      await wrapper.get(`#${id} + button`).trigger('click')
      expect(wrapper.get(`#${id}`).attributes('type'), id).toBe('text')
    }
  })

  it('enables the button only once the passwords are valid and match', async () => {
    validResetLink()
    const wrapper = await mountPage()
    const button = () => wrapper.get('button[type="submit"]')
    expect(button().attributes('disabled')).toBeDefined()

    await choosePassword(wrapper, 'Different-2026')
    expect(button().attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.auth-checks li.is-met')).toHaveLength(1)
    expect(wrapper.get('#reset-readiness').attributes('aria-live')).toBe('polite')
    expect(wrapper.findAll('#reset-readiness .sr-only').map((status) => status.text())).toEqual([
      '— Met',
      '— Not yet met',
    ])

    await wrapper.get('#reset-confirm').setValue(PASSWORD)
    expect(button().attributes('disabled')).toBeUndefined()
    expect(wrapper.findAll('.auth-checks li.is-met')).toHaveLength(2)
    expect(wrapper.findAll('#reset-readiness .sr-only').map((status) => status.text())).toEqual(['— Met', '— Met'])
  })

  it.each([
    ['ASCII characters', 'x'.repeat(80)],
    ['Chinese characters', '学'.repeat(25)],
  ])('explains the UTF-8 byte limit for %s and keeps the button off', async (_label, password) => {
    validResetLink()
    const wrapper = await mountPage()

    await wrapper.get('#reset-password').setValue(password)
    await wrapper.get('#reset-confirm').setValue(password)

    expect(wrapper.get('#reset-too-long').text()).toBe(
      'Use a shorter password (up to 72 characters in English, fewer for non-Latin scripts).',
    )
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('resets the password and opens sign in with the "password updated" notice', async () => {
    validResetLink()
    authApi.resetPassword.mockResolvedValue(undefined)
    const wrapper = await mountPage()

    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(authApi.resetPassword).toHaveBeenCalledWith({
      token: 'reset-token',
      password: PASSWORD,
      confirmPassword: PASSWORD,
    })
    expect(router.push).toHaveBeenCalledWith({ name: 'auth', query: { reset: 'done' } })
    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('ends this browser session when the reset account is the one signed in here', async () => {
    // The server already signed the reset account out everywhere: only the session in this browser is left.
    auth.currentUser = { email: 'Mei@Example.com' }
    validResetLink()
    authApi.resetPassword.mockResolvedValue(undefined)
    const wrapper = await mountPage()

    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(auth.clearSessionInAllTabs).toHaveBeenCalledTimes(1)
    expect(auth.logout).not.toHaveBeenCalled()
    expect(auth.clearSessionInAllTabs.mock.invocationCallOrder[0]).toBeLessThan(router.push.mock.invocationCallOrder[0])
    expect(router.push).toHaveBeenCalledWith({ name: 'auth', query: { reset: 'done' } })
  })

  it('signs out another account signed in here, so sign in opens with the "password updated" notice', async () => {
    // Signed in, that account would be sent from the sign-in page to its own home, without the notice.
    auth.currentUser = { email: 'admin@example.com' }
    validResetLink()
    authApi.resetPassword.mockResolvedValue(undefined)
    auth.logout.mockResolvedValue(true)
    const wrapper = await mountPage()

    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(auth.logout).toHaveBeenCalledTimes(1)
    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(auth.logout.mock.invocationCallOrder[0]).toBeLessThan(router.push.mock.invocationCallOrder[0])
    expect(router.push).toHaveBeenCalledWith({ name: 'auth', query: { reset: 'done' } })
  })

  it('keeps the session and shows the error alert when the reset fails', async () => {
    auth.currentUser = { email: 'admin@example.com' }
    validResetLink()
    authApi.resetPassword.mockRejectedValue(INVALID_RESET_LINK)
    const wrapper = await mountPage()

    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(wrapper.get('.callout.is-danger[role="alert"]').text()).toContain('Reset token is invalid.')
    expect(auth.logout).not.toHaveBeenCalled()
    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })

  it('preserves a session opened while the reset request was in flight, even on CanceledError', async () => {
    validResetLink()
    onTestFinished(() => setAccessToken(null))
    authApi.resetPassword.mockImplementationOnce(async () => {
      setAccessToken('later-session')
      auth.currentUser = { email: 'another@example.com' }
      throw new CanceledError('The sign-in session has ended.')
    })
    const wrapper = await mountPage()
    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(authApi.resetPassword).toHaveBeenCalledOnce()
    expect(wrapper.find('.callout.is-danger[role="alert"]').exists()).toBe(false)
    expect(auth.logout).not.toHaveBeenCalled()
    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })

  it('keeps another account signed in when the visitor leaves before the reset finishes', async () => {
    validResetLink()
    auth.currentUser = { email: 'another@example.com' }
    const result = deferred<void>()
    authApi.resetPassword.mockReturnValueOnce(result.promise)
    const wrapper = await mountPage()
    await choosePassword(wrapper)
    await wrapper.get('form').trigger('submit')
    wrapper.unmount()
    result.resolve()
    await flushPromises()

    expect(auth.logout).not.toHaveBeenCalled()
    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })

  it('preserves a newer session when the reset resolves after a session change without CanceledError', async () => {
    validResetLink()
    onTestFinished(() => setAccessToken(null))
    const result = deferred<void>()
    auth.currentUser = { email: 'mei@example.com' }
    authApi.resetPassword.mockReturnValueOnce(result.promise)
    const wrapper = await mountPage()
    await choosePassword(wrapper)
    await wrapper.get('form').trigger('submit')

    setAccessToken('later-session')
    result.resolve()
    await flushPromises()

    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(auth.logout).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })

  it('redirects away from a protected route if same-account reset finishes after leaving the page', async () => {
    validResetLink()
    auth.currentUser = { email: 'mei@example.com' }
    const result = deferred<void>()
    authApi.resetPassword.mockReturnValueOnce(result.promise)
    const wrapper = await mountPage()
    await choosePassword(wrapper)
    await wrapper.get('form').trigger('submit')
    router.replace.mockClear()
    router.currentRoute.value.meta = { roles: ['STUDENT'] }
    wrapper.unmount()

    result.resolve()
    await flushPromises()

    expect(auth.clearSessionInAllTabs).toHaveBeenCalledOnce()
    expect(router.replace).toHaveBeenCalledExactlyOnceWith({ name: 'auth', query: { reset: 'done' } })
  })

  it('offers a new link when the link was used or expired', async () => {
    route.query = { token: 'old-token' }
    authApi.checkResetLink.mockRejectedValue(INVALID_RESET_LINK)

    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('This reset link no longer works')
    expect(wrapper.get('[data-testid="link-invalid"]').text()).toBe(
      'The link was already used or is more than 60 minutes old.',
    )
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('Request a new link')
  })

  it('says why the link no longer works in the language the visitor switches to', async () => {
    route.query = { token: 'old-token' }
    authApi.checkResetLink.mockRejectedValue(INVALID_RESET_LINK)
    const wrapper = await mountPage()

    i18n.global.locale.value = 'ms'
    await flushPromises()

    expect(wrapper.get('[data-testid="link-invalid"]').text()).toBe(
      'Pautan ini telah digunakan atau berusia lebih daripada 60 minit.',
    )
  })

  it('does not call the server without a token', async () => {
    const wrapper = await mountPage()

    expect(authApi.checkResetLink).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="link-invalid"]').text()).toContain('Open the reset link from your email')
  })
})

describe('ResetPasswordPage admin invite (FRADM01)', () => {
  beforeEach(() => {
    route.query = { invite: 'invite-token' }
    authApi.checkAdminInvite.mockResolvedValue({ email: 'admin@example.com' })
  })

  it.each([
    ['the connection fails', { request: {} }],
    ['the server is busy', { response: { status: 503, data: {} } }],
  ])('does not call a link dead when %s while checking it, and checks again on request', async (_case, failure) => {
    authApi.checkAdminInvite.mockRejectedValueOnce(failure).mockResolvedValueOnce({ email: 'admin@example.com' })

    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('We couldn’t check your link')
    expect(wrapper.text()).not.toContain('Ask an admin to send a new invite.')
    await wrapper.get('[data-testid="link-retry"]').trigger('click')
    await flushPromises()

    expect(authApi.checkAdminInvite).toHaveBeenCalledTimes(2)
    expect(wrapper.get('h1').text()).toBe('Set up your admin account')
  })

  it('creates the admin account with a name and password, then opens sign in', async () => {
    authApi.acceptAdminInvite.mockResolvedValue(undefined)
    const wrapper = await mountPage()
    expect(authApi.checkAdminInvite).toHaveBeenCalledWith('invite-token')
    expect(router.replace).toHaveBeenCalledWith({ name: 'reset-password', query: {} })
    expect(wrapper.get('h1').text()).toBe('Set up your admin account')

    await choosePassword(wrapper)
    await submitForm(wrapper)
    expect(wrapper.get('#invite-name-error').text()).toBe('Enter your full name.')
    expect(wrapper.get('#invite-full-name').attributes('aria-invalid')).toBe('true')
    expect(authApi.acceptAdminInvite).not.toHaveBeenCalled()
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(wrapper.get('#invite-name-error').text()).toBe('Masukkan nama penuh anda.')
    i18n.global.locale.value = 'en'

    await wrapper.get('#invite-full-name').setValue('  Aisyah Rahman ')
    // The message goes as soon as there is a name.
    expect(wrapper.find('#invite-name-error').exists()).toBe(false)
    expect(wrapper.get('#invite-full-name').attributes('aria-invalid')).toBe('false')
    await submitForm(wrapper)

    expect(authApi.acceptAdminInvite).toHaveBeenCalledWith({
      token: 'invite-token',
      fullName: 'Aisyah Rahman',
      password: PASSWORD,
      confirmPassword: PASSWORD,
      preferredLanguage: 'en',
    })
    expect(router.push).toHaveBeenCalledWith({ name: 'auth', query: { invite: 'done' } })
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('signs out another account signed in here before the new admin signs in', async () => {
    auth.currentUser = { email: 'student@example.com' }
    authApi.acceptAdminInvite.mockResolvedValue(undefined)
    auth.logout.mockResolvedValue(true)
    const wrapper = await mountPage()

    await wrapper.get('#invite-full-name').setValue('Aisyah Rahman')
    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(auth.logout).toHaveBeenCalledTimes(1)
    expect(auth.logout.mock.invocationCallOrder[0]).toBeLessThan(router.push.mock.invocationCallOrder[0])
    expect(router.push).toHaveBeenCalledWith({ name: 'auth', query: { invite: 'done' } })
  })

  it('gives the new admin account the language of the page, as a sign-up does', async () => {
    authApi.acceptAdminInvite.mockResolvedValue(undefined)
    i18n.global.locale.value = 'ms'
    const wrapper = await mountPage()

    await wrapper.get('#invite-full-name').setValue('Aisyah Rahman')
    await choosePassword(wrapper)
    await submitForm(wrapper)

    expect(authApi.acceptAdminInvite).toHaveBeenCalledWith(expect.objectContaining({ preferredLanguage: 'ms' }))
  })

  it('explains an expired invite without offering a password reset', async () => {
    authApi.checkAdminInvite.mockRejectedValue({ response: { status: 400, data: {} } })

    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('This invite link no longer works')
    expect(wrapper.get('[data-testid="link-invalid"]').text()).toContain('Ask an admin to send a new invite.')
    expect(wrapper.text()).not.toContain('Request a new link')
  })
})
