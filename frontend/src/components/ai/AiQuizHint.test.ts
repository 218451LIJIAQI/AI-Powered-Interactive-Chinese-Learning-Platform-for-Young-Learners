import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { pickAiQuizOption } from '@/test/helpers'
import { hintSettings as hintSettingsFixture } from '@/test/learnFixtures'
import type { HintSettings } from '@/types/learn'

vi.mock('@/composables/useHintSettings', () => ({ useHintSettings: () => ({ settings: hintSettings }) }))

import AiChoiceQuestions, { type ChoiceQuestion } from './AiChoiceQuestions.vue'

const hintSettings = ref<HintSettings | null>(null)

const QUESTIONS: ChoiceQuestion[] = [
  {
    text: 'Which sentence means “I am a university student”?',
    options: ['我是大学生。', '我是老师。', '我叫大学。'],
    answer: '我是大学生。',
  },
  { text: 'Which word means “name”?', options: ['朋友', '名字', '学校'], answer: '名字' },
]

function settings(overrides: Partial<HintSettings> = {}): HintSettings {
  return hintSettingsFixture({ delaySeconds: 4, ...overrides })
}

function mountQuiz(questions: readonly ChoiceQuestion[] = QUESTIONS) {
  return mount(AiChoiceQuestions, { props: { questions, hints: true }, global: { stubs: { IconGlyph: true } } })
}

describe('AiQuizHint (SparkBot hints in the Quick quiz)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    hintSettings.value = settings()
  })

  it('offers a hint only once the learner has been stuck for the wait of their level', async () => {
    const wrapper = mountQuiz()
    expect(wrapper.find('[data-testid="ai-quiz-hint-offer"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(4000)

    expect(wrapper.get('[data-testid="ai-quiz-hint-offer"]').text()).toContain('Stuck? SparkBot can give you a hint.')
  })

  it('rules out a wrong option of the first unanswered question, which the question greys out', async () => {
    const wrapper = mountQuiz()
    await vi.advanceTimersByTimeAsync(4000)

    await wrapper.get('[data-testid="ai-quiz-hint-show"]').trigger('click')
    await flushPromises()

    const card = wrapper.get('[data-testid="ai-quiz-hint"]')
    // Three options: one may go, so the right answer is never the only one left.
    expect(card.get('.ai-hint-title').text()).toBe('SparkBot hint')
    expect(card.get('.ai-hint-text').text()).toBe('Question 1: it isn’t “我是老师。”.')
    // The AI quiz has no XP, so the card says nothing about it (S8.19).
    expect(card.text()).not.toMatch(/\bXP\b/i)
    expect(card.findAll('p')).toHaveLength(2)
    const options = wrapper.findAll('.ai-question')[0]!.findAll('.ai-option')
    expect(options[1]!.classes()).toContain('is-ruled-out')
    expect(options[1]!.get<HTMLInputElement>('input').element.disabled).toBe(true)
    expect(options[0]!.classes()).not.toContain('is-ruled-out')
  })

  it('moves on to the next unanswered question once that one is answered', async () => {
    const wrapper = mountQuiz()
    await pickAiQuizOption(wrapper, 0, 0)
    await vi.advanceTimersByTimeAsync(4000)

    await wrapper.get('[data-testid="ai-quiz-hint-show"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="ai-quiz-hint"]').text()).toContain('Question 2: it isn’t “朋友”.')
  })

  it('gives a question one hint: nothing more is offered while it waits for its answer', async () => {
    const wrapper = mountQuiz([{ text: 'Which is tea?', options: ['水', '茶', '汤', '饭'], answer: '茶' }])
    await vi.advanceTimersByTimeAsync(4000)
    await wrapper.get('[data-testid="ai-quiz-hint-show"]').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(wrapper.findAll('.ai-option.is-ruled-out').map((option) => option.text())).toEqual(['水ruled out by a hint'])
    expect(wrapper.findAll('[data-testid="ai-quiz-hint"] button')).toHaveLength(0)
    expect(wrapper.find('[data-testid="ai-quiz-hint-offer"]').exists()).toBe(false)
  })

  it.each([
    ['茶', '水'],
    ['茶', '茶。', '水'],
    ['茶', '茶。', '茶！'],
  ])('withholds a hint when too few wrong choices remain (%s, %s)', async (...options) => {
    hintSettings.value = settings({ mode: 'ON_REQUEST' })
    const wrapper = mountQuiz([{ text: 'Which is tea?', options, answer: '茶' }])
    await flushPromises()

    expect(wrapper.find('.ai-hint').exists()).toBe(false)
  })

  it('counts revealed hints in the current quiz and starts fresh with a new quiz', async () => {
    hintSettings.value = settings({ mode: 'ON_REQUEST', perSet: 1 })
    const wrapper = mountQuiz()
    await wrapper.get('[data-testid="ai-quiz-hint-show"]').trigger('click')
    await pickAiQuizOption(wrapper, 0, 0)
    expect(wrapper.find('.ai-hint').exists()).toBe(false)

    await wrapper.setProps({ questions: [...QUESTIONS] })
    await wrapper.get('[data-testid="ai-quiz-hint-show"]').trigger('click')
    expect(wrapper.findAll('.ai-option.is-ruled-out')).toHaveLength(1)
  })

  it('offers a hint straight away with “Only when I ask”, and never with hints off', async () => {
    hintSettings.value = settings({ mode: 'ON_REQUEST' })
    const asking = mountQuiz()
    await flushPromises()
    expect(asking.get('[data-testid="ai-quiz-hint-offer"]').text()).toContain('Want a hint?')

    hintSettings.value = settings({ mode: 'OFF' })
    const off = mountQuiz()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(off.find('[data-testid="ai-quiz-hint-offer"]').exists()).toBe(false)
  })

  it('has no hints once the answers are checked', async () => {
    const wrapper = mountQuiz()
    await pickAiQuizOption(wrapper, 0, 0)
    await pickAiQuizOption(wrapper, 1, 1)
    await wrapper.get('.ai-result-foot button').trigger('click')
    await vi.advanceTimersByTimeAsync(10_000)

    expect(wrapper.find('.ai-hint').exists()).toBe(false)
  })
})
