import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import { stubSpeech } from '@/test/fakeSpeech'
import { answerCheck, lessonPlay } from '@/test/learnFixtures'
import type { AnswerCheck, QuestionView } from '@/types/learn'

import QuestionStage from './QuestionStage.vue'

const [choice, sentence] = lessonPlay().questions as [QuestionView, QuestionView]

/** The stage bound with v-model, as the players use it; a `check` locks it. */
function mountStage(question: QuestionView, modelValue: string | null, check: AnswerCheck | null = null) {
  const wrapper = mount(QuestionStage, {
    props: {
      question,
      modelValue,
      locked: check !== null,
      check,
      'onUpdate:modelValue': (value: string | null) => wrapper.setProps({ modelValue: value }),
    },
  })
  return wrapper
}

describe('QuestionStage', () => {
  it('hides the word speaker on a Cantonese-only device and shows it when Mandarin becomes available', async () => {
    const speech = stubSpeech([{ lang: 'yue-HK' }])
    const wrapper = mountStage(choice, null)
    expect(wrapper.find('.learn-listen-play').exists()).toBe(false)
    speech.setVoices([{ lang: 'cmn' }])
    await nextTick()
    await wrapper.get('.learn-listen-play').trigger('click')
    expect(speech.utterance().text).toBe(choice.hanzi)
  })

  it('gives the next question the focus when the button that went on took it away with it', async () => {
    const wrapper = mount(QuestionStage, {
      props: { question: choice, modelValue: null, locked: false, check: null },
      attachTo: document.body,
    })
    // The Continue that led on is gone: nothing has the focus.
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()

    await wrapper.setProps({ question: sentence })
    expect(document.activeElement).toBe(wrapper.get('h2').element)

    // A focus kept elsewhere stays where it is.
    const elsewhere = document.createElement('button')
    document.body.append(elsewhere)
    elsewhere.focus()
    await wrapper.setProps({ question: choice })
    expect(document.activeElement).toBe(elsewhere)
    elsewhere.remove()
    wrapper.unmount()
  })

  it('takes back a chosen answer that a hint rules out, and keeps one it does not', async () => {
    const question: QuestionView = { ...choice, options: [...choice.options, { text: 'student', pinyin: null }] }
    const wrapper = mountStage(question, 'teacher')

    await wrapper.setProps({ ruledOut: ['student'] })
    expect(wrapper.props('modelValue')).toBe('teacher')

    await wrapper.setProps({ ruledOut: ['student', 'teacher'] })
    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
    expect(wrapper.get('[data-testid="option-1"]').attributes('aria-pressed')).toBe('false')
    expect(wrapper.get('[data-testid="option-1"]').attributes('disabled')).toBeDefined()
  })

  it('takes a ruled-out tile out of the sentence and sends the rest', async () => {
    const wrapper = mountStage(sentence, null)
    await wrapper.get('[data-testid="tile-1"]').trigger('click')
    await wrapper.get('[data-testid="tile-3"]').trigger('click')
    expect(wrapper.props('modelValue')).toBe('我 你')

    await wrapper.setProps({ ruledOut: ['你'] })
    expect(wrapper.props('modelValue')).toBe('我')
    expect(wrapper.get('[data-testid="build-answer"]').text()).toBe('我')
    expect(wrapper.get('[data-testid="tile-3"]').classes()).toContain('is-ruled-out')
  })

  it('marks only a Chinese tile as Chinese: a name, an English word or pinyin keeps the page’s language', async () => {
    const wrapper = mountStage(sentence, null)
    await wrapper.get('[data-testid="tile-2"]').trigger('click')

    expect(wrapper.get('[data-testid="tile-0"]').attributes('lang')).toBe('zh-CN')
    expect(wrapper.get('[data-testid="tile-2"]').attributes('lang')).toBeUndefined()
    const placed = wrapper.get('[data-testid="build-answer"] .learn-tile')
    expect(placed.text()).toBe('Amira')
    expect(placed.attributes('lang')).toBeUndefined()
  })

  it('keeps a tile out when a hint rules it out during a drag', async () => {
    const wrapper = mountStage(sentence, null)
    await wrapper.setProps({ draggableTiles: true })
    const tile = wrapper.get('[data-testid="tile-3"]')
    await tile.trigger('dragstart')
    await wrapper.setProps({ ruledOut: ['你'] })
    await wrapper.get('[data-testid="build-answer"]').trigger('drop')

    expect(tile.attributes('draggable')).toBe('false')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('changes nothing once the answer is checked', async () => {
    const wrapper = mountStage(choice, 'teacher', answerCheck({ correct: false }))

    await wrapper.setProps({ ruledOut: ['teacher'] })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('[data-testid="option-1"]').classes()).toContain('is-wrong')
    expect(wrapper.get('[data-testid="option-1"]').classes()).not.toContain('is-ruled-out')
  })

  it('puts where a question comes from after the word’s pinyin, or under the instruction without one', () => {
    const context = 'marked “Still learning” in Flashcards'
    const word = mount(QuestionStage, {
      props: { question: choice, modelValue: null, locked: false, check: null, context },
    })
    expect(word.get('.learn-question-pinyin').text()).toBe(`míngzi · ${context}`)
    expect(word.find('.learn-question-context').exists()).toBe(false)

    const listening: QuestionView = { ...choice, type: 'LISTEN_CHOOSE', hanzi: null, pinyin: null, audioText: '名字' }
    const heard = mount(QuestionStage, {
      props: { question: listening, modelValue: null, locked: false, check: null, context },
    })
    expect(heard.get('.learn-question-context').text()).toBe(context)
  })

  it('marks the right option as the server compares answers: case, width, spaces and punctuation do not count', () => {
    const question: QuestionView = {
      ...choice,
      options: [
        { text: 'Ｎａｍｅ!', pinyin: null },
        { text: 'teacher', pinyin: null },
      ],
    }
    const wrapper = mountStage(question, 'teacher', answerCheck({ correct: false, correctAnswer: 'name' }))

    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-testid="option-0"]').text()).toContain('Correct answer:')
    expect(wrapper.get('[data-testid="option-1"]').classes()).toContain('is-wrong')
  })
})
