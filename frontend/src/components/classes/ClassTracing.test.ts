import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ClassTracing from './ClassTracing.vue'

const characters = [
  { character: '人', pinyin: 'rén', meaning: 'person' },
  { character: '大', pinyin: 'dà', meaning: 'big' },
]

function mountTracing() {
  return mount(ClassTracing, {
    props: { characters, stage: 'GUIDED', busy: false },
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
    expect(finish.attributes('disabled')).toBeDefined()

    await drawStrokeWithKeys(wrapper)
    expect(wrapper.text()).toContain('Drawing pad · 1 stroke')
    expect(wrapper.text()).not.toContain('1 strokes')
    expect(finish.text()).toContain('Next character')
    await finish.trigger('click')

    expect(wrapper.text()).toContain('人 traced')
    expect(finish.attributes('disabled')).toBeDefined()
    await drawStrokeWithKeys(wrapper)
    expect(finish.text()).toContain('Done')
    await finish.trigger('click')

    expect(wrapper.emitted('finish')).toHaveLength(1)
  })
})
