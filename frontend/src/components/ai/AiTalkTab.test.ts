import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, ref } from 'vue'

import { publicApi } from '@/api/services'
import { i18n } from '@/plugins/i18n'
import { FakeRecognition, stubSpeech } from '@/test/fakeSpeech'
import { buttonNamed, deferred } from '@/test/helpers'
import type { XiaomeiMemoryNote, XiaomeiReply, XiaomeiSummary } from '@/types/ai'

const api = vi.hoisted(() => ({
  xiaomeiReply: vi.fn(),
  xiaomeiSummary: vi.fn(),
  xiaomeiMemory: vi.fn(),
  forgetXiaomeiNote: vi.fn(),
  restoreXiaomeiNote: vi.fn(),
  report: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('@tavus/embed', () => ({}))

import AiTalkTab from './AiTalkTab.vue'

const OPENER = '你好！我是小美。今天过得怎么样？'

function mountTalk(blocked: 'limit' | 'unavailable' | null = null, tavusDeploymentId?: string) {
  return mount(AiTalkTab, {
    props: { blocked, aiNotice: 'AI can make mistakes.', tavusDeploymentId },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
}

async function typeAndSend(wrapper: ReturnType<typeof mountTalk>, text: string) {
  await wrapper.get('.ai-talk-type input').setValue(text)
  await wrapper.get('.ai-talk-type').trigger('submit')
  await flushPromises()
}

/** A summary needs two replies. */
async function endAfterTwoReplies(wrapper: ReturnType<typeof mountTalk>, text: string) {
  await typeAndSend(wrapper, text)
  await typeAndSend(wrapper, '谢谢')
  await wrapper.get('[data-testid="ai-talk-end"]').trigger('click')
  await flushPromises()
}

function summaryWith(goodbye: string): XiaomeiSummary {
  return { status: 'SUCCESS', goodbye: { chinese: goodbye }, feedback: null, newExpressions: [], wordsUsed: [] }
}

function replyWith(chinese: string): XiaomeiReply {
  return { status: 'SUCCESS', reply: { chinese } }
}

beforeEach(() => {
  Object.values(api).forEach((fn) => fn.mockReset())
})

describe('AiTalkTab (Talk with Xiaomei)', () => {
  it('opens with Xiaomei greeting and, without speech recognition, lets the student type', () => {
    const wrapper = mountTalk()

    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe(OPENER)
    expect(wrapper.find('[data-testid="ai-talk-mic"]').exists()).toBe(false)
    expect(wrapper.find('.ai-talk-type input').exists()).toBe(true)
    expect(wrapper.find('tavus-embed').exists()).toBe(false)
    // The scripted opener is not an AI answer, so it carries no AI label and cannot be reported.
    expect(wrapper.find('[data-testid="ai-result-label"]').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="ai-talk-end"]').element.disabled).toBe(true)
  })

  it('starts live on the original microphone, shares the scenario and records the call in the same transcript', async () => {
    const wrapper = mountTalk('limit', 'demo-deployment')
    expect(wrapper.get('[data-testid="ai-talk-status"]').text()).toBe('Click the mic to talk with Xiaomei!')
    expect(wrapper.findAll('.ai-talk-voice-dock button')).toHaveLength(1)

    await buttonNamed(wrapper, 'Order at a café').trigger('click')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()

    const video = wrapper.get('.ai-talk-live-video tavus-embed')
    const greeting = '你好！欢迎光临。你想喝点儿什么？'
    expect(video.attributes('deployment-id')).toBe('demo-deployment')
    expect(video.attributes('conversational-context')).toContain('Order at a café')
    expect(video.attributes('custom-greeting')).toBe(greeting)
    expect(video.attributes('camera-on-start')).toBe('off')
    expect(wrapper.get('.ai-talk-current-scenario').text()).toBe('Order at a café')
    expect(wrapper.find('.ai-talk-scenarios .filter-chips').exists()).toBe(false)
    expect(wrapper.get('.ai-talk-live-controls [role="status"]').text()).toBe('Connecting to Xiaomei…')
    expect(wrapper.find('.xiaomei-live-placeholder .ai-xiaomei-avatar').exists()).toBe(true)
    const startConversation = vi.fn().mockResolvedValue(undefined)
    const endConversation = vi.fn().mockResolvedValue(undefined)
    Object.assign(video.element, { startConversation, endConversation })
    video.element.dispatchEvent(new CustomEvent('tavus:ready'))
    expect(startConversation).toHaveBeenCalledOnce()
    video.element.dispatchEvent(new CustomEvent('tavus:conversation-started'))

    const speak = (role: string, speech: string, final?: boolean) =>
      video.element.dispatchEvent(
        new CustomEvent('tavus:protocol-message', {
          detail: { event_type: 'conversation.utterance', properties: { role, speech, final } },
        }),
      )
    speak('replica', greeting)
    speak('user', '我想要', false)
    speak('user', '我想要一杯奶茶。')
    const longReply = '你想喝冰的还是热的？'.repeat(40)
    speak('replica', longReply)
    await flushPromises()
    expect(wrapper.find('.xiaomei-live-placeholder').exists()).toBe(false)
    expect(wrapper.get('.ai-talk-live-controls [role="status"]').text()).toBe('Live with Xiaomei')
    expect(wrapper.findAll('.ai-talk-rail .ai-transcript-line').map((line) => line.text())).toEqual([
      greeting,
      '我想要一杯奶茶。',
      longReply,
    ])

    await buttonNamed(wrapper.get('.ai-talk-live-controls'), 'Hang up').trigger('click')

    expect(endConversation).toHaveBeenCalledOnce()
    expect(wrapper.find('tavus-embed').exists()).toBe(false)
    expect(buttonNamed(wrapper, 'Order at a café').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe(longReply)
    expect(wrapper.findAll('.ai-talk-rail .ai-transcript-line')).toHaveLength(3)
  })

  it('opens the typed composer when the browser blocks the live call, and lets the learner retry in one click', async () => {
    const wrapper = mountTalk(null, 'demo-deployment')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()
    const video = wrapper.get('tavus-embed')
    const endConversation = vi.fn().mockResolvedValue(undefined)
    Object.assign(video.element, { endConversation })

    video.element.dispatchEvent(new CustomEvent('tavus:error', { detail: { code: 'MICROPHONE_BLOCKED_BY_PAGE' } }))
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Chrome or Edge')
    expect(endConversation).toHaveBeenCalledOnce()
    expect(wrapper.find('tavus-embed').exists()).toBe(false)
    expect(wrapper.get('.ai-talk-type input').attributes('disabled')).toBeUndefined()

    await buttonNamed(wrapper, 'Try the microphone again').trigger('click')
    await flushPromises()
    expect(wrapper.find('.ai-talk-live-video tavus-embed').exists()).toBe(true)
  })

  it('falls back to typing when Tavus transitions to unavailable via tavus:mode-change', async () => {
    const wrapper = mountTalk(null, 'demo-deployment')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()

    wrapper
      .get('tavus-embed')
      .element.dispatchEvent(new CustomEvent('tavus:mode-change', { detail: { mode: 'unavailable' } }))
    await flushPromises()

    expect(wrapper.find('tavus-embed').exists()).toBe(false)
    expect(wrapper.get('.ai-talk-type input').attributes('disabled')).toBeUndefined()
  })

  it('continues from earlier typed turns when the learner switches to live voice', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('你想喝什么？'))
    const wrapper = mountTalk(null, 'demo-deployment')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()
    wrapper
      .get('tavus-embed')
      .element.dispatchEvent(new CustomEvent('tavus:error', { detail: { code: 'MEDIA_ERROR' } }))
    await flushPromises()
    await typeAndSend(wrapper, '我有点渴。')

    await buttonNamed(wrapper, 'Try the microphone again').trigger('click')
    await flushPromises()
    const video = wrapper.get('.ai-talk-live-video tavus-embed')
    expect(video.attributes('custom-greeting')).toBe('我们接着聊吧。')
    expect(video.attributes('conversational-context')).toContain('我有点渴。')
    expect(video.attributes('conversational-context')).toContain('你想喝什么？')
  })

  it('redacts personal identifiers from earlier turns before sending live-call context to Tavus', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('好的。'))
    const wrapper = mountTalk(null, 'demo-deployment')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()
    wrapper
      .get('tavus-embed')
      .element.dispatchEvent(new CustomEvent('tavus:error', { detail: { code: 'MEDIA_ERROR' } }))
    await flushPromises()
    await typeAndSend(wrapper, 'Contact me at me@example.com or 012-345-6789, IC 990101-14-1234.')

    await buttonNamed(wrapper, 'Try the microphone again').trigger('click')
    await flushPromises()
    const context = wrapper.get('.ai-talk-live-video tavus-embed').attributes('conversational-context') ?? ''
    expect(context).not.toContain('me@example.com')
    expect(context).not.toContain('012-345-6789')
    expect(context).not.toContain('990101-14-1234')
    expect(context).toContain('[EMAIL]')
    expect(context).toContain('[PHONE]')
    expect(context).toContain('[ID_NUMBER]')
  })

  it('ends live video when leaving the kept-alive Talk tab', async () => {
    const isTalk = ref(true)
    const OtherTab = defineComponent(() => () => h('div', 'Other tab'))
    const Tabs = defineComponent(
      () => () =>
        h(KeepAlive, null, () =>
          isTalk.value
            ? h(AiTalkTab, { blocked: null, aiNotice: 'AI can make mistakes.', tavusDeploymentId: 'demo-deployment' })
            : h(OtherTab),
        ),
    )
    const wrapper = mount(Tabs, { global: { stubs: { IconGlyph: true } } })
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()
    const endConversation = vi.fn().mockResolvedValue(undefined)
    Object.assign(wrapper.get('tavus-embed').element, { endConversation })

    isTalk.value = false
    await flushPromises()
    expect(endConversation).toHaveBeenCalledOnce()

    isTalk.value = true
    await flushPromises()
    expect(wrapper.find('tavus-embed').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-talk-mic"]').exists()).toBe(true)
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
    const speech = stubSpeech([{ lang: 'zh-HK' }])
    api.xiaomeiReply.mockResolvedValue(replyWith('我也很好！'))
    api.xiaomeiSummary.mockResolvedValue({
      status: 'SUCCESS',
      goodbye: { chinese: '再见！下次见！', pinyin: 'Zàijiàn!', translation: 'Bye!' },
      feedback: 'You answered every question in Chinese.',
      newExpressions: [{ chinese: '下次见', pinyin: 'xià cì jiàn', translation: 'see you next time' }],
      wordsUsed: [{ chinese: '很', pinyin: 'hěn', translation: 'very' }],
    })
    const wrapper = mountTalk()
    expect(wrapper.find('.ai-talk-voice').exists()).toBe(false)
    await endAfterTwoReplies(wrapper, '我很好')

    expect(api.xiaomeiSummary).toHaveBeenCalledWith({
      scenario: 'MEET_XIAOMEI',
      turns: [
        { speaker: 'XIAOMEI', text: OPENER },
        { speaker: 'STUDENT', text: '我很好' },
        { speaker: 'XIAOMEI', text: '我也很好！' },
        { speaker: 'STUDENT', text: '谢谢' },
        { speaker: 'XIAOMEI', text: '我也很好！' },
      ],
      languagePreference: 'en',
    })
    const summary = wrapper.get('[data-testid="ai-talk-summary"]')
    expect(summary.text()).toContain('再见！下次见！')
    expect(summary.text()).toContain('You answered every question in Chinese.')
    expect(summary.text()).toContain('下次见')
    expect(summary.find('.ai-expression-listen').exists()).toBe(false)
    speech.setVoices([{ lang: 'cmn' }])
    await flushPromises()
    await summary.get('.ai-expression-listen').trigger('click')
    expect(speech.utterance().text).toBe('下次见')
  })

  it('keeps live-video data sharing in the guidance on the conversation summary', async () => {
    vi.spyOn(publicApi, 'platformSettings').mockRejectedValue(new Error('offline'))
    api.xiaomeiReply.mockResolvedValue(replyWith('你好！'))
    api.xiaomeiSummary.mockResolvedValue(summaryWith('再见！'))
    const wrapper = mountTalk(null, 'demo-deployment')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()
    wrapper.get('tavus-embed').element.dispatchEvent(new CustomEvent('tavus:error'))
    await flushPromises()
    await endAfterTwoReplies(wrapper, '你好')

    await wrapper.get('[data-testid="ai-responsible-link"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-responsible-drawer"]').text()).toContain('Tavus receives the call audio')
  })

  it('says the summary failed, not that Xiaomei could not reply, when SparkBot answers without a summary', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('我也很好！'))
    api.xiaomeiSummary.mockResolvedValue({ status: 'FAILED', message: null, newExpressions: [], wordsUsed: [] })
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我很好')

    const alert = wrapper.get('[data-testid="ai-talk-summary"]').get('[role="alert"]')
    expect(alert.text()).toContain('Xiaomei couldn’t write the summary. Please try again.')
  })

  it('offers the summary after two replies, and says so under End conversation', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('好的。'))
    const wrapper = mountTalk()
    const end = () => wrapper.get<HTMLButtonElement>('[data-testid="ai-talk-end"]')
    const hint = () => wrapper.get('[data-testid="ai-talk-end-hint"]')

    expect(hint().text()).toBe('Reply twice to get a summary.')
    expect(end().attributes('aria-describedby')).toBe(hint().attributes('id'))
    await typeAndSend(wrapper, '你好')
    expect(end().element.disabled).toBe(true)

    await typeAndSend(wrapper, '谢谢')
    expect(end().element.disabled).toBe(false)
    expect(hint().text()).toBe('Two replies done. End it to see your summary.')
    expect(end().attributes('aria-describedby')).toBe(hint().attributes('id'))
  })

  it('asks before switching scenario once the student has replied', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('好的。'))
    const wrapper = mountTalk()
    await typeAndSend(wrapper, '你好')

    await buttonNamed(wrapper, 'Order at a café').trigger('click')

    expect(wrapper.find('[data-testid="confirm-dialog-confirm"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe('好的。')
  })

  it('cannot send while the daily limit is reached', () => {
    const wrapper = mountTalk('limit')

    expect(wrapper.get<HTMLInputElement>('.ai-talk-type input').element.disabled).toBe(true)
  })

  it('shows her pinyin and English from the start, hides them on request, and the opener cannot be reported', async () => {
    const wrapper = mountTalk()
    const tools = () => wrapper.findAll('.ai-talk-tools button').map((button) => button.text())

    expect(wrapper.text()).toContain('Nǐ hǎo! Wǒ shì Xiǎoměi.')
    expect(wrapper.text()).toContain('Hi! I’m Xiaomei. How was your day?')
    expect(tools()).not.toContain('Report')
    expect(tools()).toEqual(expect.arrayContaining(['Hide pinyin', 'Hide English']))

    await buttonNamed(wrapper, 'Hide pinyin').trigger('click')

    expect(wrapper.text()).not.toContain('Nǐ hǎo! Wǒ shì Xiǎoměi.')
    expect(tools()).toContain('Pinyin')
  })

  it('names the translation toggle after what it shows: in Bahasa Melayu her line is glossed in Malay', async () => {
    const wrapper = mountTalk()
    i18n.global.locale.value = 'ms'
    await flushPromises()

    expect(wrapper.text()).toContain('Hai! Saya Xiaomei. Bagaimana hari anda?')
    expect(wrapper.findAll('.ai-talk-tools button').map((button) => button.text())).toContain('Sembunyikan terjemahan')
    expect(wrapper.text()).not.toContain('Inggeris')

    await buttonNamed(wrapper, 'Sembunyikan terjemahan').trigger('click')

    expect(wrapper.text()).not.toContain('Hai! Saya Xiaomei. Bagaimana hari anda?')
    expect(wrapper.findAll('.ai-talk-tools button').map((button) => button.text())).toContain('Terjemahan')
  })

  it('shows the last reply on the stage and in the transcript, with its tip, and marks a reported line', async () => {
    api.xiaomeiReply.mockResolvedValue({
      status: 'SUCCESS',
      reply: { chinese: '真棒！', pinyin: 'Zhēn bàng!', translation: 'Awesome!' },
      naturalVersion: '我在学校学习中文。',
      tip: 'Place comes before the verb.',
    })
    api.report.mockResolvedValue({ id: 3, status: 'PENDING_REVIEW' })
    const wrapper = mountTalk()
    await typeAndSend(wrapper, '我学习中文在学校。')

    expect(wrapper.get('.ai-talk-said').text()).toContain('我学习中文在学校。')
    expect(wrapper.get('[data-testid="ai-talk-tip"]').text()).toContain('Place comes before the verb.')
    expect(wrapper.findAll('.ai-transcript li')[1]!.text()).toContain('Tip: 我在学校学习中文。')

    await buttonNamed(wrapper.get('.ai-talk-tools'), 'Report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report.mock.calls.at(-1)![0]).toMatchObject({
      featureKey: 'XIAOMEI_CONVERSATION',
      // The lines before the reported one, by speaker.
      promptSnapshot: `小美: ${OPENER}\nYou: 我学习中文在学校。`,
      aiResponseSnapshot: '真棒！\nZhēn bàng!\nAwesome!\n我在学校学习中文。\nPlace comes before the verb.',
    })
    expect(wrapper.get('.ai-talk-tools .ai-reported').text()).toBe('Reported')
    expect(wrapper.emitted('reported')).toHaveLength(1)
  })

  it('reports the written summary like any AI answer', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('我也很好！'))
    api.xiaomeiSummary.mockResolvedValue({
      status: 'SUCCESS',
      goodbye: { chinese: '再见！' },
      feedback: 'You answered in Chinese.',
      newExpressions: [{ chinese: '下次见', translation: 'see you next time' }],
      wordsUsed: [],
    })
    api.report.mockResolvedValue({ id: 5, status: 'PENDING_REVIEW' })
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我很好')

    await wrapper.get('[data-testid="ai-summary-report"]').trigger('click')
    expect(wrapper.get('.overlay-title').text()).toBe('Report this AI answer')
    expect(wrapper.text()).toContain('What SparkBot or Xiaomei showed you is attached to the report.')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report.mock.calls.at(-1)![0]).toMatchObject({
      featureKey: 'XIAOMEI_CONVERSATION',
      promptSnapshot: `小美: ${OPENER}\nYou: 我很好\n小美: 我也很好！\nYou: 谢谢\n小美: 我也很好！`,
      aiResponseSnapshot: '再见！\nYou answered in Chinese.\n下次见 see you next time',
    })
    expect(wrapper.find('[data-testid="ai-summary-report"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="ai-talk-summary"] .ai-reported').text()).toBe('Reported')
    expect(wrapper.emitted('reported')).toHaveLength(1)
  })

  it('keeps the latest question with the reported reply when older turns would fill the snapshot', async () => {
    api.xiaomeiReply.mockResolvedValueOnce(replyWith('很'.repeat(4000))).mockResolvedValueOnce(replyWith('不客气。'))
    api.report.mockResolvedValue({ id: 5, status: 'PENDING_REVIEW' })
    const wrapper = mountTalk()
    await typeAndSend(wrapper, '你好')
    await typeAndSend(wrapper, '谢谢')

    await buttonNamed(wrapper.get('.ai-talk-tools'), 'Report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenCalledWith(
      expect.objectContaining({ promptSnapshot: 'You: 谢谢', aiResponseSnapshot: expect.stringContaining('不客气。') }),
    )
  })

  it('sends a line of hers longer than the server takes in a turn cut to 300 characters', async () => {
    const long = '很'.repeat(320)
    api.xiaomeiReply.mockResolvedValueOnce(replyWith(long)).mockResolvedValueOnce(replyWith('好的。'))
    const wrapper = mountTalk()

    await typeAndSend(wrapper, '你好')
    await typeAndSend(wrapper, '谢谢')

    const { turns } = api.xiaomeiReply.mock.calls[1]![0]
    expect(turns.at(-1)).toEqual({ speaker: 'XIAOMEI', text: '很'.repeat(300) })
    // The stage still shows her whole line.
    expect(wrapper.findAll('.ai-transcript li')[2]!.text()).toContain(long)
  })

  it('goes on past the 24 lines the server takes, sending the latest ones', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('好的。'))
    const wrapper = mountTalk()

    for (let round = 1; round <= 13; round++) await typeAndSend(wrapper, `第${round}句`)

    const { turns } = api.xiaomeiReply.mock.calls.at(-1)![0]
    expect(turns).toHaveLength(24)
    expect(turns.at(-2)).toEqual({ speaker: 'STUDENT', text: '第12句' })
    expect(wrapper.findAll('.ai-transcript li')).toHaveLength(27)
  })
})

