import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { chooseFile, pageOf } from '@/test/helpers'
import type { AiHelperStatus, PracticeCheck } from '@/types/ai'

const api = vi.hoisted(() => ({
  practiceCheckLessons: vi.fn(),
  practiceChecks: vi.fn(),
  checkPractice: vi.fn(),
  deletePracticeCheck: vi.fn(),
  report: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import AiVoiceTab from './AiVoiceTab.vue'

const STATUS: AiHelperStatus = {
  available: true,
  speechCheckAvailable: true,
  handwritingCheckAvailable: true,
  usedToday: 3,
  dailyLimit: 100,
  aiNotice: 'AI can make mistakes.',
}

const HANDWRITING_CHECK: PracticeCheck = {
  id: 41,
  type: 'HANDWRITING',
  targetText: '好',
  lessonTitle: 'Hello and Self-introduction',
  score: 88,
  tonesScore: null,
  clarityScore: null,
  paceScore: null,
  headline: 'Neat and balanced.',
  feedback: 'The left part 女 is a little narrow.',
  tips: ['Give 女 a little more room.'],
  checkedAt: '2026-10-28T09:00:00Z',
}

function mountVoice(status: AiHelperStatus = STATUS) {
  return mount(AiVoiceTab, {
    props: { blocked: null, status, aiNotice: status.aiNotice },
    global: { plugins: [createPinia()], stubs: { IconGlyph: true, teleport: true } },
  })
}

async function chooseHandwriting(wrapper: ReturnType<typeof mountVoice>) {
  const handwriting = wrapper.findAll('.segmented-option').find((button) => button.text() === 'Handwriting')!
  await handwriting.trigger('click')
}

describe('AiVoiceTab (pronunciation and handwriting)', () => {
  beforeEach(() => {
    api.practiceCheckLessons.mockResolvedValue([{ id: 5, title: 'Hello and Self-introduction' }])
    api.practiceChecks.mockResolvedValue(pageOf([HANDWRITING_CHECK], 0, 1, 5))
    URL.createObjectURL = vi.fn(() => 'blob:photo')
    URL.revokeObjectURL = vi.fn()
  })

  it('refuses a file the server would not accept before uploading anything', async () => {
    const wrapper = mountVoice()
    await flushPromises()
    await chooseHandwriting(wrapper)

    await chooseFile(wrapper.get('input[type="file"]'), new File(['gif'], 'hao.gif', { type: 'image/gif' }))

    expect(wrapper.get('.field-error').text()).toBe('Choose a PNG or JPG photo.')
    expect(wrapper.get<HTMLButtonElement>('.ai-check-submit button').element.disabled).toBe(true)
    expect(api.checkPractice).not.toHaveBeenCalled()
  })

  it('sends a handwriting photo with its target and lesson and shows the score', async () => {
    api.checkPractice.mockResolvedValue({ status: 'SUCCESS', message: null, check: HANDWRITING_CHECK })
    const wrapper = mountVoice()
    await flushPromises()
    await chooseHandwriting(wrapper)

    const photo = new File(['png'], 'hao.png', { type: 'image/png' })
    await chooseFile(wrapper.get('input[type="file"]'), photo)
    await wrapper.get('input.cjk-text').setValue('好')
    await wrapper.get('select').setValue('5')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.checkPractice).toHaveBeenCalledWith({
      type: 'HANDWRITING',
      file: photo,
      fileName: 'hao.png',
      targetText: '好',
      lessonId: 5,
      durationSeconds: null,
      languagePreference: 'en',
    })
    const result = wrapper.get('[data-testid="ai-check-result"]')
    expect(result.get('.ai-check-number').text()).toBe('88/ 100')
    expect(result.text()).toContain('The left part 女 is a little narrow.')
    expect(wrapper.emitted('used')).toHaveLength(1)
  })

  it('shows the server reason when the recording could not be scored', async () => {
    api.checkPractice.mockResolvedValue({
      status: 'FAILED',
      message:
        "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.",
      check: null,
    })
    const wrapper = mountVoice()
    await flushPromises()

    await chooseFile(wrapper.get('input[type="file"]'), new File(['voice'], 'xie.webm', { type: 'audio/webm' }))
    await wrapper.get('input.cjk-text').setValue('谢谢')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-check-result"]').exists()).toBe(false)
    expect(wrapper.get('.callout.is-danger').text()).toContain("SparkBot couldn't hear any speech")
  })

  it('says when the AI service cannot score recordings', async () => {
    const wrapper = mountVoice({ ...STATUS, speechCheckAvailable: false })
    await flushPromises()

    expect(wrapper.get('.callout.is-warning').text()).toBe(
      'SparkBot can’t score recordings with the current AI service.',
    )
  })

  it('deletes a recent check only after the student confirms', async () => {
    api.deletePracticeCheck.mockResolvedValue(null)
    const wrapper = mountVoice()
    await flushPromises()

    await wrapper.get('.ai-check-item button').trigger('click')
    const remove = wrapper.findAll('.overlay-panel button').find((button) => button.text() === 'Delete')!
    await remove.trigger('click')
    expect(api.deletePracticeCheck).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.deletePracticeCheck).toHaveBeenCalledWith(41)
    expect(api.practiceChecks).toHaveBeenCalledTimes(2)
  })
})
