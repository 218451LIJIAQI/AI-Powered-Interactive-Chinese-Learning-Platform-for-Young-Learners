import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  registerStudent: vi.fn(),
  registerTeacher: vi.fn(),
  replace: vi.fn(),
  push: vi.fn(),
  auth: { loading: false, activeRole: null as string | null },
  route: { path: '/auth', query: {} as Record<string, string> },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}))

vi.mock('@/api/services', () => ({
  authApi: { registerStudent: mocks.registerStudent, registerTeacher: mocks.registerTeacher },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get loading() {
      return mocks.auth.loading
    },
    get activeRole() {
      return mocks.auth.activeRole
    },
    login: mocks.login,
  }),
}))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ({ locale: 'ms' }) }))

import AuthPage from './AuthPage.vue'

const PASSWORD = 'Spark-pass-2026'

function mountPage(attachTo?: HTMLElement): VueWrapper {
  return mount(AuthPage, {
    attachTo,
    global: { stubs: { AppBrand: true, IconGlyph: true, LanguageSwitcher: true } },
  })
}

function apiError(status: number, extra: Record<string, unknown> = {}) {
  return { response: { status, data: {}, ...extra } }
}

async function signIn(wrapper: VueWrapper, email = ' Mei@Example.com ', password = PASSWORD): Promise<void> {
  await wrapper.get('#login-email').setValue(email)
  await wrapper.get('#login-password').setValue(password)
  await wrapper.get('#auth-panel-signin').trigger('submit')
  await flushPromises()
}

async function fillRegistration(wrapper: VueWrapper, { bio }: { bio?: string } = {}): Promise<void> {
  await wrapper.get('#register-full-name').setValue('  Tan Mei  ')
  await wrapper.get('#register-email').setValue('Mei@Example.com')
  await wrapper.get('#register-password').setValue(PASSWORD)
  await wrapper.get('#register-confirm-password').setValue(PASSWORD)
  if (bio !== undefined) await wrapper.get('#register-teacher-bio').setValue(bio)
  await wrapper.get('[data-testid="register-consent"]').setValue(true)
}

async function submitRegistration(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#auth-panel-register').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  for (const mock of [mocks.login, mocks.registerStudent, mocks.registerTeacher, mocks.replace, mocks.push]) {
    mock.mockReset()
  }
  mocks.auth.loading = false
  mocks.auth.activeRole = null
  mocks.route.query = {}
})

describe('AuthPage sign in (FRAUTH03)', () => {
  it('has one h1 and shows 回 "to return" beside the form', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('Welcome back')
    expect(wrapper.get('.auth-brand-tile').text()).toBe('回')
    expect(wrapper.get('.auth-brand-gloss').text()).toContain('to return')
  })

  it('moves between the sign-in and create-account tabs with the arrow keys', async () => {
    const wrapper = mountPage()
    const tablist = wrapper.get('[role="tablist"]')

    await tablist.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.get('#auth-tab-register').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('#auth-tab-register').attributes('tabindex')).toBe('0')
    expect(wrapper.find('#auth-panel-register').exists()).toBe(true)

    await tablist.trigger('keydown', { key: 'Home' })
    expect(wrapper.get('#auth-tab-signin').attributes('aria-selected')).toBe('true')
  })

  it('signs in with the trimmed lower-case email and "Keep me signed in", then opens the role home', async () => {
    mocks.login.mockImplementation(async () => {
      mocks.auth.activeRole = 'TEACHER'
    })
    const wrapper = mountPage()
    expect(wrapper.get<HTMLInputElement>('[data-testid="keep-signed-in"]').element.checked).toBe(true)

    await signIn(wrapper)

    expect(mocks.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, true)
    expect(mocks.push).toHaveBeenCalledWith('/teacher/dashboard')
  })

  it('returns to a safe redirect after signing in, and passes an unticked "Keep me signed in"', async () => {
    mocks.route.query = { redirect: '/student/ai' }
    mocks.login.mockResolvedValue(undefined)
    const wrapper = mountPage()
    await wrapper.get('[data-testid="keep-signed-in"]').setValue(false)

    await signIn(wrapper)

    expect(mocks.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, false)
    expect(mocks.push).toHaveBeenCalledWith('/student/ai')
  })

  it('says the email or password is incorrect, without saying which', async () => {
    mocks.login.mockRejectedValue(apiError(401))
    const wrapper = mountPage()

    await signIn(wrapper)

    expect(wrapper.get('[data-testid="auth-error"]').text()).toContain('The email or password is incorrect.')
    expect(wrapper.get('#login-email').attributes('aria-describedby')).toBe('login-error')
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it('pauses sign-in for the minutes the server gives in Retry-After', async () => {
    mocks.login.mockRejectedValue(apiError(429, { headers: { 'retry-after': '540' } }))
    const wrapper = mountPage()

    await signIn(wrapper)

    expect(wrapper.get('[data-testid="auth-locked"]').text()).toContain('Sign-in is paused for 9 minutes.')
    const button = wrapper.get('[data-testid="login-submit"]')
    expect(button.text()).toContain('Sign-in paused · try again in 9 minutes')
    expect(button.attributes('disabled')).toBeDefined()

    // Another address may be tried.
    await wrapper.get('#login-email').setValue('other@example.com')
    expect(wrapper.find('[data-testid="auth-locked"]').exists()).toBe(false)
  })

  it('falls back to a 15 minute pause without Retry-After', async () => {
    mocks.login.mockRejectedValue(apiError(429))
    const wrapper = mountPage()

    await signIn(wrapper)

    expect(wrapper.get('[data-testid="auth-locked"]').text()).toContain('Sign-in is paused for 15 minutes.')
  })

  it('explains a deactivated account (403)', async () => {
    mocks.login.mockRejectedValue(apiError(403))
    const wrapper = mountPage()

    await signIn(wrapper)

    expect(wrapper.get('[data-testid="auth-error"]').text()).toContain('This account is currently unavailable.')
  })

  it.each([
    ['reset', 'done', 'Password updated.'],
    ['email', 'changed', 'Email address changed.'],
    ['invite', 'done', 'Admin account ready.'],
  ])('shows the ?%s=%s notice', async (key, value, title) => {
    mocks.route.query = { [key]: value }
    const wrapper = mountPage()

    expect(wrapper.get('[data-testid="auth-notice"]').text()).toContain(title)
  })
})

