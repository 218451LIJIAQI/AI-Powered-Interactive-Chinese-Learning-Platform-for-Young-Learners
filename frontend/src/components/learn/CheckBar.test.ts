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
})
