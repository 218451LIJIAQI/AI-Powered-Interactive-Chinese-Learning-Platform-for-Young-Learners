import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  platformSettings: vi.fn(),
}))

vi.mock('@/api/services', () => ({
  publicApi: {
    platformSettings: mocks.platformSettings,
  },
}))

type AiDisclaimerComponent = (typeof import('./AiDisclaimer.vue'))['default']

let AiDisclaimer: AiDisclaimerComponent

function mountDisclaimer() {
  return mount(AiDisclaimer, { global: { stubs: { IconGlyph: true, RouterLink: true } } })
}

describe('AiDisclaimer (FRAI08, FRADM09)', () => {
  beforeEach(async () => {
    mocks.platformSettings.mockReset()
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: ' Admin wording. ' })
    // The component caches the loaded disclaimer at module level, so every test gets a fresh copy of the module.
    vi.resetModules()
    AiDisclaimer = (await import('./AiDisclaimer.vue')).default
  })

  it('shows the admin-configured disclaimer next to AI tools, loading it once', async () => {
    const first = mountDisclaimer()
    const second = mountDisclaimer()
    await flushPromises()

    expect(first.text()).toContain('Admin wording.')
    expect(second.text()).toContain('Admin wording.')
    expect(mocks.platformSettings).toHaveBeenCalledOnce()
  })
})
