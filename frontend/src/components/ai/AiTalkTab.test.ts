import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  xiaomeiReply: vi.fn(),
  xiaomeiSuggestions: vi.fn(),
  xiaomeiSummary: vi.fn(),
  report: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import AiTalkTab from './AiTalkTab.vue'

const OPENER = '你好！我是小美。今天过得怎么样？'

function mountTalk(blocked: 'limit' | 'unavailable' | null = null) {
  return mount(AiTalkTab, {
    props: { blocked },
    global: { plugins: [createPinia()], stubs: { IconGlyph: true, teleport: true } },
  })
}

async function typeAndSend(wrapper: ReturnType<typeof mountTalk>, text: string) {
  await wrapper.get('.ai-talk-type input').setValue(text)
  await wrapper.get('.ai-talk-type').trigger('submit')
  await flushPromises()
}

describe('AiTalkTab (Talk with Xiaomei)', () => {
  it('opens with Xiaomei greeting and, without speech recognition, lets the student type', () => {
    const wrapper = mountTalk()

    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe(OPENER)
    expect(wrapper.find('[data-testid="ai-talk-mic"]').exists()).toBe(false)
    expect(wrapper.find('.ai-talk-type input').exists()).toBe(true)
    // The scripted opener is not an AI answer, so it carries no AI label and cannot be reported.
    expect(wrapper.find('[data-testid="ai-result-label"]').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="ai-talk-end"]').element.disabled).toBe(true)
  })

  it('sends the reply with the earlier turns and shows her answer with a more natural version', async () => {
    api.xiaomeiReply.mockResolvedValue({
      status: 'SUCCESS',
      reply: { chinese: '太好了！你喜欢喝什么？', pinyin: 'Tài hǎo le!', translation: 'Great!' },
      naturalVersion: '我今天很好。',
      tip: 'Add 很 before an adjective.',
    })
    const wrapper = mountTalk()

    await typeAndSend(wrapper, '我今天好')

    expect(api.xiaomeiReply).toHaveBeenCalledWith({
      scenario: 'MEET_XIAOMEI',
      turns: [{ speaker: 'XIAOMEI', text: OPENER }],
      message: '我今天好',
      languagePreference: 'en',
    })
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe('太好了！你喜欢喝什么？')
    expect(wrapper.get('[data-testid="ai-talk-tip"]').text()).toContain('我今天很好。')
    expect(wrapper.find('[data-testid="ai-result-label"]').exists()).toBe(true)
    expect(wrapper.findAll('.ai-transcript li')).toHaveLength(3)
    expect(wrapper.emitted('used')).toHaveLength(1)
  })

  it('takes the reply back and keeps the typed text when SparkBot is unavailable', async () => {
    api.xiaomeiReply.mockResolvedValue({
      status: 'UNAVAILABLE',
      message: 'AI tutor is currently unavailable. Please try again later.',
      reply: null,
    })
    const wrapper = mountTalk()

    await typeAndSend(wrapper, '你好')

    expect(wrapper.findAll('.ai-transcript li')).toHaveLength(1)
    expect(wrapper.get<HTMLInputElement>('.ai-talk-type input').element.value).toBe('你好')
    expect(wrapper.get('.callout.is-danger').text()).toBe('AI tutor is currently unavailable. Please try again later.')
  })

  it('ends the conversation with a summary from the whole conversation', async () => {
    api.xiaomeiReply.mockResolvedValue({ status: 'SUCCESS', reply: { chinese: '我也很好！' } })
    api.xiaomeiSummary.mockResolvedValue({
      status: 'SUCCESS',
      goodbye: { chinese: '再见！下次见！', pinyin: 'Zàijiàn!', translation: 'Bye!' },
      feedback: 'You answered every question in Chinese.',
      newExpressions: [{ chinese: '下次见', pinyin: 'xià cì jiàn', translation: 'see you next time' }],
      wordsUsed: [{ chinese: '很', pinyin: 'hěn', translation: 'very' }],
    })
    const wrapper = mountTalk()
    await typeAndSend(wrapper, '我很好')

    await wrapper.get('[data-testid="ai-talk-end"]').trigger('click')
    await flushPromises()

    expect(api.xiaomeiSummary).toHaveBeenCalledWith({
      scenario: 'MEET_XIAOMEI',
      turns: [
        { speaker: 'XIAOMEI', text: OPENER },
        { speaker: 'STUDENT', text: '我很好' },
        { speaker: 'XIAOMEI', text: '我也很好！' },
      ],
      languagePreference: 'en',
    })
    const summary = wrapper.get('[data-testid="ai-talk-summary"]')
    expect(summary.text()).toContain('再见！下次见！')
    expect(summary.text()).toContain('You answered every question in Chinese.')
    expect(summary.text()).toContain('下次见')
  })

  it('asks before switching scenario once the student has replied', async () => {
    api.xiaomeiReply.mockResolvedValue({ status: 'SUCCESS', reply: { chinese: '好的。' } })
    const wrapper = mountTalk()
    await typeAndSend(wrapper, '你好')

    const cafe = wrapper.findAll('.ai-talk-scenario').find((button) => button.text() === 'Order at a café')!
    await cafe.trigger('click')

    expect(wrapper.find('[data-testid="confirm-dialog-confirm"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe('好的。')
  })

  it('cannot send while the daily limit is reached', () => {
    const wrapper = mountTalk('limit')

    expect(wrapper.get<HTMLInputElement>('.ai-talk-type input').element.disabled).toBe(true)
  })
})

/** Web Speech recognition stand-in for the voice replies. */
class FakeRecognition {
  static last: FakeRecognition | null = null
  lang = ''
  interimResults = false
  continuous = false
  maxAlternatives = 0
  onresult:
    | ((event: { resultIndex: number; results: { isFinal: boolean; 0: { transcript: string } }[] }) => void)
    | null = null
  onerror: ((event: { error: string }) => void) | null = null
  onend: (() => void) | null = null

  start(): void {
    FakeRecognition.last = this
  }

  stop(): void {
    this.onend?.()
  }

  abort(): void {}

  hear(text: string): void {
    this.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: text } }] })
  }
}

