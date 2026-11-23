import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'

import TracingPad from './TracingPad.vue'

type Stroke = Array<{ x: number; y: number }>

const BoundTracingHost = defineComponent({
  props: {
    characters: { type: String, required: true },
    showModel: { type: Boolean, required: true },
    label: { type: String, required: true },
  },
  setup(props, { expose }) {
    const strokes = ref<Stroke[]>([])
    expose({ strokes })
    return () =>
      h(TracingPad, {
        characters: props.characters,
        showModel: props.showModel,
        label: props.label,
        cellSize: 280,
        strokes: strokes.value,
        'onUpdate:strokes': (next: Stroke[]) => {
          strokes.value = next
        },
      })
  },
})

async function pointer(canvas: HTMLCanvasElement, type: string, options: PointerEventInit) {
  canvas.dispatchEvent(new PointerEvent(type, { bubbles: true, ...options }))
  await nextTick()
}

function mountPad() {
  const wrapper = mount(TracingPad, {
    props: { characters: '好', showModel: true, label: 'Trace 好', cellSize: 280, strokes: [] },
  })
  const pad = wrapper.get('canvas')
  pad.element.setPointerCapture = vi.fn()
  pad.element.hasPointerCapture = vi.fn(() => true)
  pad.element.releasePointerCapture = vi.fn()
  return { wrapper, pad }
}

