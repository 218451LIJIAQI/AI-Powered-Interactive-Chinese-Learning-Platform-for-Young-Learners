import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({
  platformSettings: vi.fn(),
}))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => ({ hash: '' }),
}))

vi.mock('@/api/services', () => ({
  publicApi: {
    platformSettings: mocks.platformSettings,
  },
}))

import ResponsibleAiPage from './ResponsibleAiPage.vue'

async function mountPage() {
  const wrapper = mount(ResponsibleAiPage, {
    global: { stubs: { IconGlyph: true } },
  })
  await flushPromises()
  return wrapper
}

function dataSectionText(wrapper: VueWrapper): string {
  return wrapper.find('#responsible-ai-data').text()
}

const OPENAI_ONLY = /OpenAI|store=false|transcription/

describe('ResponsibleAiPage (FRPUB02)', () => {
  beforeEach(() => {
    mocks.platformSettings.mockReset()
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: null, aiProvider: 'openai' })
  })

  it('has the seven sections of the guidance in the rail', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Responsible AI guidance')
    expect(wrapper.findAll('.public-rail-link').map((link) => link.text())).toEqual([
      '1.Optional support for beginners',
      '2.AI can be wrong',
      '3.How answers are bounded',
      '4.Voice, speaking and handwriting',
      '5.What is sent to the AI provider',
      '6.Safety checks',
      '7.Report an answer',
    ])
  })

  it('says who Xiaomei is the first time she is named, and nothing about teachers', async () => {
    const wrapper = await mountPage()

    const text = wrapper.get('.legal-body').text()
    expect(text).toContain('Xiaomei (the speaking partner in AI Helper)')
    expect(text.indexOf('Xiaomei')).toBe(text.indexOf('Xiaomei (the speaking partner in AI Helper)'))
    expect(text).not.toMatch(/teacher/i)
  })

  it('says under the title when the guidance was last updated, in the reader’s language', async () => {
    const wrapper = await mountPage()

    const updated = () => wrapper.get('.page-title-copy h1 + p').text()
    expect(updated()).toBe('Last updated 19 November 2026')
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(updated()).toBe('Kemas kini terakhir: 19 November 2026')
    i18n.global.locale.value = 'zh-CN'
    await flushPromises()
    expect(updated()).toBe('最后更新：2026年11月19日')
  })

  it('follows the language switch in every section', async () => {
    const wrapper = await mountPage()

    i18n.global.locale.value = 'zh-CN'
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('负责任 AI 指南')
    expect(wrapper.get('#responsible-ai-limits').text()).toContain('小美（AI 助手里的口语伙伴）')
  })

  it('describes live call data in the public guidance when that deployment is configured', async () => {
    vi.stubEnv('VITE_TAVUS_DEPLOYMENT_ID', ' example ')
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: null, aiProvider: null })
    const wrapper = await mountPage()
    expect(dataSectionText(wrapper)).toContain('The AI text and photo service is currently unavailable.')
    expect(dataSectionText(wrapper)).toContain('Tavus receives the call audio and any recent messages')
  })

  it('quotes the AI notice an admin set, and the default notice in the reader’s language', async () => {
    const notice = (wrapper: VueWrapper) => wrapper.get('#responsible-ai-limits .legal-note').text()
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: ' Check AI answers with care. ', aiModelName: null })
    const adminWording = await mountPage()
    expect(notice(adminWording)).toBe('Shown with every AI answer: Check AI answers with care.')

    // The server stores and sends the platform default in English.
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: aiSafetyMessages.en.disclaimer, aiModelName: null })
    const defaultWording = await mountPage()
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(notice(defaultWording)).toBe(`Dipaparkan bersama setiap jawapan AI: ${aiSafetyMessages.ms.disclaimer}`)
  })
})

