import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { buttonNamed, deferred } from '@/test/helpers'
import { hintSettings, practiceItem, practiceResult } from '@/test/learnFixtures'
import type { PracticeResult, PracticeSet } from '@/types/learn'

import { mountPlayer, tracingSet } from './practicePlayerTestHelpers'

const api = vi.hoisted(() => ({ complete: vi.fn(), hint: vi.fn() }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))

vi.mock('@/api/learn', () => ({ practiceApi: api, hintApi }))

import TracingPlayer from './TracingPlayer.vue'

describe('TracingPlayer', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    Object.values(hintApi).forEach((fn) => fn.mockReset())
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'OFF' }))
    api.complete.mockResolvedValue(practiceResult({ type: 'TRACING' }))
  })

  it('asks for every stroke, with Undo and Clear, and sends the strokes with the finished set', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    const saved = deferred<PracticeResult>()
    api.complete.mockReturnValue(saved.promise)
    const wrapper = await mountPlayer(TracingPlayer, tracingSet)
    const done = () => wrapper.get('[data-testid="tracing-done"]')
    const pad = wrapper.getComponent({ name: 'TracingPad' })

    expect(done().attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('0 of 2 strokes')
    // A guided set shows the model; there is no hint switch to leave on all the time.
    expect(pad.props('showModel')).toBe(true)
    expect(wrapper.find('button[aria-pressed]').exists()).toBe(false)

    const stroke = [
      { x: 1, y: 1 },
      { x: 2, y: 2 },
    ]
    pad.vm.$emit('update:strokes', [stroke, stroke])
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('2 of 2 strokes')

    // Undo takes back a stroke and Clear the rest; with nothing left both are off but keep the focus.
    await buttonNamed(wrapper, 'Undo').trigger('click')
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('1 of 2 strokes')
    buttonNamed(wrapper, 'Clear').element.focus()
    await buttonNamed(wrapper, 'Clear').trigger('click')
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('0 of 2 strokes')
    expect(buttonNamed(wrapper, 'Clear').attributes('aria-disabled')).toBe('true')
    expect(buttonNamed(wrapper, 'Undo').attributes('aria-disabled')).toBe('true')
    expect(document.activeElement).toBe(buttonNamed(wrapper, 'Clear').element)
    await buttonNamed(wrapper, 'Undo').trigger('click')
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('0 of 2 strokes')

    pad.vm.$emit('update:strokes', [stroke, stroke])
    await wrapper.vm.$nextTick()
    await done().trigger('click')
    expect(pad.props('disabled')).toBe(true)
    await buttonNamed(wrapper, 'Clear').trigger('click')
    expect(wrapper.get('[data-testid="stroke-count"]').text()).toBe('2 of 2 strokes')
    saved.resolve(practiceResult({ type: 'TRACING' }))
    await flushPromises()

    expect(api.complete).toHaveBeenCalledWith('TRACING', 13, [], 2)
    expect(wrapper.emitted('finished')![0]![1]).toBe('xué · to learn')
  })

  it('counts a word’s strokes per character when the set gives each character’s strokes', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    const word: PracticeSet = {
      ...tracingSet,
      items: [practiceItem('0', { hanzi: '中　文', pinyin: 'Zhōngwén', meaning: 'Chinese' })],
      strokeCount: 8,
      characterStrokes: [4, 4],
    }
    const wrapper = await mountPlayer(TracingPlayer, word)
    const count = () => wrapper.get('[data-testid="stroke-count"]').text()
    const pad = wrapper.getComponent({ name: 'TracingPad' })
    expect(pad.props('characters')).toBe('中文')
    expect(wrapper.findAll('.hanzi-tile').map((tile) => tile.text())).toEqual(['中', '文'])
    // A stroke belongs to the square it starts in: 中 is the first 300px square, 文 the second.
    const inFirst = [
      { x: 40, y: 40 },
      { x: 90, y: 90 },
    ]
    const inSecond = [
      { x: 340, y: 40 },
      { x: 390, y: 90 },
    ]
    expect(count()).toBe('Character 1 of 2 · 0 of 4 strokes')

    pad.vm.$emit('update:strokes', [inFirst, inFirst, inFirst])
    await wrapper.vm.$nextTick()
    expect(count()).toBe('Character 1 of 2 · 3 of 4 strokes')

    pad.vm.$emit('update:strokes', [inFirst, inFirst, inFirst, inFirst, inSecond])
    await wrapper.vm.$nextTick()
    expect(count()).toBe('Character 2 of 2 · 1 of 4 strokes')
    expect(wrapper.get('[data-testid="tracing-done"]').attributes('disabled')).toBeDefined()

    // Eight strokes entirely in the first square leave the second character untouched.
    pad.vm.$emit(
      'update:strokes',
      Array.from({ length: 8 }, () => inFirst),
    )
    await wrapper.vm.$nextTick()
    expect(count()).toBe('Character 2 of 2 · 0 of 4 strokes')
    expect(wrapper.get('[data-testid="tracing-done"]').attributes('disabled')).toBeDefined()
    pad.vm.$emit('update:strokes', [...Array(4).fill(inFirst), ...Array(4).fill(inSecond)])
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="tracing-done"]').attributes('disabled')).toBeUndefined()

    // Without each character's strokes, the line counts the whole word.
    wrapper.unmount()
    const whole = await mountPlayer(TracingPlayer, { ...word, characterStrokes: null })
    expect(whole.get('[data-testid="stroke-count"]').text()).toBe('0 of 8 strokes')
  })

  it('hides the model on INDEPENDENT sets until a hint is opened, and recovers from complete failure', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
    api.hint.mockResolvedValue({
      kind: 'STROKE_ORDER',
      words: ['学'],
      pinyin: ['xué'],
      text: null,
      count: 2,
      ruledOut: [],
    })

    const independentSet: PracticeSet = { ...tracingSet, stage: 'INDEPENDENT' }
    const wrapper = await mountPlayer(TracingPlayer, independentSet)
    const pad = wrapper.getComponent({ name: 'TracingPad' })

    expect(pad.props('showModel')).toBe(false)
    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()
    expect(api.hint).toHaveBeenCalledWith('TRACING', independentSet.id, independentSet.items[0]!.key, independentSet)
    expect(pad.props('showModel')).toBe(true)

    const stroke = [
      { x: 1, y: 1 },
      { x: 2, y: 2 },
    ]
    pad.vm.$emit('update:strokes', [stroke, stroke])
    await wrapper.vm.$nextTick()

    api.complete.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(practiceResult({ type: 'TRACING' }))
    await wrapper.get('[data-testid="tracing-done"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBeTruthy()
    expect(wrapper.emitted('finished')).toBeUndefined()

    await wrapper.get('[data-testid="tracing-done"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('finished')).toHaveLength(1)
  })
})
