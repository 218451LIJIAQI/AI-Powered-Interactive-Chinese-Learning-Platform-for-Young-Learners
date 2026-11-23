import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CanceledError } from 'axios'

import { setAccessToken } from '@/api/http'
import { i18n } from '@/plugins/i18n'
import { deferred } from '@/test/helpers'
import { SERVER_MESSAGE } from '@/utils/serverMessages'
import { storageKeys } from '@/utils/storage'
import type { UserRole } from '@/types/api'

const { auth, route, router, registerStudent } = vi.hoisted(() => ({
  registerStudent: vi.fn(),
  auth: { login: vi.fn(), loading: false, roles: [] as UserRole[], endedSessionEmail: null as string | null },
  router: { replace: vi.fn(), push: vi.fn() },
  route: { path: '/auth', query: {} as Record<string, string> },
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('@/api/services', () => ({ authApi: { registerStudent } }))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

import AuthPage from './AuthPage.vue'

const PASSWORD = 'Spark-pass-2026'
const REGISTER_SUBMIT = '[data-testid="register-submit"]'
const EMAIL_TAKEN = { response: { status: 400, data: { message: SERVER_MESSAGE.EMAIL_ALREADY_REGISTERED } } }
/** How the server refuses a deactivated account whose password was right (AccountGuards). */
const DEACTIVATED = SERVER_MESSAGE.ACCOUNT_UNAVAILABLE

afterEach(() => vi.useRealTimers())

function mountPage(attachTo?: HTMLElement): VueWrapper {
  return mount(AuthPage, {
    attachTo,
    global: { stubs: { AppBrand: true, IconGlyph: true, LanguageSwitcher: true } },
  })
}

function apiError(status: number, extra: Record<string, unknown> = {}) {
  return { response: { status, data: {}, ...extra } }
}

async function signIn(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#login-email').setValue(' Mei@Example.com ')
  await wrapper.get('#login-password').setValue(PASSWORD)
  await wrapper.get('#auth-panel-signin form').trigger('submit')
  await flushPromises()
}

async function fillRegistration(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#register-full-name').setValue('  Tan Mei  ')
  await wrapper.get('#register-email').setValue('Mei@Example.com')
  await wrapper.get('#register-password').setValue(PASSWORD)
  await wrapper.get('#register-confirm-password').setValue(PASSWORD)
  await wrapper.get('[data-testid="register-consent"]').setValue(true)
}

/** The sign-in succeeds for an account of `role`. */
function signsInAs(role: UserRole): void {
  auth.login.mockImplementation(async () => {
    auth.roles = [role]
  })
}

async function submitRegistration(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#auth-panel-register form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  for (const mock of [auth.login, registerStudent, router.push, router.replace]) {
    mock.mockReset()
  }
  auth.loading = false
  auth.roles = []
  auth.endedSessionEmail = null
  route.query = {}
})

describe('AuthPage sign in (FRAUTH03)', () => {
  it('has one h1 for screen readers and shows 你好 beside the form, with pinyin and meaning', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('Welcome back')
    expect(wrapper.get('h1').classes()).toContain('sr-only')
    expect(wrapper.findAll('.auth-word-tile').map((tile) => tile.text())).toEqual(['你', '好'])
    expect(wrapper.get('.auth-word-gloss').text()).toBe('nǐ hǎo · hello')
  })

  it('moves between the sign-in and create-account tabs with the arrow keys', async () => {
    const wrapper = mountPage(document.body)
    const tablist = wrapper.get('[role="tablist"]')
    const signInPanel = wrapper.get('[role="tabpanel"][aria-labelledby="auth-tab-signin"]')
    const registerPanel = wrapper.get('[role="tabpanel"][aria-labelledby="auth-tab-register"]')

    await tablist.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.get('#auth-tab-register').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('#auth-tab-register').attributes('tabindex')).toBe('0')
    expect(registerPanel.isVisible()).toBe(true)
    expect(registerPanel.get('form').attributes('role')).toBeUndefined()
    expect(signInPanel.isVisible()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('#auth-tab-register').element)

    await tablist.trigger('keydown', { key: 'Home' })
    expect(wrapper.get('#auth-tab-signin').attributes('aria-selected')).toBe('true')
    expect(signInPanel.isVisible()).toBe(true)
    expect(registerPanel.isVisible()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('#auth-tab-signin').element)
  })

  it('identifies missing sign-in fields in order and clears local feedback as they are corrected', async () => {
    const wrapper = mountPage(document.body)
    await wrapper.get('#auth-panel-signin form').trigger('submit')
    expect(document.activeElement).toBe(wrapper.get('#login-email').element)
    expect(wrapper.get('#login-email').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('[data-testid="auth-error"]').text()).toBe('Enter a valid email address.')

    await wrapper.get('#login-email').setValue('mei@example.com')
    expect(wrapper.get('[data-testid="auth-error"]').text()).toBe('Enter a password.')
    await wrapper.get('#login-password').setValue(PASSWORD)
    expect(wrapper.find('[data-testid="auth-error"]').exists()).toBe(false)
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('shows and hides the password with the eye button, which keeps one name', async () => {
    const wrapper = mountPage()
    const toggle = wrapper.get('#login-password + button')
    expect(wrapper.get('#login-password').attributes('type')).toBe('password')
    expect(toggle.attributes('aria-label')).toBe('Show password')
    expect(toggle.attributes('aria-pressed')).toBe('false')
    expect(toggle.attributes('aria-controls')).toBe('login-password')

    await toggle.trigger('click')

    expect(wrapper.get('#login-password').attributes('type')).toBe('text')
    expect(toggle.attributes('aria-label')).toBe('Show password')
    expect(toggle.attributes('aria-pressed')).toBe('true')
  })

  it('signs in with the trimmed lower-case email, "Keep me signed in" off by default, then opens the role home', async () => {
    signsInAs('ADMIN')
    const wrapper = mountPage()
    expect(wrapper.get<HTMLInputElement>('[data-testid="keep-signed-in"]').element.checked).toBe(false)

    await signIn(wrapper)

    expect(auth.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, false)
    // Learners and admins sign in on the same page; each lands on their own home.
    expect(router.push).toHaveBeenCalledWith('/admin/dashboard')
  })

  it('returns to a safe redirect after signing in, and passes a ticked "Keep me signed in"', async () => {
    route.query = { redirect: '/student/ai' }
    signsInAs('STUDENT')
    const wrapper = mountPage()
    await wrapper.get('[data-testid="keep-signed-in"]').setValue(true)

    await signIn(wrapper)

    expect(auth.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, true)
    expect(router.push).toHaveBeenCalledWith('/student/ai')
  })

  it.each([
    // "Sign in to join" on a public class page leads to the learner's class page; an admin signs in there.
    ['/student/classes/7', 'ADMIN', '/admin/dashboard'],
    // The session of an admin ended on a shared computer; a learner signs in next.
    ['/admin/users', 'STUDENT', '/student/home'],
  ] as const)(
    'opens the role home, not the no-access page, when the redirect %s is no page for a %s',
    async (redirect, role, home) => {
      route.query = { redirect }
      signsInAs(role)
      const wrapper = mountPage()

      await signIn(wrapper)

      expect(router.push).toHaveBeenCalledWith(home)
    },
  )

  it('does not show an error or navigate for a superseded sign-in', async () => {
    auth.login.mockRejectedValue(new CanceledError('The sign-in session has ended.'))
    const wrapper = mountPage()
    await signIn(wrapper)
    expect(wrapper.find('[data-testid="auth-error"]').exists()).toBe(false)
    expect(router.push).not.toHaveBeenCalled()
  })

  it('says the email or password is incorrect, without saying which, and waits in the emptied password field', async () => {
    auth.login.mockRejectedValue(apiError(401))
    const wrapper = mountPage(document.body)

    await signIn(wrapper)

    expect(wrapper.get('[data-testid="auth-error"]').text()).toBe(
      'The email or password is incorrect. Try again or reset your password.',
    )
    expect(wrapper.get('#login-email').attributes('aria-describedby')).toBe('login-error')
    expect(wrapper.get<HTMLInputElement>('#login-email').element.value).toBe('Mei@Example.com')
    expect(wrapper.get<HTMLInputElement>('#login-password').element.value).toBe('')
    expect(document.activeElement).toBe(wrapper.get('#login-password').element)
    expect(router.push).not.toHaveBeenCalled()
  })

  it('pauses sign-in for the minutes the server gives in Retry-After, keeping the ways out open', async () => {
    auth.login.mockRejectedValue(apiError(429, { headers: { 'retry-after': '540' } }))
    const wrapper = mountPage()

    await signIn(wrapper)

    const locked = wrapper.get('[data-testid="auth-locked"]')
    expect(locked.text()).toBe('Too many failed attempts. Try again in 9 minutes or reset your password.')
    expect(locked.attributes('role')).toBe('alert')
    const button = wrapper.get('[data-testid="login-submit"]')
    expect(button.text()).toBe('Sign in')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-describedby')).toBe('login-locked')
    // Forgot password and Create account still work.
    expect(wrapper.get('a.auth-text-link').text()).toBe('Forgot password?')
    expect(wrapper.get('#auth-tab-register').attributes('disabled')).toBeUndefined()

    // Another address may be tried.
    await wrapper.get('#login-email').setValue('other@example.com')
    expect(wrapper.find('[data-testid="auth-locked"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps a sign-in pause, and the typed address, after a look at Create account', async () => {
    auth.login.mockRejectedValue(apiError(429, { headers: { 'retry-after': '540' } }))
    const wrapper = mountPage()
    await signIn(wrapper)

    await wrapper.get('#auth-tab-register').trigger('click')
    await wrapper.get('#auth-tab-signin').trigger('click')

    expect(wrapper.get('[data-testid="auth-locked"]').text()).toContain('Try again in 9 minutes')
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get<HTMLInputElement>('#login-email').element.value).toBe('Mei@Example.com')
  })

  it('drops the last sign-in error when the visitor looks at Create account', async () => {
    auth.login.mockRejectedValue(apiError(401))
    const wrapper = mountPage()
    await signIn(wrapper)
    expect(wrapper.find('[data-testid="auth-error"]').exists()).toBe(true)

    await wrapper.get('#auth-tab-register').trigger('click')
    await wrapper.get('#auth-tab-signin').trigger('click')

    expect(wrapper.find('[data-testid="auth-error"]').exists()).toBe(false)
  })

  it.each([
    ['without Retry-After', apiError(429), 'Try again in 15 minutes'],
    ['with a pause under a minute', apiError(429, { headers: { 'retry-after': '45' } }), 'Try again in 1 minute or'],
  ])('names the pause %s', async (_scenario, error, message) => {
    auth.login.mockRejectedValue(error)
    const wrapper = mountPage()

    await signIn(wrapper)

    expect(wrapper.get('[data-testid="auth-locked"]').text()).toContain(message)
  })

  it('counts down visible minutes and opens sign-in at the exact Retry-After deadline', async () => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] })
    auth.login.mockRejectedValue(apiError(429, { headers: { 'retry-after': '65' } }))
    const wrapper = mountPage()

    await signIn(wrapper)
    expect(wrapper.get('[data-testid="auth-locked"]').text()).toContain('Try again in 2 minutes')

    vi.advanceTimersByTime(5_000)
    await flushPromises()
    expect(wrapper.get('[data-testid="auth-locked"]').text()).toContain('Try again in 1 minute or')
    vi.advanceTimersByTime(59_999)
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeDefined()
    vi.advanceTimersByTime(1)
    await flushPromises()

    expect(wrapper.find('[data-testid="auth-locked"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
  })

  it.each([
    [apiError(429), 'auth-locked'],
    [apiError(403, { data: { message: DEACTIVATED } }), 'auth-deactivated'],
  ])('keeps an earlier address’s refusal off the new address (%s)', async (failure, banner) => {
    const attempt = deferred<void>()
    auth.login.mockReturnValueOnce(attempt.promise)
    const wrapper = mountPage()
    await signIn(wrapper)

    await wrapper.get('#login-email').setValue('other@example.com')
    await wrapper.get('#login-password').setValue('New-password')
    attempt.reject(failure)
    await flushPromises()

    expect(wrapper.find(`[data-testid="${banner}"]`).exists()).toBe(false)
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get<HTMLInputElement>('#login-password').element.value).toBe('New-password')
  })

  it('says a deactivated account cannot sign in, links to the Help page form and keeps Sign in off for that address', async () => {
    auth.login.mockRejectedValue(apiError(403, { data: { message: DEACTIVATED } }))
    const wrapper = mountPage(document.body)

    await signIn(wrapper)

    const banner = wrapper.get('[data-testid="auth-deactivated"]')
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.text().replace(/\s+/g, ' ')).toBe(
      'This account has been deactivated, so you can’t sign in. If you think this is a mistake, contact us.',
    )
    const contact = wrapper
      .findAllComponents({ name: 'RouterLink' })
      .find((link) => link.attributes('data-testid') === 'auth-deactivated-contact')!
    expect(contact.text()).toBe('contact us')
    // The Help page's Contact us form, which works without signing in.
    expect(contact.props('to')).toBe('/help#contact')
    const button = wrapper.get('[data-testid="login-submit"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-describedby')).toBe('login-deactivated')
    expect(wrapper.get('#login-email').attributes('aria-describedby')).toBe('login-deactivated')
    expect(wrapper.find('[data-testid="auth-error"]').exists()).toBe(false)
    expect(wrapper.get<HTMLInputElement>('#login-password').element.value).toBe('')
    // Another address is the way on, so the cursor waits there.
    expect(document.activeElement).toBe(wrapper.get('#login-email').element)

    await wrapper.get('#auth-panel-signin form').trigger('submit')
    expect(auth.login).toHaveBeenCalledOnce()

    await wrapper.get('#login-email').setValue('other@example.com')
    expect(wrapper.find('[data-testid="auth-deactivated"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('says the deactivated account in the language the visitor switches to', async () => {
    auth.login.mockRejectedValue(apiError(403, { data: { message: DEACTIVATED } }))
    const wrapper = mountPage()
    await signIn(wrapper)

    i18n.global.locale.value = 'zh-CN'
    await flushPromises()

    expect(wrapper.get('[data-testid="auth-deactivated"]').text().replace(/\s+/g, '')).toBe(
      '此账号已被停用，无法登录。如果你认为这是个错误，请联系我们。',
    )
  })

  it('shows another refusal (403) as it comes, without blocking Sign in', async () => {
    auth.login.mockRejectedValue(apiError(403))
    const wrapper = mountPage()

    await signIn(wrapper)

    expect(wrapper.find('[data-testid="auth-deactivated"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="auth-error"]').text()).toBe('You do not have permission to do that.')
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
  })

  it.each([
    ['reset', 'done', 'Password updated. Sign in with your new password.'],
    ['email', 'changed', 'Email address changed. Sign in with your new address.'],
    ['invite', 'done', 'Admin account ready. Sign in with your email and new password.'],
  ])('shows the ?%s=%s notice in one line', async (key, value, message) => {
    route.query = { [key]: value }
    const wrapper = mountPage()

    const notice = wrapper.get('[data-testid="auth-notice"]')
    expect(notice.text()).toBe(message)
    expect(notice.classes()).toContain('is-success')
  })

  it('shows no notice without a reason', () => {
    route.query = { redirect: '/student/ai' }

    expect(mountPage().find('[data-testid="auth-notice"]').exists()).toBe(false)
  })

  it('tells a user whose session ended to sign in again, then returns them to the page they were on', async () => {
    route.query = { redirect: '/student/ai', reason: 'expired' }
    signsInAs('STUDENT')
    const wrapper = mountPage()

    const notice = wrapper.get('[data-testid="auth-notice"]')
    expect(notice.text()).toBe('Your session ended. Sign in to continue — we’ll take you straight back.')
    expect(notice.attributes('role')).toBe('status')
    // Information, not a success message.
    expect(notice.classes()).not.toContain('is-success')

    await signIn(wrapper)

    expect(router.push).toHaveBeenCalledWith('/student/ai')
  })

  it('starts with the address of the account whose session ended', () => {
    route.query = { redirect: '/student/ai', reason: 'expired' }
    auth.endedSessionEmail = 'amira.hassan@example.com'

    expect(mountPage().get<HTMLInputElement>('#login-email').element.value).toBe('amira.hassan@example.com')
  })

  it.each<Record<string, string>>([{}, { reset: 'done' }, { invite: 'done' }])(
    'starts with an empty form otherwise (%o)',
    (query) => {
      route.query = query
      auth.endedSessionEmail = 'amira.hassan@example.com'

      const wrapper = mountPage()

      expect(wrapper.get<HTMLInputElement>('#login-email').element.value).toBe('')
      expect(wrapper.get<HTMLInputElement>('#login-password').element.value).toBe('')
    },
  )

  it('hands the typed address to Forgot password', async () => {
    const wrapper = mountPage()
    await wrapper.get('#login-email').setValue(' Mei@Example.com ')

    await wrapper.get('a.auth-text-link').trigger('click')

    expect(window.sessionStorage.getItem(storageKeys.recoveryEmail)).toBe('mei@example.com')
  })

  it('drops the session notice, but not the way back, when the visitor moves to Create account', async () => {
    route.query = { redirect: '/student/ai', reason: 'expired' }
    const wrapper = mountPage()

    await wrapper.get('#auth-tab-register').trigger('click')

    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/auth',
      query: { redirect: '/student/ai', tab: 'register' },
    })
  })
})

