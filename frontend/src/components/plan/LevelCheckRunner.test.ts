import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { checkChoice } from '@/test/helpers'
import { checkDone, checkStep } from '@/test/planFixtures'
import type { LevelCheckStep } from '@/types/learn'

const api = vi.hoisted(() => ({ next: vi.fn() }))

vi.mock('@/api/learn', () => ({ levelCheckApi: { next: api.next } }))

import LevelCheckRunner from './LevelCheckRunner.vue'

type Skill = NonNullable<LevelCheckStep['next']>['skill']

/** The question `id` of the check, its right answer "Hello" among four. */
function question(
  id: number,
  answeredCount: number,
  feedback: LevelCheckStep['feedback'] = null,
  skill: Skill = 'VOCABULARY',
): LevelCheckStep {
  return checkStep({
    feedback,
    answeredCount,
    next: {
      id,
      prompt: 'What does this mean?',
      hanzi: '你好',
      pinyin: 'nǐ hǎo',
      audioText: null,
      skill,
      options: [
        { id: id * 10 + 1, text: 'Thank you' },
        { id: id * 10 + 2, text: 'Hello' },
        { id: id * 10 + 3, text: 'Goodbye' },
        { id: id * 10 + 4, text: 'Sorry' },
      ],
    },
  })
}

const WRONG = { correct: false, skipped: false, correctAnswer: 'Hello', explanation: null }
const SKIPPED = { ...WRONG, skipped: true, explanation: '你好 (nǐ hǎo) means “Hello”.' }

async function mountRunner(paused = false) {
  const wrapper = mount(LevelCheckRunner, {
    props: { startLevel: 'BEGINNER_1', paused },
    attachTo: document.body,
    global: { stubs: { IconGlyph: true } },
  })
  await flushPromises()
  return wrapper
}

function key(name: string): void {
  document.dispatchEvent(new KeyboardEvent('keydown', { key: name }))
}

