import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({
  platformSettings: vi.fn(),
}))

vi.mock('@/api/services', () => ({
  publicApi: {
    platformSettings: (...args: unknown[]) => mocks.platformSettings(...args),
  },
}))

import AiDisclaimer from './AiDisclaimer.vue'

function mountDisclaimer(props: InstanceType<typeof AiDisclaimer>['$props'] = {}) {
  return mount(AiDisclaimer, { props, global: { plugins: [i18n], stubs: { IconGlyph: true, RouterLink: true } } })
}

describe('AiDisclaimer (FR-PUB-04)', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('shows the admin-configured disclaimer next to AI tools that pass no text, loading it once', async () => {
    mocks.platformSettings.mockResolvedValue({ platformName: 'MandarinSpark', aiDisclaimerText: ' Admin wording. ' })

    const first = mountDisclaimer()
    const second = mountDisclaimer()
    await flushPromises()

    expect(first.text()).toContain('Admin wording.')
    expect(second.text()).toContain('Admin wording.')
    expect(mocks.platformSettings).toHaveBeenCalledOnce()
  })

  it('uses the text passed by the parent without loading the settings', async () => {
    mocks.platformSettings.mockClear()
    const wrapper = mountDisclaimer({ text: 'Parent wording.' })
    await flushPromises()

    expect(wrapper.text()).toContain('Parent wording.')
    expect(mocks.platformSettings).not.toHaveBeenCalled()
  })
})
