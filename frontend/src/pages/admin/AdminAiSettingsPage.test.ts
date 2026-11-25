import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { deferred } from '@/test/helpers'
import type { AdminAiSettings } from '@/types/ai'

const api = vi.hoisted(() => ({ settings: vi.fn(), updateSettings: vi.fn(), hintPolicy: vi.fn() }))

vi.mock('@/api/ai', () => ({ adminAiApi: api }))

import AdminAiSettingsPage from './AdminAiSettingsPage.vue'

const SETTINGS: AdminAiSettings = {
  aiNoticeText: 'AI can make mistakes. Check important answers.',
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
  requestsPerMinute: 10,
  requestsPerDay: 100,
  lastChangedAt: '2026-10-20T08:00:00Z',
  lastChangedBy: 'Admin Lee',
}

async function mountPage() {
  const wrapper = mount(AdminAiSettingsPage, {
    global: { stubs: { AdminAiTabs: true, IconGlyph: true, RouterLink: true, teleport: true } },
  })
  await flushPromises()
  return wrapper
}

describe('AdminAiSettingsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.settings.mockResolvedValue(SETTINGS)
    api.hintPolicy.mockResolvedValue([{ tier: 'HSK1', delaySeconds: 15, perSet: 0 }])
  })

  it('shows the request limits from the server and who changed the settings last', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('10 requests a minute · 100 a day per user')
    expect(wrapper.text()).toContain('Admin Lee')
  })

  it('shows the never-changed footer when settings have not been updated yet', async () => {
    api.settings.mockResolvedValue({ ...SETTINGS, lastChangedAt: null, lastChangedBy: null })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Not changed since the platform was set up.')
  })

  it('retries loading settings after an initial load error', async () => {
    api.settings.mockRejectedValueOnce(new Error('Service down')).mockResolvedValueOnce(SETTINGS)
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('AI settings could not be loaded.')
    await wrapper.get('.state-actions button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('10 requests a minute · 100 a day per user')
  })

  it('saves only after a change and sends the trimmed notice with both switches', async () => {
    api.updateSettings.mockResolvedValue({ ...SETTINGS, aiNoticeText: 'Check AI answers.', aiSafetyCheck: false })
    const wrapper = await mountPage()
    const save = wrapper.get<HTMLButtonElement>('button[type="submit"]')
    expect(save.element.disabled).toBe(true)

    await wrapper.get('textarea').setValue('  Check AI answers.  ')
    await wrapper.findAll('input[role="switch"]')[1]!.setValue(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.updateSettings).toHaveBeenCalledWith({
      aiNoticeText: 'Check AI answers.',
      sensitiveWordFilter: true,
      aiSafetyCheck: false,
    })
    expect(wrapper.text()).toContain('Settings saved.')
    expect(save.element.disabled).toBe(true)
  })

  it('does not save an empty notice', async () => {
    const wrapper = await mountPage()

    await wrapper.get('textarea').setValue('   ')
    await wrapper.get('form').trigger('submit')

    expect(api.updateSettings).not.toHaveBeenCalled()
    expect(wrapper.find('.field-error').exists()).toBe(true)
    expect(wrapper.get('textarea').attributes('aria-invalid')).toBe('true')

    await wrapper.get('textarea').setValue('Check AI answers against your lesson.')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(wrapper.get('textarea').attributes('aria-invalid')).toBeUndefined()
  })

  it('says the notice is missing as soon as the emptied field is left, and links the message to it', async () => {
    const wrapper = await mountPage()
    const notice = wrapper.get('textarea')

    await notice.setValue('')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    await notice.trigger('blur')

    const error = wrapper.get('.field-error')
    expect(error.text()).toBe('Write the AI notice text.')
    expect(notice.attributes('aria-describedby')).toBe(`${error.attributes('id')} ${notice.attributes('id')}-hint`)
  })

  it('shows the missing-notice error in the language the admin switches to', async () => {
    const wrapper = await mountPage()
    await wrapper.get('textarea').setValue('')
    await wrapper.get('form').trigger('submit')

    i18n.global.locale.value = 'ms'
    await flushPromises()

    expect(wrapper.get('.field-error').text()).toBe('Tulis teks notis AI.')
  })

  it('normalizes the retired teacher notice to the current default in the editor', async () => {
    api.settings.mockResolvedValue({
      ...SETTINGS,
      aiNoticeText: 'AI responses may contain errors and do not replace your teacher.',
    })
    const wrapper = await mountPage()

    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('AI can make mistakes.')
    expect(wrapper.get<HTMLButtonElement>('button[type="submit"]').element.disabled).toBe(true)
  })

  it.each(['success', 'failure'])(
    'holds the submitted settings until save %s and then enables editing',
    async (outcome) => {
      const save = deferred<AdminAiSettings>()
      api.updateSettings.mockReturnValue(save.promise)
      const wrapper = await mountPage()
      await wrapper.get('textarea').setValue('Submitted notice.')
      await wrapper.get('form').trigger('submit')

      expect(wrapper.get<HTMLTextAreaElement>('textarea').element.disabled).toBe(true)
      for (const toggle of wrapper.findAll<HTMLInputElement>('input[role="switch"]')) {
        expect(toggle.element.disabled).toBe(true)
      }
      if (outcome === 'success') save.resolve({ ...SETTINGS, aiNoticeText: 'Submitted notice.' })
      else save.reject(new Error('Save failed'))
      await flushPromises()

      expect(wrapper.get<HTMLTextAreaElement>('textarea').element.disabled).toBe(false)
      for (const toggle of wrapper.findAll<HTMLInputElement>('input[role="switch"]')) {
        expect(toggle.element.disabled).toBe(false)
      }
      if (outcome === 'failure') {
        expect(wrapper.get('.data-notice[role="alert"]').text()).toContain('The settings could not be saved.')
      }
      expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('Submitted notice.')
      await wrapper.get('textarea').setValue('Next edit.')
      expect(wrapper.get<HTMLButtonElement>('button[type="submit"]').element.disabled).toBe(false)
    },
  )
})
