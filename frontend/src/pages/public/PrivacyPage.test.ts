import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({ platformSettings: vi.fn() }))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => ({ hash: '' }),
}))
vi.mock('@/api/services', () => ({ publicApi: { platformSettings: mocks.platformSettings } }))

import PrivacyPage from './PrivacyPage.vue'

const OPENAI_ONLY = /OpenAI Responses|store=false|to OpenAI/

async function mountPage(): Promise<ReturnType<typeof mount>> {
  const wrapper = mount(PrivacyPage)
  await flushPromises()
  return wrapper
}

const text = (wrapper: ReturnType<typeof mount>) => wrapper.get('#ai-provider').text()

beforeEach(() => {
  mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'm', aiProvider: 'openai' })
})

describe('PrivacyPage', () => {
  it('has the nine sections of the notice and says when it last changed', async () => {
    const wrapper = await mountPage()

    expect(wrapper.findAll('.public-rail-link')).toHaveLength(9)
    expect(wrapper.get('.page-title-copy h1 + p').text()).toBe('Last updated 19 November 2026')
    expect(wrapper.text()).not.toMatch(/teacher|verification|Help & support/i)
  })

  it('points privacy questions to the Contact us form on the Help page', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('#privacy-contact').text()).toContain('Use the Contact us form on the Help page.')
  })

  it('lists the messages sent through the email service', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('#privacy-third-parties').text()).toContain(
      'account emails, support replies and the optional daily summary for admins.',
    )
  })
})

describe('PrivacyPage AI provider paragraph', () => {
  it('describes the OpenAI handling when the server uses OpenAI', async () => {
    const wrapper = await mountPage()

    expect(text(wrapper)).toContain('plus your level and the lesson topic')
    expect(text(wrapper)).toContain('to the OpenAI Responses API through our server')
    expect(text(wrapper)).toContain('store=false')
    expect(text(wrapper)).toContain('sends the recording and the target text to Tencent Cloud')
  })

  it('names a Chat Completions provider and promises only what holds for it', async () => {
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'm', aiProvider: 'deepseek' })

    const wrapper = await mountPage()

    expect(text(wrapper)).toContain(
      'the text you submit, plus your level and the lesson topic, is sent to DeepSeek through our server',
    )
    expect(text(wrapper)).toContain('sends the recording and the target text to Tencent Cloud')
    expect(text(wrapper)).not.toMatch(OPENAI_ONLY)
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(text(wrapper)).toContain('kepada DeepSeek melalui pelayan kami')
  })

  it('shows recording handling separately while the text and photo service is unavailable', async () => {
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'm', aiProvider: null })

    const wrapper = await mountPage()

    expect(text(wrapper)).toContain('The AI text and photo service is currently unavailable.')
    expect(text(wrapper)).toContain('sends the recording and the target text to Tencent Cloud')
    expect(text(wrapper)).not.toMatch(OPENAI_ONLY)
  })

  it('names no provider before the settings answer or when they fail', async () => {
    mocks.platformSettings.mockRejectedValue(new Error('offline'))

    const wrapper = await mountPage()

    expect(text(wrapper)).toContain('When the AI Helper is connected to an AI provider')
    expect(text(wrapper)).toContain('plus your level and the lesson topic')
    expect(text(wrapper)).not.toMatch(OPENAI_ONLY)
  })

  it.each([['openai'], ['deepseek'], ['unknown']])(
    'says what Talk with Xiaomei adds and how to forget it (%s)',
    async (provider) => {
      if (provider === 'unknown') mocks.platformSettings.mockRejectedValue(new Error('offline'))
      else mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'm', aiProvider: provider })

      const wrapper = await mountPage()

      const paragraphs = wrapper.findAll('#ai-provider p').map((paragraph) => paragraph.text())
      expect(paragraphs.at(-1)).toBe(
        'Talk with Xiaomei also sends your plan goal and the short notes she remembers from earlier chats; you can forget them in any chat summary.',
      )
      i18n.global.locale.value = 'zh-CN'
      await flushPromises()
      expect(text(wrapper)).toContain('“和小美聊天”还会发送你的计划目标')
    },
  )

  it('omits Xiaomei’s notes while AI text requests are disabled', async () => {
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'm', aiProvider: null })

    const wrapper = await mountPage()

    expect(text(wrapper)).not.toContain('Xiaomei')
  })

  it('discloses the live call provider independently of the text service', async () => {
    vi.stubEnv('VITE_TAVUS_DEPLOYMENT_ID', ' example ')
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'x', aiModelName: 'm', aiProvider: null })
    const wrapper = await mountPage()
    expect(text(wrapper)).toContain('The AI text and photo service is currently unavailable.')
    expect(text(wrapper)).toContain('Tavus receives the call audio and any recent messages')
    expect(text(wrapper)).toContain('Your camera starts off.')
  })
})