describe('LevelCheckRunner', () => {
  beforeEach(() => {
    api.next.mockReset()
  })

  it.each([2, 3, 8])(
    'shows the answer with “No problem!” after “I don’t know yet” on question %i, then goes on',
    async (number) => {
      api.next
        .mockResolvedValueOnce(question(number, number - 1))
        .mockResolvedValueOnce(question(number + 1, number, SKIPPED))
      const wrapper = await mountRunner()

      expect(wrapper.get('.page-eyebrow').text()).toBe(`Question ${number} of 8 · Vocabulary · 词语`)
      await wrapper.get('[data-testid="skip"]').trigger('click')
      await flushPromises()

      // The server got "I don't know yet" for this question and answered with the feedback first.
      expect(api.next).toHaveBeenLastCalledWith('BEGINNER_1', [{ questionId: number, optionId: null }])
      // The feedback stays on the same question.
      expect(wrapper.get('.page-eyebrow').text()).toBe(`Question ${number} of 8 · Vocabulary · 词语`)
      const bar = wrapper.get('[data-testid="check-bar"]')
      expect(bar.text()).toContain('No problem!')
      expect(bar.text()).toContain('你好 (nǐ hǎo) means “Hello”.')
      // The right option is marked in place; nothing was picked, so nothing is marked wrong.
      expect(wrapper.get('[data-testid="option-1"]').classes()).toContain('is-correct')
      expect(wrapper.findAll('.learn-option.is-wrong')).toHaveLength(0)

      await wrapper.get('[data-testid="continue"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="continue"]').exists()).toBe(false)
      expect(wrapper.emitted('progress')?.at(-1)).toEqual([number, 8])
    },
  )

  it('marks a wrong pick with “Almost!” beside the right answer', async () => {
    api.next.mockResolvedValueOnce(question(1, 0)).mockResolvedValueOnce(question(2, 1, WRONG))
    const wrapper = await mountRunner()

    await checkChoice(wrapper, 2)

    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Almost!')
    expect(wrapper.get('[data-testid="option-1"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-testid="option-2"]').classes()).toContain('is-wrong')
  })

  it.each([false, true])(
    'distinguishes options that differ only by punctuation, including a skip (%s)',
    async (skip) => {
      const first = question(1, 0)
      const options = [
        { id: 11, text: '你好？' },
        { id: 12, text: '你好。' },
      ]
      api.next
        .mockResolvedValueOnce({ ...first, next: { ...first.next!, options } })
        .mockResolvedValueOnce(
          question(2, 1, { correct: false, skipped: skip, correctAnswer: '你好。', explanation: null }),
        )
      const wrapper = await mountRunner()

      await wrapper.get('[data-testid="option-0"]').trigger('click')
      await wrapper.get(skip ? '[data-testid="skip"]' : '[data-testid="check"]').trigger('click')
      await flushPromises()

      expect(api.next).toHaveBeenLastCalledWith('BEGINNER_1', [{ questionId: 1, optionId: skip ? null : 11 }])
      expect(wrapper.get('[data-testid="option-1"]').classes()).toContain('is-correct')
      expect(wrapper.findAll('.learn-option.is-correct')).toHaveLength(1)
      expect(wrapper.get('[data-testid="option-0"]').classes().includes('is-wrong')).toBe(!skip)
    },
  )

  it('names the stretch question and calls a miss on it a tricky one', async () => {
    api.next
      .mockResolvedValueOnce(question(8, 7, null, 'IDIOMS'))
      .mockResolvedValueOnce(question(9, 8, WRONG, 'IDIOMS'))
    const wrapper = await mountRunner()

    expect(wrapper.get('.page-eyebrow').text()).toBe('Question 8 of 8 · Idioms · 成语 · Stretch question')
    await checkChoice(wrapper, 2)

    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Tricky one!')
  })

  it('shows the number of available shortcuts and checks and goes on with Enter', async () => {
    const right = { correct: true, skipped: false, correctAnswer: 'Hello', explanation: null }
    const first = question(1, 0)
    const next = first.next!
    api.next
      .mockResolvedValueOnce({
        ...first,
        next: { ...next, options: [...next.options, { id: 15, text: 'Good night' }] },
      })
      .mockResolvedValueOnce(question(2, 1, right))
    const wrapper = await mountRunner()

    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Press 1–5 to choose')
    key('5')
    await flushPromises()
    expect(wrapper.get('[data-testid="option-4"]').attributes('aria-pressed')).toBe('true')
    key('2')
    await flushPromises()
    expect(wrapper.get('[data-testid="option-1"]').attributes('aria-pressed')).toBe('true')
    key('Enter')
    await flushPromises()
    expect(api.next).toHaveBeenLastCalledWith('BEGINNER_1', [{ questionId: 1, optionId: 12 }])
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Nice!')

    // The answers count only once they are in: a key after the check picks nothing.
    key('3')
    await flushPromises()
    expect(wrapper.get('[data-testid="option-2"]').attributes('aria-pressed')).toBe('false')
    key('Enter')
    await flushPromises()
    expect(wrapper.find('[data-testid="continue"]').exists()).toBe(false)
    expect(wrapper.emitted('progress')?.at(-1)).toEqual([1, 8])
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Press 1–4 to choose')
  })

  it('leaves the keys to a dialog open over the check', async () => {
    api.next.mockResolvedValueOnce(question(1, 0))
    const wrapper = await mountRunner(true)

    key('2')
    await flushPromises()
    expect(wrapper.get('[data-testid="option-1"]').attributes('aria-pressed')).toBe('false')
  })

  it('hands the answers and the result to the page when the check is done', async () => {
    api.next.mockResolvedValueOnce(question(1, 0)).mockResolvedValueOnce(checkDone())
    const wrapper = await mountRunner()

    await checkChoice(wrapper, 1)
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()

    const [answers, result] = wrapper.emitted('finished')![0] as [unknown, LevelCheckStep['result']]
    expect(answers).toEqual([{ questionId: 1, optionId: 12 }])
    expect(result).toMatchObject({ level: 'BEGINNER_1' })
  })
})
