import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { EditorQuestion } from '@/types/adminClasses'

import LessonQuestionDrawer from './LessonQuestionDrawer.vue'

function question(correctText: string, overrides: Partial<EditorQuestion> = {}): EditorQuestion {
  return {
    id: 1,
    number: 1,
    type: 'MULTIPLE_CHOICE',
    text: 'Which one?',
    points: 3,
    correctAnswerText: null,
    explanation: null,
    hint: null,
    audioUrl: null,
    imageUrl: null,
    options: ['A', 'B', 'C'].map((text, index) => ({
      id: index + 1,
      text,
      correct: text === correctText,
      feedback: text === correctText ? null : `That’s not ${text}.`,
    })),
    aiDraft: false,
    ...overrides,
  }
}

function mountDrawer(current: EditorQuestion | null) {
  return mount(LessonQuestionDrawer, {
    props: { question: current, busy: false, error: null },
    global: { stubs: { teleport: true } },
  })
}

async function removeFirstOptionAndSave(correctText: string) {
  const wrapper = mountDrawer(question(correctText))
  await wrapper.get('button[aria-label="Remove answer 1"]').trigger('click')
  await wrapper.get('form').trigger('submit')
  return wrapper.emitted('save')![0]![0]
}

describe('LessonQuestionDrawer', () => {
  it('starts a new multiple-choice question with four answers to tick (A3.19)', () => {
    const wrapper = mountDrawer(null)

    expect(wrapper.get('h2').text()).toBe('New question')
    expect(wrapper.text()).toContain('Answers · tick the right one')
    expect(wrapper.get('.lesson-answer-row.is-head').text()).toBe('AnswerRight or wrongFeedback')
    expect(wrapper.findAll('[data-testid="answer-row"]')).toHaveLength(4)
    expect(wrapper.find('input[aria-label="Hint"]').exists()).toBe(false)
  })

  it('writes the feedback of each answer, and says which answer is right (A3.19)', async () => {
    const wrapper = mountDrawer(null)
    await wrapper.get('textarea').setValue('What does 茶 mean?')
    const rows = wrapper.findAll('[data-testid="answer-row"]')
    for (const [index, [answer, feedback]] of [
      ['Tea', 'chá = tea'],
      ['Noodles', 'That’s 面'],
      ['Rice', ' '],
    ].entries()) {
      await rows[index]!.get('input[aria-label^="Answer"]').setValue(answer)
      await rows[index]!.get(`input[aria-label="Feedback for answer ${index + 1}"]`).setValue(feedback)
    }

    // The first answer is ticked to start with: it reads Right answer, the others Wrong.
    expect(rows.map((row) => row.get('.choice [aria-hidden="true"]').text())).toEqual([
      'Right answer',
      'Wrong',
      'Wrong',
      'Wrong',
    ])
    expect(rows[1]!.get('input[type="radio"]').attributes('aria-label')).toBeUndefined()
    expect(rows[1]!.get('.choice').text()).toContain('Answer 2 is the right answer')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('save')![0]![0]).toMatchObject({
      options: [
        { optionText: 'Tea', correctOption: true, feedback: 'chá = tea' },
        { optionText: 'Noodles', correctOption: false, feedback: 'That’s 面' },
        { optionText: 'Rice', correctOption: false, feedback: null },
      ],
    })
  })

  it('keeps the right answer and the feedback on their option when an option before it is removed', async () => {
    expect(await removeFirstOptionAndSave('B')).toMatchObject({
      points: 3,
      options: [
        { optionText: 'B', correctOption: true, feedback: null },
        { optionText: 'C', correctOption: false, feedback: 'That’s not C.' },
      ],
    })
    expect(await removeFirstOptionAndSave('C')).toMatchObject({
      options: [
        { optionText: 'B', correctOption: false, feedback: 'That’s not B.' },
        { optionText: 'C', correctOption: true, feedback: null },
      ],
    })
  })

  it('requires text for the correct answer and for any answer with feedback', async () => {
    const wrapper = mountDrawer(null)
    await wrapper.get('textarea').setValue('Which one?')
    const form = wrapper.get('form')
    await wrapper.get('input[aria-label="Answer 2"]').setValue('Distractor')
    await form.trigger('submit')
    expect(wrapper.emitted('save')).toBeUndefined()

    await wrapper.get('input[aria-label="Answer 1"]').setValue('Correct')
    await wrapper.get('input[aria-label="Feedback for answer 3"]').setValue('Try again')
    await wrapper.get('input[aria-label="Answer 3"]').setValue('  ')
    await form.trigger('submit')
    expect(wrapper.emitted('save')).toBeUndefined()

    await wrapper.get('input[aria-label="Answer 3"]').setValue('Another option')
    await form.trigger('submit')
    expect(wrapper.emitted('save')![0]![0]).toMatchObject({
      options: [
        { optionText: 'Correct', correctOption: true },
        { optionText: 'Distractor', correctOption: false },
        { optionText: 'Another option', correctOption: false, feedback: 'Try again' },
      ],
    })
  })

  it('keeps the hint an older question carries, which the form no longer shows', async () => {
    const wrapper = mountDrawer(question('A', { hint: 'Think of the canteen.', explanation: '食堂 is the canteen.' }))

    expect(wrapper.get('h2').text()).toBe('Edit question')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')![0]![0]).toMatchObject({
      hint: 'Think of the canteen.',
      explanation: '食堂 is the canteen.',
    })
  })

  it('requires the editor to choose a replacement when the correct answer is removed', async () => {
    const wrapper = mountDrawer(question('B'))
    await wrapper.get('button[aria-label="Remove answer 2"]').trigger('click')
    expect(wrapper.find('input[type="radio"]:checked').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')).toBeUndefined()

    await wrapper.findAll('input[type="radio"]')[1]!.setValue()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')![0]![0]).toMatchObject({
      options: [
        { optionText: 'A', correctOption: false },
        { optionText: 'C', correctOption: true },
      ],
    })
  })

  it.each([
    ['LISTEN_CHOOSE', 'Audio link'],
    ['MATCH_WORD_IMAGE', 'Picture link'],
  ])('asks for the link a %s question needs before it can be saved, as the server does', async (type, label) => {
    const wrapper = mountDrawer(null)
    await wrapper.get('select').setValue(type)
    await wrapper.get('textarea').setValue('Which word do you hear?')
    await wrapper.get('input[aria-label="Answer 1"]').setValue('Tea')
    const save = () => wrapper.get('[data-testid="question-save"]')

    const field = wrapper.findAll('.field').find((candidate) => candidate.text().startsWith(label))!
    expect(field.find('.required-mark').exists()).toBe(true)
    expect(field.get('input').attributes('required')).toBeDefined()
    expect(save().attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')).toBeUndefined()

    await field.get('input').setValue('https://example.com/media/1')
    expect(save().attributes('disabled')).toBeUndefined()
  })

  it('asks for the answer of a typed question', async () => {
    const wrapper = mountDrawer(null)
    await wrapper.get('select').setValue('FILL_BLANK')
    await wrapper.get('textarea').setValue('我___学生。')

    expect(wrapper.get('[data-testid="question-save"]').attributes('disabled')).toBeDefined()
    await wrapper.get('input.input').setValue('是')
    expect(wrapper.get('[data-testid="question-save"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')![0]![0]).toMatchObject({
      questionType: 'FILL_BLANK',
      questionText: '我___学生。',
      correctAnswerText: '是',
      options: [],
    })
  })
})