describe('AiTalkTab: what Xiaomei will remember (S7.14, S7.15)', () => {
  const PLAN_GOAL: XiaomeiMemoryNote = { id: 1, text: 'Travel to Shanghai', planReason: 'TRAVEL' }
  const FACT: XiaomeiMemoryNote = { id: 2, text: 'Studies Chinese at school', planReason: null }
  const memorySection = (wrapper: ReturnType<typeof mountTalk>) => wrapper.get('[data-testid="ai-summary-memory"]')
  const notes = (wrapper: ReturnType<typeof mountTalk>) =>
    wrapper.findAll('[data-testid="ai-memory-note"] .row-list-copy').map((note) => note.text())

  beforeEach(() => {
    api.xiaomeiReply.mockResolvedValue(replyWith('我也很好！'))
  })

  it('lists the plan goal and the facts with Forget, and says they stay in the learner’s own chats', async () => {
    api.xiaomeiSummary.mockResolvedValue({ ...summaryWith('再见！'), memory: [PLAN_GOAL, FACT] })
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我在学校学中文')

    const section = memorySection(wrapper)
    expect(section.get('h3').text()).toBe('Xiaomei will remember')
    expect(notes(wrapper)).toEqual(['Travel to Shanghai (your plan)', 'Studies Chinese at school'])
    expect(section.findAll('button').map((button) => button.attributes('aria-label'))).toEqual([
      'Forget “Travel to Shanghai (your plan)”',
      'Forget “Studies Chinese at school”',
    ])
    expect(section.text()).toContain('Used only in your own chats.')
  })

  it('forgets a note at once, with Undo in the toast that brings it back', async () => {
    api.xiaomeiSummary.mockResolvedValue({ ...summaryWith('再见！'), memory: [PLAN_GOAL, FACT] })
    const forgotten = deferred<XiaomeiMemoryNote[]>()
    api.forgetXiaomeiNote.mockReturnValue(forgotten.promise)
    api.restoreXiaomeiNote.mockResolvedValue([PLAN_GOAL, FACT])
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我在学校学中文')

    await buttonNamed(memorySection(wrapper), 'Forget').trigger('click')
    // Gone at once, before the server has answered.
    expect(notes(wrapper)).toEqual(['Studies Chinese at school'])
    expect(api.forgetXiaomeiNote).toHaveBeenCalledWith(1)
    forgotten.resolve([FACT])
    await flushPromises()

    const [message, undo] = wrapper.emitted<[string, { label: string; run: () => void }]>('toast')!.at(-1)!
    expect(message).toBe('Xiaomei will forget this.')
    expect(undo.label).toBe('Undo')
    undo.run()
    await flushPromises()

    expect(api.restoreXiaomeiNote).toHaveBeenCalledWith(1)
    expect(notes(wrapper)).toEqual(['Travel to Shanghai (your plan)', 'Studies Chinese at school'])
  })

  it('keeps the section after the last note is forgotten, and puts back a note the server could not forget', async () => {
    api.xiaomeiSummary.mockResolvedValue({ ...summaryWith('再见！'), memory: [FACT] })
    api.forgetXiaomeiNote.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([])
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我在学校学中文')

    await buttonNamed(memorySection(wrapper), 'Forget').trigger('click')
    await flushPromises()
    expect(notes(wrapper)).toEqual(['Studies Chinese at school'])
    expect(memorySection(wrapper).get('[role="alert"]').text()).toBe('Xiaomei couldn’t forget this. Please try again.')
    expect(wrapper.emitted('toast')).toBeUndefined()

    await buttonNamed(memorySection(wrapper), 'Forget').trigger('click')
    await flushPromises()
    expect(notes(wrapper)).toEqual([])
    expect(memorySection(wrapper).text()).toContain('Nothing for now.')
    expect(memorySection(wrapper).find('[role="alert"]').exists()).toBe(false)
  })

  it('still lists the plan goal, named by its reason, when SparkBot cannot write the summary', async () => {
    api.xiaomeiSummary.mockResolvedValue({
      status: 'UNAVAILABLE',
      message: 'AI tutor is currently unavailable. Please try again later.',
      newExpressions: [],
      wordsUsed: [],
      memory: [{ id: 3, text: null, planReason: 'TRAVEL' }],
    })
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我很好')

    expect(wrapper.get('[data-testid="ai-talk-summary"] [role="alert"]').text()).toContain('AI tutor is currently')
    expect(notes(wrapper)).toEqual(['Travel (your plan)'])
  })

  it('asks for the notes alone when the summary request fails, and shows no section without any', async () => {
    api.xiaomeiSummary.mockRejectedValue(new Error('offline'))
    api.xiaomeiMemory.mockResolvedValueOnce([FACT])
    const wrapper = mountTalk()
    await endAfterTwoReplies(wrapper, '我很好')

    expect(api.xiaomeiMemory).toHaveBeenCalledOnce()
    expect(notes(wrapper)).toEqual(['Studies Chinese at school'])

    api.xiaomeiSummary.mockResolvedValue({ ...summaryWith('再见！'), memory: [] })
    await buttonNamed(wrapper, 'Talk again').trigger('click')
    await endAfterTwoReplies(wrapper, '我很好')
    expect(wrapper.find('[data-testid="ai-summary-memory"]').exists()).toBe(false)
  })
})