describe('TracingPad input lifecycle', () => {
  beforeEach(() => {
    // These tests exercise input/model behavior, not Canvas rendering in jsdom.
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
  })

  it('commits a completed pointer stroke and discards a cancelled one', async () => {
    const { wrapper, pad } = mountPad()
    await pointer(pad.element, 'pointerdown', { pointerId: 1, clientX: 10, clientY: 20 })
    await pointer(pad.element, 'pointermove', { pointerId: 1, clientX: 30, clientY: 40 })
    await pointer(pad.element, 'pointercancel', { pointerId: 1 })
    expect(wrapper.emitted('update:strokes')).toBeUndefined()
    await pointer(pad.element, 'pointerdown', { pointerId: 2, clientX: 50, clientY: 60 })
    // A second finger landing on the pad cannot interrupt the active stroke.
    await pointer(pad.element, 'pointerdown', { pointerId: 3, clientX: 80, clientY: 90 })
    await pointer(pad.element, 'pointerup', { pointerId: 3 })
    await pointer(pad.element, 'pointermove', { pointerId: 2, clientX: 70, clientY: 80 })
    await pointer(pad.element, 'pointerup', { pointerId: 2 })
    expect(wrapper.emitted('update:strokes')).toEqual([
      [
        [
          [
            { x: 50, y: 60 },
            { x: 70, y: 80 },
          ],
        ],
      ],
    ])
  })

  it('cancels an unfinished pointer stroke and releases capture when disabled', async () => {
    const { wrapper, pad } = mountPad()
    const completed = [
      [
        { x: 1, y: 2 },
        { x: 3, y: 4 },
      ],
    ]
    await wrapper.setProps({ strokes: completed })
    await pointer(pad.element, 'pointerdown', { pointerId: 3, clientX: 10, clientY: 20 })
    await pointer(pad.element, 'pointermove', { pointerId: 3, clientX: 30, clientY: 40 })
    await wrapper.setProps({ disabled: true })
    await pointer(pad.element, 'pointermove', { pointerId: 3, clientX: 90, clientY: 100 })
    await pointer(pad.element, 'pointerup', { pointerId: 3 })

    expect(wrapper.emitted('update:strokes')).toBeUndefined()
    expect(pad.element.releasePointerCapture).toHaveBeenCalledWith(3)
    expect(wrapper.props('strokes')).toEqual(completed)
    expect(pad.attributes('tabindex')).toBe('-1')

    await wrapper.setProps({ disabled: false })
    await pointer(pad.element, 'pointerdown', { pointerId: 4, clientX: 50, clientY: 60 })
    await pointer(pad.element, 'pointermove', { pointerId: 4, clientX: 70, clientY: 80 })
    await pointer(pad.element, 'pointerup', { pointerId: 4 })
    expect(wrapper.emitted('update:strokes')).toEqual([
      [
        [
          ...completed,
          [
            { x: 50, y: 60 },
            { x: 70, y: 80 },
          ],
        ],
      ],
    ])
  })

  it('finishes a keyboard stroke on leaving the pad and moves the pen freely before the next one', async () => {
    const { wrapper, pad } = mountPad()
    await pad.trigger('keydown', { key: ' ' })
    await pad.trigger('keydown', { key: 'ArrowRight' })
    await pad.trigger('blur')
    await pad.trigger('keydown', { key: 'ArrowDown' })
    const firstStroke = [
      { x: 140, y: 140 },
      { x: 150, y: 140 },
    ]
    expect(wrapper.emitted('update:strokes')).toEqual([[[firstStroke]]])
    await pad.trigger('keydown', { key: 'Enter' })
    await pad.trigger('keydown', { key: 'ArrowRight' })
    await pad.trigger('keydown', { key: 'Enter' })
    const secondStroke = [
      { x: 150, y: 150 },
      { x: 160, y: 150 },
    ]
    expect(wrapper.emitted('update:strokes')?.at(-1)).toEqual([[firstStroke, secondStroke]])
  })

  it('does not resume an old keyboard stroke after being disabled and enabled', async () => {
    const { wrapper, pad } = mountPad()
    await pad.trigger('keydown', { key: ' ' })
    await pad.trigger('keydown', { key: 'ArrowRight' })
    await wrapper.setProps({ disabled: true })
    await wrapper.setProps({ disabled: false })
    await pad.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:strokes')).toBeUndefined()
    await pad.trigger('keydown', { key: 'Enter', repeat: true })
    await pad.trigger('keydown', { key: 'ArrowDown' })
    await pad.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:strokes')).toHaveLength(1)
  })

  it('starts a fresh stroke when the next character uses the same canvas width', async () => {
    const { wrapper, pad } = mountPad()
    await pad.trigger('keydown', { key: ' ' })
    await pad.trigger('keydown', { key: 'ArrowRight' })
    await wrapper.setProps({ characters: '你', strokes: [] })
    await pad.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:strokes')).toBeUndefined()
    await pad.trigger('keydown', { key: 'ArrowRight' })
    await pad.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:strokes')).toEqual([
      [
        [
          [
            { x: 140, y: 140 },
            { x: 150, y: 140 },
          ],
        ],
      ],
    ])
  })

  it('discards the unfinished keyboard stroke when the parent clears the pad', async () => {
    const { wrapper, pad } = mountPad()
    await pad.trigger('keydown', { key: ' ' })
    await pad.trigger('keydown', { key: 'ArrowRight' })
    await wrapper.setProps({ strokes: [] })
    await pad.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:strokes')).toBeUndefined()
    await pad.trigger('keydown', { key: 'ArrowDown' })
    await pad.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:strokes')).toHaveLength(1)
  })

  it('keeps a new stroke started immediately after committing the previous stroke via v-model', async () => {
    const wrapper = mount(BoundTracingHost, {
      props: { characters: '好', showModel: true, label: 'Trace 好' },
    })
    const pad = wrapper.get('canvas')
    pad.element.setPointerCapture = vi.fn()
    pad.element.hasPointerCapture = vi.fn(() => true)
    pad.element.releasePointerCapture = vi.fn()

    // Commit stroke 1 and immediately start stroke 2 BEFORE nextTick flushes the strokes watcher.
    pad.element.dispatchEvent(
      new PointerEvent('pointerdown', { bubbles: true, pointerId: 1, clientX: 10, clientY: 10 }),
    )
    pad.element.dispatchEvent(
      new PointerEvent('pointermove', { bubbles: true, pointerId: 1, clientX: 30, clientY: 30 }),
    )
    pad.element.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 1 }))
    pad.element.dispatchEvent(
      new PointerEvent('pointerdown', { bubbles: true, pointerId: 2, clientX: 50, clientY: 50 }),
    )
    await nextTick()
    pad.element.dispatchEvent(
      new PointerEvent('pointermove', { bubbles: true, pointerId: 2, clientX: 70, clientY: 70 }),
    )
    pad.element.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 2 }))
    await nextTick()

    expect(wrapper.findComponent(TracingPad).props('strokes')).toHaveLength(2)
  })

  it('redraws a committed v-model stroke onto the canvas and avoids duplicate fallback moves', async () => {
    const moveTo = vi.fn()
    const lineTo = vi.fn()
    const fakeContext = {
      clearRect: vi.fn(),
      save: vi.fn(),
      restore: vi.fn(),
      setTransform: vi.fn(),
      setLineDash: vi.fn(),
      beginPath: vi.fn(),
      moveTo,
      lineTo,
      stroke: vi.fn(),
      fillText: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
    } as unknown as CanvasRenderingContext2D
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(fakeContext)

    const wrapper = mount(BoundTracingHost, {
      attachTo: document.body,
      props: { characters: '一', showModel: false, label: 'Trace 一' },
    })
    try {
      const pad = wrapper.get('canvas')
      // Force the window pointer fallback path to verify pointermove is not processed twice.
      pad.element.setPointerCapture = vi.fn()
      pad.element.hasPointerCapture = vi.fn(() => false)
      pad.element.releasePointerCapture = vi.fn()

      pad.element.dispatchEvent(
        new PointerEvent('pointerdown', { bubbles: true, pointerId: 1, clientX: 25, clientY: 35 }),
      )
      pad.element.dispatchEvent(
        new PointerEvent('pointermove', { bubbles: true, pointerId: 1, clientX: 120, clientY: 135 }),
      )
      moveTo.mockClear()
      lineTo.mockClear()
      pad.element.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 1 }))
      await nextTick()

      expect(wrapper.findComponent(TracingPad).props('strokes')).toEqual([
        [
          { x: 25, y: 35 },
          { x: 120, y: 135 },
        ],
      ])
      // The post-commit redraw after v-model updates must paint the committed stroke onto the canvas.
      expect(moveTo).toHaveBeenCalledWith(25, 35)
      expect(lineTo).toHaveBeenCalledWith(120, 135)
    } finally {
      wrapper.unmount()
    }
  })

  it('cancels an active fallback pointer stroke on window blur so subsequent strokes still draw', async () => {
    const wrapper = mount(TracingPad, {
      attachTo: document.body,
      props: { characters: '好', showModel: true, label: 'Trace 好', cellSize: 280, strokes: [] },
    })
    try {
      const pad = wrapper.get('canvas')
      pad.element.setPointerCapture = vi.fn()
      pad.element.hasPointerCapture = vi.fn(() => false)
      pad.element.releasePointerCapture = vi.fn()

      await pointer(pad.element, 'pointerdown', { pointerId: 1, clientX: 10, clientY: 20 })
      await pointer(pad.element, 'pointermove', { pointerId: 1, clientX: 30, clientY: 40 })
      window.dispatchEvent(new Event('blur'))
      await nextTick()
      expect(wrapper.emitted('update:strokes')).toBeUndefined()

      await pointer(pad.element, 'pointerdown', { pointerId: 2, clientX: 50, clientY: 60 })
      await pointer(pad.element, 'pointermove', { pointerId: 2, clientX: 70, clientY: 80 })
      await pointer(pad.element, 'pointerup', { pointerId: 2 })
      expect(wrapper.emitted('update:strokes')).toHaveLength(1)
    } finally {
      wrapper.unmount()
    }
  })
})
