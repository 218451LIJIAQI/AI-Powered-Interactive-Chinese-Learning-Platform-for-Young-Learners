import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { answerCheck } from '@/test/learnFixtures'

import CheckBar from './CheckBar.vue'

describe('CheckBar', () => {
  // A pronunciation lesson builds its answer from pinyin syllables; only Chinese is read as Chinese.
  it.each([
    ['我叫 Amira', 'zh-CN'],
    ['mā pà dú hē', undefined],
  ])('writes the right answer “%s” of a built sentence in its own language', (correctAnswer, lang) => {
    const wrapper = mount(CheckBar, {
      props: {
        check: answerCheck({ correct: false, correctAnswer, pinyin: null, meaning: null }),
        canCheck: false,
        checking: false,
        answerLine: true,
      },
    })

    const answer = wrapper.get('.learn-checkbar-answer span')
    expect(answer.text()).toBe(correctAnswer)
    expect(answer.attributes('lang')).toBe(lang)
  })

  it('links the Continue button to the feedback region and marks Chinese runs inside mixed explanations', () => {
    const wrapper = mount(CheckBar, {
      props: {
        check: answerCheck({
          correct: false,
          correctAnswer: '你好',
          pinyin: 'nǐ hǎo',
          meaning: 'hello',
          explanation: 'Use 你好 for a polite greeting.',
        }),
        canCheck: false,
        checking: false,
        answerLine: true,
      },
    })

    const feedback = wrapper.get('.learn-checkbar-feedback')
    const continueBtn = wrapper.get('[data-testid="continue"]')
    expect(continueBtn.attributes('aria-describedby')).toBe(feedback.attributes('id'))
    expect(wrapper.get('.learn-checkbar-answer span[lang="zh-Latn"]').text()).toContain('nǐ hǎo')
    expect(wrapper.get('.learn-checkbar-explanation span[lang="zh-CN"]').text()).toBe('你好')
  })
})
