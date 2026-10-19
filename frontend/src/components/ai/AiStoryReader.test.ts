import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import { aiStoryReply } from '@/demo/data'
import { i18n } from '@/plugins/i18n'
import type { AppLanguage } from '@/types/api'

import AiStoryReader from './AiStoryReader.vue'

function mountStory(language: AppLanguage = 'en'): ReturnType<typeof mount> {
  return mount(AiStoryReader, { props: { story: aiStoryReply, language }, global: { plugins: [i18n] } })
}

describe('AiStoryReader', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the story, pinyin toggle, both translations and the full vocabulary list', async () => {
    const wrapper = mountStory()

    expect(wrapper.text()).toContain(aiStoryReply.title)
    expect(wrapper.get('[data-testid="ai-story-chinese"]').text()).toBe(aiStoryReply.chineseStory)
    expect(wrapper.get('[data-testid="ai-story-chinese"]').attributes('lang')).toBe('zh-CN')
    expect(wrapper.get('[data-testid="ai-story-translation-en"]').text()).toContain('take a bus to Melaka')
    expect(wrapper.get('[data-testid="ai-story-translation-ms"]').text()).toContain('menaiki bas ke Melaka')

    const pinyin = wrapper.get('[data-testid="ai-story-pinyin"]')
    expect(pinyin.text()).toBe(aiStoryReply.pinyin)
    expect(pinyin.attributes('style') ?? '').not.toContain('display: none')
    const toggle = wrapper.get('[data-testid="ai-story-pinyin-toggle"]')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-pressed')).toBe('false')
    expect(toggle.text()).toBe('Show pinyin')
    expect(wrapper.get('[data-testid="ai-story-pinyin"]').attributes('style')).toContain('display: none')

    const rows = wrapper.findAll('[data-testid="ai-story-vocabulary-item"]')
    expect(rows).toHaveLength(aiStoryReply.vocabularyList.length)
    expect(rows[0].text()).toContain('周末')
    expect(rows[0].text()).toContain('zhōumò')
    expect(rows[0].text()).toContain('weekend')
  })

  it('shows the Malay translation first and Malay meanings for a Malay interface', () => {
    i18n.global.locale.value = 'ms'
    const wrapper = mountStory('ms')

    const translations = wrapper.findAll('.ai-story-translation')
    expect(translations[0].attributes('lang')).toBe('ms')
    expect(wrapper.findAll('[data-testid="ai-story-vocabulary-item"]')[0].text()).toContain('hujung minggu')
    expect(wrapper.text()).toContain('Kosa kata utama')
  })

  it('lists every comprehension question and reveals the answer with feedback on the learner choice', async () => {
    const wrapper = mountStory()

    const questions = wrapper.findAll('[data-testid="ai-story-question"]')
    expect(questions).toHaveLength(aiStoryReply.comprehensionQuestions.length)
    expect(questions[0].findAll('[data-testid="ai-story-option"]')).toHaveLength(3)
    expect(wrapper.find('[data-testid="ai-story-answer"]').exists()).toBe(false)

    // "It is delicious" matches the AI answer "It is delicious." after normalisation.
    const option = questions[1].findAll('[data-testid="ai-story-option"]')[0]
    await option.trigger('click')
    expect(option.attributes('aria-pressed')).toBe('true')
    const reveal = questions[1].get('[data-testid="ai-story-reveal"]')
    await reveal.trigger('click')
    expect(reveal.attributes('aria-expanded')).toBe('true')
    expect(questions[1].get('[data-testid="ai-story-answer"]').text()).toBe('Answer: It is delicious.')
    expect(questions[1].text()).toContain('很好吃 (hěn hǎochī) means very delicious.')
    expect(questions[1].get('[data-testid="ai-story-pick-result"]').text()).toBe('Your choice matches the answer.')

    await questions[2].findAll('[data-testid="ai-story-option"]')[0].trigger('click')
    await questions[2].get('[data-testid="ai-story-reveal"]').trigger('click')
    expect(questions[2].get('[data-testid="ai-story-pick-result"]').text()).toBe(
      'Your choice does not match the answer.',
    )

    await reveal.trigger('click')
    expect(questions[1].find('[data-testid="ai-story-answer"]').exists()).toBe(false)
  })
})
