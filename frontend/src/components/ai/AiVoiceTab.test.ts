import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, ref } from 'vue'

import { FakeMediaRecorder } from '@/test/fakeMediaRecorder'
import { buttonNamed, chooseFile, deferred, fileWithSize, pageOf } from '@/test/helpers'
import type { AiHelperStatus, PracticeCheck } from '@/types/ai'

const api = vi.hoisted(() => ({
  practiceCheckLessons: vi.fn(),
  practiceChecks: vi.fn(),
  checkPractice: vi.fn(),
  deletePracticeCheck: vi.fn(),
  restorePracticeCheck: vi.fn(),
  report: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))

import AiVoiceTab from './AiVoiceTab.vue'

const STATUS: AiHelperStatus = {
  available: true,
  speechCheckAvailable: true,
  handwritingCheckAvailable: true,
  usedToday: 3,
  dailyLimit: 100,
  aiNotice: 'AI can make mistakes.',
}

const MB = 1024 * 1024

const HANDWRITING_CHECK: PracticeCheck = {
  id: 41,
  type: 'HANDWRITING',
  targetText: '好',
  lessonTitle: 'Hello and Self-introduction',
  score: 88,
  clarityScore: null,
  paceScore: null,
  headline: 'Neat and balanced.',
  feedback: 'The left part 女 is a little narrow.',
  tips: ['Give 女 a little more room.'],
  checkedAt: '2026-10-28T09:00:00Z',
}

afterEach(() => vi.useRealTimers())

async function mountVoice(status: AiHelperStatus | null = STATUS) {
  const wrapper = mount(AiVoiceTab, {
    props: { blocked: null, status, aiNotice: status?.aiNotice ?? 'AI can make mistakes.' },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
  await flushPromises()
  return wrapper
}

type Voice = Awaited<ReturnType<typeof mountVoice>>

async function chooseType(wrapper: Voice, label: string) {
  await buttonNamed(wrapper.get('.segmented'), label).trigger('click')
}

async function submitRecording(wrapper: Voice) {
  await chooseFile(wrapper.get('input[type="file"]'), new File(['voice'], 'xie.webm', { type: 'audio/webm' }))
  await wrapper.get('input.cjk-text').setValue('谢谢')
  await wrapper.get('form').trigger('submit')
}

function stubMicrophonePrompt(permission: Promise<{ state: PermissionState }> = Promise.resolve({ state: 'prompt' })) {
  const getUserMedia = vi.fn(() => new Promise<MediaStream>(() => undefined))
  vi.stubGlobal('navigator', {
    mediaDevices: { getUserMedia },
    permissions: { query: () => permission },
  })
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  return getUserMedia
}

async function mountCachedVoice() {
  const onVoice = ref(true)
  const OtherTab = defineComponent(() => () => h('p', 'Other tab'))
  const page = mount(
    defineComponent(
      () => () =>
        h(KeepAlive, null, () =>
          onVoice.value ? h(AiVoiceTab, { blocked: null, status: STATUS, aiNotice: '' }) : h(OtherTab),
        ),
    ),
    { global: { stubs: { IconGlyph: true } } },
  )
  await flushPromises()
  return { onVoice, page }
}

describe('AiVoiceTab (pronunciation and handwriting)', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.practiceCheckLessons.mockResolvedValue([{ id: 5, title: 'Hello and Self-introduction', phrases: [] }])
    api.practiceChecks.mockResolvedValue(pageOf([HANDWRITING_CHECK], 0, 1, 5))
    URL.createObjectURL = vi.fn(() => 'blob:photo')
    URL.revokeObjectURL = vi.fn()
  })

  it('refuses a file the server would not accept before uploading anything', async () => {
    const wrapper = await mountVoice()
    await chooseType(wrapper, 'Handwriting')

    await chooseFile(wrapper.get('input[type="file"]'), new File(['gif'], 'hao.gif', { type: 'image/gif' }))

    expect(wrapper.get('.field-error').text()).toBe('Choose a JPG or PNG photo.')
    expect(wrapper.get('.ai-file-name').text()).toContain('hao.gif')
    expect(wrapper.get<HTMLButtonElement>('.ai-check-submit button').element.disabled).toBe(true)
    expect(api.checkPractice).not.toHaveBeenCalled()

    await wrapper.get('.ai-file-row > button').trigger('click')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(wrapper.get('.ai-file-name').text()).toBe('No file chosen')
  })

  it('offers recording and handwriting and keeps lesson targets available in both modes', async () => {
    api.practiceCheckLessons.mockResolvedValue([
      { id: 5, title: 'Hello and Self-introduction', phrases: [{ chinese: '你好！' }] },
    ])
    const wrapper = await mountVoice()
    expect(
      wrapper
        .get('.segmented')
        .findAll('button')
        .map((button) => button.text()),
    ).toEqual(['Recording', 'Handwriting'])
    await wrapper.get('select').setValue('5')
    const phrase = buttonNamed(wrapper, '你好！')
    const target = wrapper.get<HTMLInputElement>('input.cjk-text')
    await target.setValue(' 你好！ ')
    expect(phrase.attributes('aria-pressed')).toBe('true')
    await target.setValue('别的词')
    expect(phrase.attributes('aria-pressed')).toBe('false')
    await phrase.trigger('click')
    expect(target.element.value).toBe('你好！')
    await chooseType(wrapper, 'Handwriting')
    expect(buttonNamed(wrapper, '你好！').exists()).toBe(true)
  })

  it('retries the optional lesson list and a failed history page without showing rows from the previous page', async () => {
    api.practiceCheckLessons.mockRejectedValueOnce(new Error('offline'))
    api.practiceChecks
      .mockResolvedValueOnce(pageOf([HANDWRITING_CHECK], 0, 6, 5))
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(pageOf([{ ...HANDWRITING_CHECK, id: 42, targetText: '谢' }], 1, 6, 5))
    const wrapper = await mountVoice()
    await wrapper.get('form [role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.get('select').text()).toContain('Hello and Self-introduction')

    await wrapper.findAll('.pagination-pages button')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.ai-check-item').exists()).toBe(false)
    await buttonNamed(wrapper.get('.ai-checks'), 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.get('.ai-check-item').text()).toContain('Handwriting · 谢')
  })

  it('sends a handwriting photo with its target and lesson and shows the score', async () => {
    api.checkPractice.mockResolvedValue({ status: 'SUCCESS', message: null, check: HANDWRITING_CHECK })
    const wrapper = await mountVoice()
    await chooseType(wrapper, 'Handwriting')

    const photo = new File(['png'], 'hao.png', { type: 'image/png' })
    await chooseFile(wrapper.get('input[type="file"]'), photo)
    await wrapper.get('input.cjk-text').setValue('好')
    await wrapper.get('select').setValue('5')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.checkPractice).toHaveBeenCalledWith({
      type: 'HANDWRITING',
      file: photo,
      targetText: '好',
      lessonId: 5,
      languagePreference: 'en',
    })
    const result = wrapper.get('[data-testid="ai-check-result"]')
    expect(result.get('.ai-check-number').text()).toBe('88/ 100')
    expect(result.text()).toContain('The left part 女 is a little narrow.')
    expect(result.text()).toContain('Practice score only. AI can make mistakes.')
    // The photo it was given stays with the score (the server keeps no photo).
    expect(result.get('img.ai-check-photo').attributes('src')).toBe('blob:photo')
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.emitted('used')).toHaveLength(1)
    // Its row in Recent checks is the score on screen.
    expect(wrapper.get('.ai-check-item').text()).toContain('Viewing')
    expect(wrapper.get('.ai-check-item').find('button').exists()).toBe(false)

    await buttonNamed(result, 'Try again').trigger('click')
    expect(wrapper.find('[data-testid="ai-check-result"]').exists()).toBe(false)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:photo')
    expect(wrapper.get<HTMLInputElement>('input.cjk-text').element.value).toBe('好')
    expect(wrapper.get('.ai-check-item button').text()).toBe('Open')
  })

  it('keeps the practice form open and explains how to retake an unreadable photo', async () => {
    api.checkPractice.mockResolvedValue({
      status: 'FAILED',
      message:
        'We could not read the handwriting clearly. Retake the photo in good light with the whole character in frame.',
    })
    const wrapper = await mountVoice()
    await chooseType(wrapper, 'Handwriting')
    await wrapper.get('input.cjk-text').setValue('好')
    await chooseFile(wrapper.get('input[type="file"]'), new File(['png'], 'hao.png', { type: 'image/png' }))
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('form [role="alert"]').text()).toContain('Retake the photo in good light')
    expect(wrapper.find('[data-testid="ai-check-result"]').exists()).toBe(false)
    expect(api.practiceChecks).toHaveBeenCalledOnce()
  })

  it('asks for the microphone in its own dialog before recording, while the browser has not been asked', async () => {
    const permission = deferred<{ state: PermissionState }>()
    const getUserMedia = stubMicrophonePrompt(permission.promise)
    const wrapper = await mountVoice()

    await wrapper.get('[data-testid="ai-record"]').trigger('click')
    expect(getUserMedia).not.toHaveBeenCalled()
    permission.resolve({ state: 'prompt' })
    await flushPromises()
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Use your microphone?')
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain(
      'Tencent Cloud for pronunciation assessment.',
    )
    expect(getUserMedia).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    expect(getUserMedia).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
  })

  it('discards the pending recording request when the learner chooses handwriting', async () => {
    const permission = deferred<{ state: PermissionState }>()
    const getUserMedia = stubMicrophonePrompt(permission.promise)
    const wrapper = await mountVoice()

    await wrapper.get('[data-testid="ai-record"]').trigger('click')
    await chooseType(wrapper, 'Handwriting')
    permission.resolve({ state: 'prompt' })
    await flushPromises()

    expect(getUserMedia).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
  })

  it('takes its microphone question along when the learner leaves the tab, so nothing records out of sight', async () => {
    const getUserMedia = stubMicrophonePrompt()
    const { page, onVoice } = await mountCachedVoice()
    const question = () => document.body.querySelector<HTMLElement>('[data-testid="confirm-dialog"]')

    await page.get('[data-testid="ai-record"]').trigger('click')
    expect(question()).not.toBeNull()

    onVoice.value = false
    await flushPromises()
    expect(question()).toBeNull()

    onVoice.value = true
    await flushPromises()
    question()!.querySelector<HTMLButtonElement>('[data-testid="confirm-dialog-confirm"]')!.click()
    expect(getUserMedia).toHaveBeenCalledOnce()
  })

  it('requires a new click if a permission check outlives the tab visit', async () => {
    const permission = deferred<{ state: PermissionState }>()
    const getUserMedia = stubMicrophonePrompt(permission.promise)
    const { page, onVoice } = await mountCachedVoice()

    await page.get('[data-testid="ai-record"]').trigger('click')
    onVoice.value = false
    await flushPromises()
    onVoice.value = true
    await flushPromises()
    permission.resolve({ state: 'granted' })
    await flushPromises()
    expect(getUserMedia).not.toHaveBeenCalled()

    await page.get('[data-testid="ai-record"]').trigger('click')
    expect(getUserMedia).toHaveBeenCalledOnce()
  })

  it('takes another audio file after one that is too large, and checks it (S9.10 → S9.25)', async () => {
    const wrapper = await mountVoice({ ...STATUS, audioUploadMaxBytes: 10 * MB })
    await wrapper.get('input.cjk-text').setValue('谢谢')
    const submit = () => wrapper.get<HTMLButtonElement>('.ai-check-submit button')

    await chooseFile(wrapper.get('input[type="file"]'), fileWithSize('xiexie.wav', 'audio/wav', 14 * MB))
    expect(wrapper.get('.ai-file-name').text()).toBe('xiexie.wav · 14 MB')
    expect(wrapper.get('.field-error').text()).toBe('This file is 14 MB. Use a file up to 10 MB.')
    expect(submit().element.disabled).toBe(true)

    await chooseFile(wrapper.get('input[type="file"]'), fileWithSize('xiexie.mp3', 'audio/mpeg', 2 * MB))
    expect(wrapper.get('.ai-file-name').text()).toBe('xiexie.mp3 · 2 MB')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(submit().element.disabled).toBe(false)

    // × puts the file away again: nothing to check (S9.20).
    await wrapper.get('.ai-file-row > button').trigger('click')
    expect(wrapper.get('.ai-file-name').text()).toBe('No file chosen')
    expect(submit().element.disabled).toBe(true)
  })

  it.each([
    ['Recording', 'audioUploadMaxBytes', 'audio/webm', 'xiexie.webm', 'WEBM, WAV or MP3'],
    ['Handwriting', 'imageUploadMaxBytes', 'image/png', 'xiexie.png', 'JPG or PNG'],
  ] as const)(
    'rechecks the selected %s file against its own latest limit',
    async (label, limit, type, name, formats) => {
      const status = { ...STATUS, audioUploadMaxBytes: 20 * MB, imageUploadMaxBytes: 20 * MB }
      const wrapper = await mountVoice(status)
      await chooseType(wrapper, label)
      await wrapper.get('input.cjk-text').setValue('谢')
      const file = fileWithSize(name, type, 12 * MB)
      await chooseFile(wrapper.get('input[type="file"]'), file)
      const submit = wrapper.get<HTMLButtonElement>('.ai-check-submit button')
      expect(submit.element.disabled).toBe(false)
      expect(wrapper.text()).toContain(`${formats} · up to 20 MB`)

      await wrapper.setProps({ status: { ...status, [limit]: MB } })
      expect(wrapper.get('.field-error').text()).toBe('This file is 12 MB. Use a file up to 1 MB.')
      expect(submit.element.disabled).toBe(true)
      await wrapper.get('form').trigger('submit')
      expect(api.checkPractice).not.toHaveBeenCalled()

      await wrapper.setProps({ status })
      expect(wrapper.find('.field-error').exists()).toBe(false)
      expect(submit.element.disabled).toBe(false)

      // Older servers enforce their limit on receipt and do not advertise a size.
      await wrapper.setProps({ status: STATUS })
      expect(wrapper.get('[id$="-file-hint"]').text()).toBe(formats)
      expect(submit.element.disabled).toBe(false)
      api.checkPractice.mockResolvedValue({ status: 'FAILED', message: 'Upload refused.', check: null })
      await wrapper.get('form').trigger('submit')
      await flushPromises()
      expect(api.checkPractice).toHaveBeenCalledWith(expect.objectContaining({ file }))
      expect(wrapper.get('form [role="alert"]').text()).toBe('Upload refused.')
    },
  )

  it('applies the audio limit to a recording made in the browser', async () => {
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: async () => ({ getTracks: () => [] }) } })
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const wrapper = await mountVoice({ ...STATUS, audioUploadMaxBytes: 4 })
    await wrapper.get('input.cjk-text').setValue('谢')
    await wrapper.get('[data-testid="ai-record"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="ai-record-stop"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('form [role="alert"]').text()).toBe('This file is 5 B. Use a file up to 4 B.')
    await wrapper.get('form').trigger('submit')
    expect(api.checkPractice).not.toHaveBeenCalled()
    await wrapper.setProps({ status: { ...STATUS, audioUploadMaxBytes: 5 } })
    expect(wrapper.find('form [role="alert"]').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('.ai-check-submit button').element.disabled).toBe(false)
  })

  it('says what is happening while the chosen audio file is being checked', async () => {
    const checked = deferred()
    api.checkPractice.mockReturnValue(checked.promise)
    const wrapper = await mountVoice()

    await submitRecording(wrapper)

    const submit = wrapper.get('.ai-check-submit')
    expect(submit.text()).toContain('Checking your audio file…')
    expect(submit.get('button').text()).toBe('Checking…')

    checked.resolve({ status: 'SUCCESS', message: null, check: HANDWRITING_CHECK })
    await flushPromises()
  })

  it('names the audio file a score was given, which the server does not keep', async () => {
    const recording: PracticeCheck = {
      ...HANDWRITING_CHECK,
      id: 42,
      type: 'RECORDING',
      targetText: '谢谢',
      lessonTitle: null,
    }
    api.checkPractice.mockResolvedValue({ status: 'SUCCESS', message: null, check: recording })
    const wrapper = await mountVoice()

    await chooseFile(wrapper.get('input[type="file"]'), new File(['mp3'], 'xiexie.mp3', { type: 'audio/mpeg' }))
    await wrapper.get('input.cjk-text').setValue('谢谢')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const result = wrapper.get('[data-testid="ai-check-result"]')
    expect(result.get('h2').text().replace(/\s+/g, ' ')).toBe('Your score · Audio file · 谢谢')
    expect(result.text()).toContain('xiexie.mp3 · checked just now')
  })

  it('shows the server reason when the recording could not be scored', async () => {
    api.checkPractice.mockResolvedValue({
      status: 'FAILED',
      message:
        "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.",
      check: null,
    })
    const wrapper = await mountVoice()

    await submitRecording(wrapper)
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-check-result"]').exists()).toBe(false)
    expect(wrapper.get('.callout.is-danger').text()).toContain("SparkBot couldn't hear any speech")
  })

  it('says when the AI service cannot score recordings', async () => {
    const wrapper = await mountVoice({ ...STATUS, speechCheckAvailable: false })

    expect(wrapper.get('.callout.is-warning').text()).toBe('Pronunciation assessment is currently unavailable.')
  })

  it('does not claim the AI service cannot score while its status is still unknown', async () => {
    const wrapper = await mountVoice(null)

    expect(wrapper.find('.callout.is-warning').exists()).toBe(false)
    // Nothing can be sent before the status says what SparkBot can check.
    expect(wrapper.get<HTMLButtonElement>('.ai-check-submit button').element.disabled).toBe(true)
  })

  it('offers the audio file, not typing, when the microphone is refused', async () => {
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: () => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })),
      },
    })
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const wrapper = await mountVoice()

    await buttonNamed(wrapper, 'Start recording').trigger('click')
    await flushPromises()

    expect(wrapper.get('.ai-recorder .field-hint').text()).toBe(
      'Microphone blocked. Allow it from the lock icon in the address bar, then press Start recording, or upload a file below.',
    )
    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
  })

  it('shows Stop and the clock with what to say while recording, then the recording to check', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [] }) },
    })
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const wrapper = await mountVoice()
    await wrapper.get('input.cjk-text').setValue('谢谢')

    await wrapper.get('[data-testid="ai-record"]').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(3000)

    expect(wrapper.get('[data-testid="ai-record-stop"]').text()).toBe('Stop')
    expect(wrapper.get('[data-testid="ai-recorder-status"]').text()).toBe(
      'Recording… 0:03 · say 谢谢 now. It stays on this device until you check it.',
    )
    // The clock is not read out every second.
    expect(wrapper.get('[data-testid="ai-recorder-status"] [aria-hidden="true"]').text()).toBe('0:03')

    await wrapper.get('[data-testid="ai-record-stop"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('.ai-recording').text()).toContain('Your recording')
    const submit = () => wrapper.get<HTMLButtonElement>('.ai-check-submit button')
    expect(submit().element.disabled).toBe(false)

    vi.spyOn(wrapper.get('audio').element, 'play').mockRejectedValue(
      new DOMException('Unsupported', 'NotSupportedError'),
    )
    await buttonNamed(wrapper.get('.ai-recording'), 'Play').trigger('click')
    await flushPromises()
    expect(wrapper.get('form [role="alert"]').text()).toBe('This recording could not be played.')
    expect(submit().element.disabled).toBe(false)

    await chooseFile(wrapper.get('input[type="file"]'), new File(['gif'], 'wrong.gif', { type: 'image/gif' }))
    expect(wrapper.get('.field-error').text()).toBe('Choose a WEBM, WAV or MP3 audio file.')
    expect(submit().element.disabled).toBe(true)
    await wrapper.get('form').trigger('submit')
    expect(api.checkPractice).not.toHaveBeenCalled()

    await wrapper.get('.ai-file-row > button').trigger('click')
    expect(submit().element.disabled).toBe(false)
  })

  it('deletes a check from its drawer at once, with Undo in the toast that brings it back', async () => {
    api.deletePracticeCheck.mockResolvedValue(null)
    api.restorePracticeCheck.mockResolvedValue(HANDWRITING_CHECK)
    const wrapper = await mountVoice()

    const open = wrapper.get('.ai-check-item button')
    expect(open.text()).toBe('Open')
    expect(open.attributes('aria-label')).toBe('Open Handwriting · 好')
    await open.trigger('click')
    expect(wrapper.find('.overlay-panel--drawer').exists()).toBe(true)
    api.practiceChecks.mockResolvedValueOnce(pageOf([], 0, 0, 5))
    await buttonNamed(wrapper.get('.overlay-panel'), 'Delete').trigger('click')
    await flushPromises()

    // No question first: the check is gone, from the drawer and from Recent checks.
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(api.deletePracticeCheck).toHaveBeenCalledWith(41)
    expect(wrapper.find('.overlay-panel--drawer').exists()).toBe(false)
    expect(wrapper.find('.ai-check-item').exists()).toBe(false)
    const [message, undo] = wrapper.emitted<[string, { label: string; run: () => void }]>('toast')!.at(-1)!
    expect(message).toBe('Check deleted.')
    expect(undo.label).toBe('Undo')

    undo.run()
    await flushPromises()
    expect(api.restorePracticeCheck).toHaveBeenCalledWith(41)
    expect(api.practiceChecks).toHaveBeenCalledTimes(3)
    expect(wrapper.get('.ai-check-item').text()).toContain('Handwriting · 好')
  })

  it('keeps a check its Delete could not delete, saying why in the drawer', async () => {
    api.deletePracticeCheck.mockRejectedValue(new Error('offline'))
    const wrapper = await mountVoice()

    await wrapper.get('.ai-check-item button').trigger('click')
    await buttonNamed(wrapper.get('.overlay-panel'), 'Delete').trigger('click')
    await flushPromises()

    expect(wrapper.find('.overlay-panel--drawer').exists()).toBe(true)
    expect(wrapper.get('.overlay-panel [role="alert"]').text()).toBe(
      'The check could not be deleted. Please try again.',
    )
    expect(wrapper.emitted('toast')).toBeUndefined()
  })

  it('goes back a page after deleting the only check on the last page', async () => {
    api.deletePracticeCheck.mockResolvedValue(null)
    api.practiceChecks.mockImplementation(async (page: number) => pageOf([HANDWRITING_CHECK], page, 6, 5))
    const wrapper = await mountVoice()

    await wrapper.findAll('.pagination-pages button')[1]!.trigger('click')
    await flushPromises()
    expect(api.practiceChecks).toHaveBeenLastCalledWith(1, 5)

    await wrapper.get('.ai-check-item button').trigger('click')
    await buttonNamed(wrapper.get('.overlay-panel'), 'Delete').trigger('click')
    await flushPromises()

    expect(api.practiceChecks).toHaveBeenLastCalledWith(0, 5)
  })

  it('explains the recording score and shows that tones are not measured', async () => {
    const oldRecording: PracticeCheck = {
      ...HANDWRITING_CHECK,
      type: 'RECORDING',
      targetText: '谢谢',
      clarityScore: 85,
      paceScore: 80,
    }
    api.practiceChecks.mockResolvedValue(pageOf([oldRecording], 0, 1, 5))
    const wrapper = await mountVoice()
    await wrapper.get('.ai-check-item button').trigger('click')
    const detail = wrapper.get('.ai-check-score')

    expect(detail.findAll('.ai-subscores dt').map((label) => label.text())).toEqual([
      'Tones',
      'Recognised words',
      'Estimated pace',
    ])
    expect(detail.find('.ai-subscores dd').text()).toBe('Not measured')
    expect(detail.text()).toContain('Tone accuracy is not measured')
    expect(detail.text()).toContain('85 / 100')
    expect(detail.find('.ai-tone-diagram').exists()).toBe(false)
  })

  it('compares both matching and different tones on a newly submitted recording', async () => {
    api.checkPractice.mockResolvedValue({
      status: 'SUCCESS',
      message: null,
      check: {
        ...HANDWRITING_CHECK,
        type: 'RECORDING',
        targetText: '谢谢',
        feedback: null,
        tips: [],
        pronunciation: {
          accuracy: 85,
          fluency: 76,
          completeness: 100,
          issues: [{ text: '谢', kind: 'TONE_MISMATCH', expectedTone: 4, detectedTone: 2 }],
          toneComparisons: [
            { text: '谢', expectedTone: 4, detectedTone: 2 },
            { text: '谢', expectedTone: 4, detectedTone: 4 },
          ],
        },
      },
    })
    const wrapper = await mountVoice()
    await submitRecording(wrapper)
    await flushPromises()

    const result = wrapper.get('[data-testid="ai-check-result"]')
    expect(result.text()).toContain('Tencent Cloud checks your audio')
    expect(result.get('.ai-subscores').text()).toContain('Accuracy85 / 100')
    expect(result.get('.ai-subscores').text()).toContain('Completeness100 / 100')
    expect(result.get('.ai-tips').text()).toContain('谢 · Aim for tone 4. This sounded like tone 2.')
    const cards = result.findAll('.ai-tone-card')
    expect(cards.map((card) => card.get('.ai-tone-label').text())).toEqual(['tone 4 → tone 2', 'tone 4 → tone 4'])
    expect(cards.map((card) => card.classes('is-different'))).toEqual([true, false])
    // Target + detected tone curves are plotted for each syllable without coupling to raw SVG coordinates.
    expect(cards[0]!.findAll('svg path')).toHaveLength(2)
    expect(cards[1]!.findAll('svg path')).toHaveLength(2)
  })

  it('reports a practice check from the check drawer and marks it reported', async () => {
    api.report.mockResolvedValue({ id: 12 })
    const wrapper = await mountVoice()

    await wrapper.get('.ai-checks button').trigger('click')
    await wrapper.get('.ai-check-report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenCalledWith(
      expect.objectContaining({
        featureKey: 'PRACTICE_CHECK',
        practiceCheckId: HANDWRITING_CHECK.id,
      }),
    )
    expect(wrapper.emitted('reported')).toHaveLength(1)
    expect(wrapper.get('.ai-reported').text()).toContain('Reported')
  })

  it('plots recorded tone issues from history that predates the full comparison data', async () => {
    const savedRecording: PracticeCheck = {
      ...HANDWRITING_CHECK,
      type: 'RECORDING',
      targetText: '谢',
      pronunciation: {
        accuracy: 82,
        fluency: 90,
        completeness: 100,
        issues: [{ text: '谢', kind: 'TONE_MISMATCH', expectedTone: 4, detectedTone: 2 }],
      },
    }
    api.practiceChecks.mockResolvedValue(pageOf([savedRecording], 0, 1, 5))
    const wrapper = await mountVoice()
    await wrapper.get('.ai-check-item button').trigger('click')

    expect(wrapper.get('.ai-check-detail .ai-tone-card').get('.ai-tone-label').text()).toBe('tone 4 → tone 2')
  })

  it('cancels a pending microphone permission check when the form is submitted', async () => {
    const permission = deferred<{ state: PermissionState }>()
    const getUserMedia = stubMicrophonePrompt(permission.promise)
    api.checkPractice.mockResolvedValue({ status: 'SUCCESS', message: null, check: HANDWRITING_CHECK })
    const wrapper = await mountVoice()

    await wrapper.get('input.cjk-text').setValue('谢谢')
    await chooseFile(wrapper.get('input[type="file"]'), fileWithSize('say.webm', 'audio/webm', 64))
    await flushPromises()

    await wrapper.get('[data-testid="ai-record"]').trigger('click')
    await wrapper.get('.ai-check-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Neat and balanced.')

    permission.resolve({ state: 'prompt' })
    await flushPromises()
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(getUserMedia).not.toHaveBeenCalled()
  })
})
