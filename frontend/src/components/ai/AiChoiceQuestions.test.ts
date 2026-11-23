import { mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { pickAiQuizOption } from '@/test/helpers'

import AiChoiceQuestions, { type ChoiceQuestion } from './AiChoiceQuestions.vue'

const QUESTIONS: ChoiceQuestion[] = [
  { text: 'What does 米饭 mean?', options: ['rice', 'noodles', 'tea'], answer: 'rice', note: '米饭 is cooked rice.' },
  { text: 'Which word means tea?', options: ['水', '茶', '汤'], answer: '茶。', note: '茶 (chá) is tea.' },
]

function mountQuestions(questions: readonly ChoiceQuestion[] = QUESTIONS) {
  return mount(AiChoiceQuestions, { props: { questions }, global: { stubs: { IconGlyph: true } } })
}

/** The options of question `index`, as [classes, visually hidden mark] pairs. */
function marks(wrapper: VueWrapper, index: number): [string[], string][] {
  return wrapper
    .findAll('.ai-question')
    [index]!.findAll('.ai-option')
    .map((option) => {
      const mark = option.find('span.sr-only')
      return [option.classes().filter((name) => name.startsWith('is-')), mark.exists() ? mark.text() : '']
    })
}

function checkButton(wrapper: VueWrapper) {
  return wrapper.get<HTMLButtonElement>('.ai-result-foot button')
}

describe('AiChoiceQuestions', () => {
  it('marks nothing before the answers are checked', async () => {
    const wrapper = mountQuestions()
    await pickAiQuizOption(wrapper, 0, 1)

    expect(marks(wrapper, 0)).toEqual([
      [[], ''],
      [['is-selected'], ''],
      [[], ''],
    ])
    expect(wrapper.find('.ai-question-note').exists()).toBe(false)
    expect(wrapper.get('.ai-result-foot p').text()).toBe('1 of 2 answered')
  })

  it('keeps Check answers disabled until every question has an answer', async () => {
    const wrapper = mountQuestions()
    expect(checkButton(wrapper).element.disabled).toBe(true)

    await pickAiQuizOption(wrapper, 0, 0)
    expect(checkButton(wrapper).element.disabled).toBe(true)

    await pickAiQuizOption(wrapper, 1, 1)
    expect(checkButton(wrapper).element.disabled).toBe(false)
  })

  it('marks the right answer with a tick and the wrong pick with a cross, in words too, with no status tags', async () => {
    const wrapper = mountQuestions()
    await pickAiQuizOption(wrapper, 0, 1) // noodles: wrong
    await pickAiQuizOption(wrapper, 1, 1) // 茶: right (the key's full stop does not matter)
    await checkButton(wrapper).trigger('click')

    expect(marks(wrapper, 0)).toEqual([
      [['is-correct'], 'Correct answer:'],
      [['is-incorrect'], 'Your answer:'],
      [[], ''],
    ])
    expect(marks(wrapper, 1)).toEqual([
      [[], ''],
      [['is-correct'], 'Correct answer:'],
      [[], ''],
    ])
    const first = wrapper.findAll('.ai-question')[0]!
    expect(first.findAll('.ai-option')[0]!.find('icon-glyph-stub').attributes('name')).toBe('check')
    expect(first.findAll('.ai-option')[1]!.find('icon-glyph-stub').attributes('name')).toBe('close')
    // The answers are on screen, so the notes are too.
    expect(wrapper.findAll('.ai-question-note').map((note) => note.text())).toEqual([
      '米饭 is cooked rice.',
      '茶 (chá) is tea.',
    ])
    expect(wrapper.get('.ai-result-foot p').text()).toBe('1 of 2 correct')
    expect(wrapper.text()).not.toMatch(/Try again|Correct(?! answer)/)
  })

  it('ends the quiz once checked: every answer is locked and Check answers stays off', async () => {
    const wrapper = mountQuestions()
    await pickAiQuizOption(wrapper, 0, 1)
    await pickAiQuizOption(wrapper, 1, 1)
    await checkButton(wrapper).trigger('click')

    const radios = wrapper.findAll<HTMLInputElement>('input[type="radio"]')
    expect(radios.every((radio) => radio.element.disabled)).toBe(true)
    expect(checkButton(wrapper).element.disabled).toBe(true)
  })

  it('praises a full score', async () => {
    const wrapper = mountQuestions()
    await pickAiQuizOption(wrapper, 0, 0)
    await pickAiQuizOption(wrapper, 1, 1)
    await checkButton(wrapper).trigger('click')

    expect(wrapper.get('.ai-result-foot p').text()).toBe('2 of 2 correct. Nice work!')
  })

  it('does not invent a right answer when the answer key names none of the options', async () => {
    const wrapper = mountQuestions([{ text: 'Which word means tea?', options: ['水', '茶', '汤'], answer: 'B' }])
    await pickAiQuizOption(wrapper, 0, 0)
    await checkButton(wrapper).trigger('click')

    expect(marks(wrapper, 0)).toEqual([
      [['is-incorrect'], 'Your answer:'],
      [[], ''],
      [[], ''],
    ])
  })

  it('starts over with a new set of questions', async () => {
    const wrapper = mountQuestions()
    await pickAiQuizOption(wrapper, 0, 0)
    await pickAiQuizOption(wrapper, 1, 1)
    await checkButton(wrapper).trigger('click')

    await wrapper.setProps({ questions: [...QUESTIONS] })

    expect(marks(wrapper, 0).every(([classes]) => !classes.length)).toBe(true)
    expect(wrapper.get('.ai-result-foot p').text()).toBe('0 of 2 answered')
  })
})
