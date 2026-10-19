import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { studentPracticeSubmissions } from '@/demo/data'
import { i18n } from '@/plugins/i18n'

const mocks = vi.hoisted(() => ({
  pronunciationFeedback: vi.fn(),
  writingFeedback: vi.fn(),
  practiceSubmissionFile: vi.fn(),
}))

vi.mock('@/api/services', () => ({
  aiApi: {
    pronunciationFeedback: (...args: unknown[]) => mocks.pronunciationFeedback(...args),
    writingFeedback: (...args: unknown[]) => mocks.writingFeedback(...args),
    reportResponse: vi.fn(),
  },
  studentApi: {
    practiceSubmissionFile: (...args: unknown[]) => mocks.practiceSubmissionFile(...args),
  },
}))

import HandwritingSubmissionForm from './HandwritingSubmissionForm.vue'
import PracticeSubmissionItem from './PracticeSubmissionItem.vue'
import PronunciationSubmissionForm from './PronunciationSubmissionForm.vue'

const lessons = [
  { lessonId: 10, lessonTitle: 'Greetings' },
  { lessonId: 12, lessonTitle: 'Numbers 1-10' },
]
const global = { plugins: [i18n], stubs: { IconGlyph: true, RouterLink: { template: '<a><slot /></a>' } } }

async function chooseFile(wrapper: ReturnType<typeof mount>, file: File): Promise<void> {
  const input = wrapper.get('[data-testid="submission-file-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

class FakeRecorder {
  static isTypeSupported = () => true
  state = 'inactive'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  start(): void {
    this.state = 'recording'
  }
  stop(): void {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['voice'], { type: 'audio/webm' }) })
    this.onstop?.()
  }
}

beforeEach(() => {
  i18n.global.locale.value = 'en'
  mocks.pronunciationFeedback.mockReset()
  mocks.writingFeedback.mockReset()
})

describe('PronunciationSubmissionForm', () => {
  it('falls back to a file input when the browser cannot record', () => {
    const wrapper = mount(PronunciationSubmissionForm, { props: { lessons, languagePreference: 'en' }, global })
    expect(wrapper.find('[data-testid="submission-record"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="submission-recorder-fallback"]').text()).toContain(
      'Your browser cannot record audio here',
    )
    expect(wrapper.get('[data-testid="submission-file-input"]').attributes('accept')).toContain('audio/*')
  })

  it('shows translated validation messages and does not upload', async () => {
    const wrapper = mount(PronunciationSubmissionForm, { props: { lessons, languagePreference: 'en' }, global })
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Record or choose an audio file first.')
    expect(wrapper.text()).toContain('Enter the target text you read aloud.')
    expect(mocks.pronunciationFeedback).not.toHaveBeenCalled()

    await chooseFile(wrapper, new File(['x'], 'notes.png', { type: 'image/png' }))
    await wrapper.get('[data-testid="submission-target"]').setValue('你好')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Use a WEBM, WAV or MP3 audio file.')
  })

  it('uploads FormData with the lesson and UI language, then emits the AI result', async () => {
    mocks.pronunciationFeedback.mockResolvedValue({ status: 'UNAVAILABLE', submissionId: 41 })
    const wrapper = mount(PronunciationSubmissionForm, { props: { lessons, languagePreference: 'ms' }, global })

    await wrapper.get('[data-testid="submission-target"]').setValue('你好')
    await wrapper.get('[data-testid="submission-lesson"]').setValue('12')
    await chooseFile(wrapper, new File(['x'], 'take.webm', { type: 'audio/webm' }))
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const formData = mocks.pronunciationFeedback.mock.calls[0]?.[0] as FormData
    expect(formData.get('targetText')).toBe('你好')
    expect(formData.get('lessonId')).toBe('12')
    expect(formData.get('languagePreference')).toBe('ms')
    expect(wrapper.emitted('submitted')?.[0]?.[0]).toMatchObject({
      kind: 'audio',
      target: '你好',
      response: { submissionId: 41 },
    })
  })

  it('shows the server message, e.g. an unknown lesson', async () => {
    mocks.pronunciationFeedback.mockRejectedValue({
      response: { status: 404, data: { message: 'Lesson was not found.' } },
    })
    const wrapper = mount(PronunciationSubmissionForm, { props: { lessons: [], languagePreference: 'en' }, global })
    await wrapper.get('[data-testid="submission-target"]').setValue('你好')
    await wrapper.get('[data-testid="submission-lesson"]').setValue('999')
    await chooseFile(wrapper, new File(['x'], 'take.mp3', { type: 'audio/mpeg' }))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('Lesson was not found.')
  })

  it('records in the browser when MediaRecorder is available', async () => {
    const track = { stop: vi.fn() }
    const wrapper = mount(PronunciationSubmissionForm, {
      props: {
        lessons,
        languagePreference: 'en',
        recorderOptions: {
          env: {
            mediaDevices: {
              getUserMedia: () => Promise.resolve({ getTracks: () => [track] } as unknown as MediaStream),
            },
            MediaRecorder: FakeRecorder,
          },
          urlApi: { createObjectURL: () => 'blob:take', revokeObjectURL: vi.fn() },
        },
      },
      global,
    })
    await wrapper.get('[data-testid="submission-record"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="submission-recorder-status"]').text()).toContain('Recording')
    await wrapper.get('[data-testid="submission-stop"]').trigger('click')
    await flushPromises()
    expect(track.stop).toHaveBeenCalled()
    expect(wrapper.find('audio').attributes('src')).toBe('blob:take')
    expect(wrapper.text()).toContain('Using your recording')
  })

  it('is disabled in preview mode', () => {
    const wrapper = mount(PronunciationSubmissionForm, {
      props: { lessons, languagePreference: 'en', disabled: true, disabledMessage: 'Preview: not saved.' },
      global,
    })
    expect(wrapper.get('[data-testid="submission-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Preview: not saved.')
  })
})

describe('HandwritingSubmissionForm', () => {
  it('uploads the image with the target character', async () => {
    mocks.writingFeedback.mockResolvedValue({ status: 'SUCCESS', feedback: 'Balanced.', submissionId: 5 })
    const wrapper = mount(HandwritingSubmissionForm, { props: { lessons, languagePreference: 'zh-CN' }, global })
    await wrapper.get('[data-testid="submission-target"]').setValue('好')
    await chooseFile(wrapper, new File(['x'], 'hao.png', { type: 'image/png' }))
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const formData = mocks.writingFeedback.mock.calls[0]?.[0] as FormData
    expect(formData.get('targetCharacter')).toBe('好')
    expect(formData.get('languagePreference')).toBe('zh-CN')
    expect(formData.has('lessonId')).toBe(false)
    expect(wrapper.emitted('submitted')).toHaveLength(1)
  })
})

describe('PracticeSubmissionItem', () => {
  it('shows review status, AI label and translated teacher feedback', () => {
    const reviewed = studentPracticeSubmissions.find((item) => item.reviewStatus === 'REVIEWED')!
    const wrapper = mount(PracticeSubmissionItem, { props: { item: reviewed, previewMode: true }, global })
    expect(wrapper.get('[data-testid="submission-review-status"]').text()).toBe('Reviewed by teacher')
    expect(wrapper.get('[data-testid="ai-result-label"]').text()).toContain('AI-assisted')
    expect(wrapper.get('[data-testid="submission-teacher-feedback"]').text()).toContain('Corrective')
    expect(wrapper.get('[data-testid="authorized-media-load"]').attributes('disabled')).toBeDefined()
  })
})
