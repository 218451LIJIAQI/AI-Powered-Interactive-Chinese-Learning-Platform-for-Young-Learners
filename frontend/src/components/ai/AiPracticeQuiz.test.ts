import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import { aiQuizReply } from '@/demo/data'
import { i18n } from '@/plugins/i18n'

import AiPracticeQuiz from './AiPracticeQuiz.vue'

function mountQuiz(): ReturnType<typeof mount> {
  return mount(AiPracticeQuiz, { props: { quiz: aiQuizReply }, global: { plugins: [i18n] } })
}

describe('AiPracticeQuiz', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders every question with all options, a text box for open questions and the not-saved notice', () => {
    const wrapper = mountQuiz()

    const questions = wrapper.findAll('[data-testid="ai-quiz-question"]')
    expect(questions).toHaveLength(aiQuizReply.questions.length)
    aiQuizReply.questions.forEach((question, index) => {
      expect(questions[index].text()).toContain(question.questionText)
      for (const option of question.options) expect(questions[index].text()).toContain(option)
    })
    const optionCount = aiQuizReply.questions.reduce((total, question) => total + question.options.length, 0)
    expect(wrapper.findAll('[data-testid="ai-quiz-option"]')).toHaveLength(optionCount)
    expect(wrapper.findAll('[data-testid="ai-quiz-text-answer"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="ai-practice-not-saved"]').text()).toContain('not saved')
    expect(wrapper.find('[data-testid="ai-quiz-feedback"]').exists()).toBe(false)
  })

  it('checks answers locally, shows answers and explanations, scores and resets', async () => {
    const wrapper = mountQuiz()
    const questions = wrapper.findAll('[data-testid="ai-quiz-question"]')

    const firstOptions = questions[0].findAll('[data-testid="ai-quiz-option"]')
    await firstOptions[0].trigger('click')
    expect(firstOptions[0].attributes('aria-pressed')).toBe('true')
    expect(firstOptions[1].attributes('aria-pressed')).toBe('false')
    // Letter key "B" -> second option "Canteen".
    await questions[1].findAll('[data-testid="ai-quiz-option"]')[1].trigger('click')
    // Wrong pinyin.
    await questions[2].findAll('[data-testid="ai-quiz-option"]')[1].trigger('click')
    await questions[3].get('[data-testid="ai-quiz-text-answer"]').setValue(' 茶 ')

    await wrapper.get('[data-testid="ai-quiz-check"]').trigger('click')

    const score = wrapper.get('[data-testid="ai-quiz-score"]')
    expect(score.attributes('aria-live')).toBe('polite')
    expect(score.text()).toBe('Your score: 3 / 5')
    const feedback = wrapper.findAll('[data-testid="ai-quiz-feedback"]').map((item) => item.text())
    expect(feedback).toHaveLength(5)
    expect(feedback[0]).toContain('Correct')
    expect(feedback[1]).toContain('Correct answer: Canteen')
    expect(feedback[2]).toContain('Not quite')
    expect(feedback[2]).toContain('Correct answer: duōshao qián')
    expect(feedback[3]).toContain('Why: 茶 (chá) means tea')
    expect(feedback[4]).toContain('Not answered')
    expect(wrapper.get('[data-testid="ai-quiz-option"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="ai-quiz-reset"]').trigger('click')
    expect(wrapper.find('[data-testid="ai-quiz-feedback"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="ai-quiz-score"]').text()).toBe('')
    expect(wrapper.findAll('[aria-pressed="true"]')).toHaveLength(0)
    expect(wrapper.find('[data-testid="ai-quiz-check"]').exists()).toBe(true)
  })

  it('uses the interface language for its labels', async () => {
    i18n.global.locale.value = 'ms'
    const wrapper = mountQuiz()
    await wrapper.get('[data-testid="ai-quiz-check"]').trigger('click')
    expect(wrapper.get('[data-testid="ai-quiz-score"]').text()).toBe('Skor anda: 0 / 5')
    expect(wrapper.get('[data-testid="ai-quiz-reset"]').text()).toBe('Cuba lagi')
  })
})