describe('AiTalkTab voice replies', () => {
  const scope = window as unknown as Record<string, unknown>

  beforeEach(() => {
    scope.webkitSpeechRecognition = FakeRecognition
    api.xiaomeiReply.mockResolvedValue({ status: 'SUCCESS', reply: { chinese: '真不错！' } })
  })

  afterEach(() => {
    delete scope.webkitSpeechRecognition
  })

  it('sends what the browser heard when the student clicks Send', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我今天很好')
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-talk-listening"]').text()).toContain('我今天很好')

    const send = wrapper.findAll('.ai-talk-listening-actions button')[1]!
    await send.trigger('click')
    await flushPromises()

    expect(api.xiaomeiReply).toHaveBeenCalledTimes(1)
    expect(api.xiaomeiReply.mock.calls[0]![0]).toMatchObject({ message: '我今天很好' })
    expect(wrapper.get('.ai-transcript li.is-student').text()).toContain('我今天很好')
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe('真不错！')
  })

  it('sends the reply when the student stops talking, as in a real conversation', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我很好')
    FakeRecognition.last!.stop()
    await flushPromises()

    expect(api.xiaomeiReply).toHaveBeenCalledTimes(1)
    expect(api.xiaomeiReply.mock.calls[0]![0]).toMatchObject({ message: '我很好' })
  })

  it('sends nothing when the student cancels', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我很')
    await flushPromises()
    await wrapper.findAll('.ai-talk-listening-actions button')[0]!.trigger('click')
    await flushPromises()

    expect(api.xiaomeiReply).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="ai-talk-listening"]').exists()).toBe(false)
  })
})
