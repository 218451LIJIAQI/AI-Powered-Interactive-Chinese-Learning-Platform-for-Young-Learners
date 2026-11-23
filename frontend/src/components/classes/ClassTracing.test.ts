import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TracingPad from '@/components/learning/TracingPad.vue'

import ClassTracing from './ClassTracing.vue'

const characters = [
  { character: '人', pinyin: 'rén', meaning: 'person' },
  { character: '大', pinyin: 'dà', meaning: 'big' },
]

function mountTracing(done = false) {
  return mount(ClassTracing, {
    props: { title: 'Trace 人 and 大', characters, busy: false, done },
    global: { stubs: { IconGlyph: true } },
  })
}

/** One stroke drawn with the keyboard: Space starts it, an arrow key draws, Space ends it. */
async function drawStrokeWithKeys(wrapper: ReturnType<typeof mountTracing>): Promise<void> {
  const pad = wrapper.get('[data-testid="tracing-pad"]')
  await pad.trigger('keydown', { key: ' ' })
  await pad.trigger('keydown', { key: 'ArrowRight' })
  await pad.trigger('keydown', { key: ' ' })
}

describe('ClassTracing', () => {
  beforeEach(() => {
    // jsdom has no 2D canvas; the strokes are counted without drawing them.
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
  })

  it('can be traced and finished with the keyboard only', async () => {
    const wrapper = mountTracing()
    const pad = wrapper.get('[data-testid="tracing-pad"]')
    const finish = wrapper.get('[data-testid="activity-finish"]')
    expect(pad.attributes('tabindex')).toBe('0')
    expect(pad.attributes('role')).toBe('application')
    expect(wrapper.get(`#${pad.attributes('aria-describedby')}`).text()).toContain('Space starts or ends a stroke')
    // Off without a stroke, but focusable: going on to the next character keeps the focus on this button.
    expect(finish.attributes('aria-disabled')).toBe('true')
    expect(finish.attributes('disabled')).toBeUndefined()
    // Where the learner is, once: the character and the strokes drawn on it.
    const progress = () => wrapper.get('[data-testid="tracing-progress"]').text()
    expect(progress()).toBe('Character 1 of 2 · No strokes yet')
    expect(wrapper.get('.class-tracing-model .page-eyebrow').text()).toBe('Model')

    await drawStrokeWithKeys(wrapper)
    expect(progress()).toBe('Character 1 of 2 · 1 stroke')
    expect(finish.text()).toContain('Next character')
    await finish.trigger('click')

    expect(wrapper.text()).toContain('人 traced')
    expect(progress()).toBe('Character 2 of 2 · No strokes yet')
    expect(finish.attributes('aria-disabled')).toBe('true')
    await finish.trigger('click')
    expect(progress()).toBe('Character 2 of 2 · No strokes yet')
    expect(wrapper.emitted('finish')).toBeUndefined()
    await drawStrokeWithKeys(wrapper)
    expect(finish.text()).toContain('Done')
    await finish.trigger('click')

    expect(wrapper.emitted('finish')).toHaveLength(1)
    await wrapper.setProps({ busy: true })
    expect(pad.attributes('tabindex')).toBe('-1')
    const clear = wrapper.get<HTMLButtonElement>('.focus-footer button')
    expect(clear.element.disabled).toBe(true)
    await clear.trigger('click')
    expect(progress()).toBe('Character 2 of 2 · 1 stroke')
  })

  it('keeps the tracing on show but takes no more input once done', async () => {
    const wrapper = mountTracing(false)
    await drawStrokeWithKeys(wrapper)
    const tracingPad = wrapper.getComponent(TracingPad)
    expect(tracingPad.props('strokes')).toHaveLength(1)

    await wrapper.setProps({ done: true })
    const pad = wrapper.get('[data-testid="tracing-pad"]')
    expect(pad.attributes('tabindex')).toBe('-1')
    expect(tracingPad.props('strokes')).toHaveLength(1)
    expect(wrapper.text()).toContain('Compare your tracing with the model.')
    expect(wrapper.find('[data-testid="activity-finish"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tracing-progress"]').exists()).toBe(false)

    const emittedBefore = tracingPad.emitted('update:strokes')?.length ?? 0
    await drawStrokeWithKeys(wrapper)
    expect(tracingPad.props('strokes')).toHaveLength(1)
    expect(tracingPad.emitted('update:strokes')?.length ?? 0).toBe(emittedBefore)
  })

  it('counts only the strokes when there is one character to trace', () => {
    const wrapper = mount(ClassTracing, {
      props: { title: 'Trace 人', characters: characters.slice(0, 1), busy: false, done: false },
      global: { stubs: { IconGlyph: true } },
    })

    expect(wrapper.get('[data-testid="tracing-progress"]').text()).toBe('No strokes yet')
  })
})