describe('AuthPage create account (FRAUTH01, FRAUTH02)', () => {
  beforeEach(() => {
    route.query = { tab: 'register' }
    registerStudent.mockResolvedValue({ email: 'mei@example.com' })
    auth.login.mockResolvedValue(undefined)
  })

  it('creates a learner account: name, email, two passwords and one box for age and terms, no account type', () => {
    const wrapper = mountPage()

    expect(wrapper.get('h1').text()).toBe('Create account')
    expect(wrapper.findAll('#auth-panel-register .field-label').map((label) => label.text())).toEqual([
      'Full name*',
      'Email address*',
      'Password*',
      'Confirm password*',
    ])
    expect(wrapper.find('input[type="radio"]').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/teacher|account type/i)
    // Both documents open in a new tab, which screen readers are told.
    expect(wrapper.get('.auth-consent').text().replace(/\s+/g, ' ')).toBe(
      'I am 18 or older and agree to the Terms of Service(opens in a new tab) and Privacy Notice(opens in a new tab).',
    )
    expect(wrapper.get('[data-testid="register-consent"]').attributes('required')).toBeDefined()
    expect(wrapper.find('#register-password + button[aria-label="Show password"]').exists()).toBe(true)
    expect(wrapper.find('#register-confirm-password + button[aria-label="Show password"]').exists()).toBe(true)
  })

  it('opens Create account from an older link that still names a role, and drops the role from the address', async () => {
    route.query = { tab: 'register', role: 'teacher' }
    const wrapper = mountPage()
    expect(wrapper.get('#auth-panel-register').isVisible()).toBe(true)
    expect(wrapper.get('#auth-tab-register').attributes('aria-selected')).toBe('true')

    await wrapper.get('#auth-tab-signin').trigger('click')
    await wrapper.get('#auth-tab-register').trigger('click')

    expect(router.replace).toHaveBeenLastCalledWith({ path: '/auth', query: { tab: 'register' } })
  })

  it('lists the problems in one line and sends nothing', async () => {
    const wrapper = mountPage(document.body)

    await submitRegistration(wrapper)

    const summary = wrapper.get('[data-testid="register-summary"]')
    expect(summary.text()).toBe('Your account was not created. Fix the highlighted fields.')
    expect(document.activeElement).toBe(summary.element)
    expect(wrapper.get('#register-full-name').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#register-consent-error').text()).toBe('Tick the box to create your account.')
    expect(registerStudent).not.toHaveBeenCalled()
  })

  it('checks a field when the visitor leaves it, and keeps Create account off until the error is fixed', async () => {
    const wrapper = mountPage()
    const email = wrapper.get('#register-email')
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeUndefined()

    // Not judged while the visitor is still typing.
    await email.setValue('mei@')
    expect(wrapper.find('#register-email-error').exists()).toBe(false)

    await email.trigger('blur')
    expect(wrapper.get('#register-email-error').text()).toBe('Enter a valid email address.')
    expect(email.attributes('aria-describedby')).toBe('register-email-error')
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeDefined()
    // "Your account was not created" belongs to a refused submit, not to leaving a field.
    expect(wrapper.find('[data-testid="register-summary"]').exists()).toBe(false)

    await email.setValue('mei@example.com')
    expect(wrapper.find('#register-email-error').exists()).toBe(false)
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeUndefined()
    expect(registerStudent).not.toHaveBeenCalled()
  })

  it('keeps Create account off after a refused submit until every highlighted field is fixed', async () => {
    const wrapper = mountPage()
    await submitRegistration(wrapper)
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeDefined()

    await wrapper.get('#register-full-name').setValue('Tan Mei')
    expect(wrapper.find('#register-full-name-error').exists()).toBe(false)
    expect(wrapper.find('[data-testid="register-summary"]').exists()).toBe(true)
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeDefined()

    await fillRegistration(wrapper)
    expect(wrapper.find('[data-testid="register-summary"]').exists()).toBe(false)
    expect(wrapper.findAll('.field-error')).toHaveLength(0)
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeUndefined()

    await submitRegistration(wrapper)
    expect(registerStudent).toHaveBeenCalledOnce()
  })

  it('names one highlighted field in the singular', async () => {
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await wrapper.get('[data-testid="register-consent"]').setValue(false)

    await submitRegistration(wrapper)

    expect(wrapper.get('[data-testid="register-summary"]').text()).toBe(
      'Your account was not created. Fix the highlighted field.',
    )
  })

  it('shows the field errors in the language the visitor switches to', async () => {
    registerStudent.mockRejectedValue(EMAIL_TAKEN)
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)
    await wrapper.get('#register-full-name').setValue('')
    await wrapper.get('#register-full-name').trigger('blur')

    i18n.global.locale.value = 'ms'
    await flushPromises()

    expect(wrapper.get('#register-full-name-error').text()).toBe('Masukkan nama penuh anda.')
    expect(wrapper.get('#register-email-error').text()).toBe(
      'E-mel ini sudah didaftarkan. Log masuk atau gunakan e-mel lain.',
    )
  })

  it('says a password of only spaces is missing, as the server would, not too short', async () => {
    const wrapper = mountPage()
    await wrapper.get('#register-password').setValue('         ')
    await wrapper.get('#register-password').trigger('blur')

    expect(wrapper.get('#register-password-error').text()).toBe('Enter a password.')
  })

  it('checks the confirmation again when the password it repeats changes', async () => {
    const wrapper = mountPage()
    await wrapper.get('#register-password').setValue(PASSWORD)
    await wrapper.get('#register-password').trigger('blur')
    // An empty confirmation is not judged before the visitor reaches it.
    expect(wrapper.find('#register-confirm-error').exists()).toBe(false)

    await wrapper.get('#register-confirm-password').setValue(PASSWORD)
    await wrapper.get('#register-confirm-password').trigger('blur')
    expect(wrapper.find('#register-confirm-error').exists()).toBe(false)

    await wrapper.get('#register-password').setValue(`${PASSWORD}-changed`)
    await wrapper.get('#register-password').trigger('blur')
    expect(wrapper.get('#register-confirm-error').text()).toBe('Passwords do not match.')
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeDefined()

    await wrapper.get('#register-password').setValue(PASSWORD)
    expect(wrapper.find('#register-confirm-error').exists()).toBe(false)
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeUndefined()
  })

  it('creates a learner in the interface language, signs in and opens the first-time setup', async () => {
    i18n.global.locale.value = 'ms'
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(registerStudent).toHaveBeenCalledWith({
      fullName: 'Tan Mei',
      email: 'mei@example.com',
      password: PASSWORD,
      confirmPassword: PASSWORD,
      preferredLanguage: 'ms',
    })
    // The automatic sign-in after sign-up keeps nothing after the browser closes (Keep me signed in defaults to off).
    expect(auth.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, false)
    expect(router.push).toHaveBeenCalledWith({ name: 'student-setup' })
  })

  it('takes a new learner back to the class they came to join (a public class page sends its redirect)', async () => {
    route.query = { tab: 'register', redirect: '/student/classes/7' }
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(router.push).toHaveBeenCalledWith('/student/classes/7')
  })

  it.each(['/admin/users', 'https://example.com/student/classes/7'])(
    'opens the setup when the redirect %s is not a learner page',
    async (redirect) => {
      route.query = { tab: 'register', redirect }
      const wrapper = mountPage()

      await fillRegistration(wrapper)
      await submitRegistration(wrapper)

      expect(router.push).toHaveBeenCalledWith({ name: 'student-setup' })
    },
  )

  it('marks the email field when the address is already registered, and keeps Create account off until it changes', async () => {
    registerStudent.mockRejectedValue(EMAIL_TAKEN)
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.get('#register-email-error').text()).toBe(
      'This email is already registered. Sign in or use another email.',
    )
    expect(wrapper.get('[data-testid="register-summary"]').text()).toBe(
      'Your account was not created. Fix the highlighted field.',
    )
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeDefined()
    expect(auth.login).not.toHaveBeenCalled()

    // Another address can be tried straight away…
    await wrapper.get('#register-email').setValue('mei.tan@example.com')
    expect(wrapper.find('#register-email-error').exists()).toBe(false)
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeUndefined()

    // …and the registered one is refused again without asking the server.
    await wrapper.get('#register-email').setValue(' MEI@example.com')
    await wrapper.get('#register-email').trigger('blur')
    expect(wrapper.get('#register-email-error').text()).toContain('This email is already registered.')
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeDefined()
    expect(registerStudent).toHaveBeenCalledOnce()
  })

  it('says when the account could not be created for another reason, and keeps the form', async () => {
    registerStudent.mockRejectedValue(apiError(500))
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.get('[data-testid="register-error"]').text()).toBe(
      'The learning service is having trouble right now. Please try again shortly.',
    )
    expect(wrapper.get<HTMLInputElement>('#register-full-name').element.value).toBe('  Tan Mei  ')
    expect(wrapper.get(REGISTER_SUBMIT).attributes('disabled')).toBeUndefined()
  })

  it('opens sign in with the address filled in when the automatic sign-in fails', async () => {
    auth.login.mockRejectedValue(apiError(500))
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.get('#auth-panel-signin').isVisible()).toBe(true)
    expect(wrapper.get('#auth-tab-signin').attributes('aria-selected')).toBe('true')
    expect(wrapper.get<HTMLInputElement>('#login-email').element.value).toBe('mei@example.com')
    expect(wrapper.get('[data-testid="auth-notice"]').text()).toBe('Your account is ready. Sign in to continue.')
  })

  it('does not replace the registration view when its automatic sign-in was superseded', async () => {
    auth.login.mockRejectedValue(new CanceledError('The sign-in session has ended.'))
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)
    expect(wrapper.get('#auth-panel-register').isVisible()).toBe(true)
    expect(wrapper.get('#auth-tab-register').attributes('aria-selected')).toBe('true')
    expect(wrapper.text()).not.toContain('Your account is ready. Sign in to continue.')
    expect(router.push).not.toHaveBeenCalled()
  })

  it('keeps a newer login when an earlier registration finishes', async () => {
    const registration = deferred<void>()
    registerStudent.mockReturnValue(registration.promise)
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)
    setAccessToken('newer-session')
    try {
      registration.resolve()
      await flushPromises()
      expect(auth.login).not.toHaveBeenCalled()
      expect(router.push).not.toHaveBeenCalled()
    } finally {
      setAccessToken(null)
    }
  })

  it('keeps what was typed after a look at Sign in', async () => {
    const wrapper = mountPage()
    await fillRegistration(wrapper)

    await wrapper.get('#auth-tab-signin').trigger('click')
    await wrapper.get('#auth-tab-register').trigger('click')

    expect(wrapper.get<HTMLInputElement>('#register-full-name').element.value).toBe('  Tan Mei  ')
    expect(wrapper.get<HTMLInputElement>('#register-email').element.value).toBe('Mei@Example.com')
    expect(wrapper.get<HTMLInputElement>('#register-password').element.value).toBe(PASSWORD)
    expect(wrapper.get<HTMLInputElement>('[data-testid="register-consent"]').element.checked).toBe(true)
  })

  it('does not sign in when the registration finishes after the visitor moved to Sign in', async () => {
    const registration = deferred<void>()
    registerStudent.mockReturnValue(registration.promise)
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    await wrapper.get('#auth-tab-signin').trigger('click')
    registration.resolve()
    await flushPromises()

    expect(auth.login).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
    expect(wrapper.get('#auth-tab-signin').attributes('aria-selected')).toBe('true')
  })

  it('finishes registration with the submitted password after the form changes', async () => {
    const registration = deferred<void>()
    registerStudent.mockReturnValue(registration.promise)
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    await wrapper.get('#register-password').setValue('Different-after-submit')
    registration.resolve()
    await flushPromises()

    expect(registerStudent).toHaveBeenCalledWith(expect.objectContaining({ password: PASSWORD }))
    // The automatic sign-in after sign-up keeps nothing after the browser closes (Keep me signed in defaults to off).
    expect(auth.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, false)
    expect(router.push).toHaveBeenCalledWith({ name: 'student-setup' })
  })

  it('ignores a canceled registration request without showing an error banner', async () => {
    registerStudent.mockRejectedValueOnce(new CanceledError('The sign-in session has ended.'))
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.find('[data-testid="register-error"]').exists()).toBe(false)
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('does not trigger sign-in fallback or wipe passwords when post-login navigation fails', async () => {
    router.push.mockRejectedValueOnce(new Error('Chunk load failed'))
    const wrapper = mountPage()
    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.get('#auth-tab-register').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-testid="auth-notice"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="register-error"]').exists()).toBe(true)

    router.push.mockRejectedValueOnce(new Error('Chunk load failed'))
    await wrapper.get('#auth-tab-signin').trigger('click')
    await signIn(wrapper)

    expect(wrapper.get<HTMLInputElement>('#login-password').element.value).toBe(PASSWORD)
    expect(wrapper.find('[data-testid="auth-error"]').exists()).toBe(true)
  })
})
