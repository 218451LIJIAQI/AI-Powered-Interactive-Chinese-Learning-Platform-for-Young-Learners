import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AccountSettingsResponse } from '@/types/ops'

const accountOpsApi = vi.hoisted(() => ({
  settings: vi.fn(),
  updateProfile: vi.fn(),
  updatePreferences: vi.fn(),
  requestEmailChange: vi.fn(),
  deleteAccount: vi.fn(),
}))
const xpApi = vi.hoisted(() => ({ dailyGoal: vi.fn(), setDailyGoal: vi.fn() }))
const accountApi = vi.hoisted(() => ({ changePassword: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
const auth = vi.hoisted(() => ({
  activeRole: 'STUDENT' as 'STUDENT' | 'TEACHER' | 'ADMIN',
  applyProfileUpdate: vi.fn(),
  replaceAccessToken: vi.fn(),
  clearSession: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/api/ops', () => ({ accountOpsApi }))
vi.mock('@/api/learn', () => ({ xpApi }))
vi.mock('@/api/services', () => ({ accountApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ({ locale: 'en' }) }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRouter: () => router,
}))

import AccountSettingsPage from './AccountSettingsPage.vue'

function settings(overrides: Partial<AccountSettingsResponse> = {}): AccountSettingsResponse {
  return {
    fullName: 'Amira Hassan',
    email: 'amira.hassan@example.com',
    pendingEmail: null,
    notifications: { liveSessionReminders: true, feedback: true, contentReviews: null, reviewQueues: null },
    deletion: { blockedBy: null, publishedClasses: [] },
    ...overrides,
  }
}

async function mountPage() {
  const wrapper = mount(AccountSettingsPage, { global: { stubs: { teleport: true, IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    auth.activeRole = 'STUDENT'
    accountOpsApi.settings.mockResolvedValue(settings())
    accountOpsApi.updateProfile.mockResolvedValue({ id: 1, fullName: 'Amira H.' })
    accountOpsApi.updatePreferences.mockImplementation(async (change: object) => ({
      ...settings().notifications,
      ...change,
    }))
    xpApi.dailyGoal.mockResolvedValue({ dailyGoalXp: 20 })
    xpApi.setDailyGoal.mockImplementation(async (dailyGoalXp: number) => ({ dailyGoalXp }))
  })

  it('saves the name with the interface language and the new daily goal', async () => {
    const wrapper = await mountPage()
    expect(wrapper.find('[data-testid="account-goal-20"]').attributes('aria-pressed')).toBe('true')

    await wrapper.find('#account-name').setValue('Amira H.')
    await wrapper.find('[data-testid="account-goal-30"]').trigger('click')
    await wrapper.find('#account-profile-form').trigger('submit')
    await flushPromises()

    expect(accountOpsApi.updateProfile).toHaveBeenCalledWith({ displayName: 'Amira H.', preferredLanguage: 'en' })
    expect(auth.applyProfileUpdate).toHaveBeenCalled()
    expect(xpApi.setDailyGoal).toHaveBeenCalledWith(30)
    expect(wrapper.find('[data-testid="toast"]').text()).toBe('Changes saved.')
  })

  it('keeps working when the daily goal cannot be loaded', async () => {
    xpApi.dailyGoal.mockRejectedValue(new Error('not served'))
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="account-goal-10"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Your daily goal can’t be loaded right now.')
    await wrapper.find('#account-profile-form').trigger('submit')
    await flushPromises()
    expect(xpApi.setDailyGoal).not.toHaveBeenCalled()
    expect(accountOpsApi.updateProfile).toHaveBeenCalled()
  })

  it('saves a notification preference at once and rolls back on failure', async () => {
    const wrapper = await mountPage()
    const feedback = wrapper.find('[data-testid="account-pref-feedback"]')

    await feedback.setValue(false)
    await flushPromises()
    expect(accountOpsApi.updatePreferences).toHaveBeenCalledWith({ feedback: false })

    accountOpsApi.updatePreferences.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await wrapper.find('[data-testid="account-pref-liveSessionReminders"]').setValue(false)
    await flushPromises()
    expect(
      (wrapper.find('[data-testid="account-pref-liveSessionReminders"]').element as HTMLInputElement).checked,
    ).toBe(true)
    expect(wrapper.find('[data-testid="account-pref-contentReviews"]').exists()).toBe(false)
  })

  it('sends an email change link and says where it went', async () => {
    accountOpsApi.requestEmailChange.mockResolvedValue({
      pendingEmail: 'amira.new@example.com',
      expiresAt: '2026-05-18T11:00:00',
    })
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="account-change-email"]').trigger('click')
    await wrapper.find('#account-new-email').setValue('Amira.New@example.com')
    await wrapper.find('#account-email-password').setValue('Password123!')
    await wrapper.find('#account-email-form').trigger('submit')
    await flushPromises()

    expect(accountOpsApi.requestEmailChange).toHaveBeenCalledWith({
      newEmail: 'amira.new@example.com',
      currentPassword: 'Password123!',
    })
    expect(wrapper.text()).toContain('Confirmation link sent to amira.new@example.com.')
  })

  it('deletes the account only after the exact email is typed, then goes home with the notice', async () => {
    accountOpsApi.deleteAccount.mockResolvedValue(null)
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="account-delete"]').trigger('click')
    const confirm = wrapper.find('[data-testid="account-delete-confirm"]')
    await wrapper.find('#account-delete-confirm').setValue('amira@example.com')
    expect(confirm.attributes('disabled')).toBeDefined()

    await wrapper.find('#account-delete-confirm').setValue('AMIRA.HASSAN@example.com')
    expect(wrapper.find('[data-testid="account-delete-confirm"]').attributes('disabled')).toBeUndefined()
    await wrapper.find('#account-delete-form').trigger('submit')
    await flushPromises()

    expect(accountOpsApi.deleteAccount).toHaveBeenCalledWith('AMIRA.HASSAN@example.com')
    expect(auth.clearSession).toHaveBeenCalled()
    expect(router.replace).toHaveBeenCalledWith({ name: 'landing', query: { notice: 'account-deleted' } })
  })

  it('lists a teacher’s published classes instead of deleting', async () => {
    auth.activeRole = 'TEACHER'
    accountOpsApi.settings.mockResolvedValue(
      settings({
        notifications: { liveSessionReminders: true, feedback: null, contentReviews: true, reviewQueues: null },
        deletion: {
          blockedBy: 'PUBLISHED_CLASSES',
          publishedClasses: [{ id: 4, title: 'Mandarin for Beginners' }],
        },
      }),
    )
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="account-delete"]').trigger('click')

    const blocked = wrapper.find('[data-testid="account-blocked-dialog"]')
    expect(blocked.text()).toContain('Archive your published class first.')
    expect(blocked.text()).toContain('Mandarin for Beginners')
    expect(wrapper.find('[data-testid="account-delete-dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-pref-contentReviews"]').exists()).toBe(true)
  })

  it('offers admins no deletion, only the note about deactivation', async () => {
    auth.activeRole = 'ADMIN'
    accountOpsApi.settings.mockResolvedValue(
      settings({
        notifications: { liveSessionReminders: null, feedback: null, contentReviews: null, reviewQueues: true },
        deletion: { blockedBy: 'ADMIN_ACCOUNT', publishedClasses: [] },
      }),
    )
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="account-delete"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Admin accounts are deactivated by another admin in Users.')
    expect(wrapper.find('[data-testid="account-pref-reviewQueues"]').exists()).toBe(true)
  })

  it('changes the password and keeps this session with the new token', async () => {
    accountApi.changePassword.mockResolvedValue({ token: 'fresh-token' })
    const wrapper = await mountPage()

    await wrapper.find('#account-currentPassword').setValue('OldPassword1!')
    await wrapper.find('#account-newPassword').setValue('NewPassword1!')
    await wrapper.find('#account-confirmPassword').setValue('NewPassword1!')
    await wrapper.find('#account-password-form').trigger('submit')
    await flushPromises()

    expect(auth.replaceAccessToken).toHaveBeenCalledWith('fresh-token')
    expect(wrapper.find('[data-testid="toast"]').text()).toContain('Password changed.')
  })
})
