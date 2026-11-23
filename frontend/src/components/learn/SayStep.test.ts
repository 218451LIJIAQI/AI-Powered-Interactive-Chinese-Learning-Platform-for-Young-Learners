import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'

/** A recorder whose state the test sets; start() and stop() are spies. */
const recorder = vi.hoisted(() => ({ current: null as null | ReturnType<typeof fakeRecorder> }))

function fakeRecorder(supported = true) {
  const status = ref<'idle' | 'requesting' | 'recording' | 'recorded' | 'denied' | 'error'>('idle')
  return {
    supported,
    status,
    elapsedSeconds: ref(0),
    recording: ref(null),
    previewUrl: ref<string | null>(null),
    isRecording: computed(() => status.value === 'recording'),
    isBusy: computed(() => status.value === 'recording' || status.value === 'requesting'),
    start: vi.fn(),
    stop: vi.fn(),
    reset: vi.fn(),
  }
}

vi.mock('@/composables/useAudioRecorder', () => ({ useAudioRecorder: () => recorder.current }))

import SayStep from './SayStep.vue'

function setPermission(state: PermissionState | null): void {
  Object.defineProperty(navigator, 'permissions', {
    configurable: true,
    value: state === null ? undefined : { query: vi.fn(() => Promise.resolve({ state })) },
  })
}

function mountStep() {
  return mount(SayStep, {
    props: { hanzi: '爸爸', pinyin: 'bàba', meaning: 'dad' },
    global: { stubs: { teleport: true } },
  })
}

describe('SayStep (say a phrase from the lesson)', () => {
  beforeEach(() => {
    recorder.current = fakeRecorder()
    setPermission(null)
  })

  afterEach(() => {
    setPermission(null)
  })

  it('asks in its own dialog first: Not now closes it without the microphone, Allow lets the browser ask', async () => {
    const wrapper = mountStep()
    expect(wrapper.text()).toContain('bàba · dad')
    expect(wrapper.text()).toContain('Not graded. Play your recording back and compare it with the model.')

    await wrapper.get('[data-testid="say-record"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.get('[data-testid="mic-ask"]')
    expect(dialog.text()).toContain('Use your microphone?')
    expect(dialog.text()).toContain('Record yourself, then compare with the model audio.')
    expect(dialog.text()).toContain('Your browser asks next. Your recording stays on this device.')

    await wrapper.get('[data-testid="mic-not-now"]').trigger('click')
    expect(wrapper.find('[data-testid="mic-ask"]').exists()).toBe(false)
    expect(recorder.current!.start).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="say-record"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="mic-allow"]').trigger('click')
    expect(wrapper.find('[data-testid="mic-ask"]').exists()).toBe(false)
    expect(recorder.current!.start).toHaveBeenCalledOnce()

    // Asked once: recording again goes straight to the recorder.
    await wrapper.get('[data-testid="say-record"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="mic-ask"]').exists()).toBe(false)
    expect(recorder.current!.start).toHaveBeenCalledTimes(2)
  })

  it('does not ask again when the browser already allowed the microphone', async () => {
    setPermission('granted')
    const wrapper = mountStep()

    await wrapper.get('[data-testid="say-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="mic-ask"]').exists()).toBe(false)
    expect(recorder.current!.start).toHaveBeenCalledOnce()
  })

  it('says how to allow a blocked microphone and that the step can still be said out loud', async () => {
    setPermission('denied')
    const wrapper = mountStep()
    await wrapper.get('[data-testid="say-record"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="mic-ask"]').exists()).toBe(false)

    recorder.current!.status.value = 'denied'
    await flushPromises()

    const note = wrapper.get('.learn-say-note')
    expect(note.attributes('role')).toBe('alert')
    expect(note.text()).toBe(
      'Microphone blocked. To record, allow it in your browser’s site settings. Or say it out loud and continue — this step isn’t graded.',
    )
  })

  it('while recording, offers Stop and counts the time up with the phrase to say', async () => {
    const wrapper = mountStep()
    recorder.current!.status.value = 'recording'
    recorder.current!.elapsedSeconds.value = 2
    await flushPromises()

    const stop = wrapper.get('[data-testid="say-stop"]')
    expect(stop.text()).toBe('Stop')
    const note = wrapper.get('.learn-say-note')
    expect(note.text()).toBe('Recording… 0:02 · say 爸爸 now, then tap Stop. Not graded.')
    // The clock is not read out every second.
    expect(note.get('[aria-hidden="true"]').text()).toBe('0:02')

    recorder.current!.elapsedSeconds.value = 3
    await flushPromises()
    expect(note.text()).toBe('Recording… 0:03 · say 爸爸 now, then tap Stop. Not graded.')
    await stop.trigger('click')
    expect(recorder.current!.stop).toHaveBeenCalledOnce()
  })

  it('plays a finished recording back from its own button', async () => {
    const wrapper = mountStep()
    recorder.current!.status.value = 'recorded'
    recorder.current!.elapsedSeconds.value = 3
    recorder.current!.previewUrl.value = 'blob:recording'
    await flushPromises()

    expect(wrapper.get('[data-testid="say-record"]').text()).toBe('Record again')
    expect(wrapper.get('.learn-say-note').text()).toBe(
      'Recorded · 0:03. Not graded. Play it back and compare it with the model audio.',
    )
    const audio = wrapper.get('audio').element as HTMLAudioElement
    const play = vi
      .spyOn(audio, 'play')
      .mockRejectedValueOnce(new DOMException('Unsupported', 'NotSupportedError'))
      .mockResolvedValue()
    await wrapper.get('[data-testid="say-play"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('Audio didn’t load')
    await wrapper.get('[data-testid="say-play"]').trigger('click')
    expect(play).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    await wrapper.get('audio').trigger('play')
    expect(wrapper.get('[data-testid="say-play"]').text()).toBe('Stop')
    expect(wrapper.get('[data-testid="say-play"]').attributes('aria-pressed')).toBe('true')
  })

  it('without a recorder, says the phrase can be said out loud', () => {
    recorder.current = fakeRecorder(false)
    const wrapper = mountStep()

    expect(wrapper.find('[data-testid="say-record"]').exists()).toBe(false)
    expect(wrapper.get('.learn-say-note').text()).toBe(
      'This browser can’t record. Say it out loud and continue — this step isn’t graded.',
    )
  })
})
