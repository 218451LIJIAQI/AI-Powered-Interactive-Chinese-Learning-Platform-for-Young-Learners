import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { stubSpeech } from '@/test/fakeSpeech'
import { speakChinese } from '@/utils/speech'

const api = vi.hoisted(() => ({ story: vi.fn(), report: vi.fn() }))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))

import AiGeneratorTab from './AiGeneratorTab.vue'

const STORY = {
  title: '下雨天',
  titleTranslation: 'A rainy day',
  status: 'SUCCESS',
  lines: [{ chinese: '今天下雨。', pinyin: 'Jīntiān xià yǔ.', translation: 'It is raining today.' }],
  vocabularyList: [{ chineseWord: '下雨', pinyin: 'xià yǔ', englishMeaning: 'to rain', bahasaMelayuMeaning: 'hujan' }],
  comprehensionQuestions: [
    { question: 'What is the weather like?', options: ['Sunny', 'Rainy'], answer: 'Rainy' },
    { question: 'When is it?', options: ['Today', 'Tomorrow'], answer: 'Today' },
  ],
}

function mountStory() {
  return mount(AiGeneratorTab, {
    props: { kind: 'story', blocked: null, aiNotice: 'AI can make mistakes.' },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
}

async function submitStory(wrapper: ReturnType<typeof mountStory>, topic = 'A rainy day') {
  await wrapper.get('.ai-maker-form input').setValue(topic)
  await wrapper.get('.ai-maker-form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  Object.values(api).forEach((fn) => fn.mockReset())
})

describe('Story generator', () => {
  it('shows the AI notice in the story’s head and marks the right answer next to a wrong pick', async () => {
    api.story.mockResolvedValue(STORY)
    const wrapper = mountStory()
    await submitStory(wrapper)
    expect(api.story).toHaveBeenCalledWith({ topic: 'A rainy day', languagePreference: 'en' })
    expect(wrapper.get('.ai-panel-head').text()).toContain('AI can make mistakes.')

    const check = wrapper.get('.ai-story-check')
    const radios = check.findAll('input[type="radio"]')
    await radios[0]!.setValue(true) // Sunny: wrong
    await radios[2]!.setValue(true) // Today: right
    await check.get('.ai-result-foot button').trigger('click')

    expect(check.findAll('.ai-option.is-incorrect').map((option) => option.text())).toEqual(['Your answer:Sunny'])
    expect(check.findAll('.ai-option.is-correct').map((option) => option.text())).toEqual([
      'Correct answer:Rainy',
      'Correct answer:Today',
    ])
    expect(check.get('.ai-result-foot p').text()).toBe('1 of 2 correct')
  })

  it('keeps the learner’s Pinyin and Translation choice for the next story', async () => {
    api.story.mockResolvedValue(STORY)
    const wrapper = mountStory()
    const toggles = () => wrapper.findAll<HTMLInputElement>('.ai-story-show input[type="checkbox"]')
    const shownLines = () => wrapper.findAll('.ai-story-lines .ai-line-pinyin').map((line) => line.text())

    await submitStory(wrapper)
    expect(shownLines()).toEqual(['Jīntiān xià yǔ.'])
    await toggles()[0]!.setValue(false)
    await toggles()[1]!.setValue(true)
    expect(shownLines()).toEqual(['It is raining today.'])

    await submitStory(wrapper)
    expect(api.story).toHaveBeenCalledTimes(2)
    expect(toggles().map((toggle) => toggle.element.checked)).toEqual([false, true])
    expect(shownLines()).toEqual(['It is raining today.'])
  })

  it('offers the ten story topics of the design', () => {
    const wrapper = mountStory()

    expect(wrapper.findAll('.filter-chip').map((chip) => chip.text())).toEqual([
      'Taking the LRT',
      'My family',
      'A rainy day',
      'At the pasar malam',
      'My weekend',
      'First day at work',
      'Lost my phone',
      'Birthday party',
      'At the clinic',
      'Hari Raya open house',
    ])
  })

  it('reports a generated story through the report dialog and marks it reported', async () => {
    api.story.mockResolvedValue(STORY)
    api.report.mockResolvedValue({ id: 9 })
    const wrapper = mountStory()
    await submitStory(wrapper)

    await wrapper.get('.ai-result-report').trigger('click')
    await wrapper.get('[data-testid="ai-report-form"]').trigger('submit')
    await flushPromises()

    expect(api.report).toHaveBeenCalledWith(
      expect.objectContaining({
        featureKey: 'STORY_GENERATOR',
        promptSnapshot: 'A rainy day',
      }),
    )
    expect(wrapper.emitted('reported')).toHaveLength(1)
    expect(wrapper.get('.ai-reported').text()).toContain('Reported')
  })
})

describe('Reading a generated story aloud', () => {
  let synthesis: ReturnType<typeof stubSpeech>
  const spoken = () => synthesis.speak.mock.calls.map(([line]) => line.text)

  async function generateStory(lines: string[]) {
    api.story.mockResolvedValue({
      ...STORY,
      lines: lines.map((chinese) => ({ chinese, pinyin: null, translation: null })),
    })
    const wrapper = mountStory()
    await submitStory(wrapper)
    return wrapper
  }

  beforeEach(() => {
    synthesis = stubSpeech()
  })

  it('goes on with the story after a key word is heard, without skipping lines, and Stop reading stops', async () => {
    const wrapper = await generateStory(['今天下雨。', '我带了伞。', '我们回家。'])
    const reading = () => wrapper.findAll('.ai-story-lines li').map((line) => line.classes('is-reading'))

    await wrapper.get('.ai-story-read').trigger('click')
    synthesis.utterance(0).onstart?.()
    await wrapper.get('.ai-key-word').trigger('click')
    synthesis.utterance(1).onstart?.()
    // The browser ends the line the key word cut off only after the word has started.
    synthesis.utterance(0).onend?.()
    await flushPromises()
    expect(spoken()).toEqual(['今天下雨。', '下雨'])
    expect(reading()).toEqual([true, false, false])

    synthesis.utterance(1).onend?.()
    await flushPromises()
    expect(spoken()).toEqual(['今天下雨。', '下雨', '我带了伞。'])
    expect(reading()).toEqual([false, true, false])

    await wrapper.get('.ai-story-read').trigger('click')
    synthesis.utterance(2).onerror?.()
    await flushPromises()
    expect(spoken()).toHaveLength(3)
    expect(reading()).toEqual([false, false, false])
    expect(wrapper.get('.ai-story-read').text()).toBe('Read aloud')
  })

  it('passes over a line with nothing to read aloud instead of stalling on it', async () => {
    // The server drops lines of only spaces; should one still come, the browser has nothing to read in it.
    const wrapper = await generateStory(['\u00a0', '今天下雨。', '\u202f', '我们回家。'])
    const reading = () => wrapper.findAll('.ai-story-lines li').map((line) => line.classes('is-reading'))

    await wrapper.get('.ai-story-read').trigger('click')
    expect(spoken()).toEqual(['今天下雨。'])
    expect(reading()).toEqual([false, true, false, false])

    synthesis.utterance(0).onend?.()
    await flushPromises()
    expect(spoken()).toEqual(['今天下雨。', '我们回家。'])
    expect(reading()).toEqual([false, false, false, true])

    synthesis.utterance(1).onend?.()
    await flushPromises()
    expect(reading()).toEqual([false, false, false, false])
    expect(wrapper.get('.ai-story-read').text()).toBe('Read aloud')
  })

  it('releases the story when a different reader takes the speaker', async () => {
    const wrapper = await generateStory(['今天下雨。', '我们回家。'])
    await wrapper.get('.ai-story-read').trigger('click')
    speakChinese('你好')
    await flushPromises()

    expect(spoken()).toEqual(['今天下雨。', '你好'])
    expect(wrapper.get('.ai-story-read').text()).toBe('Read aloud')
  })

  it('advances even when a line ends before the browser returns from speak', async () => {
    synthesis.speak.mockImplementationOnce((line) => line.onend?.())
    const wrapper = await generateStory(['今天下雨。', '我们回家。'])
    await wrapper.get('.ai-story-read').trigger('click')

    expect(spoken()).toEqual(['今天下雨。', '我们回家。'])
    expect(wrapper.get('.is-reading').text()).toContain('我们回家。')
  })
})
