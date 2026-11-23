import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AccountSettingsResponse } from '@/types/ops'
import type { UserRole } from '@/types/api'
import { getAuthSessionVersion, setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'
import { hintSettings } from '@/test/learnFixtures'

const accountOpsApi = vi.hoisted(() => ({
  settings: vi.fn(),
  updateProfile: vi.fn(),
  updatePreferences: vi.fn(),
  requestEmailChange: vi.fn(),
  resendEmailChange: vi.fn(),
  cancelEmailChange: vi.fn(),
  changePassword: vi.fn(),
  deleteAccount: vi.fn(),
}))
const xpApi = vi.hoisted(() => ({ dailyGoal: vi.fn(), setDailyGoal: vi.fn() }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn(), setMode: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
const currentRoute = vi.hoisted(() => ({ value: { meta: {} as { roles?: UserRole[] } } }))
const auth = vi.hoisted(() => ({
  roles: ['STUDENT'] as UserRole[],
  applySavedName: vi.fn(),
  replaceAccessToken: vi.fn(),
  clearSessionInAllTabs: vi.fn(),
  logout: vi.fn(),
}))

const authApi = vi.hoisted(() => ({ session: vi.fn() }))

vi.mock('@/api/ops', () => ({ accountOpsApi }))
vi.mock('@/api/services', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/services')>()),
  authApi,
}))
vi.mock('@/api/learn', () => ({ xpApi, hintApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRouter: () => ({ ...router, currentRoute }),
}))

import type { HintSettings } from '@/types/learn'

import AccountSettingsPage from './AccountSettingsPage.vue'

const CHROME_ON_WINDOWS =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'

function settings(overrides: Partial<AccountSettingsResponse> = {}): AccountSettingsResponse {
  return {
    fullName: 'Amira Hassan',
    email: 'amira.hassan@example.com',
    pendingEmail: null,
    notifications: { reviewQueues: null },
    ...overrides,
  }
}

async function mountPage(attachTo?: HTMLElement) {
  const wrapper = mount(AccountSettingsPage, { attachTo, global: { stubs: { teleport: true, IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

async function submitPasswordChange(wrapper: Awaited<ReturnType<typeof mountPage>>): Promise<void> {
  await wrapper.get('#account-currentPassword').setValue('OldPassword1!')
  await wrapper.get('#account-newPassword').setValue('NewPassword1!')
  await wrapper.get('#account-confirmPassword').setValue('NewPassword1!')
  await wrapper.get('#account-password-form').trigger('submit')
}

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    for (const group of [accountOpsApi, xpApi, hintApi, router, authApi]) {
      Object.values(group).forEach((fn) => fn.mockReset())
    }
    auth.applySavedName.mockReset()
    auth.replaceAccessToken.mockReset()
    auth.clearSessionInAllTabs.mockReset()
    auth.logout.mockReset()

    setAccessToken('account-session')
    currentRoute.value = { meta: {} }
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(CHROME_ON_WINDOWS)
    auth.replaceAccessToken.mockReturnValue(true)
    auth.logout.mockResolvedValue(true)
    auth.roles = ['STUDENT']
    accountOpsApi.settings.mockResolvedValue(settings())
    accountOpsApi.updateProfile.mockResolvedValue({ id: 1, fullName: 'Amira H.' })
    accountOpsApi.updatePreferences.mockImplementation(async (change: object) => ({
      ...settings().notifications,
      ...change,
    }))
    xpApi.dailyGoal.mockResolvedValue({ dailyGoalXp: 20 })
    xpApi.setDailyGoal.mockImplementation(async (dailyGoalXp: number) => ({ dailyGoalXp }))
    hintApi.settings.mockResolvedValue(hintSettings())
    hintApi.setMode.mockImplementation(async (mode: HintSettings['mode']) => hintSettings({ mode }))
    authApi.session.mockResolvedValue({ signedInAt: '2026-05-12T09:30:00' })
  })

  afterEach(() => {
    setAccessToken(null)
  })

  it('shows the learner’s profile and daily goal, AI hints, email notifications, password and this device', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Profile and daily goal')
    expect(wrapper.find('[data-testid="account-goal-20"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="account-hints"] h2').text()).toBe('AI hints')
    // A learner's mails about the account always go: nothing to switch.
    expect(wrapper.text()).toContain('Account and security')
    expect(wrapper.text()).toContain('Always on')
    expect(wrapper.find('input[role="switch"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Password')
    expect(wrapper.text()).toContain('This device')
    expect(wrapper.get('[data-testid="account-device"]').text()).toBe('Chrome on Windows · signed in since 12 May')
    // Signing out ends this browser's session only; a password change signs out the others.
    expect(wrapper.text()).toContain('Signs you out on this browser only. Your other devices stay signed in.')
    expect(wrapper.text()).toContain('Changing your password signs you out on your other devices.')
    expect(wrapper.text()).toContain('Danger zone')
    expect(wrapper.text()).toContain('Deleting your account is permanent.')
  })

  it('shows the learner’s hint rule and saves a new hint mode straight away, with Undo', async () => {
    const wrapper = await mountPage()
    const card = wrapper.get('[data-testid="account-hints"]')
    expect(card.text()).toContain('SparkBot offers a hint when you’ve been stuck on a question for a while.')
    expect(card.text()).toContain(
      'At your level (HSK 1): offered after about 15 seconds. Hints come later and say less as your level goes up.',
    )
    expect(card.get('[data-testid="hint-mode-automatic"]').attributes('aria-pressed')).toBe('true')

    await card.get('[data-testid="hint-mode-on-request"]').trigger('click')
    await flushPromises()
    expect(hintApi.setMode).toHaveBeenCalledWith('ON_REQUEST')
    expect(card.text()).toContain('SparkBot gives a hint only when you ask for one.')
    expect(card.get('[data-testid="hint-mode-on-request"]').attributes('aria-pressed')).toBe('true')
    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.text()).toBe('AI hints: only when you ask.Undo')

    await toast.get('button').trigger('click')
    await flushPromises()
    expect(hintApi.setMode).toHaveBeenLastCalledWith('AUTOMATIC')
    expect(card.get('[data-testid="hint-mode-automatic"]').attributes('aria-pressed')).toBe('true')
    // The Undo is not offered again.
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('AI hints: automatic.')
  })

  it('saves the edited name and daily goal without changing the account language', async () => {
    const wrapper = await mountPage()

    await wrapper.find('#account-name').setValue('Amira H.')
    await wrapper.find('[data-testid="account-goal-30"]').trigger('click')
    await wrapper.find('#account-profile-form').trigger('submit')
    await flushPromises()

    expect(accountOpsApi.updateProfile).toHaveBeenCalledWith({ displayName: 'Amira H.' })
    expect(auth.applySavedName).toHaveBeenCalled()
    expect(xpApi.setDailyGoal).toHaveBeenCalledWith(30)
    expect(wrapper.find('[data-testid="toast"]').text()).toBe('Changes saved.')
  })

  it('asks for a name under the field when it is left empty, and keeps Save off until there is one', async () => {
    const wrapper = await mountPage()

    await wrapper.get('#account-name').setValue('   ')
    await wrapper.get('#account-name').trigger('blur')

    expect(wrapper.get('#account-name-error').text()).toBe('Enter your name.')
    expect(wrapper.get('#account-name').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#account-name').attributes('aria-describedby')).toBe('account-name-error')
    expect(wrapper.get('[data-testid="account-save"]').attributes('disabled')).toBeDefined()
    await wrapper.get('#account-profile-form').trigger('submit')
    await flushPromises()
    expect(accountOpsApi.updateProfile).not.toHaveBeenCalled()

    await wrapper.get('#account-name').setValue('Amira')
    expect(wrapper.find('#account-name-error').exists()).toBe(false)
    expect(wrapper.get('[data-testid="account-save"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps working when the daily goal cannot be loaded', async () => {
    xpApi.dailyGoal.mockRejectedValue(new Error('not served'))
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="account-goal-10"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Your daily goal can’t be loaded right now.')
    expect(wrapper.get('fieldset').attributes('aria-describedby')).toBe('account-goal-hint')
    await wrapper.get('#account-name').setValue('Amira H.')
    await wrapper.find('#account-profile-form').trigger('submit')
    await flushPromises()
    expect(xpApi.setDailyGoal).not.toHaveBeenCalled()
    expect(accountOpsApi.updateProfile).toHaveBeenCalled()
  })

  it('keeps the daily goal off while it loads, without saying it cannot be loaded', async () => {
    xpApi.dailyGoal.mockReturnValue(new Promise(() => undefined))
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="account-goal-10"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('Your daily goal can’t be loaded right now.')
    expect(wrapper.get('fieldset').attributes('aria-describedby')).toBeUndefined()
  })

  it.each([
    ['the session is not known', { signedInAt: null }],
    ['the session cannot be read', new Error('offline')],
  ])('names the device alone when %s', async (_case, answer) => {
    if (answer instanceof Error) authApi.session.mockRejectedValue(answer)
    else authApi.session.mockResolvedValue(answer)
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="account-device"]').text()).toBe('Chrome on Windows')
  })

  it('names the page for assistive technology only, and keeps a labelled Sign out', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('h1').classes()).toContain('sr-only')
    expect(wrapper.find('h1').text()).toBe('Account')
    expect(wrapper.find('[data-testid="account-sign-out"]').text()).toBe('Sign out')
  })

  it('sends an email change link, confirms it in a toast and says the new address waits to be confirmed', async () => {
    accountOpsApi.requestEmailChange.mockResolvedValue({ pendingEmail: 'amira.new@example.com' })
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="account-change-email"]').trigger('click')
    const dialog = wrapper.get('[data-testid="account-email-dialog"]')
    expect(dialog.find('#account-current-email').exists()).toBe(false)
    await wrapper.find('#account-new-email').setValue('Amira.New@example.com')
    await wrapper.find('#account-email-password').setValue('Password123!')
    await wrapper.find('#account-email-form').trigger('submit')
    await flushPromises()

    expect(accountOpsApi.requestEmailChange).toHaveBeenCalledWith({
      newEmail: 'amira.new@example.com',
      currentPassword: 'Password123!',
    })
    expect(wrapper.find('[data-testid="account-email-dialog"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe(
      'Link sent to amira.new@example.com. Your email changes when you open it.',
    )
    expect(wrapper.get('[data-testid="account-email-pending"]').text()).toContain('amira.new@example.com')
  })

  it('says under the field when the new address is the current one', async () => {
    accountOpsApi.settings.mockResolvedValue(settings({ email: 'Amira.Hassan@example.com' }))
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="account-change-email"]').trigger('click')
    await wrapper.find('#account-new-email').setValue('amira.hassan@example.com')
    await wrapper.find('#account-new-email').trigger('blur')
    await wrapper.get('#account-email-password').setValue('Password123!')

    expect(wrapper.get('#account-new-email-error').text()).toBe('This is already your email. Enter a different one.')
    expect(wrapper.get<HTMLButtonElement>('[data-testid="account-email-send"]').element.disabled).toBe(true)
    expect(accountOpsApi.requestEmailChange).not.toHaveBeenCalled()
    await wrapper.get('#account-new-email').setValue('another@example.com')
    expect(wrapper.get('#account-new-email').attributes('aria-invalid')).toBe('false')
    expect(wrapper.find('#account-new-email-error').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="account-email-send"]').element.disabled).toBe(false)
  })

  it('keeps Send off until there is a valid new address and the password', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="account-change-email"]').trigger('click')
    const send = () => wrapper.get<HTMLButtonElement>('[data-testid="account-email-send"]').element

    expect(send().disabled).toBe(true)
    await wrapper.get('#account-new-email').setValue('amira.new@example')
    await wrapper.get('#account-email-password').setValue('Password123!')
    expect(send().disabled).toBe(true)
    await wrapper.get('#account-new-email').setValue('amira.new@example.com')
    expect(send().disabled).toBe(false)
    await wrapper.get('#account-email-password').setValue('   ')
    expect(send().disabled).toBe(true)
  })

  it('sends the link of a waiting email change again, or cancels the change', async () => {
    accountOpsApi.settings.mockResolvedValue(settings({ pendingEmail: 'amira.new@example.com' }))
    const resend = deferred<{ pendingEmail: string }>()
    accountOpsApi.resendEmailChange.mockReturnValueOnce(resend.promise)
    accountOpsApi.cancelEmailChange.mockResolvedValue(null)
    const wrapper = await mountPage()
    const pending = () => wrapper.find('[data-testid="account-email-pending"]')

    expect(pending().text().replace(/\s+/g, ' ')).toBe(
      'Waiting for confirmation: amira.new@example.com · Resend link · Cancel change',
    )
    expect(wrapper.get('#account-email').attributes('aria-describedby')).toBe('account-email-pending')

    await wrapper.get('[data-testid="account-email-resend"]').trigger('click')
    expect(wrapper.get<HTMLButtonElement>('[data-testid="account-change-email"]').element.disabled).toBe(true)
    resend.resolve({ pendingEmail: 'amira.new@example.com' })
    await flushPromises()
    expect(accountOpsApi.resendEmailChange).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Link sent again.')

    await wrapper.get('[data-testid="account-email-cancel"]').trigger('click')
    await flushPromises()
    expect(accountOpsApi.cancelEmailChange).toHaveBeenCalledOnce()
    expect(pending().exists()).toBe(false)
    expect(wrapper.get('#account-email').attributes('aria-describedby')).toBeUndefined()
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Email change cancelled.')
  })

  it('returns keyboard focus to Change email after removing a pending request', async () => {
    accountOpsApi.settings.mockResolvedValue(settings({ pendingEmail: 'another@example.com' }))
    accountOpsApi.cancelEmailChange.mockResolvedValue(null)
    const wrapper = await mountPage(document.body)
    try {
      const cancel = wrapper.get<HTMLButtonElement>('[data-testid="account-email-cancel"]')
      cancel.element.focus()
      await cancel.trigger('click')
      await flushPromises()
      expect(document.activeElement).toBe(wrapper.get('[data-testid="account-change-email"]').element)
    } finally {
      wrapper.unmount()
    }
  })

  it('says why a waiting email change could not be sent again, and keeps it', async () => {
    accountOpsApi.settings.mockResolvedValue(settings({ pendingEmail: 'amira.new@example.com' }))
    accountOpsApi.resendEmailChange.mockRejectedValue({
      response: { status: 429, data: { message: 'You are doing that too often. Please wait a moment and try again.' } },
    })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="account-email-resend"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="account-email-pending-error"]').text()).toBe(
      'You are doing that too often. Please wait a moment and try again.',
    )
    expect(wrapper.find('[data-testid="account-email-pending"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('deletes the account only after the exact email is typed, then goes home with the notice', async () => {
    accountOpsApi.deleteAccount.mockResolvedValue(null)
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="account-delete"]').trigger('click')
    const dialog = wrapper.get('[data-testid="account-delete-dialog"]')
    expect(dialog.text()).toContain('This can’t be undone.')
    expect(dialog.text()).toContain(
      'Your name, email, notifications, support requests and personal goal text are removed.',
    )
    expect(dialog.text()).toContain('Your learning records are kept without your name.')
    const confirm = wrapper.find('[data-testid="account-delete-confirm"]')
    await wrapper.find('#account-delete-confirm').setValue('amira@example.com')
    expect(confirm.attributes('disabled')).toBeDefined()

    await wrapper.find('#account-delete-confirm').setValue('AMIRA.HASSAN@example.com')
    expect(wrapper.find('[data-testid="account-delete-confirm"]').attributes('disabled')).toBeUndefined()
    await wrapper.find('#account-delete-form').trigger('submit')
    await flushPromises()

    expect(accountOpsApi.deleteAccount).toHaveBeenCalledWith('AMIRA.HASSAN@example.com')
    expect(auth.clearSessionInAllTabs).toHaveBeenCalled()
    expect(router.replace).toHaveBeenCalledWith({ name: 'landing', query: { notice: 'account-deleted' } })
  })

  it('gives admins their profile, the daily summary switch and no deletion', async () => {
    auth.roles = ['ADMIN']
    accountOpsApi.settings.mockResolvedValue(settings({ notifications: { reviewQueues: true } }))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Profile details')
    expect(wrapper.find('[data-testid="account-goal-20"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-hints"]').exists()).toBe(false)
    expect(xpApi.dailyGoal).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="account-delete"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Admin accounts are deactivated by another admin in Users.')
    expect(wrapper.text()).toContain('A daily email when AI reports or support requests are waiting.')
    expect(wrapper.get<HTMLInputElement>('[data-testid="account-pref-daily-summary"]').element.checked).toBe(true)
  })

  it('saves the admin’s daily summary at once and rolls back on failure', async () => {
    auth.roles = ['ADMIN']
    accountOpsApi.settings.mockResolvedValue(settings({ notifications: { reviewQueues: true } }))
    const wrapper = await mountPage()
    const summary = wrapper.get<HTMLInputElement>('[data-testid="account-pref-daily-summary"]')

    await summary.setValue(false)
    await flushPromises()
    expect(accountOpsApi.updatePreferences).toHaveBeenCalledWith({ reviewQueues: false })
    expect(summary.element.checked).toBe(false)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Changes saved.')

    accountOpsApi.updatePreferences.mockRejectedValueOnce(new Error('offline'))
    await summary.setValue(true)
    await flushPromises()
    expect(summary.element.checked).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toBe('Your notification setting was not saved.')
  })

  it('keeps learner settings and admin preferences available together with admin deletion rules', async () => {
    auth.roles = ['ADMIN', 'STUDENT']
    accountOpsApi.settings.mockResolvedValue(settings({ notifications: { reviewQueues: true } }))
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="account-goal-20"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="account-hints"]').exists()).toBe(true)
    expect(wrapper.get<HTMLInputElement>('[data-testid="account-pref-daily-summary"]').element.checked).toBe(true)
    expect(wrapper.find('[data-testid="account-delete"]').exists()).toBe(false)
  })

  it('changes the password, keeps this session with the new token and clears the form', async () => {
    accountOpsApi.changePassword.mockResolvedValue({ token: 'fresh-token' })
    const wrapper = await mountPage()

    await submitPasswordChange(wrapper)
    await flushPromises()

    expect(auth.replaceAccessToken).toHaveBeenCalledWith('fresh-token', getAuthSessionVersion())
    expect(wrapper.find('[data-testid="toast"]').text()).toBe('Password changed.')
    for (const id of ['#account-currentPassword', '#account-newPassword', '#account-confirmPassword']) {
      expect(wrapper.get<HTMLInputElement>(id).element.value, id).toBe('')
    }
    expect(wrapper.get<HTMLButtonElement>('[data-testid="account-change-password"]').element.disabled).toBe(true)
  })

  it('keeps Change password off until all three fields are filled', async () => {
    const wrapper = await mountPage()
    const button = () => wrapper.get<HTMLButtonElement>('[data-testid="account-change-password"]').element

    expect(button().disabled).toBe(true)
    await wrapper.get('#account-currentPassword').setValue('OldPassword1!')
    await wrapper.get('#account-newPassword').setValue('NewPassword1!')
    expect(button().disabled).toBe(true)
    await wrapper.get('#account-confirmPassword').setValue('NewPassword1!')
    expect(button().disabled).toBe(false)
    await wrapper.get('#account-newPassword').setValue('  ')
    expect(button().disabled).toBe(true)
  })

  it('says under Current password when it is wrong, and keeps the button off until it changes', async () => {
    accountOpsApi.changePassword.mockRejectedValue({
      response: { status: 400, data: { message: 'Current password is incorrect.' } },
    })
    const wrapper = await mountPage()
    const button = () => wrapper.get<HTMLButtonElement>('[data-testid="account-change-password"]').element

    await submitPasswordChange(wrapper)
    await flushPromises()

    expect(wrapper.get('#account-currentPassword-error').text()).toBe(
      'That’s not your current password. Check it and try again.',
    )
    expect(wrapper.get('#account-currentPassword').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#account-currentPassword').attributes('aria-describedby')).toBe('account-currentPassword-error')
    // Said at the field only, not again for the whole form.
    expect(wrapper.find('#account-password-form .alert-danger').exists()).toBe(false)
    expect(button().disabled).toBe(true)
    expect(wrapper.get<HTMLInputElement>('#account-newPassword').element.value).toBe('NewPassword1!')

    await wrapper.get('#account-currentPassword').setValue('OldPassword2!')
    expect(wrapper.find('#account-currentPassword-error').exists()).toBe(false)
    expect(button().disabled).toBe(false)
  })

  it('takes back what it said about a password field as soon as that field is edited', async () => {
    const wrapper = await mountPage()
    await wrapper.get('#account-currentPassword').setValue('OldPassword1!')
    await wrapper.get('#account-newPassword').setValue('short')
    await wrapper.get('#account-confirmPassword').setValue('other')
    await wrapper.get('#account-password-form').trigger('submit')
    expect(wrapper.find('#account-newPassword-error').exists()).toBe(true)
    expect(wrapper.find('#account-confirmPassword-error').exists()).toBe(true)

    await wrapper.get('#account-newPassword').setValue('NewPassword1!')

    expect(wrapper.find('#account-newPassword-error').exists()).toBe(false)
    // The confirmation was not edited: its message stays until it is, or until the next Change password.
    expect(wrapper.find('#account-confirmPassword-error').exists()).toBe(true)
    expect(accountOpsApi.changePassword).not.toHaveBeenCalled()
  })

  it('says any other refusal of a password change for the whole form', async () => {
    accountOpsApi.changePassword.mockRejectedValue({
      response: { status: 400, data: { message: 'New password must be different from the current password.' } },
    })
    const wrapper = await mountPage()

    await submitPasswordChange(wrapper)
    await flushPromises()

    expect(wrapper.get('#account-password-form .alert-danger').text()).toBe(
      'New password must be different from the current password.',
    )
    expect(wrapper.find('#account-currentPassword-error').exists()).toBe(false)
  })

  it('does not apply or announce a profile save after another session starts', async () => {
    const save = deferred<{ id: number; fullName: string }>()
    accountOpsApi.updateProfile.mockReturnValueOnce(save.promise)
    const wrapper = await mountPage()
    await wrapper.get('#account-name').setValue('Old profile')
    await wrapper.get('#account-profile-form').trigger('submit')
    setAccessToken('another-session')
    save.resolve({ id: 1, fullName: 'Old profile' })
    await flushPromises()
    expect(auth.applySavedName).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it.each(['leave', 'new-session'])(
    'handles a password response after %s without damaging authentication',
    async (change) => {
      const response = deferred<{ token: string }>()
      accountOpsApi.changePassword.mockReturnValueOnce(response.promise)
      const wrapper = await mountPage()
      const version = getAuthSessionVersion()
      await submitPasswordChange(wrapper)
      if (change === 'leave') wrapper.unmount()
      else setAccessToken('another-session')
      response.resolve({ token: 'password-token' })
      await flushPromises()
      if (change === 'leave') expect(auth.replaceAccessToken).toHaveBeenCalledWith('password-token', version)
      else {
        expect(auth.replaceAccessToken).not.toHaveBeenCalled()
        expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
        wrapper.unmount()
      }
    },
  )

  it('does not clear or redirect a newer session after an old account deletion', async () => {
    const deletion = deferred<void>()
    accountOpsApi.deleteAccount.mockReturnValueOnce(deletion.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="account-delete"]').trigger('click')
    await wrapper.get('#account-delete-confirm').setValue('amira.hassan@example.com')
    await wrapper.get('#account-delete-form').trigger('submit')
    setAccessToken('another-session')
    deletion.resolve()
    await flushPromises()
    expect(auth.clearSessionInAllTabs).not.toHaveBeenCalled()
    expect(router.replace).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not redirect when sign-out belongs to an earlier session', async () => {
    auth.logout.mockResolvedValueOnce(false)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="account-sign-out"]').trigger('click')
    await flushPromises()
    expect(router.push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not flash the admin deletion notice when roles clear, and redirects if leaving to a protected route during sign-out', async () => {
    const signOut = deferred<boolean>()
    auth.logout.mockReturnValueOnce(signOut.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="account-sign-out"]').trigger('click')
    // Signing out to another protected route unmounts Account while logout is in flight.
    currentRoute.value = { meta: { roles: ['STUDENT'] } }
    wrapper.unmount()
    signOut.resolve(true)
    await flushPromises()
    expect(router.push).toHaveBeenCalledExactlyOnceWith('/')

    // When a learner's roles are empty (mid sign-out/deletion), admin-only copy stays hidden.
    auth.roles = []
    const signedOutView = await mountPage()
    expect(signedOutView.text()).not.toContain('Admin accounts are deactivated')
    expect(signedOutView.find('[data-testid="account-delete"]').exists()).toBe(false)
    signedOutView.unmount()
  })

  it('rolls back the daily summary switch when a concurrent token rotation cancels the request', async () => {
    auth.roles = ['ADMIN']
    accountOpsApi.settings.mockResolvedValue(settings({ notifications: { reviewQueues: true } }))
    const update = deferred<{ reviewQueues: boolean }>()
    accountOpsApi.updatePreferences.mockReturnValueOnce(update.promise)
    const wrapper = await mountPage()
    const summary = wrapper.get<HTMLInputElement>('[data-testid="account-pref-daily-summary"]')

    await summary.setValue(false)
    expect(summary.element.checked).toBe(false)
    setAccessToken('rotated-after-password-change')
    update.reject(new Error('canceled'))
    await flushPromises()

    expect(summary.element.checked).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it.each(['profile', 'goal'] as const)(
    'waits for both writes when %s fails first, then permits retry',
    async (first) => {
      const profileSave = deferred()
      const goalSave = deferred<{ dailyGoalXp: number }>()
      accountOpsApi.updateProfile.mockReturnValue(profileSave.promise)
      xpApi.setDailyGoal.mockReturnValue(goalSave.promise)
      const wrapper = await mountPage()
      await wrapper.get('#account-name').setValue('Amira H.')
      await wrapper.get('[data-testid="account-goal-30"]').trigger('click')
      await wrapper.get('#account-profile-form').trigger('submit')
      if (first === 'profile') profileSave.reject(new Error('Profile unavailable'))
      else goalSave.reject(new Error('Goal unavailable'))
      await flushPromises()
      const remainedBusy = wrapper.get('[data-testid="account-save"]').attributes('disabled')
      await wrapper.get('#account-profile-form').trigger('submit')
      if (first === 'profile') goalSave.resolve({ dailyGoalXp: 30 })
      else profileSave.resolve({ id: 1, fullName: 'Amira H.' })
      await flushPromises()

      expect(remainedBusy).toBeDefined()
      expect(accountOpsApi.updateProfile).toHaveBeenCalledTimes(1)
      expect(xpApi.setDailyGoal).toHaveBeenCalledTimes(1)
      expect(wrapper.get('[data-testid="account-save"]').attributes('disabled')).toBeUndefined()
      // The failure is the form's, not the name field's.
      expect(wrapper.get('[data-testid="account-save-error"]').text()).not.toBe('')
      expect(wrapper.find('#account-name-error').exists()).toBe(false)
      expect(wrapper.get('#account-name').attributes('aria-invalid')).toBe('false')
      expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
      accountOpsApi.updateProfile.mockResolvedValue({ id: 1, fullName: 'Amira H.' })
      xpApi.setDailyGoal.mockResolvedValue({ dailyGoalXp: 30 })
      await wrapper.get('#account-profile-form').trigger('submit')
      await flushPromises()
      expect(wrapper.get('[data-testid="toast"]').text()).toBe('Changes saved.')
      expect(wrapper.find('[data-testid="account-save-error"]').exists()).toBe(false)
      expect(xpApi.setDailyGoal).toHaveBeenCalledTimes(first === 'goal' ? 2 : 1)
      expect(accountOpsApi.updateProfile).toHaveBeenCalledTimes(first === 'profile' ? 2 : 1)
    },
  )

  it('keeps a newer daily-goal choice while the previous one finishes saving', async () => {
    const goalSave = deferred<{ dailyGoalXp: number }>()
    xpApi.setDailyGoal.mockReturnValue(goalSave.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="account-goal-30"]').trigger('click')
    await wrapper.get('#account-profile-form').trigger('submit')
    await wrapper.get('[data-testid="account-goal-10"]').trigger('click')
    goalSave.resolve({ dailyGoalXp: 30 })
    await flushPromises()

    expect(wrapper.get('[data-testid="account-goal-10"]').attributes('aria-pressed')).toBe('true')
    xpApi.setDailyGoal.mockResolvedValue({ dailyGoalXp: 10 })
    await wrapper.get('#account-profile-form').trigger('submit')
    await flushPromises()
    expect(xpApi.setDailyGoal).toHaveBeenLastCalledWith(10)
    expect(accountOpsApi.updateProfile).not.toHaveBeenCalled()
  })

  it('still applies a saved name to the same session after the page was left, so the header shows it', async () => {
    const profileSave = deferred()
    const goalSave = deferred<{ dailyGoalXp: number }>()
    accountOpsApi.updateProfile.mockReturnValue(profileSave.promise)
    xpApi.setDailyGoal.mockReturnValue(goalSave.promise)
    const wrapper = await mountPage()
    const version = getAuthSessionVersion()
    await wrapper.get('#account-name').setValue('Amira H.')
    await wrapper.get('[data-testid="account-goal-30"]').trigger('click')
    await wrapper.get('#account-profile-form').trigger('submit')
    wrapper.unmount()
    profileSave.resolve({ id: 1, fullName: 'Amira H.' })
    goalSave.resolve({ dailyGoalXp: 30 })
    await flushPromises()
    // The store itself refuses an update for another account or session.
    expect(auth.applySavedName).toHaveBeenCalledExactlyOnceWith({ id: 1, fullName: 'Amira H.' }, version)
  })
})