describe('ResponsibleAiPage AI provider wording (FRPUB02)', () => {
  beforeEach(() => {
    mocks.platformSettings.mockReset()
  })

  it('says what is sent (with the level and the lesson topic), then the OpenAI handling with the configured model', async () => {
    mocks.platformSettings.mockResolvedValue({
      aiDisclaimerText: 'Admin wording.',
      aiModelName: 'gpt-test-model',
      aiProvider: 'openai',
    })

    const wrapper = await mountPage()

    expect(dataSectionText(wrapper)).toContain(
      'The request uses the text or handwriting photo you submit, plus relevant context such as your level and the lesson topic.',
    )
    expect(dataSectionText(wrapper)).toContain('OpenAI Responses API (model gpt-test-model);')
    expect(dataSectionText(wrapper)).toContain('store=false')
    expect(dataSectionText(wrapper)).toContain('sends the recording and the target text to Tencent Cloud')
    expect(mocks.platformSettings).toHaveBeenCalledOnce()
  })

  it.each([['openai'], ['deepseek']])(
    'says that Talk with Xiaomei also sends the plan goal and the notes she remembers (%s)',
    async (provider) => {
      mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: null, aiProvider: provider })

      const wrapper = await mountPage()

      expect(dataSectionText(wrapper)).toContain(
        'In Talk with Xiaomei, also your plan goal and the short notes she remembers (forget them in any chat summary).',
      )
      i18n.global.locale.value = 'ms'
      await flushPromises()
      expect(dataSectionText(wrapper)).toContain('Dalam Berbual dengan Xiaomei, juga matlamat pelan anda')
    },
  )

  it('names no model when none is configured', async () => {
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: null, aiProvider: 'openai' })

    const wrapper = await mountPage()

    expect(dataSectionText(wrapper)).toContain('OpenAI Responses API; your browser never contacts the provider')
    expect(dataSectionText(wrapper)).not.toContain('(model')
  })

  it.each([
    ['deepseek', 'the Chat Completions API of DeepSeek (model deepseek-chat)', 'How long DeepSeek keeps'],
    [
      'openai-compatible',
      'the Chat Completions API of an OpenAI-compatible AI service (model deepseek-chat)',
      'How long an OpenAI-compatible AI service keeps',
    ],
  ])('makes no OpenAI promise for the %s provider, which only receives text', async (provider, target, terms) => {
    mocks.platformSettings.mockResolvedValue({
      aiDisclaimerText: 'x',
      aiModelName: 'deepseek-chat',
      aiProvider: provider,
    })

    const wrapper = await mountPage()

    const text = dataSectionText(wrapper)
    expect(text).toContain(
      'The request uses the text you submit, plus relevant context such as your level and the lesson topic.',
    )
    expect(text).toContain(`Requests go from our server to ${target};`)
    expect(text).toContain('sends the recording and the target text to Tencent Cloud')
    expect(text).toContain(terms)
    expect(text.replaceAll('OpenAI-compatible', '')).not.toMatch(OPENAI_ONLY)
  })

  it('distinguishes the text and photo outage from recording handling', async () => {
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'gpt-test-model', aiProvider: null })

    const wrapper = await mountPage()

    expect(dataSectionText(wrapper)).toContain('The AI text and photo service is currently unavailable.')
    expect(dataSectionText(wrapper)).not.toMatch(OPENAI_ONLY)
    i18n.global.locale.value = 'zh-CN'
    await flushPromises()
    expect(dataSectionText(wrapper)).toContain('AI 文字和照片服务暂不可用。')
    expect(dataSectionText(wrapper)).toContain('将录音和目标文字发送给腾讯云')
  })

  it('names no provider when the settings cannot be loaded, in every language', async () => {
    mocks.platformSettings.mockRejectedValue(new Error('offline'))

    const wrapper = await mountPage()

    expect(dataSectionText(wrapper)).toContain('When the AI Helper is connected to an AI provider')
    expect(dataSectionText(wrapper)).not.toMatch(OPENAI_ONLY)
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(dataSectionText(wrapper)).toContain('Apabila Pembantu AI disambungkan kepada penyedia AI')
    expect(dataSectionText(wrapper)).not.toMatch(OPENAI_ONLY)
    i18n.global.locale.value = 'zh-CN'
    await flushPromises()
    expect(dataSectionText(wrapper)).toContain('当 AI 助手连接到 AI 提供方时')
    expect(dataSectionText(wrapper)).not.toMatch(OPENAI_ONLY)
  })
})
