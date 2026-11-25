import { describe, expect, it, vi } from 'vitest'

import { mountAtRoute } from '@/test/routes'
import type { AnswerInsights } from '@/types/learn'

const api = vi.hoisted(() => ({ progressAnswers: vi.fn() }))

vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  xpApi: { progressAnswers: api.progressAnswers },
}))

import ProgressAnswers from './ProgressAnswers.vue'

function insights(overrides: Partial<AnswerInsights> = {}): AnswerInsights {
  return {
    sureAndRight: 0,
    luckyGuesses: 0,
    sureButWrong: 0,
    wrongGuesses: 0,
    hintsUsed: 0,
    mostHintsIn: null,
    ...overrides,
  }
}

async function mountAnswers(data: AnswerInsights) {
  api.progressAnswers.mockResolvedValue(data)
  return (await mountAtRoute(ProgressAnswers, '/student/progress')).wrapper
}

describe('ProgressAnswers (Progress: answers and hints this week)', () => {
  it('leaves the answers row out until there is a first answer, and keeps the hints row', async () => {
    const wrapper = await mountAnswers(insights())

    expect(wrapper.find('[data-testid="answers-this-week"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Answers this week')
    expect(wrapper.text()).toContain('Hints this week')
    expect(wrapper.text()).toContain('None used')
  })

  it('shows how sure the week’s answers were, with the way to the mistakes and the most hinted activity', async () => {
    const wrapper = await mountAnswers(
      insights({ sureAndRight: 5, luckyGuesses: 2, sureButWrong: 1, hintsUsed: 3, mostHintsIn: 'LISTENING' }),
    )

    const answers = wrapper.get('[data-testid="answers-this-week"]')
    expect(answers.findAll('.tag').map((tag) => tag.text())).toEqual([
      'Sure and right 5',
      'Lucky guesses 2',
      'Sure but wrong 1',
    ])
    expect(answers.get('a').attributes('href')).toBe('/student/practice/mistakes')
    expect(wrapper.text()).toContain('3 used')
    expect(wrapper.text()).toContain('Most in Listening')
  })

  it('shows the first rated answer and its review link when it was a wrong guess', async () => {
    const wrapper = await mountAnswers(insights({ wrongGuesses: 1 }))
    const answers = wrapper.get('[data-testid="answers-this-week"]')
    expect(answers.text()).toContain('Guessed wrong 1')
    expect(answers.get('a').attributes('href')).toBe('/student/practice/mistakes')
  })

  it('shows the answers row without a Review mistakes link when only lucky guesses were recorded', async () => {
    const wrapper = await mountAnswers(insights({ luckyGuesses: 2 }))
    const answers = wrapper.get('[data-testid="answers-this-week"]')
    expect(answers.text()).toContain('Lucky guesses 2')
    expect(answers.find('a').exists()).toBe(false)
  })
})