describe('AiTalkTab reading Xiaomei aloud', () => {
  let synthesis: ReturnType<typeof stubSpeech>

  beforeEach(() => {
    synthesis = stubSpeech()
  })

  it('stays Speaking while her reply is read, whatever the line it cut off reports late', async () => {
    api.xiaomeiReply.mockResolvedValue(replyWith('真不错！'))
    const wrapper = mountTalk()
    const state = () => wrapper.get('.ai-talk-state').text()

    await buttonNamed(wrapper, 'Listen').trigger('click')
    synthesis.utterance(0).onstart?.()
    await flushPromises()
    expect(state()).toBe('Speaking…')

    await typeAndSend(wrapper, '我很好')
    expect(synthesis.utterance(1).text).toBe('真不错！')
    synthesis.utterance(1).onstart?.()
    synthesis.utterance(0).onend?.()
    await flushPromises()
    expect(state()).toBe('Speaking…')

    synthesis.utterance(1).onend?.()
    await flushPromises()
    expect(state()).toBe('Your turn')
  })

  it('does not read a pending reply after the conversation page is unmounted', async () => {
    const pending = deferred<XiaomeiReply>()
    api.xiaomeiReply.mockReturnValueOnce(pending.promise)
    const wrapper = mountTalk()
    await typeAndSend(wrapper, '你好')
    wrapper.unmount()
    pending.resolve(replyWith('你好！'))
    await flushPromises()

    expect(synthesis.speak).not.toHaveBeenCalled()
  })
})