describe('AuthPage create account (FRAUTH01, FRAUTH02)', () => {
  beforeEach(() => {
    mocks.route.query = { tab: 'register', role: 'student' }
  })

  it('shows 学 "to learn" beside the form and the teacher note only for a teacher account', async () => {
    const wrapper = mountPage()
    expect(wrapper.get('.auth-brand-tile').text()).toBe('学')
    expect(wrapper.get('.auth-brand-gloss').text()).toContain('to learn')
    expect(wrapper.get<HTMLInputElement>('[data-testid="auth-role-student"]').element.checked).toBe(true)
    expect(wrapper.find('.auth-teacher-note').exists()).toBe(false)

    await wrapper.get('[data-testid="auth-role-teacher"]').setValue(true)

    expect(wrapper.get('.auth-teacher-note').text()).toContain('after an admin approves them')
    expect(mocks.replace).toHaveBeenLastCalledWith({ path: '/auth', query: { tab: 'register', role: 'teacher' } })
  })

  it('lists every problem in one summary and sends nothing', async () => {
    const wrapper = mountPage(document.body)

    await submitRegistration(wrapper)

    const summary = wrapper.get('[data-testid="register-summary"]')
    expect(summary.text()).toContain('Your account was not created.')
    expect(summary.text()).toContain('Fix the 5 highlighted items and try again.')
    expect(document.activeElement).toBe(summary.element)
    expect(wrapper.get('#register-full-name').attributes('aria-invalid')).toBe('true')
    expect(mocks.registerStudent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('creates a student in the interface language, signs in and opens the first-time setup', async () => {
    mocks.registerStudent.mockResolvedValue({ email: 'mei@example.com' })
    mocks.login.mockResolvedValue(undefined)
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(mocks.registerStudent).toHaveBeenCalledWith({
      fullName: 'Tan Mei',
      email: 'mei@example.com',
      password: PASSWORD,
      confirmPassword: PASSWORD,
      preferredLanguage: 'ms',
    })
    expect(mocks.login).toHaveBeenCalledWith('mei@example.com', PASSWORD, true)
    expect(mocks.push).toHaveBeenCalledWith({ name: 'student-setup' })
  })

  it('asks a teacher for a 20 to 1,000 character bio and sends it trimmed', async () => {
    mocks.route.query = { tab: 'register', role: 'teacher' }
    mocks.registerTeacher.mockResolvedValue({ email: 'mei@example.com' })
    mocks.login.mockResolvedValue(undefined)
    const wrapper = mountPage()
    expect(wrapper.get('#register-teacher-bio').attributes('maxlength')).toBe('1000')

    await fillRegistration(wrapper, { bio: '  I teach Mandarin.  ' })
    await submitRegistration(wrapper)
    expect(wrapper.get('#register-bio-error').text()).toBe(
      'Write 20 to 1,000 characters about your teaching background.',
    )
    expect(wrapper.get('[data-testid="register-summary"]').text()).toContain('Fix the highlighted item and try again.')
    expect(mocks.registerTeacher).not.toHaveBeenCalled()

    await wrapper.get('#register-teacher-bio').setValue('  I teach Mandarin to young adults.  ')
    await submitRegistration(wrapper)

    expect(mocks.registerTeacher).toHaveBeenCalledWith(
      expect.objectContaining({ email: 'mei@example.com', bio: 'I teach Mandarin to young adults.' }),
    )
    expect(mocks.push).toHaveBeenCalledWith({ name: 'teacher-dashboard' })
  })

  it('marks the email field when the address is already registered', async () => {
    mocks.registerStudent.mockRejectedValue(apiError(400, { data: { message: 'Email is already registered.' } }))
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.get('#register-email-error').text()).toContain('This email is already registered.')
    expect(wrapper.find('[data-testid="register-summary"]').exists()).toBe(true)
    expect(mocks.login).not.toHaveBeenCalled()
  })

  it('opens sign in with the address filled in when the automatic sign-in fails', async () => {
    mocks.registerStudent.mockResolvedValue({ email: 'mei@example.com' })
    mocks.login.mockRejectedValue(apiError(500))
    const wrapper = mountPage()

    await fillRegistration(wrapper)
    await submitRegistration(wrapper)

    expect(wrapper.find('#auth-panel-signin').exists()).toBe(true)
    expect(wrapper.get<HTMLInputElement>('#login-email').element.value).toBe('mei@example.com')
    expect(wrapper.text()).toContain('Your account is ready. Sign in to continue.')
  })
})
