import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  checkResetLink: vi.fn(),
  checkAdminInvite: vi.fn(),
  acceptAdminInvite: vi.fn(),
  resetPassword: vi.fn(),
  replace: vi.fn(),
  push: vi.fn(),
  route: { path: '/reset-password', query: {} as Record<string, string> },
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}))

vi.mock('@/api/ops', () => ({
  publicOpsApi: {
    checkResetLink: mocks.checkResetLink,
    checkAdminInvite: mocks.checkAdminInvite,
    acceptAdminInvite: mocks.acceptAdminInvite,
  },
}))
vi.mock('@/api/services', () => ({ authApi: { resetPassword: mocks.resetPassword } }))

import ResetPasswordPage from './ResetPasswordPage.vue'

const PASSWORD = 'New-spark-2026'

async function mountPage(): Promise<VueWrapper> {
  const wrapper = mount(ResetPasswordPage, {
    global: { stubs: { AppBrand: true, IconGlyph: true, LanguageSwitcher: true, StatusPill: true } },
  })
  await flushPromises()
  return wrapper
}

async function choosePassword(wrapper: VueWrapper, confirm = PASSWORD): Promise<void> {
  await wrapper.get('#reset-password').setValue(PASSWORD)
  await wrapper.get('#reset-confirm').setValue(confirm)
}

beforeEach(() => {
  for (const mock of Object.values(mocks)) if (typeof mock === 'function') mock.mockReset()
  mocks.route.query = {}
})

describe('ResetPasswordPage password reset (FRAUTH05)', () => {
  it('checks the link, names the account and takes the token out of the address bar', async () => {
    mocks.route.query = { token: 'reset-token' }
    mocks.checkResetLink.mockResolvedValue({ email: 'mei@example.com' })

    const wrapper = await mountPage()

    expect(mocks.checkResetLink).toHaveBeenCalledWith('reset-token')
    expect(mocks.replace).toHaveBeenCalledWith({ name: 'reset-password', query: {} })
    expect(wrapper.get('[data-testid="link-email"]').text()).toBe('Resetting the password for mei@example.com')
  })

  it('enables the button only once the passwords are valid and match', async () => {
    mocks.route.query = { token: 'reset-token' }
    mocks.checkResetLink.mockResolvedValue({ email: 'mei@example.com' })
    const wrapper = await mountPage()
    const button = () => wrapper.get('button[type="submit"]')
    expect(button().attributes('disabled')).toBeDefined()

    await choosePassword(wrapper, 'Different-2026')
    expect(button().attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.auth-checks li.is-met')).toHaveLength(2)

    await wrapper.get('#reset-confirm').setValue(PASSWORD)
    expect(button().attributes('disabled')).toBeUndefined()
    expect(wrapper.findAll('.auth-checks li.is-met')).toHaveLength(3)
  })

  it('resets the password and opens sign in with the "password updated" notice', async () => {
    mocks.route.query = { token: 'reset-token' }
    mocks.checkResetLink.mockResolvedValue({ email: 'mei@example.com' })
    mocks.resetPassword.mockResolvedValue(undefined)
    const wrapper = await mountPage()

    await choosePassword(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.resetPassword).toHaveBeenCalledWith({
      token: 'reset-token',
      password: PASSWORD,
      confirmPassword: PASSWORD,
    })
    expect(mocks.push).toHaveBeenCalledWith({ name: 'auth', query: { reset: 'done' } })
  })

  it('offers a new link when the link was used or expired', async () => {
    mocks.route.query = { token: 'old-token' }
    mocks.checkResetLink.mockRejectedValue({ response: { status: 400, data: { message: 'This link has expired.' } } })

    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('This reset link no longer works')
    expect(wrapper.get('[data-testid="link-invalid"]').text()).toContain('This link has expired.')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('does not call the server without a token', async () => {
    const wrapper = await mountPage()

    expect(mocks.checkResetLink).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="link-invalid"]').text()).toContain('Open the reset link from your email')
  })
})

describe('ResetPasswordPage admin invite (FRADM01)', () => {
  beforeEach(() => {
    mocks.route.query = { invite: 'invite-token' }
    mocks.checkAdminInvite.mockResolvedValue({ email: 'admin@example.com' })
  })

  it('creates the admin account with a name and password, then opens sign in', async () => {
    mocks.acceptAdminInvite.mockResolvedValue(undefined)
    const wrapper = await mountPage()
    expect(mocks.checkAdminInvite).toHaveBeenCalledWith('invite-token')
    expect(mocks.replace).toHaveBeenCalledWith({ name: 'reset-password', query: {} })
    expect(wrapper.get('h1').text()).toBe('Set up your admin account')

    await choosePassword(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#invite-name-error').text()).toBe('Enter your full name.')
    expect(mocks.acceptAdminInvite).not.toHaveBeenCalled()

    await wrapper.get('#invite-full-name').setValue('  Aisyah Rahman ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.acceptAdminInvite).toHaveBeenCalledWith({
      token: 'invite-token',
      fullName: 'Aisyah Rahman',
      password: PASSWORD,
      confirmPassword: PASSWORD,
    })
    expect(mocks.push).toHaveBeenCalledWith({ name: 'auth', query: { invite: 'done' } })
  })

  it('explains an expired invite without offering a password reset', async () => {
    mocks.checkAdminInvite.mockRejectedValue({ response: { status: 400, data: {} } })

    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('This invite link no longer works')
    expect(wrapper.get('[data-testid="link-invalid"]').text()).toContain('Ask an admin to send a new invite.')
    expect(wrapper.text()).not.toContain('Request a new link')
  })
})
