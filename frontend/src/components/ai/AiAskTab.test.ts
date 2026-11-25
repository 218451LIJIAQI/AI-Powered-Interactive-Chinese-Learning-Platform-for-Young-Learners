import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'

const api = vi.hoisted(() => ({ chatStream: vi.fn(), report: vi.fn() }))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))

import AiAskTab from './AiAskTab.vue'

function mountAsk(initialQuestion: string | null = null) {
  return mount(AiAskTab, {
    props: {
      blocked: null,
      initialQuestion,
      aiNotice: 'AI can make mistakes.',
    },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
}

async function askQuestion(wrapper: ReturnType<typeof mountAsk>, text: string) {
  await wrapper.get('textarea').setValue(text)
  await wrapper.get('form.ai-ask-bar').trigger('submit')
  await flushPromises()
}

describe('AiAskTab (Ask SparkBot)', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('fills the question box from a link', () => {
    const wrapper = mountAsk('What does 加油 mean?')

    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('What does 加油 mean?')
  })

  it('does not start a request when the tab closes before its first render finishes', async () => {
    api.chatStream.mockResolvedValue({ aiResponse: 'Answer', status: 'SUCCESS' })
    const wrapper = mountAsk('Hello')

    wrapper.get('form.ai-ask-bar').element.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    wrapper.unmount()
    await flushPromises()

    expect(api.chatStream).not.toHaveBeenCalled()
  })

  it('aborts a request already streaming when the tab closes', async () => {
    let signal: AbortSignal | undefined
    api.chatStream.mockImplementation((_payload, handlers) => {
      signal = handlers.signal
      return new Promise((_resolve, reject) => {
        signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      })
    })
    const wrapper = mountAsk()
    await askQuestion(wrapper, 'Hello')
    expect(signal?.aborted).toBe(false)

    wrapper.unmount()
    await flushPromises()

    expect(signal?.aborted).toBe(true)
  })

  it('streams the answer, labels it as AI and keeps the chat session for follow-up questions', async () => {
    const firstReply = deferred<{
      sessionId: number
      chatMessageId: number
      aiResponse: string
      status: string
    }>()
    let emitDelta: ((chunk: string) => void) | undefined
    api.chatStream
      .mockImplementationOnce((_payload, handlers) => {
        emitDelta = handlers.onDelta
        return firstReply.promise
      })
      .mockResolvedValueOnce({
        sessionId: 9,
        chatMessageId: 56,
        aiResponse: 'You can say 不客气 (bù kèqi).',
        status: 'SUCCESS',
      })
    const wrapper = mountAsk()

    await wrapper.get('textarea').setValue('How do I say thank you?')
    await wrapper.get('form.ai-ask-bar').trigger('submit')
    await flushPromises()

    emitDelta!('谢谢 (xièxie) ')
    await flushPromises()
    expect(wrapper.get('.ai-message.is-bot .ai-answer').text()).toBe('谢谢 (xièxie)')
    expect(wrapper.find('[data-testid="ai-result-label"]').exists()).toBe(false)

    emitDelta!('means thank you.')
    firstReply.resolve({
      sessionId: 9,
      chatMessageId: 55,
      aiResponse: '谢谢 (xièxie) means thank you.',
      status: 'SUCCESS',
    })
    await flushPromises()
    await askQuestion(wrapper, 'And a reply?')

    expect(api.chatStream.mock.calls[0]![0]).toEqual({
      message: 'How do I say thank you?',
      languagePreference: 'en',
      sessionId: undefined,
    })
    expect(api.chatStream.mock.calls[1]![0]).toMatchObject({ message: 'And a reply?', sessionId: 9 })
    expect(wrapper.findAll('.ai-message.is-bot .ai-answer')[0]!.text()).toBe('谢谢 (xièxie) means thank you.')
    expect(wrapper.findAll('[data-testid="ai-result-label"]')).toHaveLength(2)
    expect(wrapper.emitted('used')).toHaveLength(2)
  })

  it('reports an answer by its chat message, so the server attaches the question and answer', async () => {
    api.chatStream.mockResolvedValue({ sessionId: 9, chatMessageId: 55, aiResponse: 'Answer', status: 'SUCCESS' })
    api.report.mockResolvedValue({ id: 3, status: 'PENDING_REVIEW' })
    const wrapper = mountAsk()
    await askQuestion(wrapper, 'Is 我很好 correct?')

    await wrapper.get('.ai-message-actions button').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"] textarea').setValue('  The tone mark is wrong.  ')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenCalledWith({
      featureKey: 'CHAT_TUTOR',
      chatMessageId: 55,
      reasonCategory: 'INCORRECT_CONTENT',
      description: 'The tone mark is wrong.',
    })
    expect(wrapper.find('[data-testid="ai-report-form"]').exists()).toBe(false)
    expect(wrapper.get('.ai-message-actions .ai-reported').text()).toBe('Reported')
    expect(wrapper.emitted('reported')).toHaveLength(1)
  })

  it('shows the fixed reason when SparkBot could not answer, without a report button', async () => {
    api.chatStream.mockResolvedValue({
      sessionId: null,
      chatMessageId: null,
      aiResponse: 'AI tutor is currently unavailable. Please try again later.',
      status: 'UNAVAILABLE',
    })
    const wrapper = mountAsk()

    await askQuestion(wrapper, 'Hello?')

    const answer = wrapper.get('.ai-message.is-bot .ai-answer')
    expect(answer.classes()).toContain('is-problem')
    expect(answer.text()).toBe('AI tutor is currently unavailable. Please try again later.')
    expect(wrapper.find('.ai-message-actions').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-result-label"]').exists()).toBe(false)
  })

  it('ignores Enter during IME composition (including WebKit keyCode 229) and lets the learner stop a stream cleanly', async () => {
    let signal: AbortSignal | undefined
    let emitDelta: ((chunk: string) => void) | undefined
    api.chatStream.mockImplementation((_payload, handlers) => {
      signal = handlers.signal
      emitDelta = handlers.onDelta
      return new Promise((_resolve, reject) => {
        signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      })
    })
    const wrapper = mountAsk()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('nihao')

    // WebKit/Safari fires keyCode 229 with isComposing=false on Enter confirming Pinyin composition.
    await textarea.trigger('keydown', { key: 'Enter', keyCode: 229 })
    expect(api.chatStream).not.toHaveBeenCalled()

    await textarea.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(api.chatStream).toHaveBeenCalledTimes(1)

    emitDelta!('你好 means hello')
    await flushPromises()
    await wrapper.get('[data-testid="ai-ask-stop"]').trigger('click')
    await flushPromises()

    expect(signal?.aborted).toBe(true)
    const stoppedBubble = wrapper.get('.ai-message.is-bot .ai-answer')
    expect(stoppedBubble.text()).toBe('你好 means hello')
    expect(stoppedBubble.classes()).not.toContain('is-problem')
  })

  it('unlocks the composer cleanly if the auth session changes before the stream starts', async () => {
    api.chatStream.mockResolvedValue({ sessionId: 9, chatMessageId: 55, aiResponse: 'Answer', status: 'SUCCESS' })
    const wrapper = mountAsk('Hello')

    wrapper.get('form.ai-ask-bar').element.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    setAccessToken('different-session-token')
    await flushPromises()
    setAccessToken(null)

    expect(api.chatStream).not.toHaveBeenCalled()
    expect(wrapper.findAll('.ai-message')).toHaveLength(0)
    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.disabled).toBe(false)
  })
})
