import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ checkMatching: vi.fn(), matchingHint: vi.fn() }))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))

import ClassMatching from './ClassMatching.vue'

const words = [
  { id: 1, chinese: '妈妈', pinyin: 'māma' },
  { id: 2, chinese: '爸爸', pinyin: 'bàba' },
]

function mountMatching(stage: 'GUIDED' | 'SCAFFOLDED' | 'INDEPENDENT') {
  return mount(ClassMatching, {
    props: { activityId: 5, words, meanings: ['dad', 'mum'], stage },
    global: { stubs: { IconGlyph: true } },
  })
}

async function pair(wrapper: ReturnType<typeof mountMatching>, wordId: number, meaning: string) {
  await wrapper.get(`[data-testid="match-word-${wordId}"]`).trigger('click')
  await wrapper
    .findAll('.class-matching-item')
    .find((item) => item.text() === meaning)!
    .trigger('click')
}

describe('ClassMatching', () => {
  beforeEach(() => {
    api.checkMatching.mockReset()
    api.matchingHint.mockReset()
  })

  it('checks the pairs on the server, marks the wrong ones and finishes when every pair is right', async () => {
    const wrapper = mountMatching('GUIDED')
    await pair(wrapper, 1, 'dad')
    await pair(wrapper, 2, 'mum')
    expect(wrapper.text()).toContain('2 of 2 paired')

    api.checkMatching.mockResolvedValueOnce({
      done: false,
      pairs: [
        { wordId: 1, correct: false },
        { wordId: 2, correct: false },
      ],
      stepsDone: 1,
      stepsTotal: 4,
      lessonStatus: 'IN_PROGRESS',
    })
    await wrapper.get('[data-testid="activity-finish"]').trigger('click')
    await flushPromises()

    expect(api.checkMatching).toHaveBeenCalledWith(5, [
      { wordId: 1, meaning: 'dad' },
      { wordId: 2, meaning: 'mum' },
    ])
    expect(wrapper.get('[data-testid="match-word-1"]').classes()).toContain('is-wrong')
    expect(wrapper.text()).toContain('0 of 2 paired')
    expect(wrapper.emitted('outcome')).toBeUndefined()

    const outcome = {
      done: true,
      pairs: [],
      stepsDone: 2,
      stepsTotal: 4,
      lessonStatus: 'IN_PROGRESS',
    }
    api.checkMatching.mockResolvedValueOnce(outcome)
    await pair(wrapper, 1, 'mum')
    await pair(wrapper, 2, 'dad')
    await wrapper.get('[data-testid="activity-finish"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('outcome')?.[0]).toEqual([outcome])
  })

  it('gives one hint in a Supported activity and none in an Independent one', async () => {
    api.matchingHint.mockResolvedValue({ wordId: 1, meaning: 'mum' })
    const supported = mountMatching('SCAFFOLDED')

    await supported.get('[data-testid="matching-hint"]').trigger('click')
    await flushPromises()

    expect(api.matchingHint).toHaveBeenCalledWith(5, 1)
    expect(supported.text()).toContain('Hint: 妈妈 means mum')
    expect(supported.get('[data-testid="matching-hint"]').attributes('disabled')).toBeDefined()
    expect(mountMatching('INDEPENDENT').find('[data-testid="matching-hint"]').exists()).toBe(false)
  })
})
