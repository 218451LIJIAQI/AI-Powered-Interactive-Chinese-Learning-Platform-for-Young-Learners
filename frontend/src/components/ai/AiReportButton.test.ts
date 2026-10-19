import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({
  reportResponse: vi.fn(),
}))

vi.mock('@/api/services', () => ({
  aiApi: {
    reportResponse: (...args: unknown[]) => mocks.reportResponse(...args),
  },
}))

import AiReportButton from './AiReportButton.vue'

type ReportButtonProps = InstanceType<typeof AiReportButton>['$props']

function mountButton(props: ReportButtonProps): ReturnType<typeof mount> {
  return mount(AiReportButton, {
    props,
    global: { plugins: [i18n], stubs: { IconGlyph: true } },
  })
}

describe('AiReportButton', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    mocks.reportResponse.mockReset()
    mocks.reportResponse.mockResolvedValue({ id: 7 })
  })

  it('offers exactly the five backend report reasons', async () => {
    const wrapper = mountButton({ featureKey: 'VOCABULARY_HELPER', responseSnapshot: '食堂 shítáng: canteen' })
    await wrapper.get('[data-testid="ai-report-toggle"]').trigger('click')

    const values = wrapper.findAll('option').map((option) => option.attributes('value'))
    expect(values).toEqual(['INCORRECT_CONTENT', 'HALLUCINATION', 'UNSAFE_CONTENT', 'INAPPROPRIATE_CONTENT', 'OTHER'])
  })

  it('sends the feature key, reason, note and bounded snapshots', async () => {
    const longResponse = 'x'.repeat(4100)
    const wrapper = mountButton({
      featureKey: 'SENTENCE_PRACTICE',
      responseSnapshot: longResponse,
      promptSnapshot: '我是大学生。',
    })
    await wrapper.get('[data-testid="ai-report-toggle"]').trigger('click')
    await wrapper.get('select').setValue('INAPPROPRIATE_CONTENT')
    await wrapper.get('textarea').setValue('  Not about Mandarin  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.reportResponse).toHaveBeenCalledWith({
      featureKey: 'SENTENCE_PRACTICE',
      reasonCategory: 'INAPPROPRIATE_CONTENT',
      description: 'Not about Mandarin',
      chatSessionId: undefined,
      promptSnapshot: '我是大学生。',
      aiResponseSnapshot: 'x'.repeat(4000),
    })
    expect(wrapper.text()).toContain('Thanks. An admin will review this response.')
    expect(wrapper.emitted('reported')).toHaveLength(1)
  })

  it('passes the chat session for chat tutor reports and never sends in preview mode', async () => {
    const chat = mountButton({ featureKey: 'CHAT_TUTOR', responseSnapshot: '你好 means hello.', chatSessionId: 12 })
    await chat.get('[data-testid="ai-report-toggle"]').trigger('click')
    await chat.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.reportResponse).toHaveBeenCalledWith(expect.objectContaining({ chatSessionId: 12 }))

    mocks.reportResponse.mockClear()
    const preview = mountButton({ featureKey: 'QUIZ_GENERATOR', responseSnapshot: 'Quiz', preview: true })
    await preview.get('[data-testid="ai-report-toggle"]').trigger('click')
    await preview.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.reportResponse).not.toHaveBeenCalled()
    expect(preview.text()).toContain('Preview mode: the report was not sent.')
  })

  it('clears the previous report status when a new AI result arrives', async () => {
    const wrapper = mountButton({ featureKey: 'VOCABULARY_HELPER', responseSnapshot: 'first reply' })
    await wrapper.get('[data-testid="ai-report-toggle"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Thanks. An admin will review this response.')

    await wrapper.setProps({ responseSnapshot: 'second reply' })

    expect(wrapper.text()).not.toContain('Thanks. An admin will review this response.')
    expect(wrapper.find('form').exists()).toBe(false)
  })
})
