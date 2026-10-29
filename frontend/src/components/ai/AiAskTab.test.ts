import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ chatStream: vi.fn(), report: vi.fn() }))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import AiAskTab from './AiAskTab.vue'

function mountAsk(initialQuestion: string | null = null) {
  return mount(AiAskTab, {
    props: {
      blocked: null,
      initialQuestion,
      aiNotice: 'AI can make mistakes.',
    },
    global: { plugins: [createPinia()], stubs: { IconGlyph: true, teleport: true } },
  })
}

async function askQuestion(wrapper: ReturnType<typeof mountAsk>, text: string) {
  await wrapper.get('textarea').setValue(text)
  await wrapper.get('form.ai-ask-bar').trigger('submit')
  await flushPromises()
}

describe('AiAskTab (Ask SparkBot)', () => {
  it('fills the question box from a link', () => {
    const wrapper = mountAsk('What does 加油 mean?')

    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('What does 加油 mean?')
  })

  it('streams the answer, labels it as AI and keeps the chat session for follow-up questions', async () => {
    api.chatStream.mockImplementation(async (_payload, handlers) => {
      handlers.onDelta('谢谢 (xièxie) ')
      handlers.onDelta('means thank you.')
      return { sessionId: 9, chatMessageId: 55, aiResponse: '谢谢 (xièxie) means thank you.', status: 'SUCCESS' }
    })
    const wrapper = mountAsk()

    await askQuestion(wrapper, 'How do I say thank you?')
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
})
