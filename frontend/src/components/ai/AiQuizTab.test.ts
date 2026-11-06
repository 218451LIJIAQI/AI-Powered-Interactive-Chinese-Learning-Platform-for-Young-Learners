import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ quiz: vi.fn(), report: vi.fn() }))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import AiQuizTab from './AiQuizTab.vue'

const QUIZ = {
  title: 'Food words',
  topic: 'Food',
  status: 'SUCCESS',
  questions: [
    {
      questionText: 'What does 米饭 mean?',
      questionType: 'MULTIPLE_CHOICE',
      options: ['rice', 'noodles', 'tea'],
      correctAnswer: 'rice',
      explanation: '米饭 (mǐfàn) is cooked rice.',
    },
    {
      questionText: 'Which word means tea?',
      questionType: 'MULTIPLE_CHOICE',
      options: ['水', '茶', '汤'],
      correctAnswer: '茶',
      explanation: '茶 (chá) is tea.',
    },
  ],
}

function mountQuiz(blocked: 'limit' | 'unavailable' | null = null) {
  return mount(AiQuizTab, {
    props: { blocked, aiNotice: 'AI can make mistakes.' },
    global: { plugins: [createPinia()], stubs: { IconGlyph: true, teleport: true } },
  })
}

describe('AiQuizTab (Quick quiz)', () => {
  it('asks for the chosen number of questions and checks answers in the browser', async () => {
    api.quiz.mockResolvedValue(QUIZ)
    const wrapper = mountQuiz()

    await wrapper.get('.ai-maker-form input').setValue('Food')
    await wrapper.get('.ai-maker-form select').setValue('2')
    await wrapper.get('.ai-maker-form').trigger('submit')
    await flushPromises()

    expect(api.quiz).toHaveBeenCalledWith({ topic: 'Food', numberOfQuestions: 2, languagePreference: 'en' })
    const result = wrapper.get('[data-testid="ai-quiz-result"]')
    expect(result.find('[data-testid="ai-result-label"]').exists()).toBe(true)

    const radios = result.findAll('input[type="radio"]')
    await radios[0]!.setValue(true) // rice: correct
    await radios[3]!.setValue(true) // 水: wrong
    await result.get('.ai-result-foot button').trigger('click')

    expect(result.get('.ai-result-foot p').text()).toBe('1 of 2 correct')
    expect(result.findAll('.ai-option.is-correct')).toHaveLength(1)
    expect(result.findAll('.ai-option.is-incorrect')).toHaveLength(1)
    expect(result.text()).toContain('米饭 (mǐfàn) is cooked rice.')
    expect(wrapper.emitted('used')).toHaveLength(1)
  })

  it('shows the making state while the request runs', async () => {
    let finish: (value: typeof QUIZ) => void = () => {}
    api.quiz.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const wrapper = mountQuiz()
    await wrapper.get('.ai-maker-form input').setValue('Food')
    await wrapper.get('.ai-maker-form').trigger('submit')

    const button = wrapper.get<HTMLButtonElement>('.ai-maker-form button')
    expect(button.text()).toBe('Making…')
    expect(button.element.disabled).toBe(true)
    expect(wrapper.get('.ai-panel').text()).toContain('SparkBot is writing 3 questions on Food…')

    finish(QUIZ)
    await flushPromises()
    expect(wrapper.get('.ai-maker-form button').text()).toBe('Make quiz')
  })

  it('offers topic ideas that fill the topic', async () => {
    const wrapper = mountQuiz()

    await wrapper
      .findAll('.filter-chip')
      .find((chip) => chip.text() === 'Food and drink')!
      .trigger('click')

    expect(wrapper.get<HTMLInputElement>('.ai-maker-form input').element.value).toBe('Food and drink')
  })

  it('waits for a new answer before checking again and praises a full score', async () => {
    api.quiz.mockResolvedValue(QUIZ)
    const wrapper = mountQuiz()
    await wrapper.get('.ai-maker-form input').setValue('Food')
    await wrapper.get('.ai-maker-form').trigger('submit')
    await flushPromises()

    const check = wrapper.get<HTMLButtonElement>('.ai-result-foot button')
    const radios = wrapper.findAll('input[type="radio"]')
    expect(check.element.disabled).toBe(true)

    await radios[0]!.setValue(true) // rice: correct
    await radios[3]!.setValue(true) // 水: wrong
    await check.trigger('click')
    expect(check.element.disabled).toBe(true)

    await radios[4]!.setValue(true) // 茶: correct
    expect(check.element.disabled).toBe(false)
    await check.trigger('click')

    expect(wrapper.get('.ai-result-foot p').text()).toBe('2 of 2 correct. Nice work!')
    expect(check.element.disabled).toBe(true)
  })

  it('reports the quiz with the topic and the questions as the student saw them', async () => {
    api.quiz.mockResolvedValue(QUIZ)
    api.report.mockResolvedValue({ id: 8, status: 'PENDING_REVIEW' })
    const wrapper = mountQuiz()
    await wrapper.get('.ai-maker-form input').setValue('Food')
    await wrapper.get('.ai-maker-form').trigger('submit')
    await flushPromises()

    await wrapper.get('.ai-result-report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenCalledWith({
      featureKey: 'QUIZ_GENERATOR',
      promptSnapshot: 'Food',
      aiResponseSnapshot:
        'Food words\n1. What does 米饭 mean? [rice / noodles / tea] → rice\n2. Which word means tea? [水 / 茶 / 汤] → 茶',
      reasonCategory: 'INCORRECT_CONTENT',
      description: undefined,
    })
    expect(wrapper.get('.ai-result-report').text()).toBe('Reported')
  })

  it('says SparkBot is unavailable instead of showing a made-up quiz', async () => {
    api.quiz.mockResolvedValue({ title: '', topic: 'Food', status: 'UNAVAILABLE', questions: [] })
    const wrapper = mountQuiz()

    await wrapper.get('.ai-maker-form input').setValue('Food')
    await wrapper.get('.ai-maker-form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-quiz-result"]').exists()).toBe(false)
    expect(wrapper.find('.callout.is-danger').exists()).toBe(true)
  })

  it('cannot make a quiz while AI requests are blocked', () => {
    const wrapper = mountQuiz('unavailable')

    expect(wrapper.get<HTMLInputElement>('.ai-maker-form input').element.disabled).toBe(true)
    expect(wrapper.get<HTMLButtonElement>('.ai-maker-form button').element.disabled).toBe(true)
  })
})