describe('AiTalkTab voice replies', () => {
  function dockButton(wrapper: ReturnType<typeof mountTalk>, label: string) {
    return buttonNamed(wrapper.get('.ai-talk-voice-dock'), label)
  }

  beforeEach(() => {
    vi.stubGlobal('webkitSpeechRecognition', FakeRecognition)
    api.xiaomeiReply.mockResolvedValue(replyWith('真不错！'))
  })

  it('sends what the browser heard when the student clicks Send', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我今天很好')
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-talk-listening"]').text()).toContain('我今天很好')

    await dockButton(wrapper, 'Send').trigger('click')
    await flushPromises()

    expect(api.xiaomeiReply).toHaveBeenCalledTimes(1)
    expect(api.xiaomeiReply.mock.calls[0]![0]).toMatchObject({ message: '我今天很好' })
    expect(wrapper.findAll('.ai-transcript li')[1]!.text()).toContain('我今天很好')
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

  it('cuts a spoken reply to the 200 characters the server takes, where typing stops too', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('好'.repeat(220))
    FakeRecognition.last!.stop()
    await flushPromises()

    expect(api.xiaomeiReply.mock.calls[0]![0]).toMatchObject({ message: '好'.repeat(200) })
  })

  it('sends nothing when the student cancels', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我很')
    await flushPromises()
    await dockButton(wrapper, 'Cancel').trigger('click')
    await flushPromises()

    expect(api.xiaomeiReply).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="ai-talk-listening"]').exists()).toBe(false)
  })

  it('turns the microphone off and sends nothing when the student leaves for another tab', async () => {
    const abort = vi.spyOn(FakeRecognition.prototype, 'abort')
    const page = {
      components: { AiTalkTab },
      props: ['showTalk'],
      template: '<div><KeepAlive><AiTalkTab v-if="showTalk" :blocked="null" ai-notice="" /></KeepAlive></div>',
    }
    const wrapper = mount(page, {
      props: { showTalk: true },
      global: { stubs: { IconGlyph: true, teleport: true } },
    })

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我很')
    await wrapper.setProps({ showTalk: false })
    await flushPromises()

    expect(abort).toHaveBeenCalledOnce()
    expect(api.xiaomeiReply).not.toHaveBeenCalled()
  })

  it('disables scenario switching while the microphone is listening so recognition cannot fire behind the switch modal', async () => {
    const wrapper = mountTalk()
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.hear('我很好')
    FakeRecognition.last!.stop()
    await flushPromises()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    expect(buttonNamed(wrapper, 'Order at a café').element.disabled).toBe(true)
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)

    // Cancelling listening re-enables scenario chips; confirming the switch starts the new scenario cleanly.
    await buttonNamed(wrapper, 'Cancel').trigger('click')
    expect(buttonNamed(wrapper, 'Order at a café').element.disabled).toBe(false)
    await buttonNamed(wrapper, 'Order at a café').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')

    const mic = () => wrapper.get<HTMLButtonElement>('[data-testid="ai-talk-mic"]')
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe('你好！欢迎光临。你想喝点儿什么？')
    expect(mic().element.disabled).toBe(false)

    const next = deferred<XiaomeiReply>()
    api.xiaomeiReply.mockReturnValueOnce(next.promise)
    await mic().trigger('click')
    FakeRecognition.last!.hear('我要一杯茶')
    FakeRecognition.last!.stop()
    await flushPromises()
    expect(api.xiaomeiReply.mock.lastCall?.[0]).toMatchObject({ scenario: 'CAFE', message: '我要一杯茶' })
    expect(mic().element.disabled).toBe(true)

    next.resolve(replyWith('马上来！'))
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-talk-line"]').text()).toBe('马上来！')
    expect(mic().element.disabled).toBe(false)
  })

  it('offers a single microphone action at the opener', () => {
    const wrapper = mountTalk()

    expect(wrapper.get('[data-testid="ai-talk-mic"]').attributes('aria-label')).toBe('Speak your reply in Chinese')
    expect(wrapper.find('.ai-talk-type').exists()).toBe(false)
    expect(wrapper.findAll('.ai-talk-voice-dock button')).toHaveLength(1)
  })

  it('turns the mic into a stop button that sends what was heard', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    expect(wrapper.get('[data-testid="ai-talk-mic"]').attributes('aria-label')).toBe('Stop listening')
    FakeRecognition.last!.hear('我很好')
    await flushPromises()
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()

    expect(api.xiaomeiReply).toHaveBeenCalledTimes(1)
    expect(api.xiaomeiReply.mock.calls[0]![0]).toMatchObject({ message: '我很好' })
  })

  it('cannot reach the microphone while the AI is blocked', () => {
    const wrapper = mountTalk('unavailable')

    expect(wrapper.get<HTMLButtonElement>('[data-testid="ai-talk-mic"]').element.disabled).toBe(true)
  })

  it('says “Your turn” and “Click the mic” at her opener', () => {
    const wrapper = mountTalk()

    expect(wrapper.get('.ai-talk-state').text()).toBe('Your turn')
    expect(wrapper.get('.ai-talk-hint').text()).toBe('Click the mic to talk with Xiaomei!')
  })

  describe('asking for the microphone', () => {
    function stubMicPermission(state: PermissionState) {
      const permission = { state }
      Object.defineProperty(navigator, 'permissions', {
        configurable: true,
        value: { query: vi.fn().mockResolvedValue(permission) },
      })
      return permission
    }

    afterEach(() => {
      delete (navigator as { permissions?: unknown }).permissions
    })

    it('asks in its own dialog before the browser does, then listens', async () => {
      stubMicPermission('prompt')
      const wrapper = mountTalk()
      await flushPromises()

      await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
      expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Use your microphone?')
      expect(FakeRecognition.last).toBeNull()

      await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')

      expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
      expect(FakeRecognition.last).not.toBeNull()
      expect(wrapper.find('[data-testid="ai-talk-listening"]').exists()).toBe(true)
    })

    it('types instead when the learner turns the microphone down', async () => {
      stubMicPermission('prompt')
      const wrapper = mountTalk()
      await flushPromises()

      await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
      const cancel = wrapper.get('[data-testid="confirm-dialog-cancel"]')
      expect(cancel.text()).toBe('Cancel')
      await cancel.trigger('click')

      expect(wrapper.find('.ai-talk-type input').exists()).toBe(true)
    })

    it('listens straight away once the microphone is allowed', async () => {
      const permission = stubMicPermission('prompt')
      const wrapper = mountTalk()
      await flushPromises()

      permission.state = 'granted'
      await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')

      expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ai-talk-listening"]').exists()).toBe(true)
    })
  })

  it('says what to do in the status line under the dock while listening and when nothing was heard', async () => {
    const wrapper = mountTalk()
    const status = () => wrapper.get('[data-testid="ai-talk-status"]').text()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    expect(status()).toBe('Listening… Click Send when you’re done, or Cancel.')

    FakeRecognition.last!.onerror?.({ error: 'no-speech' })
    FakeRecognition.last!.stop()
    await flushPromises()

    expect(status()).toBe('We didn’t catch that. Click the mic and speak a little longer.')
    expect(wrapper.find('.callout.is-danger').exists()).toBe(false)
    // Her line keeps its tools, as on her other lines.
    expect(wrapper.findAll('.ai-talk-tools button').map((button) => button.text())).toContain('Hide pinyin')

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    expect(status()).toBe('Listening… Click Send when you’re done, or Cancel.')
  })

  it('says nothing was heard when the stop button is pressed before anything was heard', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    await flushPromises()

    expect(api.xiaomeiReply).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="ai-talk-status"]').text()).toContain('We didn’t catch that.')
  })

  it('explains how to allow a blocked microphone and opens the typed composer', async () => {
    const wrapper = mountTalk()

    await wrapper.get('[data-testid="ai-talk-mic"]').trigger('click')
    FakeRecognition.last!.onerror?.({ error: 'not-allowed' })
    FakeRecognition.last!.stop()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(
      'Microphone blocked or not found. Allow it from the lock icon in the address bar.',
    )
    expect(wrapper.find('[data-testid="ai-talk-mic"]').exists()).toBe(false)
    expect(wrapper.get('.ai-talk-type input').attributes('disabled')).toBeUndefined()

    await buttonNamed(wrapper, 'Try the microphone again').trigger('click')
    expect(wrapper.find('[data-testid="ai-talk-listening"]').exists()).toBe(true)
  })
})
