import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({
  platformSettings: vi.fn(),
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRouter: () => ({ back: vi.fn(), push: vi.fn(), options: { history: { state: {} } } }),
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: false, activeRole: null }) }))

vi.mock('@/api/services', () => ({
  publicApi: {
    platformSettings: mocks.platformSettings,
  },
}))

type ResponsibleAiPageComponent = (typeof import('./ResponsibleAiPage.vue'))['default']

let ResponsibleAiPage: ResponsibleAiPageComponent

function mountPage() {
  return mount(ResponsibleAiPage, {
    global: { stubs: { IconGlyph: true } },
  })
}

function dataSectionText(wrapper: ReturnType<typeof mountPage>): string {
  return wrapper.find('#responsible-ai-data').text()
}

describe('ResponsibleAiPage AI model wording (FRPUB02)', () => {
  beforeEach(async () => {
    mocks.platformSettings.mockReset()
    // The public settings are cached at module level, so every test gets a fresh copy of the modules.
    vi.resetModules()
    ResponsibleAiPage = (await import('./ResponsibleAiPage.vue')).default
  })

  it('names the AI model configured on the server', async () => {
    mocks.platformSettings.mockResolvedValue({
      aiDisclaimerText: 'Admin wording.',
      aiModelName: ' gpt-test-model ',
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(dataSectionText(wrapper)).toContain('OpenAI Responses API (model gpt-test-model);')
    expect(mocks.platformSettings).toHaveBeenCalledOnce()
  })

  it.each([
    ['no model is configured', { aiDisclaimerText: 'x', aiModelName: null }],
    ['the field is missing', { aiDisclaimerText: 'x' }],
  ])('names no model when %s', async (_case, settings) => {
    mocks.platformSettings.mockResolvedValue(settings)

    const wrapper = mountPage()
    await flushPromises()

    expect(dataSectionText(wrapper)).toContain('OpenAI Responses API; your browser never contacts the provider')
    expect(dataSectionText(wrapper)).not.toContain('(model')
  })

  it('names no model when the settings cannot be loaded, in every language', async () => {
    mocks.platformSettings.mockRejectedValue(new Error('offline'))

    const wrapper = mountPage()
    await flushPromises()

    expect(dataSectionText(wrapper)).not.toContain('(model')
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(dataSectionText(wrapper)).toContain('OpenAI Responses API; pelayar anda')
    i18n.global.locale.value = 'zh-CN'
    await flushPromises()
    expect(dataSectionText(wrapper)).toContain('OpenAI Responses API；你的浏览器')
  })
})
