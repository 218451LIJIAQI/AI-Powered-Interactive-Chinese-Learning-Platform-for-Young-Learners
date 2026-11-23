import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/plugins/i18n'
import { buttonNamed } from '@/test/helpers'
import { storageKeys } from '@/utils/storage'

import LanguageSwitcher from './LanguageSwitcher.vue'

const mocks = vi.hoisted(() => ({ updateProfile: vi.fn() }))

vi.mock('@/api/ops', () => ({ accountOpsApi: { updateProfile: mocks.updateProfile } }))

describe('LanguageSwitcher', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.updateProfile.mockReset()
  })

  it('switches the interface and, for a signed-in account, keeps the choice on the account', async () => {
    const auth = useAuthStore()
    auth.currentUser = {
      id: 7,
      email: 'aina@example.com',
      fullName: 'Aina',
      roles: ['STUDENT'],
      preferredLanguage: 'en',
    }
    mocks.updateProfile.mockResolvedValue({ ...auth.currentUser, preferredLanguage: 'zh-CN' })
    const wrapper = mount(LanguageSwitcher)

    await buttonNamed(wrapper, '中文').trigger('click')

    expect(i18n.global.locale.value).toBe('zh-CN')
    expect(buttonNamed(wrapper, '中文').attributes('aria-pressed')).toBe('true')
    expect(mocks.updateProfile).toHaveBeenCalledExactlyOnceWith({ preferredLanguage: 'zh-CN' })
  })

  it('only switches the interface (and this browser’s choice) when nobody is signed in', async () => {
    const wrapper = mount(LanguageSwitcher)

    await buttonNamed(wrapper, 'BM').trigger('click')

    expect(i18n.global.locale.value).toBe('ms')
    expect(window.localStorage.getItem(storageKeys.language)).toBe('ms')
    expect(mocks.updateProfile).not.toHaveBeenCalled()
  })
})
