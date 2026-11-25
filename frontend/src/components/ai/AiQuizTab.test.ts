import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'

const api = vi.hoisted(() => ({ quiz: vi.fn(), report: vi.fn() }))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
// Hints have their own tests (AiQuizHint.test.ts): here they are off.
vi.mock('@/composables/useHintSettings', async () => {
  const { ref } = await import('vue')
  return { useHintSettings: () => ({ settings: ref(null) }) }
})

import AiGeneratorTab from './AiGeneratorTab.vue'

const QUIZ = {
  title: 'Food words',
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
  return mount(AiGeneratorTab, {
    props: { kind: 'quiz', blocked, aiNotice: 'AI can make mistakes.' },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
}

async function submitTopic(wrapper: ReturnType<typeof mountQuiz>, topic: string) {
  await wrapper.get('.ai-maker-form input').setValue(topic)
  await wrapper.get('.ai-maker-form').trigger('submit')
}

describe('Quick quiz', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

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
    // The AI notice sits in the quiz's head, beside Report.
    expect(result.get('.ai-panel-head').text()).toContain('AI can make mistakes.')

    const radios = result.findAll('input[type="radio"]')
    await radios[0]!.setValue(true) // rice: correct
    await radios[3]!.setValue(true) // 水: wrong
    await result.get('.ai-result-foot button').trigger('click')

    expect(result.get('.ai-result-foot p').text()).toBe('1 of 2 correct')
    // The right answer of every checked question is marked, so the wrong pick (水) also shows what was right (茶).
    const options = result.findAll('.ai-option')
    expect(result.findAll('.ai-option.is-correct').map((option) => option.text())).toEqual([
      'Correct answer:rice',
      'Correct answer:茶',
    ])
    expect(result.findAll('.ai-option.is-incorrect').map((option) => option.text())).toEqual(['Your answer:水'])
    expect(options[4]!.classes()).not.toContain('is-selected')
    expect(options[4]!.find('.sr-only:not(input)').text()).toBe('Correct answer:')
    expect(result.text()).toContain('米饭 (mǐfàn) is cooked rice.')
    expect(result.text()).toContain('茶 (chá) is tea.')
    expect(wrapper.emitted('used')).toHaveLength(1)
  })

  it('shows the making state while the request runs', async () => {
    const made = deferred<typeof QUIZ>()
    api.quiz.mockReturnValue(made.promise)
    const wrapper = mountQuiz()
    await submitTopic(wrapper, 'Food')

    const button = wrapper.get<HTMLButtonElement>('.ai-maker-form button')
    expect(button.text()).toBe('Making…')
    expect(button.element.disabled).toBe(true)
    expect(wrapper.get('.ai-panel').text()).toContain('SparkBot is writing 3 questions on “Food”…')
    expect(wrapper.get('.ai-panel').text()).toContain('AI can make mistakes.')

    made.resolve(QUIZ)
    await flushPromises()
    expect(wrapper.get('.ai-maker-form button').text()).toBe('Make quiz')
  })

  it('offers the ten topic ideas of the design, which fill the topic', async () => {
    const wrapper = mountQuiz()

    expect(wrapper.findAll('.filter-chip').map((chip) => chip.text())).toEqual([
      'Numbers',
      'Food and drink',
      'Directions',
      'Time',
      'Family',
      'Shopping',
      'Weather',
      'Hobbies',
      'Transport',
      'Campus life',
    ])
    await wrapper
      .findAll('.filter-chip')
      .find((chip) => chip.text() === 'Food and drink')!
      .trigger('click')

    expect(wrapper.get<HTMLInputElement>('.ai-maker-form input').element.value).toBe('Food and drink')
  })

  it('reports the quiz with the topic and the questions as the student saw them', async () => {
    api.quiz.mockResolvedValue(QUIZ)
    api.report.mockResolvedValue({ id: 8, status: 'PENDING_REVIEW' })
    const wrapper = mountQuiz()
    await submitTopic(wrapper, 'Food')
    await flushPromises()

    await wrapper.get('.ai-result-report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenCalledWith({
      featureKey: 'QUIZ_GENERATOR',
      promptSnapshot: 'Food',
      aiResponseSnapshot:
        'Food words\n1. What does 米饭 mean? [rice / noodles / tea] → rice (米饭 (mǐfàn) is cooked rice.)\n2. Which word means tea? [水 / 茶 / 汤] → 茶 (茶 (chá) is tea.)',
      reasonCategory: 'INCORRECT_CONTENT',
      description: undefined,
    })
    expect(wrapper.get('.ai-result-report').text()).toBe('Reported')
  })

  it('reports the quiz on screen under its own topic when a later one could not be made', async () => {
    api.quiz.mockResolvedValueOnce(QUIZ).mockResolvedValueOnce({ title: '', status: 'FAILED', questions: [] })
    api.report.mockResolvedValue({ id: 9, status: 'PENDING_REVIEW' })
    const wrapper = mountQuiz()
    await submitTopic(wrapper, 'Food')
    await flushPromises()
    await submitTopic(wrapper, 'Family')
    await flushPromises()

    await wrapper.get('.ai-result-report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenLastCalledWith(expect.objectContaining({ promptSnapshot: 'Food' }))
  })

  it('says SparkBot is unavailable instead of showing a made-up quiz', async () => {
    api.quiz.mockResolvedValue({ title: '', status: 'UNAVAILABLE', questions: [] })
    const wrapper = mountQuiz()

    await submitTopic(wrapper, 'Food')
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-quiz-result"]').exists()).toBe(false)
    expect(wrapper.find('.callout.is-danger').exists()).toBe(true)
  })

  it('shows the server safety reason when the quiz request is blocked and keeps AI notice visible when blocked', async () => {
    const blockedMsg =
      'This request was not sent to the AI helper because it may contain unsafe content. Please keep AI requests to Mandarin learning.'
    api.quiz.mockResolvedValue({ title: null, status: 'FAILED', questions: [], message: blockedMsg })
    const wrapper = mountQuiz()

    await submitTopic(wrapper, 'Unsafe')
    await flushPromises()

    expect(wrapper.get('.callout.is-danger').text()).toBe(blockedMsg)

    const unavailableWrapper = mountQuiz('unavailable')
    expect(unavailableWrapper.text()).toContain('AI can make mistakes.')
  })

  it('cannot make a quiz while AI requests are blocked', () => {
    const wrapper = mountQuiz('unavailable')

    expect(wrapper.get<HTMLInputElement>('.ai-maker-form input').element.disabled).toBe(true)
    expect(wrapper.get<HTMLButtonElement>('.ai-maker-form button').element.disabled).toBe(true)
  })
})
