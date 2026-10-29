import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AdminAiSettings } from '@/types/ai'

const api = vi.hoisted(() => ({ settings: vi.fn(), updateSettings: vi.fn(), reportCounts: vi.fn() }))

vi.mock('@/api/ai', () => ({ adminAiApi: api }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminAiSettingsPage from './AdminAiSettingsPage.vue'

const SETTINGS: AdminAiSettings = {
  aiNoticeText: 'AI can make mistakes. Check important answers with your teacher.',
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
  requestsPerMinute: 10,
  requestsPerDay: 100,
  lastChangedAt: '2026-10-20T08:00:00Z',
  lastChangedBy: 'Admin Lee',
}

function mountPage() {
  return mount(AdminAiSettingsPage, { global: { stubs: { AdminAiTabs: true, IconGlyph: true, RouterLink: true } } })
}

describe('AdminAiSettingsPage', () => {
  beforeEach(() => {
    api.settings.mockResolvedValue(SETTINGS)
  })

  it('previews the AI notice and shows the request limits from the server', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('textarea').setValue('Check AI answers with your teacher.')

    expect(wrapper.get('[data-testid="ai-notice"]').text()).toBe('Check AI answers with your teacher.')
    expect(wrapper.get('.admin-ai-limits-box').text()).toContain('10')
    expect(wrapper.get('.admin-ai-limits-box').text()).toContain('100')
    expect(wrapper.text()).toContain('Admin Lee')
  })

  it('saves only after a change and sends the trimmed notice with both switches', async () => {
    api.updateSettings.mockResolvedValue({ ...SETTINGS, aiNoticeText: 'Check AI answers.', aiSafetyCheck: false })
    const wrapper = mountPage()
    await flushPromises()
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
    expect(save.element.disabled).toBe(true)
  })

  it('does not save an empty notice', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('textarea').setValue('   ')
    await wrapper.get('form').trigger('submit')

    expect(api.updateSettings).not.toHaveBeenCalled()
    expect(wrapper.find('.field-error').exists()).toBe(true)
  })
})
